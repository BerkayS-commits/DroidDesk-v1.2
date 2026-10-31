package com.droiddesk.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional. Feeds the notification centre in the bottom-right panel. Notifications are read in memory
 * only to display them; nothing is stored or sent anywhere.
 */
class DroidNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        DroidBridge.listener = this
        DroidBridge.listenerConnected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        DroidBridge.listener = null
        DroidBridge.listenerConnected = false
        DroidBridge.notificationCount = 0
        DroidBridge.onNotificationsChanged?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        DroidBridge.notificationCount = try {
            activeNotifications?.count { !it.isOngoing } ?: 0
        } catch (e: Exception) {
            0
        }
        DroidBridge.onNotificationsChanged?.invoke()
    }
}
