# Location consumer observations, 2026-10-20

Reference: the exact OnePlus 11 / API 36 profile in `device_profiles.json`, controller/probe/native companion 0.1.1. Only System Framework is in LSPosed scope. No consumer APKs or application processes were patched. Selection uses the ordinary UID/certificate mechanism; there are no consumer package branches in the runtime.

## GMS fused location

`GmsLocationTest` uses the actual `play-services-location:21.3.0` SDK from Google Maven, with installed Google Play services 26.36.35 (260400-991383798). Continuous high-accuracy callbacks, a fresh current request and last-location cache all stayed within the scene region and the ten-second freshness bound. The complete successful instrumentation result is `gms-location-0.1.1.txt`. Request/cache semantics follow the [FusedLocationProviderClient reference](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient).

The client manifest must contain the Play services protocol metadata even when the SDK itself is in the test APK. A missing metadata error is an integration error, not evidence that the backend failed. Mixed-recipient brokers retain failure-closed recipient policy. This observation does not establish every priority, geofence, batching mode, service version or shared-cache case.

## Tencent location consumer

The official mobile download supplied consumer version 11.7.0. Its live location display followed the synthetic Shanghai route region. Android recorded 72 location deliveries and 12 raw measurement callbacks over an observed 12.7-second interval, with precise foreground location permission. A subsequent uninterrupted drive observation showed the live marker move, its map-relative heading change and the speed indicator change from 12 to 41 km/h while the physical phone remained at rest. Requests used the consumer's own WorkSource. No login or developer key was borrowed.

This verifies the GNSS-backed consumer path in that application version. It does not identify or certify every version of the standalone Tencent SDK, nor its network/server-only location path.

## AMap location consumer

Consumer version 17.01.0.2015 displayed the synthetic Shanghai position and direction. An observed 10.7-second interval delivered 10 GPS fixes, 20 passive fixes and 11 raw measurement events. Framework input remains WGS84; the map performs its own rendering conversion.

The separate fitness consumer's map component subscribed to hardware sensor type 3. The hardware display name contained “Rotation Vector”, but its numeric type was deprecated ORIENTATION. The previous release omitted that type. The generic service adapter now replaces it and all available heading variants; independent API cross-checks are in `heading-channels-0.1.1.txt`. No physical-phone rotation test is claimed by that automated evidence.

## Baidu location consumer

Consumer version 22.0.40 received continuous GPS/passive fixes. Over an observed 42.6 seconds, system counters recorded 205 GPS-channel deliveries and 80 passive deliveries. Its cruise screen showed changing distance and a speed of 43 km/h during the synthetic drive. WGS84 transport coordinates were retained; no second GCJ-02/BD-09 conversion was added to Android services.

## Scope and privacy

Each external consumer was selected through the general controller, observed with a synthetic public route, then deselected and the scene stopped. Consumer screenshots, account identifiers and physical coordinates remain outside the public repository. The existing unselected-UID test covers physical sensor/GPS isolation; a map screenshot is not a substitute for that check.

The vendor labels in Diagnostics refer to these precise consumer scopes and versions. Independent proprietary SDK calls requiring a developer key need a key issued for the probe's own identity. Server fixes derived directly from Wi-Fi, cellular or IP observations, SDK shared caches, tunnels, network-only operation, coarse grants, all SDK releases and all background policies are not certified by these observations. Keep those limitations when adapting another device.

The reusable workflow and coordinate-system/cache/heading lessons are in `skills/openlocation-device-adaptation/SKILL.md`.
