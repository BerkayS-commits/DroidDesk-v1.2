package com.droiddesk.launcher

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.util.Locale

/** Small read-only helpers shared by the launcher, the taskbar overlay and the notification panel. */
object SysInfo {

    fun networkConnected(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork) != null
        } catch (e: Exception) {
            false
        }
    }

    fun networkName(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> ctx.getString(R.string.net_none)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ctx.getString(R.string.net_wifi)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ctx.getString(R.string.net_mobile)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ctx.getString(R.string.net_eth)
                else -> ctx.getString(R.string.net_other)
            }
        } catch (e: Exception) {
            ctx.getString(R.string.net_none)
        }
    }

    fun bluetoothOn(ctx: Context): Boolean {
        return try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    private fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryPercent(ctx: Context): Int {
        val i = batteryIntent(ctx) ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    /** e.g. "78% · charging · 31°C" */
    fun batteryText(ctx: Context): String {
        val i = batteryIntent(ctx) ?: return "—"
        val pct = batteryPercent(ctx)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val sb = StringBuilder()
        sb.append(if (pct >= 0) "$pct%" else "—")
        if (charging) sb.append(" ⚡")
        if (temp != Int.MIN_VALUE) sb.append(" · ").append(String.format(Locale.getDefault(), "%.0f°C", temp / 10f))
        return sb.toString()
    }

    private fun gb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / 1073741824.0)

    fun storageText(): String {
        return try {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.blockCountLong * st.blockSizeLong
            val used = total - st.availableBlocksLong * st.blockSizeLong
            gb(used) + " / " + gb(total) + " GB"
        } catch (e: Exception) {
            "—"
        }
    }

    fun ramText(ctx: Context): String {
        return try {
            val mi = ActivityManager.MemoryInfo()
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            gb(mi.totalMem - mi.availMem) + " / " + gb(mi.totalMem) + " GB"
        } catch (e: Exception) {
            "—"
        }
    }

    fun deviceText(): String = Build.MODEL + " · Android " + Build.VERSION.RELEASE
}
