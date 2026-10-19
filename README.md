# OpenLocation

Kotlin + Jetpack Compose application for reproducible location and motion tests on controlled Android devices. Data is supplied in system services; target applications are not patched or hooked.

> **必须使用与设备型号及完整系统指纹匹配的适配版本。系统升级后须重新适配验证。错误的系统级 hook 可能导致系统无法正常启动。安装包会记录适配型号、设备代号和完整指纹；不匹配时警告并停用模拟，不提供强制运行开关。**
>
> **Use only the build adapted to your exact device and complete system fingerprint. An OS update requires revalidation. Incorrect system hooks can prevent Android from booting. Mismatched builds warn and disable simulation.**

The reference profile is OnePlus 11 / PHB110 / OP591BL1 / API 36, pinned to the full fingerprint and SensorService SHA-256 in [device_profiles.json](hook-runtime/src/main/assets/device_profiles.json). This is not general Android 14–16 support. Check [verification status](docs/status.md) and [device adaptation](docs/device-adaptation.md) before installation.

## Build

Requirements: JDK 21, Android SDK 36, network access to Google Maven / Maven Central / Xposed API Maven. Gradle 8.13, AGP 8.13.2 and Kotlin 2.3.0 are pinned. Set `JAVA_HOME` and create an untracked `local.properties` containing `sdk.dir=...`.

```powershell
.\gradlew.bat :simulation-core:test :app:assembleDebug :probe-app:assembleDebug :probe-app:assembleDebugAndroidTest
```

The APKs are produced in `app/build/outputs/apk/debug/` and `probe-app/build/outputs/apk/debug/`. Minimum Android 14 (34), target Android 16 (36); first supported ABI arm64-v8a. Debug packages share the standard local debug signing identity, required by the signature-protected control provider. Production builds must sign both APKs with the same private release key.

## Run

1. Install both APKs on the reference device.
2. Install the matching OpenLocation native Magisk ZIP. In LSPosed, enable **OpenLocation** and select only **System Framework**. Restart the device. Do not select target apps in LSPosed.
3. Read the first-use notice. Select target apps under Diagnostics, then choose movement parameters and a route. Start the scene.
4. Open a selected app or the independent probe. Grant that consumer its normal location and activity permissions.
5. Stop the scene to restore real sensor subscriptions. The controller notification also provides Stop. The module does not alter mock-location flags, root visibility or application detection logic.

The native companion is required for SensorService channels on the reference firmware. It mounts the native library for system_server to execute. The APK rejects a companion with a different SHA-256. No global SELinux changes or additional injection framework are used. Both packages must come from the same build.

## Adapt a device

Use the [OpenLocation device adaptation skill](skills/openlocation-device-adaptation/SKILL.md) with your coding agent. The skill covers firmware identification, service signatures, native symbol resolution, channel verification, UID isolation and rollback. A new profile must be backed by device tests; adding a model/fingerprint entry alone does not constitute adaptation.

## Features

- One deterministic 10 ms dynamics timeline for WGS84 routes, speed, attitude, ideal IMU, gait and step events.
- Still, walk, run, bicycle and car modes; hand, pocket, backpack and mount placements.
- Rounded routes with curvature speed limiting, acceleration/braking, configurable speed plans in the scene format.
- GPX import/export, map point editing, coordinate entry and bounded offline route evaluation.
- Private physical GPS recording, timed GPX export and recovery of interrupted tracks.
- Unified GPX library for imported/recorded/saved tracks: search, preview, rename, export, delete and use as a simulation route. Imported originals retain their metadata unchanged.
- Per-mode speed, stride, arm swing and heading variation; bounded route deviations.
- Location observation environments: open, urban, obstructed, tunnel and reacquisition. IMU truth continues during loss of GPS.
- Service-side location and GNSS transports, plus eight sensor types delivered through normal application APIs. Independent consumer tests cover Java and native queues and direct channels; see current evidence for verified channels.
- Probe diagnostics per sensor instance, callback timestamps, frequency, gaps and callback delay. NDJSON recording preserves copied arrays and hardware metadata.
- Material 3 Expressive theme and shape-morphing primary controls, dynamic wallpaper colors, light/dark mode, adaptive launcher icon with a monochrome layer.
- Android string resources, plurals, positional format parameters and RTL-aware layouts form the localization boundary.
- English, Simplified Chinese and 20 other languages; first-use notice and readable open source licenses.

See [architecture](docs/architecture.md), [testing](docs/testing.md), [design](docs/design.md), [internationalization](docs/i18n.md) and [development status](docs/status.md) for exact scope and evidence. GNSS measurements are generated from orbital data and configurable signal noise. This is a software observation and motion model, not a radio-frequency simulator.

SDKs deriving server-side Wi-Fi/cellular location can bypass Android location transports. Mixed brokers fail closed to protect unselected recipients. Installed applications with ordinary app UIDs can be selected; controller/Android/system service UIDs are excluded. This cannot guarantee every proprietary SDK. No test-application package branches are present.

## Release and license

Apache-2.0; notices are in [NOTICE](NOTICE), `third-party/` and Settings → Open source licenses. Run `python tools/create-release-key.py` once and securely back up `.secrets/`. Sign controller and probe with that same private identity; never commit credentials. Build `:app:assembleRelease :probe-app:assembleRelease`, then run `python tools/package-native.py <controller.apk> <output.zip>` and `python tools/verify-apk.py <apk>` for both APKs.
