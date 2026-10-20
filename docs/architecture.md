# Architecture and contracts

## Modules

- `scenario-format`: immutable scenes, profiles, timing and validation; no Android dependency.
- `simulation-core`: routes, 10 ms dynamics, attitude, steps, orbital GNSS and NMEA; no Android/root dependency.
- `android-runtime`: Android conversions, exact device profiles, GPX boundaries and IPC constants.
- `hook-runtime`: System Framework entry point, service adapters, installed-ELF discovery and native SensorService hooks.
- `app`: Compose controller, target selection, private configuration, foreground coordination, physical GPS recording and licenses.
- `probe-app`: independent Java/native consumer and instrumentation; no application-process hooks.
- `magisk-module`: mandatory native library mount and firmware guard; no extra injector or SELinux policy.

## Shared model

WGS84 routes use a local east/north/up approximation limited to ±85° latitude and 50 km extent/length. Catmull–Rom smoothing and arc-length lookup remove abrupt turns. Curvature, acceleration and braking constrain speed. Dynamics are precomputed at 100 Hz, capped at four hours; long scenes can occupy tens of megabytes. This is short-route software modeling, not survey geodesy or measured gait replay.

Speed, stride, arm swing and heading use separate seeded continuous variations. Per-mode profiles preserve settings. The horizontal observation stays within the route corridor. Position, steps and pose share one clock. Phone +Y projects along the instantaneous tangent: tilt is compensated before bounded heading noise (0–15°). Zero amplitude aligns it exactly. Rotation vector, gravity, magnetic field and gyro derive from this quaternion; location bearing describes movement. Stationary output does not add heading motion.

Body axes follow Android, with SI units. Quaternions rotate body to world. Accelerometer combines linear acceleration and gravity; gyro is rad/s, magnetic field µT. The world magnetic vector is synthetic, not a geomagnetic field service.

## GNSS and time

Identifiable GPS/BeiDou/QZSS/SBAS PRNs from OMM data are propagated with SGP4/SDP4. TEME/ECEF transforms, signal-transit Earth rotation, elevation and signal noise feed status, raw range/rate/ADR/clock and checksummed NMEA. Constellations without verified PRN mapping are omitted. Leap-second constants need maintenance. Orbit refresh validates HTTPS content before commit. No RF signal is transmitted.

Legacy extras (`satellites`, `maxCn0`, `meanCn0`) derive from the same sky model and supplement GNSS callbacks. Tunnels suppress observations while motion continues. UTC/elapsed time are tied at start. Pause freezes progress/delivery; resume uses live timestamps. Stop/completion restores physical delivery. Controller recreation fails closed and does not resume synthetic sessions.

## Service and UID boundary

Adapters install only in `android` / system_server. Target apps are neither patched nor placed in LSPosed scope. Selected package certificates are pinned and resolved to UIDs; controller/Android are excluded. Signature-matched clients may control scenes; only UID 1000 consumes backend snapshots/reports. Configuration is parsed on a worker, never on sensor hot paths. First use requires a versioned local acknowledgement before simulation or recording.

LocationProviderManager retains permission, AppOps, coarse filtering, cancellation and transport checks. Synthetic observations enter before filtering. Last/current/listener/PendingIntent and GPS/network/fused/passive share this boundary. WorkSource broker streams are replaced only when every attributed recipient opts in; mixed streams fail closed. SDKs deriving server locations from Wi-Fi/cellular/network traffic are outside this boundary. Selection cannot guarantee every proprietary SDK reports synthetic coordinates.

GNSS proxies retain fine-location/AppOps checks. Native queues retain SensorService registration/access and flush metadata. Fourteen supported types are synthesized: 1, 2, 3, 4, 9, 10, 11, 14, 15, 16, 18, 19, 20 and 35. Other types stay physical. Legacy orientation, rotation/game/geomagnetic vectors and calibrated/uncalibrated IMU values derive from one frame quaternion; numeric sensor type, rather than the vendor name, determines the event format. Each native frame carries fourteen six-float slots, retaining bias fields in uncalibrated events. Direct channels use public memory structures, verified runtime symbols and release-ordered 104-byte events. Hardware fallback is NORMAL rate only; higher-rate checks remain intact. After stop, consumers may need to recreate/reconfigure formats unsupported by physical hardware.

Private ABIs require exact model, codename, API, fingerprint and library SHA-256. Installed ELF/XZ mini debug symbols replace fixed addresses/private offsets. The APK checks the companion against its own native SHA-256.

## Real recording and files

A separate user-started location foreground service requests physical GPS every second, without background-location permission. Mock, invalid, duplicate, future and >15-second-old fixes are rejected. The controller cannot be a simulation target.

Private SQLite transactions preserve points/summaries; backups are disabled. A new process marks unfinished tracks interrupted without restarting. The limit is 12 hours/43,200 points. GPS disabled, permission loss, storage errors and limits preserve written points. GPX 1.1 export streams UTC time, optional elevation and accuracy extensions through the document picker. OpenLocation never uploads real tracks. Deletion requires confirmation. Recordings are separate from scenes.

Scene imports are limited to 8 MiB/10,000 points and validated before commit. MapLibre uses on-demand OSM tiles with attribution, no bulk download/routing service. Probe captures are bounded and require explicit export.

The unified track library stores imported GPX bytes atomically with parsed overviews. Rename never rewrites source XML; chunked original-document export avoids CursorWindow blob limits. Recorded and saved routes share listing, search, preview, export and deletion. Simulation reuse samples at most 10,000 points and still enforces scene extent/latitude limits; original exports retain every point. Schema v1 recording data migrates to v2 without discarding tracks. Deleting a library copy does not delete its source document or the independent current scene.

Map previews request OSM tiles for the viewed area; those requests reveal the approximate viewport to the tile service. Track files themselves stay local unless the user exports them. No analytics or account backend is included.
