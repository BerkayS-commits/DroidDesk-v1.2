package com.droiddesk.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display

/**
 * Freeform (floating) windows are a system feature. An ordinary app can only *ask* for one, and the
 * system honours the request only when freeform support is switched on and the window-mode API is
 * reachable. This helper makes the request and reports, honestly, why it may not work on a device.
 */
object Freeform {
    const val WINDOWING_MODE_FREEFORM = 5
    private const val PERM_SECURE = "android.permission.WRITE_SECURE_SETTINGS"

    class Status(
        val sdk: Int,
        val manufacturer: String,
        val feature: Boolean,
        val enableSetting: Int,
        val forceResizable: Int,
        val apiReachable: Boolean,
        val externalDisplay: Boolean,
        val canWriteSecure: Boolean
    )

    /** Asks for a freeform window. Returns false when the window-mode API is not reachable. */
    fun requestFreeform(options: ActivityOptions): Boolean {
        val intType = Int::class.javaPrimitiveType
        return try {
            val m = try {
                ActivityOptions::class.java.getMethod("setLaunchWindowingMode", intType)
            } catch (e: NoSuchMethodException) {
                ActivityOptions::class.java.getDeclaredMethod("setLaunchWindowingMode", intType).also { it.isAccessible = true }
            }
            m.invoke(options, WINDOWING_MODE_FREEFORM)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun hasFeature(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    private fun globalInt(ctx: Context, key: String): Int =
        try { Settings.Global.getInt(ctx.contentResolver, key, -1) } catch (e: Exception) { -1 }

    fun status(ctx: Context): Status {
        val api = try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            true
        } catch (e: Throwable) {
            try {
                ActivityOptions::class.java.getDeclaredMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                true
            } catch (e2: Throwable) { false }
        }
        val external = try {
            (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).displays.any { it.displayId != Display.DEFAULT_DISPLAY }
        } catch (e: Exception) { false }
        return Status(
            Build.VERSION.SDK_INT, Build.MANUFACTURER ?: "", hasFeature(ctx),
            globalInt(ctx, "enable_freeform_support"), globalInt(ctx, "force_resizable_activities"),
            api, external, ctx.checkSelfPermission(PERM_SECURE) == PackageManager.PERMISSION_GRANTED
        )
    }

    /** Needs the one-time `adb shell pm grant ... WRITE_SECURE_SETTINGS`. A restart is still required. */
    fun enableSettings(ctx: Context): Boolean = try {
        Settings.Global.putInt(ctx.contentResolver, "enable_freeform_support", 1)
        Settings.Global.putInt(ctx.contentResolver, "force_resizable_activities", 1)
        true
    } catch (e: Exception) { false }

    fun report(ctx: Context, st: Status): String {
        fun yn(b: Boolean) = ctx.getString(if (b) R.string.ff_yes else R.string.ff_no)
        fun flag(v: Int) = if (v < 0) ctx.getString(R.string.ff_unknown) else yn(v == 1)
        val sb = StringBuilder()
        sb.append(ctx.getString(R.string.ff_android)).append(": ").append(st.sdk).append(" (").append(st.manufacturer).append(")\n")
        sb.append(ctx.getString(R.string.ff_feature)).append(": ").append(yn(st.feature)).append('\n')
        sb.append(ctx.getString(R.string.ff_setting)).append(": ").append(flag(st.enableSetting)).append('\n')
        sb.append(ctx.getString(R.string.ff_force)).append(": ").append(flag(st.forceResizable)).append('\n')
        sb.append(ctx.getString(R.string.ff_api)).append(": ").append(yn(st.apiReachable)).append('\n')
        sb.append(ctx.getString(R.string.ff_display)).append(": ").append(yn(st.externalDisplay)).append("\n\n")
        if (!st.apiReachable) sb.append(ctx.getString(R.string.ff_hint_api)).append("\n\n")
        if (!st.feature || st.enableSetting != 1) sb.append(ctx.getString(R.string.ff_hint_off)).append("\n\n")
        if (st.manufacturer.equals("xiaomi", true) || st.manufacturer.equals("redmi", true) || st.manufacturer.equals("poco", true)) {
            sb.append(ctx.getString(R.string.ff_hint_xiaomi)).append("\n\n")
        }
        if (!st.canWriteSecure) sb.append(ctx.getString(R.string.ff_adb, ctx.packageName))
        return sb.toString().trim()
    }
}
