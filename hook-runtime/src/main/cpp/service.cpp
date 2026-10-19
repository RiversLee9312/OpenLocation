#include <algorithm>
#include <android/binder_ibinder.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/sensor.h>
#include <android/sharedmem.h>
#include <array>
#include <cstring>
#include <dlfcn.h>
#include <jni.h>
#include <link.h>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>

// Public LSPosed native API. Native initialization only records the hooker.
using Hook = int (*)(void *, void *, void **);
using OnLoaded = void (*)(const char *, void *);
struct NativeAPIEntries {
  uint32_t version;
  Hook hook_func;
  int (*unhook_func)(void *);
};
static Hook hooker = nullptr;
static std::recursive_mutex mutex;
static bool active = false, delivering = false;
static std::set<int> targets;
static constexpr std::array<int, 8> types{1, 2, 4, 9, 10, 11, 18, 19};
static std::array<std::array<float, 5>, 8> values{};
static int64_t timestamp = 0;
static uint64_t steps = 0;
static std::map<int, int> handleTypes;
static bool metadataLoaded = false;
static int indexOf(int type) {
  auto it = std::find(types.begin(), types.end(), type);
  return it == types.end() ? -1 : static_cast<int>(it - types.begin());
}
static bool selected(int uid) { return active && targets.contains(uid); }
struct Binding {
  int type = 0;
  int64_t period = 20'000'000, last = 0;
  uint64_t steps = 0;
  int rate = 1, token = 0;
};
struct Queue {
  int uid = -1;
  std::map<int, Binding> bindings;
};
static std::map<void *, Queue> queues;

// AOSP hardware/sensors.h ABI, not an offset into a private service object.
// cutils/native_handle.h layout; read-only borrowed handle, never closed here.
struct NativeHandle {
  int version;
  int numFds;
  int numInts;
  int data[0];
};
struct DirectMemory {
  int type;
  int format;
  size_t size;
  const NativeHandle *handle;
};
struct DirectConfig {
  int rate;
};
struct Channel {
  int uid = -1;
  int hal = 0;
  void *memory = MAP_FAILED;
  AHardwareBuffer *buffer = nullptr;
  size_t size = 0, cursor = 0;
  uint32_t counter = 0;
  bool synthetic = false;
  std::map<int, Binding> bindings;
  ~Channel() {
    if (buffer) {
      AHardwareBuffer_unlock(buffer, nullptr);
      AHardwareBuffer_release(buffer);
    } else if (memory != MAP_FAILED)
      munmap(memory, size);
  }
};
static std::map<void *, std::unique_ptr<Channel>> channels;
static std::set<int> virtualHalChannels;
static int nextHal = 0x20000000;
static int (*realSend)(void *, const ASensorEvent *, size_t, ASensorEvent *,
                       const void *) = nullptr;
static int (*realEnable)(void *, int, bool, int64_t, int64_t, int) = nullptr;
static int (*realRate)(void *, int, int64_t) = nullptr;
static void (*realDestroy)(void *) = nullptr;
static void (*realCtor36)(void *, const void *, unsigned, int,
                          const DirectMemory *, int, const void *,
                          int) = nullptr;
static void (*realCtor34)(void *, const void *, unsigned, int,
                          const DirectMemory *, int, const void *) = nullptr;
static int (*realConfigure)(void *, int, const DirectConfig *) = nullptr;
static void (*realDirectDestroy)(void *) = nullptr;
static int (*realAidlRegister)(void *, const DirectMemory *, int *) = nullptr;
static int (*realHidlRegister)(void *, const DirectMemory *, int *) = nullptr;
static int (*realConfigureChannel)(void *, int, int) = nullptr;
static void (*realStopAll)(void *, bool) = nullptr;
using HalConfigure = int (*)(void *, int, int, const DirectConfig *);
static HalConfigure realAidlConfigure = nullptr, realHidlConfigure = nullptr;
static thread_local bool bypassHal = false;
static int physicalConfigure(void *owner, int handle, const DirectConfig *cfg) {
  const bool previous = bypassHal;
  bypassHal = true;
  const int result = realConfigure(owner, handle, cfg);
  bypassHal = previous;
  return result;
}

