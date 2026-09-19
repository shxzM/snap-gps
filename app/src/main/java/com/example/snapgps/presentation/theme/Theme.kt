package com.example.snapgps.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.snapgps.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF74F8E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    tertiary = Color(0xFF456179)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF53DBC9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF74F8E5),
    secondary = Color(0xFFB1CCC6),
    tertiary = Color(0xFFADCAE5)
)

/** Status colors for the GPS chip; fixed because they sit on top of the camera image. */
object GpsColors {
    val Ready = Color(0xFF34C759)
    val Warning = Color(0xFFFFB020)
    val Off = Color(0xFFFF6B5E)
    val Neutral = Color(0xFFE0E0E0)
}

@Composable
fun SnapGpsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    // A keyed DisposableEffect (not SideEffect) so screens that override bar icons, which are
    // composed after this, win on first composition.
    DisposableEffect(view, dark) {
        val window = (view.context as? Activity)?.window
        if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        onDispose { }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
