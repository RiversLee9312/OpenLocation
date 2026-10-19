# Verification guide

## Automated checks

```powershell
.\gradlew.bat :simulation-core:test :android-runtime:testDebugUnitTest :hook-runtime:testDebugUnitTest :app:lintRelease :probe-app:lintRelease
python tools/check-locales.py
```

Tests cover reproducibility, route/heading bounds across placements, shared attitude/magnetic data, steps, pause/tunnels, firmware matching, mock/stale fix rejection, locale-independent timed GPX and mini debug ELF parsing.

Build `:app:assembleReleaseAndroidTest` to run `TrackLibraryTest` and `LauncherIconTest` against the signed release. They exercise >2 MiB lossless GPX import/rename/export, invalid-import atomicity and platform adaptive-monochrome transparency/safe-region rendering. Instrumentation must share the controller signing identity. Recording schema migration is also checked by retaining a pre-upgrade recording on device.

## Independent service consumers

Install matching controller/companion and probe/test APKs. Select **only System Framework** in LSPosed and restart. Grant probe precise location/activity recognition. Both packages must share a signing identity.

```powershell
python tools/run-device-tests.py --output docs/verification/system-service-channels.txt
```

Seven tests cover eight Java sensor types/instances, independent rates, flush/batch/unregister, pause/restore, existing GPS subscriptions, last/current/listener/tunnel, system provider last fixes, NDK queues, both direct-memory formats at NORMAL rate, RATE_STOP, raw GNSS/status/NMEA. The probe asserts absence of application hooks. Values are compared to the core at event timestamps.

For a separate unselected UID, build/install probe/test with `-PobserverSuffix=.observer`, grant location/activity permissions, and run:

```powershell
python tools/run-device-tests.py --classes dev.openlocation.probe.UidIsolationTest --component dev.openlocation.probe.observer.test/androidx.test.runner.AndroidJUnitRunner --output docs/verification/uid-isolation.txt
```

This requires physical GPS and a demo route far from the receiver. During another UID's scene, the observer must receive physical IMU/GPS. Do not publish private coordinates or real tracks as evidence.

## Manual acceptance

Use external navigation/fitness clients without placing them in LSPosed scope. Observe driving, walking, running, signal/position/distance/steps, selection removal and restoration. Use test accounts/non-personal evidence. Proprietary server fixes/mixed brokers need separate evidence; GPS success does not prove them.

Record real GPS while selected clients receive synthetic data. Stop/export through the document picker; validate UTC, elevation and accuracy. Kill the controller during another recording and verify interrupted/exportable points. Check permission denial/GPS disabled, first-use acceptance/exit, light/dark dynamic color, RTL, large fonts, parameters, attribution, licenses, device warning and themed icon. Restore preferences afterward.

`tools/verify-apk.py <apk>` checks ELF LOAD and APK ZIP 16 KiB alignment. The reference phone uses 4 KiB pages; static checks do not prove 16 KiB hardware. Android 14/15, other firmware, long-duration battery/latency and proprietary SDKs need separate validation.
