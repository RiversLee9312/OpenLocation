---
name: openlocation-device-adaptation
description: Debug and adapt OpenLocation's system service backend to a new Android 14–16 device and firmware combination. Use for a new device profile, firmware update, missing location/GNSS/sensor channel, or native service symbol mismatch.
---

# OpenLocation device adaptation

Read the repository README's compatibility warning and `docs/architecture.md` before changing an adapter. Existing device profiles authorize only their exact model, codename and full fingerprint. A profile entry is evidence of validation, not a way to silence an error.

## Invariants

- Only `android` / `system_server` may install adapters. LSPosed scope is System Framework. Do not add the probe or another target to module scope. Do not patch target APKs, load OpenLocation into target processes or suppress target security checks.
- Retain Magisk and LSPosed as the runtime dependencies. Native code is mounted by the matching OpenLocation Magisk companion; do not disable SELinux or introduce global permissive rules.
- Resolve native symbols from the installed library's symbol tables / `.gnu_debugdata`, verify the exact ABI against that firmware, and reject missing symbols. Never copy absolute addresses or private field offsets from another build.
- Keep per-UID selection and original permission / AppOps / privacy / listener lifecycle checks. A broker stream with mixed selected and unselected WorkSource recipients must not be replaced wholesale.
- Use one elapsed clock, seed and scene revision across position, GNSS and motion. Preserve flush metadata, cancellation, monotonic timestamps and original data after stop.

## Establish the baseline

Use a connected device explicitly authorized for debugging. Find `adb` through the configured SDK or `PATH`; choose the intended serial when multiple devices exist. Collect only model, codename, API, full build fingerprint, security patch, native library SHA-256, page size and runtime versions. Do not collect credentials, account data or device identifiers. Never save an unlock code in scripts, profiles or logs.

Read `docs/device-adaptation.md` for the firmware-specific interfaces, source links and rollback procedure. Pull `libsensorservice.so` to an ignored work directory. Inspect both exported symbols and the XZ mini debug ELF. Missing symbols require a new verified adapter path, not approximate matching.

The controller APK and native ZIP must come from the same build. `tools/package-native.py` extracts the exact library and profile from that APK. Runtime SHA-256 validation rejects a stale companion.

## Validate before claiming support

1. Validate the Kotlin core and resource coverage with the repository's build and locale scripts.
2. Enable only System Framework in LSPosed, install the matching companion, and restart. Confirm device-profile checks pass and the backend reports each installed channel.
3. Run `tools/run-device-tests.py` with the independent foreground probe. Java sensors, NDK queue, both direct-memory formats, GPS/current/last/continuous, system providers, raw GNSS, status and NMEA need actual observations. Record failures as failures.
4. Test start, pause, stop, unregister, batching/flush, privacy, process death and target UID isolation. Compare consumer timestamps and values to the core at those timestamps.
5. Inspect target process maps and module logs to establish that targets do not load the module. Observe independent navigation and fitness clients in driving, walking and running modes. Use test accounts and retain non-personal evidence.
6. Exercise a deliberately mismatched model/fingerprint through the compatibility tests. Both UI warning and backend fail-closed behavior must hold. Verify ELF/APK alignment separately from hardware page-size testing.

Only after these pass, update the profile, device evidence and README support statement. Preserve exact firmware identifiers and test versions. Do not mark another Android release or a similar phone as supported by inference.

## Heading channels

Never infer a sensor type from its display name. A hardware sensor named “Rotation Vector” on the reference firmware actually reports deprecated type 3 (ORIENTATION); supplying only type 11 leaves that path physical. Inspect consumer registrations and the numeric type/handle with `dumpsys sensorservice`.

Cover every available orientation source at the service connection boundary: legacy orientation (3), rotation vector (11), game rotation vector (15), geomagnetic rotation vector (20), plus magnetic/gyro/accelerometer uncalibrated variants (14/16/35). Service-side fusion may still use physical HAL inputs internally, so replacing calibrated inputs for a selected UID does not automatically replace the outputs of those fusion sensors.

