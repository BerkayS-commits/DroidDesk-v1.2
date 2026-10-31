package com.droiddesk.launcher

/** Tiny in-process bridge between the optional services and the launcher UI. */
object DroidBridge {
    @Volatile var accessibility: DroidAccessibilityService? = null
    @Volatile var listener: DroidNotificationListener? = null
    @Volatile var notificationCount: Int = 0
    @Volatile var listenerConnected: Boolean = false
    @Volatile var onNotificationsChanged: (() -> Unit)? = null
    @Volatile var foregroundPkg: String = ""
    @Volatile private var startRequested: Boolean = false
    @Volatile private var shrinkPkg: String? = null
    @Volatile private var pendingAction: String? = null

    /** Ask the launcher to run an action ("drawer", "search", "files", "settings", "notifications", "desktop", "switcher"). */
    fun requestAction(name: String) { pendingAction = name }

    fun consumeAction(): String? {
        val a = pendingAction
        pendingAction = null
        return a
    }

    fun requestStart() { startRequested = true }

    fun consumeStartRequest(): Boolean {
        val v = startRequested
        startRequested = false
        return v
    }

    fun requestShrink(pkg: String) { shrinkPkg = pkg }

    fun consumeShrink(): String? {
        val p = shrinkPkg
        shrinkPkg = null
        return p
    }
}
