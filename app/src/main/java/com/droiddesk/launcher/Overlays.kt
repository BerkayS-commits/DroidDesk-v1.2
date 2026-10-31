package com.droiddesk.launcher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Date

/**
 * Windows drawn by the accessibility service on top of other apps (no overlay permission needed):
 *  - a floating taskbar, summoned by a user-chosen key or by moving the mouse to the bottom edge
 *  - the recent-apps switcher (Alt+Tab / Win+Tab)
 */
class OverlayController(private val svc: DroidAccessibilityService) : RecentsHost {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(svc) }
    private var bar: View? = null
    private var switcherRoot: View? = null
    private var ui: RecentsUi? = null
    private var viaAlt = false
    private var edge: View? = null
    private val hideRunnable = Runnable { hideBar() }

    private fun refreshTheme() {
        Theme.accentColor = Theme.accentFor(prefs.accent, prefs.accentCustom)
    }

    private fun overlayParams(w: Int, h: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)

    // ------------------------------------------------------------------ floating taskbar

    fun toggleBar() { if (bar != null) hideBar() else showBar() }

    fun showBar() {
        if (bar != null) return
        hideSwitcher()
        refreshTheme()
        val v = buildBar()
        val m = svc.resources.displayMetrics
        val lp = overlayParams(
            minOf(m.widthPixels - svc.dp(24), svc.dp(980)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = svc.dp(20)
        try {
            wm.addView(v, lp)
            bar = v
        } catch (e: Exception) {
            return
        }
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 9000L)
    }

    fun hideBar() {
        handler.removeCallbacks(hideRunnable)
        val b = bar
        if (b != null) {
            try { wm.removeView(b) } catch (e: Exception) { }
        }
        bar = null
    }

    private fun cell(content: View, sizeDp: Int, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(svc)
        f.isClickable = true
        f.addView(content, FrameLayout.LayoutParams(svc.dp(sizeDp), svc.dp(sizeDp), Gravity.CENTER))
        f.setOnClickListener {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, 9000L)
            onClick()
        }
        svc.hoverable(f, 12, 1.12f)
        return f
    }

    private fun buildBar(): View {
        val accent = Theme.accentColor
        val pm = svc.packageManager
        val row = LinearLayout(svc)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(svc.dp(8), svc.dp(6), svc.dp(8), svc.dp(6))
        row.background = svc.rounded(Color.argb(242, 22, 33, 54), 24, Color.argb(70, 255, 255, 255), 1)
        row.elevation = svc.dp(8).toFloat()
        row.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) {
                hideBar()
                true
            } else {
                false
            }
        }
        val cellW = LinearLayout.LayoutParams(svc.dp(48), svc.dp(48))

        if (prefs.showNav) {
            row.addView(cell(svc.iconView(R.drawable.ic_nav_back, Theme.text), 22) { svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }, LinearLayout.LayoutParams(cellW))
            row.addView(cell(svc.iconView(R.drawable.ic_nav_home, Theme.text), 22) { hideBar(); svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }, LinearLayout.LayoutParams(cellW))
            row.addView(cell(svc.iconView(R.drawable.ic_nav_square, Theme.text), 22) { hideBar(); showSwitcher(false) }, LinearLayout.LayoutParams(cellW))
            val gap = View(svc)
            gap.setBackgroundColor(Color.argb(50, 255, 255, 255))
            val gp = LinearLayout.LayoutParams(svc.dp(1), svc.dp(28))
            gp.setMargins(svc.dp(6), 0, svc.dp(6), 0)
            row.addView(gap, gp)
        }

        val startRes = StartIcons.res[prefs.startIcon.coerceIn(0, StartIcons.res.size - 1)]
        row.addView(cell(svc.iconView(startRes, accent), 26) { openLauncher("drawer") }, LinearLayout.LayoutParams(cellW))

        val fg = DroidBridge.foregroundPkg
        val pinned = prefs.pinned
        val extras = prefs.recents.filter { it !in pinned }.take(5).toMutableList()
        if (fg.isNotEmpty() && fg !in pinned && fg !in extras) extras.add(0, fg)
        val size = svc.dp(40)
        for (pkg in pinned + extras) {
            val launchIntent = try { pm.getLaunchIntentForPackage(pkg) } catch (e: Exception) { null } ?: continue
            if (launchIntent.component == null || pkg == svc.packageName) continue
            val bmp = try {
                IconStyler.render(pm.getApplicationIcon(pkg), size, prefs.iconShape)
            } catch (e: Exception) {
                continue
            }
            val img = ImageView(svc)
            img.setImageBitmap(bmp)
            val c = cell(img, 36) { launch(pkg) }
            try { c.tip(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))) } catch (e: Exception) { }
            if (pkg == fg) {
                val light = View(svc)
                light.background = svc.rounded(accent, 2)
                val lp = FrameLayout.LayoutParams(svc.dp(18), svc.dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                lp.bottomMargin = svc.dp(1)
                c.addView(light, lp)
            }
            row.addView(c, LinearLayout.LayoutParams(cellW))
        }

        row.addView(cell(svc.iconView(R.drawable.ic_recents, Theme.text), 22) { hideBar(); showSwitcher(false) }, LinearLayout.LayoutParams(cellW))
        val clock = svc.label(android.text.format.DateFormat.getTimeFormat(svc).format(Date()), 13, Theme.text, true)
        clock.setPadding(svc.dp(10), 0, svc.dp(6), 0)
        row.addView(clock)
        row.addView(cell(svc.iconView(R.drawable.ic_close, Theme.muted), 18) { hideBar() }, LinearLayout.LayoutParams(svc.dp(40), svc.dp(40)))
        return row
    }

    private fun openLauncher(action: String) {
        hideBar()
        DroidBridge.requestAction(action)
        svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun launch(pkg: String) {
        hideBar()
        hideSwitcher()
        val i = RecentsData.launchIntent(svc, pkg) ?: return
        try {
            svc.startActivity(i)
            val l = prefs.recents
            l.remove(pkg)
            l.add(0, pkg)
            while (l.size > 6) l.removeAt(l.size - 1)
            prefs.recents = l
            RecentsData.clearDismissed(pkg)
        } catch (e: Exception) {
            Toast.makeText(svc, R.string.toast_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ recent apps switcher

    fun showSwitcher(alt: Boolean) {
        val current = ui
        if (current != null) {
            current.move(1)
            return
        }
        hideBar()
        refreshTheme()
        val m = svc.resources.displayMetrics
        val items = RecentsData.load(svc, prefs)
        val u = RecentsUi(svc, items, this, m.widthPixels, m.heightPixels)
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        try {
            wm.addView(u.view, lp)
        } catch (e: Exception) {
            return
        }
        ui = u
        switcherRoot = u.view
        viaAlt = alt
        if (items.size > 1) u.move(1)
    }

    fun hideSwitcher() {
        val r = switcherRoot
        if (r != null) {
            try { wm.removeView(r) } catch (e: Exception) { }
        }
        switcherRoot = null
        ui = null
        viaAlt = false
    }

    fun isSwitcherOpen(): Boolean = ui != null
    fun switcherViaAlt(): Boolean = viaAlt
    fun switcherKey(e: KeyEvent): Boolean = ui?.onKey(e) ?: false
    fun activateSwitcher() { ui?.activate() }

    override fun open(item: RecentItem) { launch(item.pkg) }

    override fun close(item: RecentItem) {
        kill(item.pkg)
        RecentsData.markDismissed(item.pkg)
        val l = prefs.recents
        l.remove(item.pkg)
        prefs.recents = l
    }

    override fun closeAll(items: List<RecentItem>) { items.forEach { close(it) } }

    override fun dismiss() { hideSwitcher() }

    private fun kill(pkg: String) {
        try {
            (svc.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        } catch (e: Exception) {
            // Not permitted for this process.
        }
    }

    // ------------------------------------------------------------------ bottom-edge reveal

    fun refreshEdge() {
        if (!prefs.tbEdge) {
            removeEdge()
            return
        }
        if (edge != null) return
        val v = View(svc)
        v.setOnHoverListener { _, e ->
            if (e.action == MotionEvent.ACTION_HOVER_ENTER) showBar()
            false
        }
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT, svc.dp(3),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        lp.gravity = Gravity.BOTTOM
        try {
            wm.addView(v, lp)
            edge = v
        } catch (e: Exception) {
            // Overlay not allowed on this device.
        }
    }

    private fun removeEdge() {
        val v = edge
        if (v != null) {
            try { wm.removeView(v) } catch (e: Exception) { }
        }
        edge = null
    }

    fun dismissAll() {
        hideBar()
        hideSwitcher()
        removeEdge()
    }
}
