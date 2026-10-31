package com.droiddesk.launcher

import android.graphics.Color

class AccentPreset(val key: String, val labelRes: Int, val color: Int)
class GradientPreset(val key: String, val labelRes: Int, val colors: IntArray)

object Theme {
    val bg: Int = Color.rgb(15, 23, 42)
    val text: Int = Color.rgb(241, 245, 249)
    val muted: Int = Color.rgb(174, 190, 211)
    val panel: Int = Color.rgb(25, 36, 57)
    val panel2: Int = Color.rgb(37, 52, 78)

    /** Current accent colour; set by MainActivity so every helper can read it. */
    var accentColor: Int = Color.rgb(96, 165, 250)

    val accents: List<AccentPreset> = listOf(
        AccentPreset("blue", R.string.accent_blue, Color.rgb(96, 165, 250)),
        AccentPreset("purple", R.string.accent_purple, Color.rgb(168, 85, 247)),
        AccentPreset("teal", R.string.accent_teal, Color.rgb(45, 212, 191)),
        AccentPreset("green", R.string.accent_green, Color.rgb(74, 222, 128)),
        AccentPreset("orange", R.string.accent_orange, Color.rgb(251, 146, 60)),
        AccentPreset("pink", R.string.accent_pink, Color.rgb(244, 114, 182)),
        AccentPreset("red", R.string.accent_red, Color.rgb(239, 68, 68)),
        AccentPreset("yellow", R.string.accent_yellow, Color.rgb(250, 204, 21)),
        AccentPreset("cyan", R.string.accent_cyan, Color.rgb(34, 211, 238)),
        AccentPreset("lime", R.string.accent_lime, Color.rgb(163, 230, 53)),
        AccentPreset("indigo", R.string.accent_indigo, Color.rgb(99, 102, 241)),
        AccentPreset("rose", R.string.accent_rose, Color.rgb(251, 113, 133)),
        AccentPreset("slate", R.string.accent_slate, Color.rgb(148, 163, 184))
    )

    val gradients: List<GradientPreset> = listOf(
        GradientPreset("bluepurple", R.string.grad_bluepurple, intArrayOf(Color.rgb(20, 48, 82), bg, Color.rgb(36, 31, 72))),
        GradientPreset("sunset", R.string.grad_sunset, intArrayOf(Color.rgb(80, 35, 70), Color.rgb(190, 75, 55), Color.rgb(35, 30, 65))),
        GradientPreset("forest", R.string.grad_forest, intArrayOf(Color.rgb(12, 70, 70), Color.rgb(15, 40, 50), Color.rgb(35, 70, 45))),
        GradientPreset("pink", R.string.grad_pink, intArrayOf(Color.rgb(70, 35, 90), Color.rgb(35, 30, 70), Color.rgb(100, 45, 75))),
        GradientPreset("solid", R.string.grad_solid, intArrayOf(bg, bg, bg))
    )

    fun accent(key: String): AccentPreset = accents.firstOrNull { it.key == key } ?: accents[0]
    fun accentFor(key: String, custom: Int): Int = if (key == "custom") custom else accent(key).color
    fun gradient(key: String): GradientPreset = gradients.firstOrNull { it.key == key } ?: gradients[0]
}
