package com.droiddesk.launcher

import android.content.Context
import android.content.SharedPreferences

/** Thin wrapper over SharedPreferences. Also migrates the v1.0/v1.1 keys. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("droiddesk", Context.MODE_PRIVATE)

    private fun readList(key: String): MutableList<String> =
        (sp.getString(key, "") ?: "").split("|").filter { it.isNotBlank() }.toMutableList()

    private fun writeList(key: String, value: List<String>) {
        sp.edit().putString(key, value.joinToString("|")).apply()
    }

    var shortcuts: MutableList<String>
        get() {
            if (!sp.contains("shortcutList")) {
                // v1.0/v1.1 stored shortcuts as a string set.
                val legacy = try { sp.getStringSet("shortcuts", null) } catch (e: ClassCastException) { null }
                if (legacy != null) return legacy.toMutableList()
            }
            return readList("shortcutList")
        }
        set(value) { writeList("shortcutList", value) }

    var pinned: MutableList<String>
        get() = readList("pinned")
        set(value) { writeList("pinned", value) }

    var recents: MutableList<String>
        get() = readList("recents")
        set(value) { writeList("recents", value) }

    var accent: String
        get() = sp.getString("accentKey", null) ?: when (sp.getString("accentName", null)) {
            "Mor" -> "purple"
            "Turkuaz" -> "teal"
            "Yeşil" -> "green"
            "Turuncu" -> "orange"
            "Pembe" -> "pink"
            else -> "blue"
        }
        set(value) { sp.edit().putString("accentKey", value).apply() }

    var glass: Boolean
        get() = sp.getBoolean("glassMode", true)
        set(value) { sp.edit().putBoolean("glassMode", value).apply() }

    var gradient: String
        get() = sp.getString("gradientMode", "bluepurple") ?: "bluepurple"
        set(value) { sp.edit().putString("gradientMode", value).apply() }

    var wallpaper: String?
        get() = sp.getString("wallpaper", null)
        set(value) {
            if (value == null) sp.edit().remove("wallpaper").apply()
            else sp.edit().putString("wallpaper", value).apply()
        }

    var taskbarSize: Int
        get() = sp.getInt("taskbarSize", 64)
        set(value) { sp.edit().putInt("taskbarSize", value).apply() }

    var drawerWidth: Float
        get() = sp.getFloat("drawerWidth", .88f).coerceIn(.65f, .98f)
        set(value) { sp.edit().putFloat("drawerWidth", value).apply() }

    var drawerHeight: Float
        get() = sp.getFloat("drawerHeight", .68f).coerceIn(.30f, .82f)
        set(value) { sp.edit().putFloat("drawerHeight", value).apply() }

    var iconSize: Int
        get() = sp.getInt("iconSize", 1).coerceIn(0, 2)
        set(value) { sp.edit().putInt("iconSize", value).apply() }

    /** Bit mask of enabled desktop widgets: 1 clock+weather, 2 calendar, 4 system status, 8 quote. */
    var widgetMask: Int
        get() = sp.getInt("widgetMask", 7)
        set(value) { sp.edit().putInt("widgetMask", value).apply() }

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(value) { sp.edit().putBoolean("seeded", value).apply() }

    var weatherCity: String
        get() = sp.getString("weatherCity", "") ?: ""
        set(value) { sp.edit().putString("weatherCity", value).apply() }

    fun loadWeather(): WeatherInfo? {
        if (!sp.contains("wxTime")) return null
        return WeatherInfo(
            sp.getString("wxName", "") ?: "",
            sp.getInt("wxTemp", 0),
            sp.getInt("wxCode", 0),
            sp.getLong("wxTime", 0L)
        )
    }

    fun saveWeather(w: WeatherInfo) {
        sp.edit().putString("wxName", w.city).putInt("wxTemp", w.tempC)
            .putInt("wxCode", w.code).putLong("wxTime", w.fetchedAt).apply()
    }

    fun clearWeather() { sp.edit().remove("wxTime").apply() }
}
