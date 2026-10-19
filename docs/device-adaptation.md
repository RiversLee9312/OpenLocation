# Device adaptation

OpenLocation is a privileged system service test tool. A binary adapted to a different firmware can break system_server. Use only the release whose model, codename and complete system fingerprint match the phone. Android version and marketing name alone are insufficient.

The APK records its profiles in `assets/device_profiles.json`. Both the controller and the LSPosed backend compare these values before starting. A mismatch displays a warning and disables simulation. The native companion installer also checks the same profile. There is no “ignore and run” switch.

## Current development baseline

The connected OnePlus 11 reports model `PHB110`, codename `OP591BL1`, API 36. Its complete fingerprint is recorded in the checked-in profile; do not shorten or normalize it. The sensor service binary hash is recorded there as well. This profile is under development until the service-side test evidence is complete. Android 14 and 15 remain adaptation targets, not tested devices.

## Service boundaries

Location uses `LocationProviderManager` registrations before permission filtering and transport. The scheduler runs under the manager's multiplexer lock and delegates to its original `acceptLocationChange` / `executeOperation` path. It preserves coarse filtering, AppOps, expiration, maximum updates, listener transport and wakelocks. Broker WorkSource handling must not leak selected data to unselected recipients.

GNSS registration is observed at `LocationManagerService`. Remote callback proxies are intercepted inside system_server only. Status, NMEA and raw measurements share an SGP4 orbital epoch. Fine-location permission and AppOps remain required. A foreground-only grant does not authorize background probe callbacks.

On this firmware, native `SensorService` lives in system_server. The AArch64 ELF's `.gnu_debugdata` contains private connection symbols. `ElfSymbols` parses the installed ELF and XZ image, and native installation verifies resolved addresses belong to executable load segments. Symbol names and signatures, not stored addresses, select the functions.

Queue interception observes successful enable/rate calls, substitutes data at `SensorEventConnection::sendEvents`, and preserves flush metadata. The original send path retains UID activity and sensor-privacy checks. Direct channels map the service's shared-memory handles and use the documented 104-byte event format with the counter published last.

Firmware-specific return-code handling for direct capability emulation must be revalidated against `SensorDirectConnection::configureChannel`: this baseline checks access before returning `INVALID_OPERATION` for unsupported direct capabilities. Do not reuse that interpretation on an unknown implementation. Permission failures must never become successful synthetic registrations.

## Native loading

Android denies executable mappings from this APK in system_server. Magisk supplies `/system/lib64/libopenlocation.so` through a removable module mount. LSPosed supplies its documented native hook API. The APK compares the mounted library's SHA-256 to its packaged copy before loading it. No SELinux policy file or separate injection framework is used.

Package with `python tools/package-native.py <controller.apk> <native.zip>`. Install the APK and its matching ZIP, enable only System Framework in LSPosed, then restart. Installing just one component can leave the sensor backend unavailable.

## Rollback

Stop the scene first. In LSPosed, disable OpenLocation. In Magisk, disable the module with id `openlocation`, then restart. With working authorized root ADB, the equivalent module-disable action is `su -c 'touch /data/adb/modules/openlocation/disable'`, followed by a restart. Remove only this module when uninstalling. If Android cannot boot, use the device's existing recovery procedure to disable this specific module; do not erase user data.

## Primary references

- [AOSP LocationProviderManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/location/provider/LocationProviderManager.java)
- [AOSP SensorEventConnection](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/sensorservice/SensorEventConnection.cpp)
- [AOSP SensorDirectConnection](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/sensorservice/SensorDirectConnection.cpp)
- [AOSP direct memory ABI](https://android.googlesource.com/platform/hardware/libhardware/+/refs/heads/android16-release/include_all/hardware/sensors.h)
- [LSPosed native hook API](https://github.com/LSPosed/LSPosed/wiki/Native-Hook)
