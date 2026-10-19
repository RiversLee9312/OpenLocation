package dev.openlocation.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenLocationTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors =
        if (dynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark)
            darkColorScheme(
                primary = Color(0xFFC3C0FF),
                secondary = Color(0xFFC8C4DB),
                tertiary = Color(0xFFB2CCC3),
            )
        else
            lightColorScheme(
                primary = Color(0xFF55519A),
                secondary = Color(0xFF605D72),
                tertiary = Color(0xFF3C665A),
                surface = Color(0xFFFCF8FF),
            )
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
