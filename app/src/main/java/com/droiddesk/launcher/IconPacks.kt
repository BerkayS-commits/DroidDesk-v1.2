package com.droiddesk.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconPackInfo(val pkg: String, val label: String)

class IconPackData(private val res: Resources, private val pkg: String, private val map: Map<String, String>) {
    @Suppress("DEPRECATION")
    fun icon(c: ComponentName): Drawable? {
        val name = map[c.packageName + "/" + c.className] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try { res.getDrawable(id, null) } catch (e: Exception) { null }
    }
}

/** Support for third-party icon packs in the common ADW / Nova / Go launcher "appfilter" format. */
object IconPacks {
    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.fede.launcher.THEME_ICONPACK",
        "com.gau.go.launcherex.theme"
    )

    @Suppress("DEPRECATION")
    fun list(ctx: Context): List<IconPackInfo> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (action in actions) {
            val infos = try { pm.queryIntentActivities(Intent(action), 0) } catch (e: Exception) { emptyList() }
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (!seen.containsKey(pkg)) seen[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
            }
        }
        return seen.values.toList()
    }

    fun load(ctx: Context, pkg: String): IconPackData? {
        return try {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) {
                res.getXml(id)
            } else {
                val p = XmlPullParserFactory.newInstance().newPullParser()
                p.setInput(res.assets.open("appfilter.xml"), null)
                p
            }
            val map = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                        map[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = dr
                    }
                }
                ev = parser.next()
            }
            IconPackData(res, pkg, map)
        } catch (e: Exception) {
            null
        }
    }
}
