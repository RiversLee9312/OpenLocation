#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/sensor.h>
#include <android/sharedmem.h>
#include <chrono>
#include <cstring>
#include <jni.h>
#include <sys/mman.h>
#include <thread>
#include <unistd.h>
#include <vector>

static jdoubleArray result(JNIEnv *env, const std::vector<double> &data) {
  auto array = env->NewDoubleArray(static_cast<jsize>(data.size()));
  env->SetDoubleArrayRegion(array, 0, static_cast<jsize>(data.size()),
                            data.data());
  return array;
}
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_openlocation_probe_NativeProbe_queue(JNIEnv *env, jobject,
                                              jint durationMs) {
  std::vector<double> samples{0, 0, 0};
  auto *manager =
      ASensorManager_getInstanceForPackage("dev.openlocation.probe");
  auto *sensor =
      ASensorManager_getDefaultSensor(manager, ASENSOR_TYPE_ACCELEROMETER);
  if (!sensor) {
    samples[1] = -1;
    return result(env, samples);
  }
  auto *looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
  auto *queue =
      ASensorManager_createEventQueue(manager, looper, 1, nullptr, nullptr);
  if (!queue) {
    samples[1] = -2;
    return result(env, samples);
  }
  ASensorEventQueue_registerSensor(queue, sensor, 20000, 0);
  auto end =
      std::chrono::steady_clock::now() + std::chrono::milliseconds(durationMs);
  while (std::chrono::steady_clock::now() < end) {
    int fd = 0, events = 0;
    void *data = nullptr;
    if (ALooper_pollOnce(50, &fd, &events, &data) >= 0) {
      ASensorEvent values[32];
      ssize_t count = ASensorEventQueue_getEvents(queue, values, 32);
      for (ssize_t i = 0; i < count; i++)
        if (values[i].type == ASENSOR_TYPE_ACCELEROMETER) {
          samples.push_back(static_cast<double>(values[i].timestamp));
          for (int j = 0; j < 3; j++)
            samples.push_back(values[i].data[j]);
          samples[0]++;
        }
    }
  }
  ASensorEventQueue_disableSensor(queue, sensor);
  ASensorManager_destroyEventQueue(manager, queue);
  return result(env, samples);
}
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_openlocation_probe_NativeProbe_direct(JNIEnv *env, jobject,
                                               jboolean hardware) {
  constexpr size_t size = 104 * 128;
  auto *manager =
      ASensorManager_getInstanceForPackage("dev.openlocation.probe");
  auto *sensor =
      ASensorManager_getDefaultSensor(manager, ASENSOR_TYPE_ACCELEROMETER);
  std::vector<double> samples{0, 0, 0};
  if (!sensor)
    return result(env, samples);
  int fd = -1, channel = 0;
  void *memory = nullptr;
  AHardwareBuffer *buffer = nullptr;
  if (hardware) {
    AHardwareBuffer_Desc desc{};
    desc.width = size;
    desc.height = 1;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_BLOB;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN |
                 AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
                 AHARDWAREBUFFER_USAGE_SENSOR_DIRECT_DATA;
    if (AHardwareBuffer_allocate(&desc, &buffer) != 0) {
      samples[0] = -1;
      return result(env, samples);
    }
    channel =
        ASensorManager_createHardwareBufferDirectChannel(manager, buffer, size);
    if (channel > 0)
      AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1,
                           nullptr, &memory);
  } else {
    fd = ASharedMemory_create("OpenLocation-consumer", size);
    if (fd < 0)
      return result(env, samples);
    memory = mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0);
    channel = ASensorManager_createSharedMemoryDirectChannel(manager, fd, size);
  }
  if (channel > 0 && memory && memory != MAP_FAILED) {
    const int token = ASensorManager_configureDirectReport(
        manager, sensor, channel, ASENSOR_DIRECT_RATE_NORMAL);
    samples[1] = token;
    std::this_thread::sleep_for(std::chrono::milliseconds(900));
    ASensorManager_configureDirectReport(manager, sensor, channel,
                                         ASENSOR_DIRECT_RATE_STOP);
    std::vector<uint32_t> counters;
    for (size_t i = 0; i < 128; i++) {
      const char *slot = static_cast<const char *>(memory) + i * 104;
      uint32_t counter = __atomic_load_n(
          reinterpret_cast<const uint32_t *>(slot + 12), __ATOMIC_ACQUIRE);
      counters.push_back(counter);
      if (counter == 0)
        continue;
      ASensorEvent event{};
      memcpy(&event, slot, sizeof(event));
      if (event.sensor != token || event.version != 104)
        continue;
      samples.push_back(static_cast<double>(event.timestamp));
      for (int j = 0; j < 3; j++)
        samples.push_back(event.data[j]);
      samples[0]++;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(180));
    samples[2] = 1;
    for (size_t i = 0; i < 128; i++)
      if (counters[i] !=
          __atomic_load_n(reinterpret_cast<const uint32_t *>(
                              static_cast<const char *>(memory) + i * 104 + 12),
                          __ATOMIC_ACQUIRE))
        samples[2] = 0;
    ASensorManager_destroyDirectChannel(manager, channel);
  }
  if (buffer) {
    if (memory)
      AHardwareBuffer_unlock(buffer, nullptr);
    AHardwareBuffer_release(buffer);
  } else {
    if (memory && memory != MAP_FAILED)
      munmap(memory, size);
    if (fd >= 0)
      close(fd);
  }
  return result(env, samples);
}
