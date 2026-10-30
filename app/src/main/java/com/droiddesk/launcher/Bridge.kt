package com.droiddesk.launcher

/** Tiny in-process bridge between the optional services and the launcher UI. */
object DroidBridge {
    @Volatile var accessibility: DroidAccessibilityService? = null
    @Volatile var notificationCount: Int = 0
    @Volatile var listenerConnected: Boolean = false
    @Volatile private var startRequested: Boolean = false

    fun requestStart() { startRequested = true }

    fun consumeStartRequest(): Boolean {
        val v = startRequested
        startRequested = false
        return v
    }
}
