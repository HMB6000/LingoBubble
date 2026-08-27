package com.hmaluk.lingobubble

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lingobubble_settings", Context.MODE_PRIVATE)

    var sourceLang: String
        get() = prefs.getString("source_lang", "Korean") ?: "Korean"
        set(value) { prefs.edit().putString("source_lang", value).apply() }

    var targetLang: String
        get() = prefs.getString("source_lang", "English") ?: "English"
        set(value) { prefs.edit().putString("target_lang", value).apply() }

    var engineType: String
        get() = prefs.getString("engine_type", "Azure") ?: "Azure"
        set(value) { prefs.edit().putString("engine_type", value).apply() }

    var groqRate: Float
        get() = prefs.getFloat("groq_rate", 2f)
        set(value) { prefs.edit().putFloat("groq_rate", value).apply() }

    var fontSize: Float
        get() = prefs.getFloat("font_size", 20f)
        set(value) { prefs.edit().putFloat("font_size", value).apply() }

    var opacity: Float
        get() = prefs.getFloat("opacity", 0.6f)
        set(value) { prefs.edit().putFloat("opacity", value).apply() }

    var showBackground: Boolean
        get() = prefs.getBoolean("show_background", true)
        set(value) { prefs.edit().putBoolean("show_background", value).apply() }

    var showVisualizer: Boolean
        get() = prefs.getBoolean("show_visualizer", true)
        set(value) { prefs.edit().putBoolean("show_visualizer", value).apply() }

    var azureKey: String
        get() = prefs.getString("azure_key", "") ?: ""
        set(value) { prefs.edit().putString("azure_key", value).apply() }

    var azureRegion: String
        get() = prefs.getString("azure_region", "eastus") ?: "eastus"
        set(value) { prefs.edit().putString("azure_region", value).apply() }

    var groqKey: String
        get() = prefs.getString("groq_key", "") ?: ""
        set(value) { prefs.edit().putString("groq_key", value).apply() }
}
