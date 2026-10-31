package com.droiddesk.launcher

import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import java.util.Date
import kotlin.math.abs

/**
 * The taskbar: start button, optional search, pinned + active apps, and the system tray.
 * Active apps get a light under their icon; long-press an app, then drag it sideways to reorder.
 */
class Taskbar(private val a: MainActivity) {
    lateinit var card: FrameLayout
        private set
    lateinit var trayBox: LinearLayout
        private set
    var startView: View? = null
        private set
    var expanded = true

    private lateinit var centerScroll: HorizontalScrollView
    private lateinit var items: LinearLayout
    private lateinit var chevron: ImageView
    private lateinit var wifi: FrameLayout
    private lateinit var bt: FrameLayout
    private lateinit var vol: FrameLayout
    private lateinit var bellWrap: FrameLayout
    private lateinit var bell: FrameLayout
    private lateinit var badge: TextView
    private lateinit var bat: FrameLayout
    private lateinit var clock: TextView

    private var active: Map<String, Int> = emptyMap()
    private val cells = ArrayList<FrameLayout>()
    private val cellPkgs = ArrayList<String>()
    private var dragIndex = -1
    private val slop by lazy { ViewConfiguration.get(a).scaledTouchSlop }

    fun build(): FrameLayout {
        val bar = FrameLayout(a)
        centerScroll = HorizontalScrollView(a)
        centerScroll.isHorizontalScrollBarEnabled = false
        centerScroll.overScrollMode = View.OVER_SCROLL_NEVER
        items = LinearLayout(a)
        items.orientation = LinearLayout.HORIZONTAL
        items.gravity = Gravity.CENTER_VERTICAL
        centerScroll.addView(items, FrameLayout.LayoutParams(-2, -1))
        bar.addView(centerScroll, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER))

        trayBox = LinearLayout(a)
        trayBox.orientation = LinearLayout.HORIZONTAL
        trayBox.gravity = Gravity.CENTER_VERTICAL
        trayBox.setPadding(a.dp(4), 0, a.dp(12), 0)

        chevron = a.iconView(R.drawable.ic_chevron_up, Theme.muted)
        chevron.setOnClickListener { expanded = !expanded; layout() }
        trayBox.addView(chevron, llp(a.dp(28), a.dp(28)))

