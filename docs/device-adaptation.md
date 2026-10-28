# Device adaptation

OpenLocation is a privileged system service test tool. A binary adapted to a different firmware can break system_server. Use only the release whose model, codename and complete system fingerprint match the phone. Android version and marketing name alone are insufficient.

The APK records its profiles in `assets/device_profiles.json`. Both the controller and the LSPosed backend compare these values before starting. A mismatch displays a warning and disables simulation. The native companion installer also checks the same profile. There is no “ignore and run” switch.

## Current development baseline

Android 17: official LOS `24.0-20261023-NIGHTLY-salami`, API 37, incremental `c4db0ea4ab`, security patch `2026-10-01`, kernel `5.15.220-ga681efd8f0bf`, 4096-byte pages. The full vendor fingerprint is unchanged from Android 16; API, incremental and SensorService hash therefore remain independent required checks. The installed `libsensorservice.so` SHA-256 is `f409f837ddf3d406cc0f2c755350173ac7f43e08b33f4c974e704a3346a23d30`. All 13 required mangled symbols were found in its XZ mini-debug ELF. The direct-connection constructor retains the API 36 extra integer argument. No addresses or private field offsets were copied into the profile.

The exact ROM manifest pins `android_frameworks_native` to `46018f54b42489de960c62c3db9de131abce43ea` and `android_frameworks_base` to `653be4a9d2b0c727136694c789975e27fa7cdcef`. SensorDirectConnection still checks access before the unsupported-capability `INVALID_OPERATION` result; actual queue and both direct-memory tests passed. Installed services/framework DEX method tables were checked against the Java adapters. See `status.md` for the limits of physical and proprietary-client validation.

KernelSU 3.3.1 with meta-overlayfs 1.3.1 mounts the matching companion. Vector 2.2 stages module APK native libraries in its permitted class-loader directory; use the resolved file only after checking its SHA-256 against this APK. Directly loading `/system/lib64/libopenlocation.so` in Vector failed with an isolated-namespace error on the test phone. The fallback for older LSPosed is retained. SELinux stayed Enforcing throughout.

The older Android 16 profile records model `PHB110`, codename `OP591BL1`, API 36 and its own SensorService hash. Its historical evidence does not certify the Android 17 vendor clients. Android 14 and 15 remain adaptation targets, not tested devices.

## Service boundaries

Location uses `LocationProviderManager` registrations before permission filtering and transport. The scheduler runs under the manager's multiplexer lock and delegates to its original `acceptLocationChange` / `executeOperation` path. It preserves coarse filtering, AppOps, expiration, maximum updates, listener transport and wakelocks. Broker WorkSource handling must not leak selected data to unselected recipients.

GNSS registration is observed at `LocationManagerService`. Remote callback proxies are intercepted inside system_server only. Status, NMEA and raw measurements share an SGP4 orbital epoch. Fine-location permission and AppOps remain required. A foreground-only grant does not authorize background probe callbacks.

On this firmware, native `SensorService` lives in system_server. The AArch64 ELF's `.gnu_debugdata` contains private connection symbols. `ElfSymbols` parses the installed ELF and XZ image, and native installation verifies resolved addresses belong to executable load segments. Symbol names and signatures, not stored addresses, select the functions.

Queue interception observes successful enable/rate calls, substitutes data at `SensorEventConnection::sendEvents`, and preserves flush metadata. The original send path retains UID activity and sensor-privacy checks. Direct channels map the service's shared-memory handles and use the documented 104-byte event format with the counter published last.

Firmware-specific return-code handling for direct capability emulation must be revalidated against `SensorDirectConnection::configureChannel`: this baseline checks access before returning `INVALID_OPERATION` for unsupported direct capabilities. Do not reuse that interpretation on an unknown implementation. Permission failures must never become successful synthetic registrations.

## Native loading

Android denies executable mappings from this APK in system_server. Magisk supplies `/system/lib64/libopenlocation.so` through a removable module mount. LSPosed supplies its documented native hook API. The APK compares the mounted library's SHA-256 to its packaged copy before loading it. No SELinux policy file or separate injection framework is used.

Package with `python tools/package-native.py <controller.apk> <native.zip> --profile <exact-profile-id>`. Install the APK and its matching ZIP through Magisk/KernelSU, enable only System Framework in LSPosed/Vector, then restart. Installing just one component can leave the sensor backend unavailable.

## Rollback

Stop the scene first. In LSPosed, disable OpenLocation. In Magisk, disable the module with id `openlocation`, then restart. With working authorized root ADB, the equivalent module-disable action is `su -c 'touch /data/adb/modules/openlocation/disable'`, followed by a restart. Remove only this module when uninstalling. If Android cannot boot, use the device's existing recovery procedure to disable this specific module; do not erase user data.

## Primary references

- [AOSP LocationProviderManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/location/provider/LocationProviderManager.java)
- [AOSP SensorEventConnection](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/sensorservice/SensorEventConnection.cpp)
- [AOSP SensorDirectConnection](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/sensorservice/SensorDirectConnection.cpp)
- [AOSP direct memory ABI](https://android.googlesource.com/platform/hardware/libhardware/+/refs/heads/android16-release/include_all/hardware/sensors.h)
- [LSPosed native hook API](https://github.com/LSPosed/LSPosed/wiki/Native-Hook)
