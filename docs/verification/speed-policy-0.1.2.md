# Speed policy and display units

Version 0.1.2 makes existing curvature limiting an explicit scene option, enabled by default, and adds controller preferences for m/s, km/h and mph. Old scene files retain curvature limiting when the new field is absent.

The engine limits speed to `sqrt(2.5 / abs(curvature))` when this option is enabled. Curvature includes the configured path deviation. There is no global 17 m/s ceiling. A private reproduction of the reported 35 m/s scene measured a 17.440 m/s peak; disabling only curve limiting reached 35.000 m/s. Removing only path deviation while keeping curve limiting also reached 35.000 m/s. Private route coordinates are excluded from this evidence.

`SpeedPolicyTest` verifies the difference on a procedural straight route with 1.5 m deviation and 60 m wavelength. With curve limiting disabled it reaches 35 m/s, reports the same Android-boundary observation speed, preserves 2 m/s² initial acceleration and comes to a stop at the endpoint. Serialization checks cover both old files and the new option.

`SpeedUnitTest` checks 35 m/s = 126 km/h, 22.352 m/s = 50 mph, round-trip conversion and preference fallback. Scene and Android API values remain in m/s; display and slider values/ranges are converted at the UI boundary.

On the reference OnePlus 11, both 0.1.2 APKs and the matching companion were installed and the phone restarted so system_server loaded the new policy. With curve limiting disabled, the independent probe's Android LocationManager callback displayed 35.00 m/s. The probe also received satellite status and raw GNSS; these observations are a speed-policy check, not a repeat certification of every SDK path.

Controller UI checks showed 126.0 km/h and 78.3 mph for the unchanged 35 m/s target. The km/h choice survived package replacement, and the configuration displayed the converted target and the default-enabled curve switch/hint. After checking, the scene was stopped, curve limiting restored to ON and the display unit returned to m/s. The original route and other motion parameters were compared with the pre-test private configuration and remained unchanged. The About version now follows build metadata instead of a translated version literal.
