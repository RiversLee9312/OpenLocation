---
name: openlocation-device-adaptation
description: Debug and adapt OpenLocation's system service backend to a new Android device and firmware combination. Use for a new device profile, firmware update, root/hook-framework migration, missing location/GNSS/sensor channel, or native service symbol mismatch.
---

# OpenLocation device adaptation

Read the repository README's compatibility warning and `docs/architecture.md` before changing an adapter. Existing device profiles authorize only their exact model, codename and full fingerprint. A profile entry is evidence of validation, not a way to silence an error.

## Invariants

- Only `android` / `system_server` may install adapters. LSPosed scope is System Framework. Do not add the probe or another target to module scope. Do not patch target APKs, load OpenLocation into target processes or suppress target security checks.
- Use the user's chosen root and hook framework, and verify its native loading and systemless mounting contracts. Keep the matching native companion and SELinux Enforcing; do not introduce global permissive rules. When a framework stages native libraries for an isolated namespace, resolve the path through its class loader and validate it against the APK hash. Use a mounted-library fallback only where that framework and firmware allow it.
- Resolve native symbols from the installed library's symbol tables / `.gnu_debugdata`, verify the exact ABI against that firmware, and reject missing symbols. Never copy absolute addresses or private field offsets from another build.
- Keep per-UID selection and original permission / AppOps / privacy / listener lifecycle checks. A broker stream with mixed selected and unselected WorkSource recipients must not be replaced wholesale.
- Use one elapsed clock, seed and scene revision across position, GNSS and motion. Preserve flush metadata, cancellation, monotonic timestamps and original data after stop.

## Establish the baseline

Use a connected device explicitly authorized for debugging. Find `adb` through the configured SDK or `PATH`; choose the intended serial when multiple devices exist. Collect only model, codename, API, full build fingerprint, OS incremental, security patch, native library SHA-256, page size and runtime versions. A ROM can retain an older vendor fingerprint across Android upgrades, so check API and incremental separately. Do not collect credentials, account data or device identifiers. Never save an unlock code in scripts, profiles or logs.

Read `docs/device-adaptation.md` for the firmware-specific interfaces, source links and rollback procedure. Pull `libsensorservice.so` to an ignored work directory. Inspect both exported symbols and the XZ mini debug ELF. Missing symbols require a new verified adapter path, not approximate matching.

Keep device names, exact firmware identifiers, binary hashes and test outcomes in device profiles and verification documents. This skill describes the reusable adaptation method; a previously validated device is evidence for that profile, not a default assumption for the next one.

The controller APK and native ZIP must come from the same build. `tools/package-native.py --profile <exact-profile-id>` extracts the exact library and selected profile from that APK. Runtime SHA-256 validation rejects a stale companion or staged library. Check real GPS acquisition separately from a short IMU sample window; a missing physical fix is an unverified/failed GPS isolation result, not proof of synthetic leakage or a passing test.

## Validate before claiming support

1. Validate the Kotlin core and resource coverage with the repository's build and locale scripts.
2. Enable only System Framework in LSPosed, install the matching companion, and restart. Confirm device-profile checks pass and the backend reports each installed channel.
3. Run `tools/run-device-tests.py` with the independent foreground probe. Java sensors, NDK queue, both direct-memory formats, GPS/current/last/continuous, system providers, raw GNSS, status and NMEA need actual observations. Record failures as failures.
4. Test start, pause, stop, unregister, batching/flush, privacy, process death and target UID isolation. Compare consumer timestamps and values to the core at those timestamps.
5. Inspect target process maps and module logs to establish that targets do not load the module. For navigation or fitness consumers, follow [App adaptation](../openlocation-app-adaptation/SKILL.md). Keep firmware/channel evidence separate from local consumer observations.
6. Exercise a deliberately mismatched model/fingerprint through the compatibility tests. Both UI warning and backend fail-closed behavior must hold. Verify ELF/APK alignment separately from hardware page-size testing.

Only after these pass, update the profile, device evidence and README support statement. Preserve exact firmware identifiers and test versions. Do not mark another Android release or a similar phone as supported by inference.

## Heading channels

Never infer a sensor type from its display name: a rotation-related label may refer to deprecated type 3 (ORIENTATION), so supplying only type 11 can leave that path physical. Inspect consumer registrations and the numeric type/handle with `dumpsys sensorservice`.

Cover every available orientation source at the service connection boundary: legacy orientation (3), rotation vector (11), game rotation vector (15), geomagnetic rotation vector (20), plus magnetic/gyro/accelerometer uncalibrated variants (14/16/35). Service-side fusion may still use physical HAL inputs internally, so replacing calibrated inputs for a selected UID does not automatically replace the outputs of those fusion sensors.

Derive all these outputs from the same body-to-ENU quaternion. Legacy angles are degrees; rotation vectors use x/y/z/w; uncalibrated events carry six floats, including three bias values. This ideal model reports zero bias. Keep `Contract.SUPPORTED_SENSORS`, native type order, payload stride, JNI array length and direct-event encoding synchronized. Do not overwrite a rotation quaternion with vector accuracy metadata.

Use the official [Android position-sensor reference](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position) to check event units and coordinate conventions, and the [FusedLocationProviderClient reference](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) for GMS request/cache semantics.

Run `HeadingChannelsTest` and the all-instance consumer test. Cross-check rotation matrices/azimuth using Android's independent SensorManager APIs, including both hardware and software instances. Check a fixed route with heading variation set to zero, then configured variation. When practical, rotate the physical phone while a selected consumer is moving: synthetic heading must remain tied to the route. Preserve physical orientation for an unselected UID. Record what was actually observed; an automated static-phone test alone does not establish a physical-rotation test.

## Consumer coverage

Use [App adaptation](../openlocation-app-adaptation/SKILL.md) when a service channel passes but an application still behaves differently, or when validating a new consumer. Device profiles describe exact firmware compatibility and established channel coverage; do not introduce application allowlists or package-specific profile fields. Follow the repository's existing verification structure and preserve the scope of historical evidence.

## Device UI verification

On multi-display firmware, inspect the active/focused display and use explicit `adb shell input -d <displayId>` for taps and swipes. Otherwise a gesture sent to a secondary display can look like a broken map editor. Keep UI-inspection instrumentation separate from consumer-test instrumentation: starting a second instrumentation run for the same package interrupts the first.

Exercise route editing with an existing route: select and delete a middle point, undo/redo, move a point, insert after a selected point, search a place, save/reopen, and discard without changing the active route. Camera-center confirmation must use live camera movement, including fling animation, not a stale idle callback. Inspect RTL and large fonts. Use the configured Android geocoding provider; do not silently introduce an unauthorized public geocoding endpoint.

## Failure and recovery

Keep an ADB connection and the original state available during native work. If the backend cannot load, leave that channel disabled and inspect its precise error. For a system instability, disable only OpenLocation's native companion and hook-framework module, then restart. Do not remove unrelated modules, clear target app data, factory reset, or expand permissions as a troubleshooting shortcut.

Produce a small adapter change plus reproducible evidence. Update `docs/device-adaptation.md` when the firmware changes a signature, return-code interpretation, service placement or lifecycle contract.
