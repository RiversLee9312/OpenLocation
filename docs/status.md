# Verification status

## Android 17 / KernelSU adaptation, 2026-10-28

Official LOS 24.0-20261023, PHB110 / OP591BL1 / API 37 / incremental `c4db0ea4ab`; KernelSU 3.3.1, meta-overlayfs 1.3.1, Zygisk Next 1.5.2, Vector 2.2. SELinux remains Enforcing, with only `system/0` in OpenLocation's scope. The profile records firmware and channel coverage; consumer observations do not add application-specific compatibility fields.

28 JVM tests passed, including rejection of a different OS incremental sharing the same vendor fingerprint. Eight on-device installer cases passed without installing: valid, wrong model/device/API/fingerprint/incremental, wrong native hash and missing hash. Both APKs passed ELF/ZIP alignment checks; locale keys/XML/format arguments passed for all localized resource files.

Seven service-consumer tests passed for Java sensors, rates/lifecycle/flush/pause, GPS last/current/continuous, system providers, raw GNSS/status/NMEA, NDK queues and both direct-memory formats. Heading validation and the actual GMS location SDK 21.3.0 test also passed (installed Play services 26.41.31, target SDK 37). All nine passed again with the restored root-hiding configuration enabled. See `verification/android17-system-service-channels.txt`, `android17-heading-gms.txt` and `android17-with-root-hiding.txt`.

The first unselected-UID test observed physical IMU but failed its six-second physical GPS assertion because no real fix arrived. With the original assertions retained and an additional bounded GNSS acquisition wait, the independent UID test passed in 23.246 seconds: physical IMU/GPS stayed separate from the selected UID's synthetic route. See `verification/android17-uid-isolation.txt`. Physical rotation and long-duration stability are not covered by these automated results; Android 16 evidence below retains its original scope.

Separate bounded UI observations confirmed live route positions in four independent navigation consumers, with driving-speed updates in two. Two fitness consumers showed increasing running distance with consistent pace/cadence, and increasing walking distance with step-count or cadence changes. All six consumers had readable process maps without an OpenLocation library. Stop returned location to the physical region. These observations verify the exercised service-backed paths, not every SDK version, server-derived location or end-to-end activity platform. Consumer identities, versions and screenshots remain in ignored local research; the profile continues to describe firmware and shared channels. The reusable workflow is in `skills/openlocation-app-adaptation/SKILL.md`.

Reference: OnePlus 11, PHB110 / OP591BL1 / API 36, updated 2026-10-20. The complete fingerprint and SensorService SHA-256 are in `hook-runtime/src/main/assets/device_profiles.json`. Similar devices, other firmware and Android 14/15 are not verified.

## Implemented

- System Framework scope only; no target-process hooks, package-specific runtime branches or target APK modification.
- UID/certificate selection, exact firmware guard, matching native companion and failure-closed behavior.
- Location transports, fourteen Java/native sensor types, both direct-memory formats, GNSS status/raw/NMEA and legacy receiver summaries from one orbital model.
- Per-mode speed/stride/arm swing/heading variation, route corridor, GPX editing and physical GPS recording/export/recovery.
- Unified imported/recorded/saved GPX library: search, preview, rename, lossless original export, delete and reuse as a simulation route.
- Transactional route editing with device-geocoder search, center-pin placement, existing-point movement, arbitrary-point deletion, coordinate entry and bounded undo/redo.
- A compact Routes overview; path-deviation settings are in Scene configuration, and GPX file actions are in the track library.
- Probe overview, independent heading comparison and searchable sensor-instance cards.
- Default-enabled curve slowdown with an explicit target-speed explanation, plus persistent m/s, km/h and mph display/input units and a decimal target-speed field using the full 0–70 m/s scene range.
- Material 3 Expressive, dynamic color, adaptive/themed icon, first-use notice, open source licenses and 22 resource locales.
- Agent adaptation skill, rollback instructions, private release signing and no cloud/device-transfer backup of recordings.

## Evidence

JVM: 27 tests pass (16 dynamics/GNSS/speed-policy, 9 profile/recorded-GPX/recipient-policy/route-editing/coordinate-input/speed-units/speed-input, 2 ELF parsing). Release lint has zero errors. Both APKs pass ELF/ZIP 16 KiB alignment checks; the physical phone uses 4 KiB pages.