Derive all these outputs from the same body-to-ENU quaternion. Legacy angles are degrees; rotation vectors use x/y/z/w; uncalibrated events carry six floats, including three bias values. This ideal model reports zero bias. Keep `Contract.SUPPORTED_SENSORS`, native type order, payload stride, JNI array length and direct-event encoding synchronized. Do not overwrite a rotation quaternion with vector accuracy metadata.

Use the official [Android position-sensor reference](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position) to check event units and coordinate conventions, and the [FusedLocationProviderClient reference](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) for GMS request/cache semantics.

Run `HeadingChannelsTest` and the all-instance consumer test. Cross-check rotation matrices/azimuth using Android's independent SensorManager APIs, including both hardware and software instances. Check a fixed route with heading variation set to zero, then configured variation. When practical, rotate the physical phone while a selected consumer is moving: synthetic heading must remain tied to the route. Preserve physical orientation for an unselected UID. Record what was actually observed; an automated static-phone test alone does not establish a physical-rotation test.

## Location SDK consumers

Use the SDK as an independent consumer, not as an injection target. Keep adapters in system_server; never add application package-name branches or borrow another application's developer key.

- **GMS fused location:** invoke the actual Play services SDK for high-accuracy continuous updates, a current request with zero maximum cache age, and cached last location. Check timestamps and geographic distance against the scene. The probe needs the documented Play services manifest protocol metadata even when the SDK is an instrumentation-only dependency. `GmsLocationTest` exercises SDK 21.3.0; record the SDK and installed service version separately.
- **Tencent and AMap:** retain WGS84 in Android Location transports. These clients may convert to GCJ-02 themselves. Do not globally feed GCJ-02 into Android APIs to repair a map offset. Inspect GPS/passive/fused registrations, GNSS status/raw requests, cache age, and the actual moving location marker. SDK network/server fixes derived from Wi-Fi/cellular/IP are a separate path; GPS success does not verify them.
- **Baidu:** distinguish WGS84 input from GCJ-02 and BD-09 rendering. Check the moving marker, requested provider, cache freshness and reacquisition; a roughly city-scale or hundreds-of-metres error can be a coordinate conversion issue. Do not add a second conversion in the framework.

For each vendor, test only a selected consumer, selection removal/stop, and an unselected consumer. Broker aggregate WorkSource recipients must all opt in before replacing a shared stream. Shared SDK caches and mixed recipients need their own evidence; never weaken recipient isolation merely to obtain a successful location result. Keep failure-closed behavior documented.

A map camera jumped to a searched coordinate is not proof of location compatibility. Use its live location control, watch movement over time, and corroborate with system subscription/delivery counts. Record tested consumer and SDK versions explicitly; a map application test does not certify every release of that vendor's standalone SDK. Proprietary SDK tests requiring a developer key must use a key supplied for the probe's own package/signature, kept outside the repository.

Store public evidence without account identifiers, physical coordinates, private tracks or screenshots of real location. Add a vendor to `verifiedChannels` only after the claimed scope passes; include its version, scope and evidence under `consumerEvidence`. Keep unsupported paths explicit.

## Device UI verification

On multi-display firmware, inspect the active/focused display and use explicit `adb shell input -d <displayId>` for taps and swipes. Otherwise a gesture sent to a secondary display can look like a broken map editor. Keep UI-inspection instrumentation separate from consumer-test instrumentation: starting a second instrumentation run for the same package interrupts the first.

Exercise route editing with an existing route: select and delete a middle point, undo/redo, move a point, insert after a selected point, search a place, save/reopen, and discard without changing the active route. Camera-center confirmation must use live camera movement, including fling animation, not a stale idle callback. Inspect RTL and large fonts. Use the configured Android geocoding provider; do not silently introduce an unauthorized public geocoding endpoint.

## Failure and recovery

Keep an ADB connection and the original state available during native work. If the backend cannot load, leave that channel disabled and inspect its precise error. For a system instability, disable only the OpenLocation Magisk module and the OpenLocation LSPosed module, then restart. Do not remove unrelated modules, clear target app data, factory reset, or expand permissions as a troubleshooting shortcut.

Produce a small adapter change plus reproducible evidence. Update `docs/device-adaptation.md` when the firmware changes a signature, return-code interpretation, service placement or lifecycle contract.
