package com.droiddesk.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.util.Locale

/** A launchable app with label and icon resolved once (call [AppRepository.load] off the main thread). */
class AppEntry(val pkg: String, val label: String, val icon: Bitmap, val component: ComponentName)

class AppRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    fun load(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val size = maxOf(48, (46 * context.resources.displayMetrics.density).toInt())
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppEntry(
                    pkg,
                    ri.loadLabel(pm).toString(),
                    toBitmap(ri.loadIcon(pm), size),
                    ComponentName(pkg, ri.activityInfo.name)
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bmp
    }
}
