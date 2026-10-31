package com.droiddesk.launcher

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Full settings screen inside the launcher: tabs with switches, chips, sliders, colour swatches,
 * wallpaper thumbnails and icon-shape previews.
 */
class SettingsPanel(private val a: MainActivity) {
    lateinit var card: FrameLayout
        private set
    var tab = 0
        private set

    private lateinit var tabsRow: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private val thumbs = HashMap<Int, android.graphics.Bitmap>()

    private val tabTitles = intArrayOf(
        R.string.st_appearance, R.string.st_taskbar, R.string.st_drawer, R.string.st_widgets,
        R.string.st_notifications, R.string.st_mouse, R.string.st_search, R.string.st_system
    )

    private fun s(res: Int): String = a.getString(res)

    fun build(): FrameLayout {
        val rootCol = LinearLayout(a)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12))

        val header = LinearLayout(a)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(a.label(s(R.string.settings_title), 20, Theme.text, true), LinearLayout.LayoutParams(0, a.dp(42), 1f))
        val close = a.circleIcon(R.drawable.ic_close, Theme.panel2, Theme.text, 18)
        close.setOnClickListener { a.closeSettings() }
        header.addView(close, llp(a.dp(36), a.dp(36)))
        rootCol.addView(header)

        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        tabsRow = LinearLayout(a)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        hs.addView(tabsRow)
        val hp = LinearLayout.LayoutParams(-1, a.dp(40))
        hp.topMargin = a.dp(4)
        rootCol.addView(hs, hp)

        scroll = ScrollView(a)
        scroll.isVerticalScrollBarEnabled = false
        content = LinearLayout(a)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, a.dp(4), 0, a.dp(16))
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        val sp = LinearLayout.LayoutParams(-1, 0, 1f)
        sp.topMargin = a.dp(8)
        rootCol.addView(scroll, sp)

        card = a.glassCard(rootCol, 24)
        return card
    }

    fun scrollY(): Int = if (::scroll.isInitialized) scroll.scrollY else 0

    private fun refreshKeep() { if (a.currentSettings === this) select(tab, scroll.scrollY) }

    fun select(t: Int, scrollTo: Int = 0) {
        tab = t
        tabsRow.removeAllViews()
        tabTitles.forEachIndexed { i, res ->
            val chip = a.label(s(res), 13, if (i == t) Theme.bg else Theme.text, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(a.dp(14), 0, a.dp(14), 0)
            chip.background = a.rounded(if (i == t) Theme.accentColor else Theme.panel2.withAlpha(180), 17)
            chip.setOnClickListener { select(i, 0) }
            tabsRow.addView(chip, llp(-2, a.dp(34), if (i == 0) 0 else a.dp(6)))
        }
        content.removeAllViews()
        when (t) {
            0 -> appearance()
            1 -> taskbarTab()
            2 -> drawerTab()
            3 -> widgetsTab()
            4 -> notificationsTab()
            5 -> mouseTab()
            6 -> searchTab()
            else -> systemTab()
        }
        scroll.post { scroll.scrollTo(0, scrollTo) }
    }

    // ------------------------------------------------------------------ row builders

    private fun section(res: Int) {
        val t = a.label(s(res).uppercase(), 11, Theme.accentColor, true)
        t.letterSpacing = .1f
        t.setPadding(a.dp(2), a.dp(16), 0, a.dp(6))
        content.addView(t)
    }

    private fun hint(res: Int) {
        val t = a.label(s(res), 12, Theme.muted)
        t.setPadding(a.dp(4), a.dp(2), a.dp(4), a.dp(6))
        content.addView(t)
    }

    private fun box(): LinearLayout {
        val b = LinearLayout(a)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
        b.background = a.rounded(Theme.panel2.withAlpha(140), 14)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = a.dp(6)
        content.addView(b, lp)
        return b
    }

    private fun switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val row = box()
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.addView(a.label(title, 14, Theme.text))
        if (sub.isNotEmpty()) col.addView(a.label(sub, 12, Theme.muted))
        val sw = Switch(a)
        sw.isChecked = on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(Theme.accentColor, Theme.muted))
        sw.trackTintList = ColorStateList(states, intArrayOf(Theme.accentColor.withAlpha(110), Theme.panel))
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
    }

    private fun chipsRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val b = box()
        b.addView(a.label(title, 14, Theme.text))
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        options.forEachIndexed { i, text ->
            val chip = a.label(text, 13, if (i == selected) Theme.bg else Theme.text, i == selected)
            chip.gravity = Gravity.CENTER
            chip.setPadding(a.dp(14), 0, a.dp(14), 0)
            chip.background = a.rounded(if (i == selected) Theme.accentColor else Theme.panel.withAlpha(220), 17)
            chip.setOnClickListener {
                onSelect(i)
                refreshKeep()
            }
            row.addView(chip, llp(-2, a.dp(34), if (i == 0) 0 else a.dp(6)))
        }
        hs.addView(row)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = a.dp(8)
        b.addView(hs, lp)
    }

    private fun sliderRow(title: String, value: Int, min: Int, max: Int, fmt: (Int) -> String, onDone: (Int) -> Unit) {
        val b = box()
        val head = LinearLayout(a)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(a.label(title, 14, Theme.text), LinearLayout.LayoutParams(0, -2, 1f))
        val valueLabel = a.label(fmt(value), 13, Theme.accentColor, true)
        head.addView(valueLabel)
        b.addView(head)
        val seek = SeekBar(a)
        seek.max = max - min
        seek.progress = (value - min).coerceIn(0, max - min)
        seek.progressTintList = ColorStateList.valueOf(Theme.accentColor)
        seek.thumbTintList = ColorStateList.valueOf(Theme.accentColor)
        seek.progressBackgroundTintList = ColorStateList.valueOf(Theme.panel)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueLabel.text = fmt(min + progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { onDone(min + seek.progress) }
        })
        b.addView(seek)
    }

    private fun actionRow(title: String, sub: String, iconRes: Int, onClick: () -> Unit) {
        val row = box()
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isClickable = true
        val ic = a.circleIcon(iconRes, Theme.accentColor.withAlpha(60), Theme.accentColor, 18)
        ic.isClickable = false
        row.addView(ic, LinearLayout.LayoutParams(a.dp(36), a.dp(36)))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.addView(a.label(title, 14, Theme.text))
        if (sub.isNotEmpty()) col.addView(a.label(sub, 12, Theme.muted))
        val cp = LinearLayout.LayoutParams(0, -2, 1f)
        cp.leftMargin = a.dp(12)
        row.addView(col, cp)
        row.addView(a.iconView(R.drawable.ic_chevron_right, Theme.muted), LinearLayout.LayoutParams(a.dp(18), a.dp(18)))
        row.setOnClickListener { onClick() }
        a.hoverable(row, 14)
    }

    private fun swatchRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
        val b = box()
        val grid = GridLayout(a)
        grid.columnCount = 7
        colors.forEachIndexed { i, color ->
            val cell = FrameLayout(a)
            val d = android.graphics.drawable.GradientDrawable()
            d.shape = android.graphics.drawable.GradientDrawable.OVAL
            d.setColor(color)
            if (i == selected) d.setStroke(a.dp(3), Color.WHITE)
            cell.background = d
            if (i == selected) {
                val t = a.label("✓", 16, Theme.bg, true)
                t.gravity = Gravity.CENTER
                cell.addView(t, FrameLayout.LayoutParams(-1, -1))
            }
            cell.isClickable = true
            cell.setOnClickListener {
                onSelect(i)
                refreshKeep()
            }
            val p = GridLayout.LayoutParams()
            p.width = a.dp(38)
            p.height = a.dp(38)
            p.setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
            grid.addView(cell, p)
        }
        b.addView(grid)
    }

    // ------------------------------------------------------------------ tabs

    private fun appearance() {
        section(R.string.sec_colors)
        val colors = Theme.accents.map { it.color } + listOf(a.prefs.accentCustom)
        val sel = if (a.prefs.accent == "custom") colors.size - 1 else Theme.accents.indexOfFirst { it.key == a.prefs.accent }.coerceAtLeast(0)
        swatchRow(colors, sel) { i ->
            if (i < Theme.accents.size) {
                a.prefs.accent = Theme.accents[i].key
                a.onAppearanceChanged()
            } else {
                customAccentDialog()
            }
        }
        actionRow(s(R.string.accent_custom), s(R.string.accent_custom_sub), R.drawable.ic_tune) { customAccentDialog() }

        section(R.string.sec_background)
        wallpaperGallery()
        chipsRow(
            s(R.string.set_gradient), Theme.gradients.map { s(it.labelRes) },
            Theme.gradients.indexOfFirst { it.key == a.prefs.gradient }.coerceAtLeast(0)
        ) { i ->
            a.prefs.gradient = Theme.gradients[i].key
            a.prefs.wallpaper = null
            a.onAppearanceChanged()
        }
        switchRow(s(R.string.set_glass), s(R.string.set_glass_sub), a.prefs.glass) {
            a.prefs.glass = it
            a.onAppearanceChanged()
        }

        section(R.string.sec_icons)
        shapeRow()
        val packs = IconPacks.list(a)
        val packNames = listOf(s(R.string.pack_default)) + packs.map { it.label }
        val packIdx = packs.indexOfFirst { it.pkg == a.prefs.iconPack } + 1
        chipsRow(s(R.string.set_icon_pack), packNames, packIdx) { i ->
            a.prefs.iconPack = if (i == 0) "" else packs[i - 1].pkg
            a.reloadApps()
        }
        if (packs.isEmpty()) hint(R.string.pack_none)
        switchRow(s(R.string.set_show_labels), "", a.prefs.showLabels) {
            a.prefs.showLabels = it
            a.applyIconPrefs()
        }
        chipsRow(
            s(R.string.set_desktop_icons),
            listOf(s(R.string.view_small), s(R.string.view_medium), s(R.string.view_large)), a.prefs.iconSize
        ) { i ->
            a.prefs.iconSize = i
            a.applyIconPrefs()
        }
    }

    private fun wallpaperGallery() {
        val b = box()
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        val cur = a.prefs.wallpaper
        for (i in 0 until Wallpapers.names.size) {
            val bmp = thumbs.getOrPut(i) { Wallpapers.render(i, 240, 135) }
            val img = ImageView(a)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setImageBitmap(bmp)
            row.addView(thumbTile(img, s(Wallpapers.names[i]), cur == "builtin:$i") {
                a.prefs.wallpaper = "builtin:$i"
                a.loadWallpaper("builtin:$i", false)
                refreshKeep()
            }, llp(-2, -2, if (i == 0) 0 else a.dp(8)))
        }
        val photo = FrameLayout(a)
        photo.setBackgroundColor(Theme.panel)
        photo.addView(a.iconView(R.drawable.ic_image, Theme.accentColor), FrameLayout.LayoutParams(a.dp(30), a.dp(30), Gravity.CENTER))
        val photoSel = cur != null && !cur.startsWith("builtin:")
        row.addView(thumbTile(photo, s(R.string.wp_photo), photoSel) { a.pickWallpaper() }, llp(-2, -2, a.dp(8)))
        hs.addView(row)
        b.addView(hs)
    }

    private fun thumbTile(inner: View, name: String, selected: Boolean, onClick: () -> Unit): View {
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val frame = FrameLayout(a)
        frame.setPadding(a.dp(3), a.dp(3), a.dp(3), a.dp(3))
        frame.background = a.rounded(0, 14, if (selected) Theme.accentColor else 0, 2)
        inner.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, a.dp(11).toFloat()) }
        }
        inner.clipToOutline = true
        frame.addView(inner, FrameLayout.LayoutParams(a.dp(112), a.dp(63)))
        col.addView(frame)
        val t = a.label(name, 11, if (selected) Theme.accentColor else Theme.muted, selected)
        t.gravity = Gravity.CENTER
        col.addView(t, LinearLayout.LayoutParams(-2, a.dp(22)))
        col.isClickable = true
        col.setOnClickListener { onClick() }
        return col
    }

    private fun shapeRow() {
        val b = box()
        b.addView(a.label(s(R.string.set_icon_shape), 14, Theme.text))
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in IconStyler.names.indices) {
            val col = LinearLayout(a)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(4))
            val sel = a.prefs.iconShape == i
            col.background = a.rounded(if (sel) Theme.accentColor.withAlpha(60) else 0, 12, if (sel) Theme.accentColor else 0, 2)
            val img = ImageView(a)
            img.setImageBitmap(IconStyler.preview(i, a.dp(44), Theme.accentColor))
            col.addView(img, LinearLayout.LayoutParams(a.dp(44), a.dp(44)))
            col.addView(a.label(s(IconStyler.names[i]), 10, Theme.text), LinearLayout.LayoutParams(-2, a.dp(20)))
            col.isClickable = true
            col.setOnClickListener {
                a.prefs.iconShape = i
                a.reloadApps()
                refreshKeep()
            }
            row.addView(col, llp(-2, -2, if (i == 0) 0 else a.dp(6)))
        }
        hs.addView(row)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = a.dp(8)
        b.addView(hs, lp)
    }

    private fun taskbarTab() {
        section(R.string.st_taskbar)
        sliderRow(s(R.string.set_taskbar), a.prefs.taskbarSize, 54, 82, { "$it dp" }) {
            a.prefs.taskbarSize = it
            a.rebuildUi()
        }
        chipsRow(s(R.string.set_align), listOf(s(R.string.align_left), s(R.string.align_center)), a.prefs.taskbarAlign) {
            a.prefs.taskbarAlign = it
            a.taskbar.layout()
        }
        startIconRow()
        switchRow(s(R.string.set_nav), s(R.string.set_nav_sub), a.prefs.showNav) {
            a.prefs.showNav = it
            a.taskbar.layout()
        }
        chipsRow(
            s(R.string.set_recents_mode),
            listOf(s(R.string.recents_dc), s(R.string.recents_sys)), a.prefs.recentsMode
        ) { a.prefs.recentsMode = it }
        switchRow(s(R.string.set_task_view), s(R.string.set_task_view_sub), a.prefs.showTaskView) {
            a.prefs.showTaskView = it
            a.taskbar.render()
        }
        switchRow(s(R.string.set_windowed), s(R.string.set_windowed_sub), a.prefs.windowed) { a.prefs.windowed = it }

        section(R.string.sec_active)
        chipsRow(
            s(R.string.set_active),
            listOf(s(R.string.active_off), s(R.string.active_fg), s(R.string.active_30), s(R.string.active_120)), a.prefs.activeMode
        ) {
            a.prefs.activeMode = it
            a.updateActive()
        }
        val granted = ActiveApps.hasAccess(a)
        actionRow(
            s(R.string.usage_access),
            s(if (granted) R.string.status_granted else R.string.status_not_granted) + " · " + s(R.string.usage_access_sub),
            R.drawable.ic_display
        ) { a.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        hint(R.string.active_hint)

        section(R.string.sec_tray)
        val trayItems = listOf(
            1 to R.string.tile_internet, 2 to R.string.tile_bluetooth, 4 to R.string.tile_sound,
            8 to R.string.tab_notifications, 16 to R.string.tile_battery
        )
        trayItems.forEach { (bit, res) ->
            switchRow(s(res), "", (a.prefs.trayMask and bit) != 0) { on ->
                a.prefs.trayMask = if (on) a.prefs.trayMask or bit else a.prefs.trayMask and bit.inv()
                a.taskbar.layout()
            }
        }
        hint(R.string.reorder_hint)
    }

    /** App-drawer button icons, shown as previews. */
    private fun startIconRow() {
        val b = box()
        b.addView(a.label(s(R.string.set_start_icon), 14, Theme.text))
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in StartIcons.res.indices) {
            val col = LinearLayout(a)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(4))
            val sel = a.prefs.startIcon == i
            col.background = a.rounded(if (sel) Theme.accentColor.withAlpha(60) else Theme.panel.withAlpha(200), 12, if (sel) Theme.accentColor else 0, 2)
            col.addView(a.iconView(StartIcons.res[i], Theme.accentColor), LinearLayout.LayoutParams(a.dp(30), a.dp(30)))
            col.addView(a.label(s(StartIcons.names[i]), 10, Theme.text), LinearLayout.LayoutParams(-2, a.dp(20)))
            col.isClickable = true
            col.setOnClickListener {
                a.prefs.startIcon = i
                a.taskbar.render()
                refreshKeep()
            }
            row.addView(col, llp(-2, -2, if (i == 0) 0 else a.dp(6)))
        }
        hs.addView(row)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = a.dp(8)
        b.addView(hs, lp)
    }

    private fun drawerTab() {
        section(R.string.st_drawer)
        chipsRow(
            s(R.string.set_drawer_mode),
            listOf(s(R.string.mode_scroll), s(R.string.mode_pages)), a.prefs.drawerMode
        ) {
            a.prefs.drawerMode = it
            a.applyDrawerPrefs()
        }
        chipsRow(
            s(R.string.set_drawer_pos),
            listOf(s(R.string.align_left), s(R.string.align_center), s(R.string.align_right)), a.prefs.drawerAlign
        ) {
            a.prefs.drawerAlign = it
            a.applyDrawerPrefs()
        }
        sliderRow(s(R.string.set_drawer_w), (a.prefs.drawerWidth * 100).toInt(), 50, 98, { "$it%" }) {
            a.prefs.drawerWidth = it / 100f
            a.applyDrawerPrefs()
        }
        sliderRow(s(R.string.set_drawer_h), (a.prefs.drawerHeight * 100).toInt(), 30, 82, { "$it%" }) {
            a.prefs.drawerHeight = it / 100f
            a.applyDrawerPrefs()
        }
        chipsRow(
            s(R.string.set_drawer_cols),
            listOf(s(R.string.cols_auto), "4", "5", "6"),
            when (a.prefs.drawerCols) { 4 -> 1; 5 -> 2; 6 -> 3; else -> 0 }
        ) {
            a.prefs.drawerCols = if (it == 0) 0 else it + 3
            a.applyDrawerPrefs()
        }
        switchRow(s(R.string.set_show_labels), "", a.prefs.showLabels) {
            a.prefs.showLabels = it
            a.applyIconPrefs()
        }
        hint(R.string.drawer_hint)
    }

    private fun widgetsTab() {
        section(R.string.st_widgets)
        val items = listOf(
            1 to R.string.widget_clock, 2 to R.string.widget_calendar,
            4 to R.string.widget_system, 8 to R.string.widget_quote
        )
        items.forEach { (bit, res) ->
            switchRow(s(res), "", (a.prefs.widgetMask and bit) != 0) { on ->
                a.prefs.widgetMask = if (on) a.prefs.widgetMask or bit else a.prefs.widgetMask and bit.inv()
                a.widgets.rebuild()
                a.onRootResized()
            }
        }
        actionRow(s(R.string.widget_resize), s(R.string.widget_resize_sub), R.drawable.ic_maximize) {
            a.closeSettings()
            a.widgets.setEdit(true)
        }
        actionRow(s(R.string.set_weather), a.prefs.weatherCity.ifBlank { s(R.string.weather_set_city) }, R.drawable.ic_widgets) {
            a.widgets.askCity()
        }
        actionRow(s(R.string.widget_remove_all), s(R.string.widget_remove_all_sub), R.drawable.ic_close) {
            a.prefs.widgetMask = 0
            a.widgets.rebuild()
            a.onRootResized()
            refreshKeep()
        }
        actionRow(s(R.string.widget_restore), s(R.string.widget_restore_sub), R.drawable.ic_refresh) {
            a.widgets.restoreDefaults()
            refreshKeep()
        }
    }

    private fun notificationsTab() {
        section(R.string.st_notifications)
        chipsRow(
            s(R.string.set_np_width),
            listOf(s(R.string.w_narrow), s(R.string.w_normal), s(R.string.w_wide)), a.prefs.npWidth
        ) {
            a.prefs.npWidth = it
            a.updateOverlayLayout()
        }
        chipsRow(
            s(R.string.set_np_tab),
            listOf(s(R.string.tab_notifications), s(R.string.tab_quick)), a.prefs.npTab
        ) { a.prefs.npTab = it }
        switchRow(s(R.string.set_np_group), s(R.string.set_np_group_sub), a.prefs.npGroup) {
            a.prefs.npGroup = it
            a.notif.refresh()
        }
        switchRow(s(R.string.set_np_preview), s(R.string.set_np_preview_sub), a.prefs.npPreview) {
            a.prefs.npPreview = it
            a.notif.refresh()
        }
        switchRow(s(R.string.set_np_ongoing), s(R.string.set_np_ongoing_sub), a.prefs.npOngoing) {
            a.prefs.npOngoing = it
            a.notif.refresh()
        }
        chipsRow(
            s(R.string.set_np_max), listOf("10", "20", "50"),
            when (a.prefs.npMax) { 10 -> 0; 50 -> 2; else -> 1 }
        ) {
            a.prefs.npMax = if (it == 0) 10 else if (it == 2) 50 else 20
            a.notif.refresh()
        }

        section(R.string.sec_quick)
        val tiles = listOf(
            1 to R.string.tile_internet, 2 to R.string.tile_bluetooth, 4 to R.string.tile_airplane,
            8 to R.string.tile_display, 16 to R.string.tile_sound, 32 to R.string.tile_battery,
            64 to R.string.tile_power, 128 to R.string.tile_settings
        )
        tiles.forEach { (bit, res) ->
            switchRow(s(res), "", (a.prefs.qsMask and bit) != 0) { on ->
                a.prefs.qsMask = if (on) a.prefs.qsMask or bit else a.prefs.qsMask and bit.inv()
                a.notif.buildQuick()
            }
        }
        chipsRow(
            s(R.string.set_qs_shape), listOf(s(R.string.shape_circle), s(R.string.shape_squircle)), a.prefs.qsShape
        ) {
            a.prefs.qsShape = it
            a.notif.buildQuick()
        }
        actionRow(s(R.string.set_notifications), s(R.string.set_notifications_sub), R.drawable.ic_bell) {
            a.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private val tbPresets = listOf(
        Triple("Win + Space", KeyEvent.KEYCODE_SPACE, 8),
        Triple("Alt + Space", KeyEvent.KEYCODE_SPACE, 1),
        Triple("Ctrl + Alt + T", KeyEvent.KEYCODE_T, 3),
        Triple("F9", KeyEvent.KEYCODE_F9, 0)
    )

    private val winPresets = listOf(
        Triple("Win + ↓", KeyEvent.KEYCODE_DPAD_DOWN, 8),
        Triple("Alt + ↓", KeyEvent.KEYCODE_DPAD_DOWN, 1),
        Triple("Ctrl + Alt + ↓", KeyEvent.KEYCODE_DPAD_DOWN, 3),
        Triple("Alt + F10", KeyEvent.KEYCODE_F10, 1)
    )

    private fun mouseTab() {
        section(R.string.st_mouse)
        chipsRow(
            s(R.string.set_rc_icon),
            listOf(s(R.string.rc_menu), s(R.string.rc_open), s(R.string.rc_info)), a.prefs.rcIcon
        ) { a.prefs.rcIcon = it }
        chipsRow(
            s(R.string.set_rc_desktop),
            listOf(s(R.string.rc_menu), s(R.string.rc_nothing)), a.prefs.rcDesktop
        ) { a.prefs.rcDesktop = it }
        chipsRow(
            s(R.string.set_icon_open),
            listOf(s(R.string.open_single), s(R.string.open_double)), a.prefs.iconOpen
        ) { a.prefs.iconOpen = it }
        chipsRow(
            s(R.string.set_dc_empty),
            listOf(s(R.string.dc_nothing), s(R.string.dc_drawer), s(R.string.dc_menu), s(R.string.dc_notifications)),
            a.prefs.dcEmpty
        ) { a.prefs.dcEmpty = it }
        hint(R.string.mouse_hint)

        section(R.string.sec_keys)
        actionRow(s(R.string.set_taskbar_key), describe(a.prefs.tbKey, a.prefs.tbMods), R.drawable.ic_nav_square) {
            pickKey(R.string.set_taskbar_key, a.prefs.tbKey, a.prefs.tbMods, tbPresets) { c, m ->
                a.prefs.tbKey = c
                a.prefs.tbMods = m
            }
        }
        hint(R.string.taskbar_key_hint)
        switchRow(s(R.string.set_edge), s(R.string.set_edge_sub), a.prefs.tbEdge) { on ->
            a.prefs.tbEdge = on
            DroidBridge.accessibility?.let { it.overlays.refreshEdge() }
        }
        actionRow(s(R.string.set_window_key), describe(a.prefs.winKey, a.prefs.winMods), R.drawable.ic_maximize) {
            pickKey(R.string.set_window_key, a.prefs.winKey, a.prefs.winMods, winPresets) { c, m ->
                a.prefs.winKey = c
                a.prefs.winMods = m
            }
        }
        hint(R.string.window_key_hint)
        actionRow(s(R.string.set_keyboard), s(R.string.set_keyboard_sub), R.drawable.ic_display) {
            a.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        actionRow(s(R.string.dev_options), s(R.string.dev_options_sub), R.drawable.ic_settings) {
            a.safeStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }

        section(R.string.sec_shortcuts)
        val b = box()
        b.addView(a.label(s(R.string.shortcuts_list), 12, Theme.text))
    }

    private fun searchTab() {
        section(R.string.st_search)
        switchRow(s(R.string.set_show_search), s(R.string.set_show_search_sub), a.prefs.showSearch) {
            a.prefs.showSearch = it
            a.taskbar.render()
        }
        switchRow(s(R.string.set_search_files), s(R.string.set_search_files_sub), a.prefs.searchFiles) {
            a.prefs.searchFiles = it
        }
        val ok = FileIndex.hasAccess(a)
        actionRow(
            s(R.string.file_access),
            s(if (ok) R.string.status_granted else R.string.status_not_granted) + " · " + s(R.string.file_access_sub),
            R.drawable.ic_folder
        ) { a.requestFileAccess() }
        actionRow(s(R.string.reindex), s(R.string.reindex_sub), R.drawable.ic_refresh) {
            FileIndex.invalidate()
            a.toast(R.string.toast_reindexing)
        }
    }

    private fun systemTab() {
        section(R.string.st_system)
        actionRow(s(R.string.set_keyboard), s(R.string.set_keyboard_sub), R.drawable.ic_display) {
            a.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        actionRow(s(R.string.usage_access), s(R.string.usage_access_sub), R.drawable.ic_recents) {
            a.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        actionRow(s(R.string.set_notifications), s(R.string.set_notifications_sub), R.drawable.ic_bell) {
            a.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        actionRow(s(R.string.file_access), s(R.string.file_access_sub), R.drawable.ic_folder) { a.requestFileAccess() }
        actionRow(s(R.string.set_default_launcher), "", R.drawable.ic_apps) {
            a.safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        actionRow(s(R.string.android_settings), "", R.drawable.ic_settings) { a.openAndroidSettings() }
        actionRow(s(R.string.dev_options), s(R.string.dev_options_sub), R.drawable.ic_tune) {
            a.safeStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        actionRow(s(R.string.reset_layout), s(R.string.reset_layout_sub), R.drawable.ic_refresh) {
            AlertDialog.Builder(a)
                .setMessage(R.string.confirm_clear)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear) { _, _ -> a.resetLayout() }
                .show()
        }
    }

    // ------------------------------------------------------------------ dialogs

    private fun customAccentDialog() {
        val hsv = FloatArray(3)
        Color.colorToHSV(a.prefs.accentCustom, hsv)
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(4))
        val preview = View(a)
        preview.background = a.rounded(Color.HSVToColor(hsv), 12)
        col.addView(preview, LinearLayout.LayoutParams(-1, a.dp(44)))
        fun update() { preview.background = a.rounded(Color.HSVToColor(hsv), 12) }
        fun seek(max: Int, value: Int, onChange: (Int) -> Unit) {
            val sb = SeekBar(a)
            sb.max = max
            sb.progress = value.coerceIn(0, max)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    onChange(progress)
                    update()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = a.dp(10)
            col.addView(sb, lp)
        }
        seek(360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        seek(100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        seek(100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }
        AlertDialog.Builder(a)
            .setTitle(R.string.accent_custom)
            .setView(col)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                a.prefs.accentCustom = Color.HSVToColor(hsv)
                a.prefs.accent = "custom"
                a.onAppearanceChanged()
            }
            .show()
    }

    private fun modsOf(e: KeyEvent): Int =
        (if (e.isAltPressed) 1 else 0) or (if (e.isCtrlPressed) 2 else 0) or
            (if (e.isShiftPressed) 4 else 0) or (if (e.isMetaPressed) 8 else 0)

    private fun isModifier(code: Int): Boolean = code in listOf(
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT
    )

    private fun describe(code: Int, mods: Int): String {
        if (code == 0) return s(R.string.key_not_set)
        val sb = StringBuilder()
        if ((mods and 8) != 0) sb.append("Win + ")
        if ((mods and 2) != 0) sb.append("Ctrl + ")
        if ((mods and 1) != 0) sb.append("Alt + ")
        if ((mods and 4) != 0) sb.append("Shift + ")
        sb.append(KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"))
        return sb.toString()
    }

    private fun modsText(mods: Int): String {
        val sb = StringBuilder()
        if ((mods and 8) != 0) sb.append("Win + ")
        if ((mods and 2) != 0) sb.append("Ctrl + ")
        if ((mods and 1) != 0) sb.append("Alt + ")
        if ((mods and 4) != 0) sb.append("Shift + ")
        return sb.toString()
    }

    /**
     * Key picker screen: shows the combination live while you press it on a physical keyboard, offers
     * ready-made presets, and can clear the binding.
     */
    private fun pickKey(titleRes: Int, code0: Int, mods0: Int, presets: List<Triple<String, Int, Int>>, onSave: (Int, Int) -> Unit) {
        var code = code0
        var mods = mods0
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(4))
        val display = a.label(describe(code, mods), 22, Theme.accentColor, true)
        display.gravity = Gravity.CENTER
        display.background = a.rounded(Theme.panel2, 16)
        col.addView(display, LinearLayout.LayoutParams(-1, a.dp(76)))
        val msg = a.label(s(R.string.key_record_msg), 12, Theme.muted)
        msg.setPadding(a.dp(2), a.dp(10), a.dp(2), a.dp(8))
        col.addView(msg)
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        presets.forEachIndexed { i, p ->
            val chip = a.label(p.first, 13, Theme.text, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(a.dp(14), 0, a.dp(14), 0)
            chip.background = a.rounded(Theme.panel2, 17)
            chip.setOnClickListener {
                code = p.second
                mods = p.third
                display.text = describe(code, mods)
            }
            row.addView(chip, llp(-2, a.dp(34), if (i == 0) 0 else a.dp(6)))
        }
        hs.addView(row)
        col.addView(hs)
        val dlg = AlertDialog.Builder(a)
            .setTitle(titleRes)
            .setView(col)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.key_clear) { _, _ ->
                onSave(0, 0)
                refreshKeep()
            }
            .setPositiveButton(R.string.save) { _, _ ->
                if (code != 0) onSave(code, mods)
                refreshKeep()
            }
            .create()
        dlg.setOnKeyListener { _, keyCode, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && keyCode != KeyEvent.KEYCODE_BACK) {
                if (isModifier(keyCode)) {
                    mods = modsOf(ev)
                    display.text = modsText(mods) + "…"
                } else {
                    code = keyCode
                    mods = modsOf(ev)
                    display.text = describe(code, mods)
                }
                true
            } else {
                false
            }
        }
        dlg.show()
    }
}
