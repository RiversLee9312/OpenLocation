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

## Failure and recovery

Keep an ADB connection and the original state available during native work. If the backend cannot load, leave that channel disabled and inspect its precise error. For a system instability, disable only the OpenLocation Magisk module and the OpenLocation LSPosed module, then restart. Do not remove unrelated modules, clear target app data, factory reset, or expand permissions as a troubleshooting shortcut.

Produce a small adapter change plus reproducible evidence. Update `docs/device-adaptation.md` when the firmware changes a signature, return-code interpretation, service placement or lifecycle contract.