static ASensorEvent event(int handle, int type) {
  ASensorEvent e{};
  e.version = sizeof(e);
  e.sensor = handle;
  e.type = type;
  e.timestamp = timestamp;
  const int index = indexOf(type);
  if (index >= 0)
    memcpy(e.data, values[index].data(), sizeof(float) * 5);
  if (type == 19)
    e.u64.step_counter = steps;
  if (type == 1 || type == 2 || type == 4 || type == 9 || type == 10)
    e.vector.status = ASENSOR_STATUS_ACCURACY_HIGH;
  return e;
}
static bool due(Binding &b) {
  if (timestamp - b.last < b.period)
    return false;
  if ((b.type == 18 || b.type == 19) && b.last && b.steps == steps)
    return false;
  if (b.type == 18 && !b.last) {
    b.last = timestamp;
    b.steps = steps;
    return false;
  }
  b.last = timestamp;
  b.steps = steps;
  return true;
}
static int sendEvents(void *owner, const ASensorEvent *input, size_t count,
                      ASensorEvent *scratch, const void *flushMap) {
  std::lock_guard guard(mutex);
  auto it = queues.find(owner);
  if (it == queues.end() || !selected(it->second.uid))
    return realSend(owner, input, count, scratch, flushMap);
  if (!scratch) {
    std::vector<ASensorEvent> preserved;
    for (size_t i = 0; i < count; ++i)
      if (indexOf(input[i].type) < 0)
        preserved.push_back(input[i]);
    return preserved.empty() ? 0
                             : realSend(owner, preserved.data(),
                                        preserved.size(), nullptr, nullptr);
  }
  // Preserve original array indices: flush-map entries correspond to those
  // indices.
  std::vector<ASensorEvent> filtered(input, input + count);
  for (auto &e : filtered)
    if (indexOf(e.type) >= 0)
      e.sensor = -1;
  return realSend(owner, filtered.data(), count, scratch, flushMap);
}
static int enable(void *owner, int handle, bool enabled, int64_t period,
                  int64_t latency, int flags) {
  const int uid = static_cast<int>(AIBinder_getCallingUid());
  const int result = realEnable(owner, handle, enabled, period, latency, flags);
  if (result == 0) {
    std::lock_guard guard(mutex);
    auto &q = queues[owner];
    q.uid = uid;
    if (enabled)
      q.bindings[handle] =
          Binding{.type = handleTypes[handle],
                  .period = std::max<int64_t>(period, 5'000'000)};
    else
      q.bindings.erase(handle);
  }
  return result;
}
static int setRate(void *owner, int handle, int64_t period) {
  const int result = realRate(owner, handle, period);
  if (result == 0) {
    std::lock_guard guard(mutex);
    auto it = queues.find(owner);
    if (it != queues.end() && it->second.bindings.contains(handle))
      it->second.bindings[handle].period = std::max<int64_t>(period, 5'000'000);
  }
  return result;
}
static void destroy(void *owner) {
  {
    std::lock_guard guard(mutex);
    queues.erase(owner);
  }
  realDestroy(owner);
}
static std::unique_ptr<Channel> mapMemory(int uid, int hal,
                                          const DirectMemory *mem) {
  if (!mem || !mem->handle || mem->size < 104 || mem->size > 64 * 1024 * 1024)
    return nullptr;
  auto channel = std::make_unique<Channel>();
  channel->uid = uid;
  channel->hal = hal;
  channel->size = mem->size;
  if (mem->type == 1 && mem->handle->numFds >= 1) {
    if (ASharedMemory_getSize(mem->handle->data[0]) < mem->size)
      return nullptr;
    channel->memory = mmap(nullptr, mem->size, PROT_READ | PROT_WRITE,
                           MAP_SHARED, mem->handle->data[0], 0);
  } else if (mem->type == 2) {
    AHardwareBuffer_Desc desc{};
    desc.width = static_cast<uint32_t>(mem->size);
    desc.height = 1;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_BLOB;
    desc.usage = AHARDWAREBUFFER_USAGE_SENSOR_DIRECT_DATA |
                 AHARDWAREBUFFER_USAGE_CPU_READ_RARELY;
    // AOSP VNDK API, resolved in the platform process. Missing exports disable
    // this channel.
    using Import = int (*)(const AHardwareBuffer_Desc *, const NativeHandle *,
                           int32_t, AHardwareBuffer **);
    static auto import = reinterpret_cast<Import>(
        dlsym(dlopen("libnativewindow.so", RTLD_NOW | RTLD_LOCAL),
              "AHardwareBuffer_createFromHandle"));
    if (!import ||
        import(&desc, mem->handle, 3 /* CLONE */, &channel->buffer) != 0)
      return nullptr;
    void *mapped = nullptr;
    if (AHardwareBuffer_lock(channel->buffer,
                             AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr,
                             &mapped) != 0) {
      AHardwareBuffer_release(channel->buffer);
      channel->buffer = nullptr;
      return nullptr;
    }
    channel->memory = mapped;
  }
  if (channel->memory == MAP_FAILED || !channel->memory)
    return nullptr;
  return channel;
}
static void recordChannel(void *owner, unsigned uid, const DirectMemory *memory,
                          int hal) {
  std::lock_guard guard(mutex);
  auto channel = mapMemory(static_cast<int>(uid), hal, memory);
  if (channel) {
    channel->synthetic = selected(static_cast<int>(uid));
    channels[owner] = std::move(channel);
  }
}
static void ctor36(void *owner, const void *service, unsigned uid, int pid,
                   const DirectMemory *memory, int hal, const void *package,
                   int device) {
  realCtor36(owner, service, uid, pid, memory, hal, package, device);
  recordChannel(owner, uid, memory, hal);
}
static void ctor34(void *owner, const void *service, unsigned uid, int pid,
                   const DirectMemory *memory, int hal, const void *package) {
  realCtor34(owner, service, uid, pid, memory, hal, package);
  recordChannel(owner, uid, memory, hal);
}
static int configure(void *owner, int handle, const DirectConfig *cfg) {
  std::lock_guard guard(mutex);
  auto it = channels.find(owner);
  if (it == channels.end() || !cfg)
    return realConfigure(owner, handle, cfg);
  auto &channel = *it->second;
  const int type = handleTypes[handle];
  const bool synth = selected(channel.uid) && indexOf(type) >= 0;
  int result;
  if (synth) {
    if (!virtualHalChannels.contains(channel.hal)) {
      DirectConfig stop{0};
      physicalConfigure(owner, handle, &stop);
    }
    result = cfg->rate == 0 ? 0 : handle + 1;
  } else
    result = realConfigure(owner, handle, cfg);
  if (cfg->rate == 0 && result == 0)
    channel.bindings.erase(handle);
  else if (result > 0) {
    channel.bindings[handle] = Binding{.type = type,
                                       .period = cfg->rate == 1   ? 20'000'000
                                                 : cfg->rate == 2 ? 5'000'000
                                                                  : 1'250'000,
                                       .rate = cfg->rate,
                                       .token = result};
  }
  return result;
}
static void directDestroy(void *owner) {
  {
    std::lock_guard guard(mutex);
    channels.erase(owner);
  }
  realDirectDestroy(owner);
}
static int configureChannel(void *owner, int handle, int rate) {
  // The original checks UID activity, sensor privacy and sensor access before
  // capability flags.
  const int result = realConfigureChannel(owner, handle, rate);
  std::lock_guard guard(mutex);
  auto it = channels.find(owner);
  if (it == channels.end() || !selected(it->second->uid))
    return result;
  if (rate == 0 && result == 0) {
    if (handle == -1)
      it->second->bindings.clear();
    else
      it->second->bindings.erase(handle);
  }
  // This fingerprint's AOSP-compatible implementation returns INVALID_OPERATION
  // only for unsupported direct capabilities after those permission checks.
  // Emulate NORMAL only: higher rates require additional permission and
  // microphone-privacy checks later in AOSP.
  const int type = handleTypes[handle];
  if (result == -38 && rate == 0 && it->second->bindings.contains(handle)) {
    const DirectConfig cfg{0};
    return configure(owner, handle, &cfg);
  }
  if (result == -38 && rate == 1 && (type == 1 || type == 2 || type == 4)) {
    const DirectConfig cfg{rate};
    return configure(owner, handle, &cfg);
  }
  return result;
}
static void stopAll(void *owner, bool backup) {
  realStopAll(owner, backup);
  std::lock_guard guard(mutex);
  auto it = channels.find(owner);
  if (it != channels.end())
    it->second->bindings.clear();
}
static int halConfigure(HalConfigure original, void *owner, int handle,
                        int channelId, const DirectConfig *cfg) {
  if (bypassHal || !cfg)
    return original(owner, handle, channelId, cfg);
  std::lock_guard guard(mutex);
  auto found =
      std::find_if(channels.begin(), channels.end(), [&](const auto &entry) {
        return entry.second->hal == channelId;
      });
  if (found == channels.end())
    return original(owner, handle, channelId, cfg);
  auto &channel = *found->second;
  const int type = handleTypes[handle];
  const bool synthetic = selected(channel.uid) && indexOf(type) >= 0;
  const int result = synthetic ? (cfg->rate == 0 ? 0 : handle + 1)
                               : original(owner, handle, channelId, cfg);
  if (cfg->rate == 0 && result == 0)
    channel.bindings.erase(handle);
  else if (result > 0)
    channel.bindings[handle] = Binding{.type = type,
                                       .period = cfg->rate == 1   ? 20'000'000
                                                 : cfg->rate == 2 ? 5'000'000
                                                                  : 1'250'000,
                                       .rate = cfg->rate,
                                       .token = result};
  return result;
}
static int aidlConfigure(void *owner, int handle, int channel,
                         const DirectConfig *cfg) {
  return halConfigure(realAidlConfigure, owner, handle, channel, cfg);
}
static int hidlConfigure(void *owner, int handle, int channel,
                         const DirectConfig *cfg) {
  return halConfigure(realHidlConfigure, owner, handle, channel, cfg);
}
static int aidlRegister(void *owner, const DirectMemory *memory, int *handle) {
  const int result = realAidlRegister(owner, memory, handle);
  std::lock_guard guard(mutex);
  if (result != 0 && selected(static_cast<int>(AIBinder_getCallingUid()))) {
    *handle = nextHal++;
    virtualHalChannels.insert(*handle);
    return 0;
  }
  return result;
}
static int hidlRegister(void *owner, const DirectMemory *memory, int *handle) {
  const int result = realHidlRegister(owner, memory, handle);
  std::lock_guard guard(mutex);
  if (result != 0 && selected(static_cast<int>(AIBinder_getCallingUid()))) {
    *handle = nextHal++;
    virtualHalChannels.insert(*handle);
    return 0;
  }
  return result;
}
static uintptr_t base = 0;
static std::vector<std::pair<uintptr_t, uintptr_t>> executable;
static int findLibrary(dl_phdr_info *info, size_t, void *) {
  if (!strstr(info->dlpi_name, "/libsensorservice.so"))
    return 0;
  base = info->dlpi_addr;
  for (int i = 0; i < info->dlpi_phnum; ++i) {
    const auto &p = info->dlpi_phdr[i];
    if (p.p_type == PT_LOAD && (p.p_flags & PF_X))
      executable.emplace_back(p.p_vaddr, p.p_vaddr + p.p_memsz);
  }
  return 1;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_openlocation_hook_ServiceNative_install(JNIEnv *env, jclass,
                                                 jlongArray input, jint api) {
  if (getuid() != 1000 || !hooker || env->GetArrayLength(input) != 13 ||
      api < 34 || api > 36)
    return false;
  std::array<jlong, 13> offsets{};
  env->GetLongArrayRegion(input, 0, 13, offsets.data());
  dl_iterate_phdr(findLibrary, nullptr);
  if (!base)
    return false;
  for (auto value : offsets)
    if (!std::any_of(executable.begin(), executable.end(), [&](auto range) {
          return uintptr_t(value) >= range.first &&
                 uintptr_t(value) + 16 <= range.second;
        }))
      return false;
  const std::array<void *, 13> replacements{
      reinterpret_cast<void *>(sendEvents),
      reinterpret_cast<void *>(enable),
      reinterpret_cast<void *>(setRate),
      reinterpret_cast<void *>(destroy),
      api >= 36 ? reinterpret_cast<void *>(ctor36)
                : reinterpret_cast<void *>(ctor34),
      reinterpret_cast<void *>(configure),
      reinterpret_cast<void *>(directDestroy),
      reinterpret_cast<void *>(aidlRegister),
      reinterpret_cast<void *>(hidlRegister),
      reinterpret_cast<void *>(configureChannel),
      reinterpret_cast<void *>(stopAll),
      reinterpret_cast<void *>(aidlConfigure),
      reinterpret_cast<void *>(hidlConfigure)};
  const std::array<void **, 13> backups{
      reinterpret_cast<void **>(&realSend),
      reinterpret_cast<void **>(&realEnable),
      reinterpret_cast<void **>(&realRate),
      reinterpret_cast<void **>(&realDestroy),
      api >= 36 ? reinterpret_cast<void **>(&realCtor36)
                : reinterpret_cast<void **>(&realCtor34),
      reinterpret_cast<void **>(&realConfigure),
      reinterpret_cast<void **>(&realDirectDestroy),
      reinterpret_cast<void **>(&realAidlRegister),
      reinterpret_cast<void **>(&realHidlRegister),
      reinterpret_cast<void **>(&realConfigureChannel),
      reinterpret_cast<void **>(&realStopAll),
      reinterpret_cast<void **>(&realAidlConfigure),
      reinterpret_cast<void **>(&realHidlConfigure)};
  for (size_t i = 0; i < offsets.size(); ++i)
    if (hooker(reinterpret_cast<void *>(base + offsets[i]), replacements[i],
               backups[i]) != 0)
      return false;
  // Query metadata only after the service's Binder endpoint becomes available.
  return true;
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_openlocation_hook_ServiceNative_publish(
    JNIEnv *env, jclass, jboolean enabled, jboolean running, jintArray uids,
    jlong time, jlong count, jfloatArray data) {
  if (env->GetArrayLength(data) != 40)
    return 0;
  if (!metadataLoaded) {
    auto *manager = ASensorManager_getInstanceForPackage("android");
    ASensorList sensors = nullptr;
    const int length = ASensorManager_getSensorList(manager, &sensors);
    std::lock_guard metadataGuard(mutex);
    for (int i = 0; i < length; ++i)
      handleTypes[ASensor_getHandle(sensors[i])] = ASensor_getType(sensors[i]);
    metadataLoaded = length > 0;
  }
  std::vector<jint> uidList(env->GetArrayLength(uids));
  env->GetIntArrayRegion(uids, 0, static_cast<jsize>(uidList.size()),
                         uidList.data());
  std::lock_guard guard(mutex);
  active = enabled;
  delivering = running;
  targets = std::set<int>(uidList.begin(), uidList.end());
  timestamp = time;
  steps = static_cast<uint64_t>(count);
  env->GetFloatArrayRegion(data, 0, 40, values[0].data());
  for (auto &[owner, ptr] : channels) {
    auto &channel = *ptr;
    const bool synth = selected(channel.uid);
    if (synth != channel.synthetic) {
      for (auto &[handle, b] : channel.bindings) {
        if (!virtualHalChannels.contains(channel.hal)) {
          DirectConfig cfg{synth ? 0 : b.rate};
          physicalConfigure(owner, handle, &cfg);
        }
        b.last = 0;
      }
      channel.synthetic = synth;
    }
  }
  if (!active || !delivering)
    return 0;
  for (auto &[owner, q] : queues)
    if (selected(q.uid)) {
      std::vector<ASensorEvent> events;
      for (auto &[handle, b] : q.bindings) {
        if (!b.type)
          b.type = handleTypes[handle];
        if (indexOf(b.type) >= 0 && due(b))
          events.push_back(event(handle, b.type));
      }
      if (!events.empty())
        realSend(owner, events.data(), events.size(), nullptr, nullptr);
    }
  static_assert(sizeof(ASensorEvent) == 104);
  for (auto &[owner, ptr] : channels)
    if (selected(ptr->uid)) {
      (void)owner;
      for (auto &[handle, b] : ptr->bindings)
        if (indexOf(b.type) >= 0 && due(b)) {
          (void)handle;
          const auto e = event(b.token, b.type);
          auto *dest = static_cast<char *>(ptr->memory) + ptr->cursor * 104;
          memcpy(dest, &e, 12);
          memcpy(dest + 16, reinterpret_cast<const char *>(&e) + 16, 88);
          if (++ptr->counter == 0)
            ++ptr->counter;
          __atomic_store_n(reinterpret_cast<uint32_t *>(dest + 12),
                           ptr->counter, __ATOMIC_RELEASE);
          ptr->cursor = (ptr->cursor + 1) % (ptr->size / 104);
        }
    }
  return static_cast<jint>(queues.size());
}
static void onLoaded(const char *, void *) {}
extern "C" [[gnu::visibility("default")]] [[gnu::used]] OnLoaded
native_init(const NativeAPIEntries *api) {
  if (api && api->hook_func)
    hooker = api->hook_func;
  return onLoaded;
}
