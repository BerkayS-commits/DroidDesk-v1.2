package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Windows drawn by the accessibility service on top of other apps (no overlay permission needed):
 *  - the real DroidDesk taskbar (same layout, tray and app lights), summoned by a user-chosen key or by
 *    moving the mouse to a screen zone
 *  - the recent-apps switcher (Alt+Tab / Win+Tab)
 */
class OverlayController(private val svc: DroidAccessibilityService) : RecentsHost {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(svc) }
    private var bar: FrameLayout? = null
    private var barTaskbar: Taskbar? = null
    private var switcherRoot: View? = null
    private var ui: RecentsUi? = null
    private var viaAlt = false
    private var switcherPending = false
    private var edge: View? = null
    private val entryCache = HashMap<String, AppEntry>()
    private val homes by lazy { AppRepository(svc).homePackages() }
    private val hideRunnable = Runnable { hideBar() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            barTaskbar?.updateTray()
            handler.postDelayed(this, 15_000L)
        }
    }

    private fun refreshTheme() {
        Theme.accentColor = Theme.accentFor(prefs.accent, prefs.accentCustom)
    }

    private fun overlayParams(w: Int, h: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)

    fun isShowing(): Boolean = bar != null || ui != null

    // ------------------------------------------------------------------ taskbar over other apps

    /** The same Taskbar class the launcher uses, wired to the service instead of the activity. */
    private val barHost = object : TaskbarHost {
        override val ctx: Context get() = svc
        override val prefs: Prefs get() = this@OverlayController.prefs
        override val handler: Handler get() = this@OverlayController.handler
        override fun pinnedList(): MutableList<String> = this@OverlayController.prefs.pinned
        override fun entry(pkg: String): AppEntry? = entryFor(pkg)

        override fun glass(content: View, radiusDp: Int): FrameLayout {
            val card = FrameLayout(svc)
            val r = svc.dp(radiusDp).toFloat()
            card.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            card.clipToOutline = true
            content.background = svc.rounded(Color.argb(232, 25, 36, 57), radiusDp, Color.argb(40, 255, 255, 255), 1)
            card.addView(content, FrameLayout.LayoutParams(-1, -1))
            return card
        }

        override fun launch(app: AppEntry) { this@OverlayController.launch(app.pkg) }
        override fun appMenu(app: AppEntry) { togglePin(app.pkg) }

        override fun reorderPinned(order: List<String>) {
            val cur = this@OverlayController.prefs.pinned
            val re = order.filter { cur.contains(it) }
            this@OverlayController.prefs.pinned = (re + cur.filter { !re.contains(it) }).toMutableList()
            barTaskbar?.render()
        }

        override fun toggleDrawer() { openLauncher("drawer") }
        override fun openSearch() { openLauncher("search") }
        override fun systemBack() { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }

        override fun goHome() {
            hideBar()
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }

        override fun openRecents() {
            hideBar()
            if (this@OverlayController.prefs.recentsMode == 1) svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            else showSwitcher(false)
        }

        override fun openInternet() {
            startSettings(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS)
        }

        override fun openBluetooth() { startSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }

        override fun openVolume() {
            startSettings(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)
        }

        override fun openPower() { startSettings(Intent.ACTION_POWER_USAGE_SUMMARY) }
        override fun toggleNotifications(tab: Int) { openLauncher("notifications") }
        override fun onRendered() { }
    }

    private fun entryFor(pkg: String): AppEntry? {
        val cached = entryCache[pkg]
        if (cached != null) return cached
        if (pkg == svc.packageName) return null
        val pm = svc.packageManager
        val li = try { pm.getLaunchIntentForPackage(pkg) } catch (e: Exception) { null } ?: return null
        val comp: ComponentName = li.component ?: return null
        return try {
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            val bmp = IconStyler.render(pm.getApplicationIcon(pkg), maxOf(48, svc.dp(46)), prefs.iconShape)
            val e = AppEntry(pkg, label, bmp, comp)
            entryCache[pkg] = e
            e
        } catch (e: Exception) {
            null
        }
    }

    private fun togglePin(pkg: String) {
        val l = prefs.pinned
        val nowPinned = !l.remove(pkg)
        if (nowPinned) l.add(pkg)
        prefs.pinned = l
        Toast.makeText(svc, if (nowPinned) R.string.toast_pinned else R.string.toast_unpinned, Toast.LENGTH_SHORT).show()
        barTaskbar?.render()
    }

    fun toggleBar() { if (bar != null) hideBar() else showBar() }

    fun showBar() {
        if (bar != null) return
        hideSwitcher()
        refreshTheme()
        entryCache.clear()
        val tb = Taskbar(barHost)
        val card = tb.build()
        val m = svc.resources.displayMetrics
        val widthPx = m.widthPixels - svc.dp(36)
        tb.expanded = widthPx >= svc.dp(640)
        tb.layout()
        tb.setActive(ActiveLogic.compute(svc, prefs, prefs.recents, homes))
        tb.render()
        tb.updateTray()
        card.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) {
                hideBar()
                true
            } else {
                false
            }
        }
        val lp = overlayParams(
            widthPx, svc.dp(prefs.taskbarSize),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = svc.dp(12) + navBarHeight()
        try {
            wm.addView(card, lp)
        } catch (e: Exception) {
            return
        }
        bar = card
        barTaskbar = tb
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 12_000L)
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 15_000L)
    }

    private fun navBarHeight(): Int {
        val id = svc.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) svc.resources.getDimensionPixelSize(id) else 0
    }

    fun hideBar() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(tickRunnable)
        val b = bar
        if (b != null) {
            try { wm.removeView(b) } catch (e: Exception) { }
        }
        bar = null
        barTaskbar = null
    }

    private fun openLauncher(action: String) {
        hideBar()
        DroidBridge.requestAction(action)
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun startSettings(action: String) {
        hideBar()
        try {
            val i = Intent(action)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            svc.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(svc, R.string.toast_cannot_open, Toast.LENGTH_SHORT).show()
        }
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
        if (switcherPending) return
        switcherPending = true
        // Take a fresh preview of the app that is in front, then show the switcher.
        svc.captureForeground { doShowSwitcher(alt) }
    }

    private fun doShowSwitcher(alt: Boolean) {
        switcherPending = false
        if (ui != null) return
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
        switcherPending = false
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

    // ------------------------------------------------------------------ pointer zone that brings the taskbar back

    /** zone: 0 off, 1 bottom edge, 2 bottom-right corner, 3 bottom-left corner. Call again after the setting changes. */
    fun refreshEdge() {
        removeEdge()
        val zone = prefs.tbZone
        if (zone == 0) return
        val v = View(svc)
        v.setOnHoverListener { _, e ->
            if (e.action == MotionEvent.ACTION_HOVER_ENTER) showBar()
            false
        }
        val w = if (zone == 1) WindowManager.LayoutParams.MATCH_PARENT else svc.dp(28)
        val h = if (zone == 1) svc.dp(3) else svc.dp(28)
        val lp = overlayParams(
            w, h,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        lp.gravity = when (zone) {
            2 -> Gravity.BOTTOM or Gravity.END
            3 -> Gravity.BOTTOM or Gravity.START
            else -> Gravity.BOTTOM
        }
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
