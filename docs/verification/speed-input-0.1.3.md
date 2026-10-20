# Numeric target-speed input · 0.1.3

Reference device: OnePlus 11, PHB110 / OP591BL1 / API 36; 2026-10-20. The exact firmware remains pinned in the device profile.

The previous driving slider ended at 35 m/s (126 km/h), although the scene format and engine already supported 70 m/s (252 km/h). A Material outlined decimal field now accepts the full existing scene range for every movement mode, in the selected m/s, km/h or mph unit. The unit suffix is separate from the editable number. Decimal keyboard and Done are provided. Invalid, empty and out-of-range drafts show a localized range message and disable Save; cancellation leaves the saved scene intact. An untouched field preserves the original SI precision.

## Automated checks

- 16 core and 9 Android-boundary JVM tests passed. The added highway fixture is a procedural straight route long enough to accelerate to 70 m/s; both velocity and Android location observation reach that value. Start/end and curve-policy tests continue to pass.
- Numeric input checks include 220 and 252 km/h, rejection of 253 km/h, negative/non-finite/trailing input, German decimal comma and ASCII decimal input, Arabic numeric formatting, and boundary round trips in all three units.
- Main app and Probe Release lint passed with zero errors. Both APKs passed ELF/ZIP 16 KiB alignment checks.
- All 22 resource locales cover the new range message; XML, positional placeholders and Android escaping passed `tools/check-locales.py`. The requested Luna localization subagent supplied the 21 non-default translations.

## Phone acceptance

Both signed 0.1.3 APKs were installed over 0.1.2. The configuration field displayed 126 km/h for the existing 35 m/s target. Entering 300 km/h showed the localized 0–252 km/h range message; dismiss/reopen retained 126. Entering 220.5 km/h and saving/reopening retained 220.5. Tapping the field opened the numeric keyboard. The dark Material layout was visually inspected.

After checking, the original route, all motion settings (including the user's existing curve-policy choice), and UI preferences were restored and compared to the private pre-test baseline. No private coordinates or screenshots are published. The scene was left stopped.

## Scope and upgrade

The engine's 70 m/s bound and movement policy are unchanged. The scene-format constant is now shared by the engine and input validator, without changing the serialized schema or constructor parameters. Curvature slowdown still defaults on; configured target speed can be reduced by bends/path variation, acceleration and braking.

No system injection transport or native code changed. The companion library/profile match 0.1.2; users who already completed the 0.1.2 system-component update and restart do not need another restart for this UI change. Initial installation and upgrades from 0.1.1 or earlier still require the companion and restart described in the README. Existing SDK/sensor results retain their original versioned scope; this acceptance does not claim a new live third-party high-speed test.
