package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional. Enabled by the user in Android accessibility settings. It does NOT read screen content
 * (canRetrieveWindowContent=false); it only sees hardware key events and performs global actions.
 *  - Alt+Tab  -> system Recents
 *  - Win key  -> Home, and DroidDesk then opens its Start menu
 *  - used by DroidDesk for: power dialog, notification shade
 */
class DroidAccessibilityService : AccessibilityService() {
    private var metaDown = false
    private var metaChord = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        DroidBridge.accessibility = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DroidBridge.accessibility = null
        return super.onUnbind(intent)
    }

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
        if (code == KeyEvent.KEYCODE_TAB && event.isAltPressed) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            return true
        }
        return false
    }
}
