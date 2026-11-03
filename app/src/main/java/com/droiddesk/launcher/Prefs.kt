package com.droiddesk.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Thin wrapper over SharedPreferences. Also migrates the v1.0/v1.1 keys. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("droiddesk", Context.MODE_PRIVATE)

    private fun intP(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sp.getInt(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp.edit().putInt(key, value).apply() }
    }

    private fun boolP(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    }

    private fun strP(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(key, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(key, value).apply() }
    }

    private fun readList(key: String): MutableList<String> =
        (sp.getString(key, "") ?: "").split("|").filter { it.isNotBlank() }.toMutableList()

    private fun writeList(key: String, value: List<String>) {
        sp.edit().putString(key, value.joinToString("|")).apply()
    }

    var shortcuts: MutableList<String>
        get() {
            if (!sp.contains("shortcutList")) {
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

    var accentCustom by intP("accentCustom", Color.rgb(96, 165, 250))
    /** "" = follow the system language; otherwise a BCP-47 tag such as "de" or "pt-BR". */
    var language by strP("language", "")
    var glass by boolP("glassMode", true)
    var gradient by strP("gradientMode", "bluepurple")

    /** null = gradient only; "builtin:N" = embedded wallpaper; otherwise a content:// URI. */
    var wallpaper: String?
        get() = sp.getString("wallpaper", null)
        set(value) {
            if (value == null) sp.edit().remove("wallpaper").apply()
            else sp.edit().putString("wallpaper", value).apply()
        }

    var taskbarSize by intP("taskbarSize", 64)

    var drawerWidth: Float
        get() = sp.getFloat("drawerWidth", .88f).coerceIn(.50f, .98f)
        set(value) { sp.edit().putFloat("drawerWidth", value).apply() }

    var drawerHeight: Float
        get() = sp.getFloat("drawerHeight", .68f).coerceIn(.30f, .82f)
        set(value) { sp.edit().putFloat("drawerHeight", value).apply() }

    var iconSize by intP("iconSize", 1)
    var seeded by boolP("seeded", false)

    // ---- icons
    /** 0 original, 1 circle, 2 squircle, 3 square, 4 teardrop, 5 leaf, 6 hexagon */
    var iconShape by intP("iconShape", 0)
    var iconPack by strP("iconPack", "")
    var showLabels by boolP("showLabels", true)

    // ---- taskbar
    var taskbarAlign by intP("taskbarAlign", 1)          // 0 left, 1 centre
    var showSearch by boolP("showSearch", true)
    var showTaskView by boolP("showTaskView", true)
    var startIcon by intP("startIcon", 0)                // 0 dots, 1 ring, 2 hex
    var activeMode by intP("activeMode", 2)              // 0 off, 1 foreground, 2 recent 30 min, 3 recent 2 h
    var trayMask by intP("trayMask", 31)                 // 1 wifi, 2 bluetooth, 4 volume, 8 bell, 16 battery
    var windowed by boolP("windowed", true)

    // ---- app drawer
    var drawerAlign by intP("drawerAlign", 1)            // 0 left, 1 centre, 2 right
    var drawerCols by intP("drawerCols", 0)              // 0 auto

    // ---- widgets (real Android widgets hosted by AppWidgetHost)
    fun widgetIdList(): MutableList<Int> =
        (sp.getString("hostedWidgets", "") ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()

    private fun writeWidgetIds(l: List<Int>) { sp.edit().putString("hostedWidgets", l.joinToString(",")).apply() }

    fun addWidget(id: Int) {
        val l = widgetIdList()
        if (id !in l) { l.add(id); writeWidgetIds(l) }
    }

    fun removeWidget(id: Int) {
        writeWidgetIds(widgetIdList().filter { it != id })
        sp.edit().remove("gw$id").remove("gh$id").apply()
    }

    fun moveWidget(id: Int, dir: Int) {
        val l = widgetIdList()
        val i = l.indexOf(id)
        val j = i + dir
        if (i < 0 || j < 0 || j >= l.size) return
        l[i] = l[j].also { l[j] = l[i] }
        writeWidgetIds(l)
    }

    fun widgetW(id: Int): Int = sp.getInt("gw$id", 0)
    fun widgetH(id: Int): Int = sp.getInt("gh$id", 0)
    fun setWidgetSize(id: Int, wDp: Int, hDp: Int) { sp.edit().putInt("gw$id", wDp).putInt("gh$id", hDp).apply() }

    // ---- notification centre
    var npWidth by intP("npWidth", 1)                    // 0 narrow, 1 normal, 2 wide
    var npTab by intP("npTab", 0)                        // 0 notifications, 1 quick settings
    var npGroup by boolP("npGroup", true)
    var npPreview by boolP("npPreview", true)
    var npOngoing by boolP("npOngoing", false)
    var npMax by intP("npMax", 20)
    var qsMask by intP("qsMask", 8191)
    var qsShape by intP("qsShape", 0)                    // 0 circle, 1 rounded square

    // ---- mouse / keys
    var rcIcon by intP("rcIcon", 0)                      // 0 menu, 1 open, 2 app info
    var rcDesktop by intP("rcDesktop", 0)                // 0 menu, 1 nothing
    var iconOpen by intP("iconOpen", 0)                  // 0 single click, 1 double click
    var dcEmpty by intP("dcEmpty", 0)                    // 0 nothing, 1 drawer, 2 menu, 3 notifications
    var winKey by intP("winKey", 0)                      // key code, 0 = unset
    var winMods by intP("winMods", 0)                    // 1 alt, 2 ctrl, 4 shift, 8 meta

    // ---- desktop feel
    var drawerMode by intP("drawerMode", 1)              // 0 pages (horizontal), 1 vertical scroll
    var showNav by boolP("showNav", false)               // system navigation buttons on the taskbar
    var recentsMode by intP("recentsMode", 0)            // 0 DroidDesk switcher, 1 system recents
    var tbKey by intP("tbKey", 0)                        // key that brings the taskbar over any app
    var tbMods by intP("tbMods", 0)
    var tbEdge by boolP("tbEdge", false)                 // (legacy, replaced by tbZone)
    var tbZone by intP("tbZone", 0)                      // pointer zone that brings the taskbar back: 0 off, 1 bottom edge, 2 bottom-right, 3 bottom-left
    var recentsPreviews by boolP("recentsPreviews", false)
    var infoMask by intP("infoMask", 15)                 // device info cards in the notification panel
    var keepAwake by boolP("keepAwake", false)

    // ---- search
    var searchFiles by boolP("searchFiles", true)
}
