package com.psadiag.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// PSADiag Dark Theme Colors
private val PSADarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),           // Light blue
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF81C784),          // Green for OK/active
    onSecondary = Color(0xFF003A02),
    secondaryContainer = Color(0xFF005304),
    onSecondaryContainer = Color(0xFFA8F5A0),
    tertiary = Color(0xFFFFB74D),           // Orange for warnings
    onTertiary = Color(0xFF462B00),
    error = Color(0xFFEF5350),             // Red for errors/DTCs
    onError = Color(0xFF601410),
    background = Color(0xFF1A1C1E),        // Dark background
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF2C2F33),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199)
)

private val PSALightColorScheme = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF2E7D32),
    tertiary = Color(0xFFE65100),
    error = Color(0xFFC62828),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF1A1C1E)
)

@Composable
fun PSADiagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PSADarkColorScheme else PSALightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
