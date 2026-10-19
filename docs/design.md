# Interface and asset specification

The controller uses `MaterialExpressiveTheme` and `MotionScheme.expressive()` from the pinned API-36-compatible Compose Material 3 expressive release (1.5.0-alpha14). Experimental APIs are isolated to the UI module. Primary 64 dp controls use official `ButtonDefaults.shapesFor` and padding tokens; navigation, chips, sliders, switches, dialogs and lists use Material components. Shape, typography and color roles come from Material theme tokens.

Wallpaper colors use Android `dynamicLightColorScheme` / `dynamicDarkColorScheme`; dark mode follows the system. A restrained indigo fallback is available in Settings. Maps take their route accent from the same theme. Text uses system font fallback for broad script coverage. Controls have Android accessibility semantics and at least standard Material touch targets. Layout uses start/end padding, wrapping choice rows, scrollable settings and navigation labels.

Copy describes actions and state: Start, Pause, Resume, Stop, Route, Diagnostics. No promotional headlines or slogans are included. Technical scope and model limitations appear in Diagnostics, where they affect interpretation. User-visible strings belong in Android resources, not engine enums or hook logs.

## Primary sources

- [Material 3 Expressive](https://m3.material.io/)
- [Material 3 Compose release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Google Fonts Material Symbols](https://fonts.google.com/icons)
- [Adaptive and monochrome icon requirements](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

## Launcher icon

Generated with the built-in GPT-image/imagegen tool. Final foreground: `app/src/main/res/drawable-nodpi/launcher_mark.png`. It is a transparent route mark, with no mask or shadow. The adaptive icon uses a separate full-bleed background and the same foreground alpha as its monochrome layer. The mark is positioned inside the central safe region of the 108 dp layer; the system applies the launcher mask and wallpaper tint. All actual color and monochrome preview checks are recorded in the development status.

Final generation prompt:

> Android adaptive launcher icon foreground for OpenLocation, a restrained precision route and movement testing tool. One simple flat geometric symbol: a soft rounded open loop/path with a small circular endpoint and subtle directional cut. Single deep indigo silhouette, transparent background, refined Material 3 Expressive proportions, compact mark with no thin lines. Entire mark inside the central 56% of the square canvas, at least 22% transparent margins. All negative space transparent. No text, letters, gradients, shadows, border, outer tile, baked-in mask, mockup or decorations. Suitable as the alpha mask of a monochrome themed icon.

## In-app icons

All current UI icons are official Material Symbols Rounded from Google Fonts, retrieved from Google's [material-design-icons](https://github.com/google/material-design-icons) repository. `tools/fetch-icons.py` preserves official vector paths and generates Android vector drawables. Source SVGs are retained under `artwork/`. Their Apache 2.0 license is documented in `NOTICE`. No generated substitutes were necessary.
