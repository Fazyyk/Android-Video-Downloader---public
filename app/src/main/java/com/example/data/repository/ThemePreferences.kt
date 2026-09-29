package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.viewmodel.AccentColor
import com.example.ui.viewmodel.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistent storage and reactive state for application global theme settings.
 */
class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mediafetch_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getStoredThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(getStoredAccentColor())
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    fun getStoredThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun getStoredAccentColor(): AccentColor {
        val name = prefs.getString(KEY_ACCENT_COLOR, AccentColor.CYAN.name) ?: AccentColor.CYAN.name
        return try {
            AccentColor.valueOf(name)
        } catch (_: Exception) {
            AccentColor.CYAN
        }
    }

    fun setAccentColor(accent: AccentColor) {
        prefs.edit().putString(KEY_ACCENT_COLOR, accent.name).apply()
        _accentColor.value = accent
    }

    companion object {
        private const val KEY_THEME_MODE = "key_global_theme_mode"
        private const val KEY_ACCENT_COLOR = "key_global_accent_color"
    }
}
