package com.droiddesk.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import java.util.Locale

/** A launchable app with label and icon resolved once (call [AppRepository.load] off the main thread). */
class AppEntry(val pkg: String, val label: String, val icon: Bitmap, val component: ComponentName)

class AppRepository(private val context: Context) {

    /** Packages that are home screens themselves; they must never count as "running apps". */
    @Suppress("DEPRECATION")
    fun homePackages(): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return try {
            context.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    @Suppress("DEPRECATION")
    fun load(shape: Int, packPkg: String): List<AppEntry> {
        val pm = context.packageManager
        val pack = if (packPkg.isBlank()) null else IconPacks.load(context, packPkg)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val size = maxOf(48, (46 * context.resources.displayMetrics.density).toInt())
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val comp = ComponentName(pkg, ri.activityInfo.name)
                val packed = pack?.icon(comp)
                val bmp = try {
                    if (packed != null) IconStyler.render(packed, size, IconStyler.ORIGINAL)
                    else IconStyler.render(ri.loadIcon(pm), size, shape)
                } catch (e: Exception) {
                    IconStyler.render(ri.loadIcon(pm), size, IconStyler.ORIGINAL)
                }
                AppEntry(pkg, ri.loadLabel(pm).toString(), bmp, comp)
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
}
