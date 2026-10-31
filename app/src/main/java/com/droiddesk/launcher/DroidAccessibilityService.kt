package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional. Enabled by the user in Android accessibility settings. It does NOT read screen content
 * (canRetrieveWindowContent=false); it sees hardware key events, learns which app is in front, and
 * performs global actions:
 *  - Alt+Tab  -> system Recents
 *  - Win key  -> Home, and DroidDesk then opens its app drawer
 *  - user-chosen "window mode" key -> Home, then DroidDesk relaunches the app that was in front in a window
 */
class DroidAccessibilityService : AccessibilityService() {
    private var metaDown = false
    private var metaChord = false
    private val prefs by lazy { Prefs(this) }

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
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DroidBridge.accessibility = null
        return super.onUnbind(intent)
    }

    private fun modsOf(e: KeyEvent): Int =
        (if (e.isAltPressed) 1 else 0) or (if (e.isCtrlPressed) 2 else 0) or
            (if (e.isShiftPressed) 4 else 0) or (if (e.isMetaPressed) 8 else 0)

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
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

        // User-chosen "shrink the front app to a window" key.
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
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            return true
        }
        return false
    }
}
