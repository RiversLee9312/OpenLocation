# Verification status

Reference: OnePlus 11, PHB110 / OP591BL1 / API 36, 2026-10-19. The complete fingerprint and SensorService SHA-256 are in `hook-runtime/src/main/assets/device_profiles.json`. Similar devices, other firmware and Android 14/15 are not verified.

## Implemented

- System Framework scope only; no target-process hooks, package-specific runtime branches or target APK modification.
- UID/certificate selection, exact firmware guard, matching native companion and failure-closed behavior.
- Location transports, eight Java/native sensor types, both direct-memory formats, GNSS status/raw/NMEA and legacy receiver summaries from one orbital model.
- Per-mode speed/stride/arm swing/heading variation, route corridor, GPX editing and physical GPS recording/export/recovery.
- Unified imported/recorded/saved GPX library: search, preview, rename, lossless original export, delete and reuse as a simulation route.
- Material 3 Expressive, dynamic color, adaptive/themed icon, first-use notice, open source licenses and 22 resource locales.
- Agent adaptation skill, rollback instructions, private release signing and no cloud/device-transfer backup of recordings.

## Evidence

JVM: 19 tests pass (13 dynamics/GNSS, 4 profile/recorded-GPX/recipient-policy, 2 ELF parsing). Release lint has zero errors. Both APKs pass ELF/ZIP 16 KiB alignment checks; the physical phone uses 4 KiB pages.

`verification/system-service-channels.txt` records independent consumer instrumentation. `verification/uid-isolation.txt` records physical IMU/GPS retained by an unselected UID while another UID receives synthetic motion. Historical application-process prototype results have been removed from release evidence.

Manual physical recording/export produced 54 timestamped GPS points with increasing UTC, elevation and accuracy. External fitness observation received distance/pace/cadence/steps and saved a controlled guest-account activity (0.76 km, 4:17, 636 steps). A walking consumer also received coherent position/distance/steps. Raw account screens, physical coordinates and private tracks are excluded from this repository.

Signed release controller/companion passed all seven service-consumer tests and the separate UID isolation test. Three controller instrumentation tests passed: 2.2 MB original GPX bytes survived import/rename/export unchanged, invalid imports remained atomic, and the adaptive monochrome layer stayed inside its safe region. See `verification/library-and-icon.txt`.

Recording process-death recovery preserved/exported 29 fixes; upgrading the recording database retained those points. GUI import, preview, rename, reuse and deletion were exercised. The signed build supplied running consumers (including 0.15 km/cadence 151 and 0.26 km/cadence 145) and driving navigation with changing instructions/speed (21–25 mph). Test fitness records were removed where appropriate; explicitly authorized guest tests were retained. Chinese/light, Arabic RTL, large fonts, dark-mode switching, first-use acceptance/exit and license rendering were inspected. No claim is made that every locale has received human visual review.

## Limits

Broker streams with mixed selected/unselected recipients fail closed. Proprietary server-derived Wi-Fi/cellular fixes may bypass Android location transports; dedicated vendor SDK paths are not marked verified merely because GPS works. Direct-channel hardware fallback covers NORMAL rate only. Unsupported sensor types remain physical. A channel format unsupported by real hardware may require consumer reconfiguration after stop.

The model is synthetic, not calibrated human gait, survey geodesy, RF simulation or a universal guarantee about third-party services. Long-duration power/latency and physical 16 KiB operation remain unverified.