Version 0.1.3 reran 16 core and 9 Android-boundary JVM tests and both Release lint checks. On the reference phone, the decimal target-speed field accepted 220.5 km/h, persisted it on Save/reopen, and showed the localized range error for 300 km/h. Dismissing the invalid draft retained the saved target. Original route, all motion settings and UI preferences were restored after acceptance. The unchanged 0–70 m/s engine range is shared with input validation; a procedural highway test confirms 70 m/s reaches location output after acceleration. Existing system-channel/SDK evidence was not unnecessarily repeated. See `verification/speed-input-0.1.3.md`.

Version 0.1.2 reran 15 core and 7 Android-boundary JVM tests. After installing both APKs/companion and restarting, an independent Android LocationManager consumer received 35.00 m/s with curve limiting disabled. Unit switching showed 126.0 km/h and 78.3 mph for the same SI target. Original route/motion settings, default-enabled curve limiting and the previous m/s preference were restored after checking. See `verification/speed-policy-0.1.2.md`. The previous SDK/sensor transport evidence retains its original versioned scope.

Version 0.1.1 adds independent checks for seven heading variants (including numeric TYPE_ORIENTATION despite a misleading vendor name), a real GMS Fused Location Provider client, fourteen-type Java queues, native queues/direct memory, and unselected-UID isolation. All five device tests passed. See `verification/heading-channels-0.1.1.txt`, `gms-location-0.1.1.txt`, `sensor-expansion-0.1.1.txt` and `uid-isolation-0.1.1.txt`. The heading test cross-checks received events using Android's rotation-matrix APIs; a physical device-rotation test is not included in this evidence.

GNSS-backed consumers Tencent Maps 11.7.0, AMap 17.01.0.2015 and Baidu Maps 22.0.40 received route data on this firmware. System subscriptions/callbacks and map movement were observed; driving displays also showed changing speed and distance. See `verification/sdk-consumers-0.1.1.md` for exact scope. Independent network-only SDK paths remain unverified.

The 0.1.1 editor was exercised with place search, camera-center movement of an existing point, insertion after a selected point, middle-point deletion, undo/redo, Save/reopen and discard. The saved point coordinates and counts persisted; discarding an empty new draft preserved the saved route. The simplified Routes page has no duplicate footer, and Scene configuration retains the existing deviation-limit/wavelength values (1.50 m / 60.00 m in the acceptance scene). Probe overview received 49 satellite-status entries and 49 raw measurements; its heading selector distinguishes numeric type 3 from type 11 despite identical vendor names. Chinese, Arabic RTL and 1.3× font layouts were inspected, with font/locale/night settings restored afterward.

`verification/system-service-channels.txt` records independent consumer instrumentation. `verification/uid-isolation.txt` records physical IMU/GPS retained by an unselected UID while another UID receives synthetic motion. Historical application-process prototype results have been removed from release evidence.

Manual physical recording/export produced 54 timestamped GPS points with increasing UTC, elevation and accuracy. External fitness observation received distance/pace/cadence/steps and saved a controlled guest-account activity (0.76 km, 4:17, 636 steps). A walking consumer also received coherent position/distance/steps. Raw account screens, physical coordinates and private tracks are excluded from this repository.

The 0.1.0 signed release controller/companion passed all seven service-consumer tests and the separate UID isolation test. Three controller instrumentation tests passed: 2.2 MB original GPX bytes survived import/rename/export unchanged, invalid imports remained atomic, and the adaptive monochrome layer stayed inside its safe region. See `verification/library-and-icon.txt`. The 0.1.1 regression checks above target the changed sensor paths; unchanged recording/library/icon tests were not repeated.

Recording process-death recovery preserved/exported 29 fixes; upgrading the recording database retained those points. GUI import, preview, rename, reuse and deletion were exercised. The signed build supplied running consumers (including 0.15 km/cadence 151 and 0.26 km/cadence 145) and driving navigation with changing instructions/speed (21–25 mph). Test fitness records were removed where appropriate; explicitly authorized guest tests were retained. Chinese/light, Arabic RTL, large fonts, dark-mode switching, first-use acceptance/exit and license rendering were inspected. No claim is made that every locale has received human visual review.

## Limits

Broker streams with mixed selected/unselected recipients fail closed. Proprietary server-derived Wi-Fi/cellular fixes may bypass Android location transports; dedicated vendor SDK paths are not marked verified merely because GPS works. Direct-channel hardware fallback covers NORMAL rate only. Unsupported sensor types remain physical. A channel format unsupported by real hardware may require consumer reconfiguration after stop.

The model is synthetic, not calibrated human gait, survey geodesy, RF simulation or a universal guarantee about third-party services. Long-duration power/latency and physical 16 KiB operation remain unverified.
