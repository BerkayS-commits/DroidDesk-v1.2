package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional. Enabled by the user in Android accessibility settings. It does NOT read screen content
 * (canRetrieveWindowContent=false); it sees hardware key events, learns which app is in front, performs
 * global actions and draws the floating taskbar / recent-apps switcher on top of other apps:
 *  - Alt+Tab / Win+Tab : recent-apps switcher
 *  - Win key           : launcher + app drawer;  Win+D / E / S / A / I : desktop, files, search, notifications, settings
 *  - user-chosen key   : floating taskbar over the running app
 *  - user-chosen key   : reopen the front app in a window
 */
class DroidAccessibilityService : AccessibilityService() {
    private var metaDown = false
    private var metaChord = false
    private val prefs by lazy { Prefs(this) }
    val overlays by lazy { OverlayController(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui") || pkg.contains("inputmethod")) return
        DroidBridge.foregroundPkg = pkg
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        DroidBridge.accessibility = this
        overlays.refreshEdge()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlays.dismissAll()
        DroidBridge.accessibility = null
        return super.onUnbind(intent)
    }

    private fun modsOf(e: KeyEvent): Int =
        (if (e.isAltPressed) 1 else 0) or (if (e.isCtrlPressed) 2 else 0) or
            (if (e.isShiftPressed) 4 else 0) or (if (e.isMetaPressed) 8 else 0)

    private fun toLauncher(action: String) {
        DroidBridge.requestAction(action)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        // The switcher is open: it owns the keyboard.
        if (overlays.isSwitcherOpen()) {
            if (event.action == KeyEvent.ACTION_UP && (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) && overlays.switcherViaAlt()) {
                overlays.activateSwitcher()
                return true
            }
            if (overlays.switcherKey(event)) return true
        }

        if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) { metaDown = true; metaChord = false }
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (metaDown && !metaChord) {
                    DroidBridge.requestStart()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                metaDown = false
            }
            return false
        }
        if (metaDown) metaChord = true

        // Win + letter shortcuts, like a desktop OS.
        if (metaDown && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val action = when (code) {
                KeyEvent.KEYCODE_D -> "desktop"
                KeyEvent.KEYCODE_E -> "files"
                KeyEvent.KEYCODE_S -> "search"
                KeyEvent.KEYCODE_A -> "notifications"
                KeyEvent.KEYCODE_I -> "settings"
                KeyEvent.KEYCODE_TAB -> "switcher"
                else -> null
            }
            if (action != null) {
                if (action == "switcher") overlays.showSwitcher(false) else toLauncher(action)
                return true
            }
        }

        // User-chosen key: bring the taskbar over whatever app is open.
        val tb = prefs.tbKey
        if (tb != 0 && code == tb && modsOf(event) == prefs.tbMods) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) overlays.toggleBar()
            return true
        }

        // User-chosen key: reopen the front app in a window.
        val bound = prefs.winKey
        if (bound != 0 && code == bound && modsOf(event) == prefs.winMods) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val pkg = DroidBridge.foregroundPkg
                if (pkg.isNotEmpty()) {
                    DroidBridge.requestShrink(pkg)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            return true
        }

        if (code == KeyEvent.KEYCODE_TAB && event.isAltPressed) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (prefs.recentsMode == 1) {
                    if (event.repeatCount == 0) performGlobalAction(GLOBAL_ACTION_RECENTS)
                } else {
                    overlays.showSwitcher(true)
                }
            }
            return true
        }
        return false
    }
}
