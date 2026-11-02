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

    /** Packages ordered by last activity, newest first, with the time of that activity. */
    fun recentOrder(ctx: Context, windowMs: Long): List<Pair<String, Long>> {
        val last = HashMap<String, Long>()
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - windowMs, end) ?: return emptyList()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val p = e.packageName ?: continue
                if (e.eventType == 1 || e.eventType == 2 || e.eventType == 23) last[p] = e.timeStamp
            }
        } catch (ex: Exception) {
            // Permission revoked or unavailable.
        }
        return last.entries.sortedByDescending { it.value }.map { it.key to it.value }
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

/** Decides which apps get a light on the taskbar. Shared by the launcher and the taskbar overlay. */
object ActiveLogic {
    fun compute(ctx: Context, prefs: Prefs, session: Collection<String>, homes: Set<String>): Map<String, Int> {
        val mode = prefs.activeMode
        val m = HashMap<String, Int>()
        if (mode == 0) return m
        if (ActiveApps.hasAccess(ctx)) {
            val window = when (mode) {
                2 -> 30 * 60_000L
                3 -> 120 * 60_000L
                else -> 6 * 3_600_000L
            }
            for ((p, v) in ActiveApps.compute(ctx, window)) {
                if (mode == 1 && v < 2) continue
                m[p] = v
            }
        } else {
            val fg = DroidBridge.foregroundPkg
            if (fg.isNotEmpty()) m[fg] = 2
        }
        if (mode != 1) {
            for (p in session) if (!m.containsKey(p)) m[p] = 1
        }
        m.remove(ctx.packageName)
        m.remove("com.android.systemui")
        for (h in homes) m.remove(h)
        return m
    }
}
