package com.droiddesk.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Finds apps that are in front or were used recently, via Android's usage events.
 * Requires the user to grant "Usage access" to DroidDesk. Android gives no API to list running apps.
 */
object ActiveApps {

    @Suppress("DEPRECATION")
    fun hasAccess(ctx: Context): Boolean {
        return try {
            val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** package -> 2 (visible now) or 1 (used within [windowMs]). */
    fun compute(ctx: Context, windowMs: Long): Map<String, Int> {
        val out = HashMap<String, Int>()
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - windowMs, end) ?: return out
            val visible = HashMap<String, Boolean>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val p = e.packageName ?: continue
                when (e.eventType) {
                    1 -> visible[p] = true            // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                    2, 23 -> visible[p] = false       // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND / ACTIVITY_STOPPED
                }
            }
            for ((p, v) in visible) out[p] = if (v) 2 else 1
        } catch (e: Exception) {
            // Permission revoked or unavailable.
        }
        return out
    }
}
