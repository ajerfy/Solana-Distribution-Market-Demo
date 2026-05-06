package com.solanadistributionmarketdemo.data

import android.content.Context
import com.solanadistributionmarketdemo.ui.ThemeMode

class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("theme-store", Context.MODE_PRIVATE)

    fun load(): ThemeMode {
        val raw = prefs.getString(KEY, ThemeMode.Light.name) ?: ThemeMode.Light.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.Light)
    }

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val KEY = "mode"
    }
}
