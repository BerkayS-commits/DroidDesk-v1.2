package com.droiddesk.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Optional. Only counts active, non-ongoing notifications for the taskbar tray; never reads their content. */
class DroidNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        DroidBridge.listenerConnected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        DroidBridge.listenerConnected = false
        DroidBridge.notificationCount = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        DroidBridge.notificationCount = try {
            activeNotifications?.count { !it.isOngoing } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