        wifi = a.circleIcon(R.drawable.ic_wifi, Theme.panel2, Theme.text, 18)
        wifi.setOnClickListener { a.openInternetPanel() }
        wifi.tooltipText = a.getString(R.string.tile_internet)
        bt = a.circleIcon(R.drawable.ic_bluetooth, Theme.panel2, Theme.text, 18)
        bt.setOnClickListener { a.openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) }
        bt.tooltipText = a.getString(R.string.tile_bluetooth)
        vol = a.circleIcon(R.drawable.ic_volume, Theme.panel2, Theme.text, 18)
        vol.setOnClickListener { a.openVolumePanel() }
        vol.tooltipText = a.getString(R.string.tile_sound)

        bellWrap = FrameLayout(a)
        bell = a.circleIcon(R.drawable.ic_bell, Theme.panel2, Theme.text, 18)
        bell.setOnClickListener { a.toggleNotifications(0) }
        bell.tooltipText = a.getString(R.string.tab_notifications)
        bellWrap.addView(bell, FrameLayout.LayoutParams(a.dp(34), a.dp(34), Gravity.CENTER))
        badge = a.label("0", 9, Color.WHITE, true)
        badge.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.rgb(239, 68, 68))
        badge.background = bg
        badge.visibility = View.GONE
        bellWrap.addView(badge, FrameLayout.LayoutParams(a.dp(16), a.dp(16), Gravity.TOP or Gravity.END))

        bat = a.circleIcon(R.drawable.ic_battery, Theme.panel2, Theme.text, 18)
        bat.setOnClickListener { a.openSettings(android.content.Intent.ACTION_POWER_USAGE_SUMMARY) }
        bat.tooltipText = a.getString(R.string.tile_battery)

        trayBox.addView(wifi, llp(a.dp(34), a.dp(34), a.dp(6)))
        trayBox.addView(bt, llp(a.dp(34), a.dp(34), a.dp(6)))
        trayBox.addView(vol, llp(a.dp(34), a.dp(34), a.dp(6)))
        trayBox.addView(bellWrap, llp(a.dp(36), a.dp(36), a.dp(6)))
        trayBox.addView(bat, llp(a.dp(34), a.dp(34), a.dp(6)))

        clock = a.label("", 13, Theme.text, true)
        clock.gravity = Gravity.CENTER
        clock.setPadding(a.dp(10), 0, 0, 0)
        clock.setOnClickListener { a.toggleNotifications(a.prefs.npTab) }
        trayBox.addView(clock, llp(-2, -1))
        bar.addView(trayBox, FrameLayout.LayoutParams(-2, -1, Gravity.END or Gravity.CENTER_VERTICAL))

        card = a.glassCard(bar, 22)
        return card
    }

    /** Show/hide tray icons per settings, and keep the app group clear of the tray. */
    fun layout() {
        if (!::trayBox.isInitialized) return
        val m = a.prefs.trayMask
        wifi.visibility = vis((m and 1) != 0)
        bt.visibility = vis((m and 2) != 0 && expanded)
        vol.visibility = vis((m and 4) != 0 && expanded)
        bellWrap.visibility = vis((m and 8) != 0)
        bat.visibility = vis((m and 16) != 0)
        chevron.rotation = if (expanded) 180f else 0f
        trayBox.measure(0, 0)
        val margin = trayBox.measuredWidth + a.dp(8)
        val lp = centerScroll.layoutParams as FrameLayout.LayoutParams
        if (a.prefs.taskbarAlign == 1) {
            lp.gravity = Gravity.CENTER
            lp.leftMargin = margin
            lp.rightMargin = margin
        } else {
            lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            lp.leftMargin = a.dp(6)
            lp.rightMargin = margin
        }
        centerScroll.layoutParams = lp
    }

    private fun vis(on: Boolean): Int = if (on) View.VISIBLE else View.GONE

    fun setActive(map: Map<String, Int>) {
        if (map == active) return
        active = map
        render()
    }

    fun render() {
        if (!::items.isInitialized) return
        items.removeAllViews()
        cells.clear()
        cellPkgs.clear()
        val accent = Theme.accentColor

        val start = FrameLayout(a)
        start.isClickable = true
        start.isFocusable = true
        val startRes = when (a.prefs.startIcon) {
            1 -> R.drawable.ic_start_ring
            2 -> R.drawable.ic_start_hex
            else -> R.drawable.ic_start_dots
        }
        start.addView(a.iconView(startRes, accent), FrameLayout.LayoutParams(a.dp(24), a.dp(24), Gravity.CENTER))
        start.setOnClickListener { a.toggleDrawer() }
        start.tooltipText = a.getString(R.string.apps)
        a.hoverable(start)
        startView = start
        items.addView(start, LinearLayout.LayoutParams(a.dp(48), -1))

        if (a.prefs.showSearch) {
            items.addView(fixedIcon(R.drawable.ic_search, R.string.search_tooltip) { a.openDrawer(true) }, LinearLayout.LayoutParams(a.dp(48), -1))
        }

        val pinned = a.pinned
        val running = active.keys
            .filter { it !in pinned && a.appMap.containsKey(it) }
            .sortedByDescending { active[it] ?: 0 }
            .take(8)
        for (pkg in pinned + running) {
            val app = a.appMap[pkg] ?: continue
            val level = active[pkg] ?: 0
            val cell = FrameLayout(a)
            cell.isClickable = true
            cell.tooltipText = app.label
            a.hoverable(cell)
            val icon = ImageView(a)
            icon.setImageBitmap(app.icon)
            icon.setPadding(a.dp(3), a.dp(3), a.dp(3), a.dp(3))
            icon.contentDescription = app.label
            cell.addView(icon, FrameLayout.LayoutParams(a.dp(36), a.dp(36), Gravity.CENTER))
            if (level > 0) addLight(cell, level, accent)
            attach(cell, app)
            items.addView(cell, LinearLayout.LayoutParams(a.dp(48), -1))
            cells.add(cell)
            cellPkgs.add(pkg)
        }

        if (a.prefs.showTaskView) {
            items.addView(fixedIcon(R.drawable.ic_recents, R.string.task_view) { a.altTab() }, LinearLayout.LayoutParams(a.dp(48), -1))
        }
        a.updateScrim()
    }

    /** The light under an active app: a glow plus a bar (long = in front, short = used recently). */
    private fun addLight(cell: FrameLayout, level: Int, accent: Int) {
        val glow = View(a)
        val g = GradientDrawable()
        g.gradientType = GradientDrawable.RADIAL_GRADIENT
        g.gradientRadius = a.dp(16).toFloat()
        g.colors = intArrayOf(accent.withAlpha(if (level == 2) 150 else 70), accent.withAlpha(0))
        glow.background = g
        cell.addView(glow, FrameLayout.LayoutParams(a.dp(40), a.dp(18), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        val bar = View(a)
        bar.background = a.rounded(accent.withAlpha(if (level == 2) 255 else 150), 2)
        val lp = FrameLayout.LayoutParams(a.dp(if (level == 2) 18 else 8), a.dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = a.dp(2)
        cell.addView(bar, lp)
    }

    private fun fixedIcon(res: Int, tooltipRes: Int, action: () -> Unit): FrameLayout {
        val cell = FrameLayout(a)
        cell.isClickable = true
        cell.isFocusable = true
        cell.tooltipText = a.getString(tooltipRes)
        cell.addView(a.iconView(res, Theme.text), FrameLayout.LayoutParams(a.dp(22), a.dp(22), Gravity.CENTER))
        cell.setOnClickListener { action() }
        a.hoverable(cell)
        return cell
    }

    // ------------------------------------------------------------------ touch: tap, long-press drag, right-click

    private fun attach(cell: FrameLayout, app: AppEntry) {
        var downX = 0f
        var moved = false
        var held = false
        var pending: Runnable? = null
        cell.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    moved = false
                    held = false
                    if ((ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                        held = true
                        moved = true
                        a.showAppMenu(app, false)
                    } else {
                        val r = Runnable {
                            held = true
                            startDrag(cell)
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        pending = r
                        a.handler.postDelayed(r, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    if (held && dragIndex >= 0) {
                        drag(cell, dx)
                    } else if (abs(dx) > slop) {
                        moved = true
                        pending?.let { a.handler.removeCallbacks(it) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    pending?.let { a.handler.removeCallbacks(it) }
                    if (held && dragIndex >= 0) endDrag(cell, ev.rawX - downX, app)
                    else if (!moved && !held) a.launchApp(app)
                }
                MotionEvent.ACTION_CANCEL -> {
                    pending?.let { a.handler.removeCallbacks(it) }
                    if (held && dragIndex >= 0) endDrag(cell, 0f, app)
                }
            }
            true
        }
    }

    private fun startDrag(cell: View) {
        dragIndex = cells.indexOf(cell)
        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        cell.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).start()
        cell.translationZ = a.dp(8).toFloat()
    }

    private fun drag(cell: View, dx: Float) {
        val w = cell.width.toFloat()
        if (w <= 0f) return
        cell.translationX = dx
        val target = (dragIndex + Math.round(dx / w)).coerceIn(0, cells.size - 1)
        for (j in cells.indices) {
            val c = cells[j]
            if (c === cell) continue
            c.translationX = when {
                j in (dragIndex + 1)..target -> -w
                j in target until dragIndex -> w
                else -> 0f
            }
        }
    }

    private fun endDrag(cell: View, dx: Float, app: AppEntry) {
        val w = maxOf(1f, cell.width.toFloat())
        val from = dragIndex
        val target = (from + Math.round(dx / w)).coerceIn(0, cells.size - 1)
        dragIndex = -1
        for (c in cells) c.translationX = 0f
        cell.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        cell.translationZ = 0f
        if (abs(dx) < slop) {
            a.showAppMenu(app, false)   // long-press without moving opens the menu
            return
        }
        if (target != from && from in cellPkgs.indices) {
            val order = cellPkgs.toMutableList()
            val moved = order.removeAt(from)
            order.add(target, moved)
            a.reorderPinned(order)
        }
    }

    // ------------------------------------------------------------------ tray state + hit tests

    fun updateTray() {
        if (!::clock.isInitialized) return
        val accent = Theme.accentColor
        clock.text = android.text.format.DateFormat.getTimeFormat(a).format(Date())
        val online = a.networkConnected()
        setCircle(wifi, if (online) accent else Theme.panel2, if (online) Theme.bg else Theme.text)
        val btOn = a.bluetoothOn()
        setCircle(bt, if (btOn) accent else Theme.panel2, if (btOn) Theme.bg else Theme.text)
        val n = DroidBridge.notificationCount
        badge.visibility = if (n > 0) View.VISIBLE else View.GONE
        badge.text = if (n > 99) "99+" else n.toString()
        setCircle(bell, if (n > 0) accent else Theme.panel2, if (n > 0) Theme.bg else Theme.text)
        val pct = a.batteryPercent()
        setCircle(bat, Theme.panel2, if (pct in 0..15) Color.rgb(248, 113, 113) else Theme.text)
    }

    fun isOverStart(x: Float, y: Float): Boolean = isOverView(startView, x, y)
    fun isOverTray(x: Float, y: Float): Boolean = isOverView(trayBox, x, y)
    fun isOverApp(x: Float, y: Float): Boolean = cells.any { isOverView(it, x, y) }
}
