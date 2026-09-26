package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.ui.viewmodel.AccentColor
import com.example.ui.viewmodel.ThemeMode

@Composable
fun MediaFetchTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.CYAN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AMOLED -> true
    }

    val primaryColor = Color(accentColor.hex)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == ThemeMode.AMOLED -> {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = Color.Black,
                primaryContainer = primaryColor.copy(alpha = 0.2f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor,
                onSecondary = Color.Black,
                background = AmoledBg,
                onBackground = Color(0xFFF1F5F9),
                surface = AmoledSurface,
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = AmoledSurfaceVariant,
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = AmoledBorder
            )
        }
        isDark -> {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = Color(0xFF0F172A),
                primaryContainer = primaryColor.copy(alpha = 0.25f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor,
                onSecondary = Color.White,
                background = DarkBg,
                onBackground = Color(0xFFF8FAFC),
                surface = DarkSurface,
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = DarkBorder
            )
        }
        else -> {
            lightColorScheme(
                primary = primaryColor,
                onPrimary = Color.White,
                primaryContainer = primaryColor.copy(alpha = 0.15f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor,
                onSecondary = Color.White,
                background = LightBg,
                onBackground = Color(0xFF0F172A),
                surface = LightSurface,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = LightSurfaceVariant,
                onSurfaceVariant = Color(0xFF64748B),
                outline = LightBorder
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
