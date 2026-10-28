---
name: openlocation-app-adaptation
description: Diagnose and validate OpenLocation compatibility with Android navigation, location and fitness apps through system-service channels. Use when a consumer receives stale, missing or inconsistent location, heading or motion data, or when testing a new app or app version.
---

# OpenLocation App adaptation

Read `README.md`, `docs/architecture.md` and the current verification status before changing code. Use the existing controller, probe and system-service adapters. If the firmware guard or native backend fails, first follow [device adaptation](../openlocation-device-adaptation/SKILL.md).

## Scope and invariants

- Keep hooks in `android` / `system_server`. A consumer remains an ordinary app: no target-process injection, APK patching, package-name branches or suppression of its security checks.
- Select consumers through the existing UID/certificate policy. Preserve permissions, AppOps, privacy, listener cleanup and unselected-UID isolation. Replace a shared broker stream only when all attributed recipients opt in.
- Keep the workflow independent of handset, firmware and application brands. Use runtime-discovered package/UID values only as test inputs, never as compatibility logic.
- Follow the repository's profile and verification conventions. Profiles authorize firmware and channels; do not add concrete consumer names, package lists or app-specific compatibility fields. Public validation describes channels, observations and limits. Keep the exact tested app version and local operation log in an ignored research directory when needed for reproduction.

## Establish the failing channel

1. Confirm the selected UID, current scene revision, active channels, framework scope and matching native companion. Establish whether the independent probe reproduces the symptom before altering an adapter.
2. Observe actual subscriptions and deliveries with `dumpsys location`, `dumpsys sensorservice` and relevant module diagnostics. Distinguish direct app requests from a broker with attributed recipients, cached requests and network/server-derived fixes.
3. Identify the consumed numeric sensor types and instances. Cover heading outputs as well as input sensors; replacing accelerometer or gyro events alone does not prove fused orientation is synthetic. Inspect process maps/module logs to confirm that the consumer has not loaded the module.
4. Compare the consumer's timestamps and values with the scene at those timestamps. Separate delivery errors from stale caches, coordinate conversion, background throttling, permission state and rendering behavior. Change a shared service boundary only when the evidence identifies a contract mismatch.

## Location and map consumers

Keep Android Location transports in WGS84. Consumers may render GCJ-02 or BD-09; an offset must not be repaired by applying a global second conversion. Check the requested provider, elapsed-realtime age, accuracy, speed, bearing and acquisition after restart.

Exercise continuous updates, a current request with zero allowed cache age where supported, and cached last location separately. A search result or moved map camera is not evidence of live positioning: use the live location control, observe successive marker positions and corroborate them with subscriptions/deliveries.

An installed map client verifies only the observed consumer path. It does not certify all releases of its SDK or server-derived Wi-Fi/cellular/IP positioning. SDK probe tests must use the SDK's documented integration requirements; credentials, if required, must belong to the probe package/signature and stay outside the repository.

## Walking and running consumers

Use the controller's existing scenario API and a synthetic public test route. Verify the actual activity mode and data sources before starting a test. Allow enough route duration for the observation window; a finite route reaching its end is not a consumer delivery failure. Observe a bounded session at two or more elapsed times: route progress, distance, duration, speed/pace, heading, cadence or step count where exposed. Compare only metrics with known definitions; display smoothing and paused time can differ from raw samples.

Check consistency between location-derived distance and motion-derived activity. A changing timer alone does not establish motion compatibility; a step counter may use a separate sensor path from an accelerometer. Investigate unsupported channels through their system-service contract rather than substituting a UI score or app-specific value.

Use the user's authorized account context. Check the app's finish/save and automatic-sync behavior before treating a stop button as a discard action. Keep test activities local and discard them unless retaining or submitting results is part of the request. If ending a test automatically creates a record, identify and remove only that test record through the normal UI, report any recycle-bin retention, and never publish it to a feed. Do not clear existing activity history or alter account membership as a test shortcut.

## Lifecycle and isolation

Test the selected consumer while the scene runs, then pause/stop and verify fresh physical data resumes. Separately remove selection and verify the UID is no longer receiving the synthetic route; retain an independent unselected consumer as a control. Allow a bounded physical GNSS acquisition window, distinct from the IMU sample window. Record an absent fix as unverified or failed, not as proof that isolation passed.

Keep recovery observations separate from an activity being recorded: a paused activity timer may still accept map fixes. Finish or discard that activity before stopping/resetting the scene unless the test explicitly measures that transition. Confirm the active app/window and dismiss transient overlays before acting; background or off-screen accessibility nodes do not establish the visible UI state.

For a broker, inspect recipient attribution and mixed-recipient behavior. Do not weaken fail-closed handling to make a consumer test pass. For heading, rotate the handset when practical and distinguish actual physical-rotation evidence from static automated matrix checks.

On multi-display devices, discover and explicitly select the interactive display for input and capture. Do not start another instrumentation session in the probe package while a consumer test is running. Keep location/account screenshots local, and omit credentials, real coordinates and private tracks from public evidence.

## Finish and report

Stop the scene, end/discard temporary activities as agreed, and restore temporary input, display and scenario settings. Retain the user's intended target selections. Run focused regression checks for changed service contracts and UID isolation; a documentation-only change needs skill validation, not a new full device test suite.

Record observed channel behavior and remaining limitations in the existing verification format. Distinguish pass, fail and untested; do not infer support from installation, a moving camera or a passing probe alone. Keep specific app inventories and screenshots out of device profiles and public verification documents.
