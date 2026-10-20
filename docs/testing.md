# Verification guide

## Automated checks

```powershell
.\gradlew.bat :simulation-core:test :android-runtime:testDebugUnitTest :hook-runtime:testDebugUnitTest :app:lintRelease :probe-app:lintRelease
python tools/check-locales.py
```

Tests cover reproducibility, route/heading bounds across placements, shared attitude/magnetic data, steps, pause/tunnels, firmware matching, mock/stale fix rejection, locale-independent timed GPX, transactional route editing/undo/redo, Unicode coordinate input and mini debug ELF parsing.

`SpeedPolicyTest` covers a 35 m/s target with path deviation, enabled/disabled curvature policy, start acceleration/end braking and backward-compatible scene decoding. `SpeedUnitTest` checks conventional conversions, round trips and stable preference identifiers. Display units must not rewrite scene m/s values or alter an active session.

Build `:app:assembleReleaseAndroidTest` to run `TrackLibraryTest` and `LauncherIconTest` against the signed release. They exercise >2 MiB lossless GPX import/rename/export, invalid-import atomicity and platform adaptive-monochrome transparency/safe-region rendering. Instrumentation must share the controller signing identity. Recording schema migration is also checked by retaining a pre-upgrade recording on device.

## Independent service consumers

Install matching controller/companion and probe/test APKs. Select **only System Framework** in LSPosed and restart. Grant probe precise location/activity recognition. Both packages must share a signing identity.

```powershell
python tools/run-device-tests.py --output docs/verification/system-service-channels.txt
```

Seven tests cover supported Java sensor types/instances, independent rates, flush/batch/unregister, pause/restore, existing GPS subscriptions, last/current/listener/tunnel, system provider last fixes, NDK queues, both direct-memory formats at NORMAL rate, RATE_STOP, raw GNSS/status/NMEA. The probe asserts absence of application hooks. Values are compared to the core at event timestamps. The original 0.1.0 evidence covered eight types; 0.1.1 expands the Java/native regression to fourteen types.

`HeadingChannelsTest` independently compares legacy orientation and rotation/game/geomagnetic vectors using Android's `SensorManager` matrix functions, and checks six-value uncalibrated events. `GmsLocationTest` uses the actual test-only Play services Location SDK for fresh continuous, current and cached fixes. The probe declares the required GMS client protocol metadata even though SDK code is confined to the test APK. See the versioned output under `verification/`.

Run consumer instrumentation serially. Launching another instrumentation in the same probe package for UI inspection can kill an active capture and invalidate its result. Inspect the UI after the consumer run has ended.

For a separate unselected UID, build/install probe/test with `-PobserverSuffix=.observer`, grant location/activity permissions, and run:

```powershell
python tools/run-device-tests.py --classes dev.openlocation.probe.UidIsolationTest --component dev.openlocation.probe.observer.test/androidx.test.runner.AndroidJUnitRunner --output docs/verification/uid-isolation.txt
```

This requires physical GPS and a demo route far from the receiver. During another UID's scene, the observer must receive physical IMU/GPS. Do not publish private coordinates or real tracks as evidence.

## Manual acceptance

Use external navigation/fitness clients without placing them in LSPosed scope. Observe driving, walking, running, signal/position/distance/steps, selection removal and restoration. Use test accounts/non-personal evidence. Proprietary server fixes/mixed brokers need separate evidence; GPS success does not prove them.

For common SDK consumers, follow the [adaptation skill](../skills/openlocation-device-adaptation/SKILL.md) and record exact client versions and framework callbacks. The reference results for GMS, Tencent, AMap and Baidu are in `verification/sdk-consumers-0.1.1.md`. Test SDK output in WGS84 before its normal map-coordinate conversion; never apply a vendor conversion globally to Android fixes.

In the route editor, search/jump, add after a selected point, move an existing point, delete a middle point, undo/redo, Save and reopen. Verify that closing/discarding leaves the active route intact. Check the center coordinate while moving the map, including a fling. On devices with multiple active displays, send ADB gestures to the observed physical display explicitly, for example `input -d 0`; a successful shell exit does not prove that the visible UI received the gesture.

Record real GPS while selected clients receive synthetic data. Stop/export through the document picker; validate UTC, elevation and accuracy. Kill the controller during another recording and verify interrupted/exportable points. Check permission denial/GPS disabled, first-use acceptance/exit, light/dark dynamic color, RTL, large fonts, parameters, attribution, licenses, device warning and themed icon. Restore preferences afterward.

`tools/verify-apk.py <apk>` checks ELF LOAD and APK ZIP 16 KiB alignment. The reference phone uses 4 KiB pages; static checks do not prove 16 KiB hardware. Android 14/15, other firmware, long-duration battery/latency and proprietary SDKs need separate validation.
