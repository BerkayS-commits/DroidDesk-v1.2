package com.droiddesk.launcher

import android.graphics.Bitmap
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

/** Search results shown in the app drawer: matching apps, then folders and files from shared storage. */
class SearchResults(private val a: MainActivity) {
    val view = ScrollView(a)
    private val list = LinearLayout(a)
    private var token = 0
    private var query = ""
    private var pending: Runnable? = null
    private var firstAction: (() -> Unit)? = null

    init {
        list.orientation = LinearLayout.VERTICAL
        view.addView(list, FrameLayout.LayoutParams(-1, -2))
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.visibility = View.GONE
    }

    fun update(q: String) {
        query = q.trim()
        token++
        val t = token
        pending?.let { a.handler.removeCallbacks(it) }
        pending = null
        list.removeAllViews()
        firstAction = null
        if (query.isEmpty()) return

        val appHits = a.apps.filter { it.label.contains(query, ignoreCase = true) }.take(8)
        if (appHits.isNotEmpty()) {
            header(a.getString(R.string.search_apps_section))
            appHits.forEach { app -> row(app.icon, 0, app.label, "") { a.launchApp(app) } }
        }
        if (!a.prefs.searchFiles) {
            if (appHits.isEmpty()) note(a.getString(R.string.no_results))
            return
        }
        if (!FileIndex.hasAccess(a)) {
            permissionCard()
            return
        }
        if (!FileIndex.isReady()) {
            note(a.getString(R.string.search_indexing))
            FileIndex.ensureIndex(a) { a.runOnUiThread { if (query.isNotEmpty()) update(query) } }
            return
        }
        val r = Runnable {
            Thread {
                val hits = FileIndex.search(query, 40)
                a.runOnUiThread { if (t == token) appendFiles(hits, appHits.isNotEmpty()) }
            }.start()
        }
        pending = r
        a.handler.postDelayed(r, 180L)
    }

    fun openFirst(): Boolean {
        val f = firstAction ?: return false
        f()
        return true
    }

    fun onResume() {
        if (view.visibility == View.VISIBLE && query.isNotEmpty()) update(query)
    }

    private fun appendFiles(hits: List<FileHit>, hadApps: Boolean) {
        val base = Environment.getExternalStorageDirectory().absolutePath
        val dirs = hits.filter { it.isDir }
        val fls = hits.filter { !it.isDir }
        if (dirs.isEmpty() && fls.isEmpty() && !hadApps) {
            note(a.getString(R.string.no_results))
            return
        }
        if (dirs.isNotEmpty()) {
            header(a.getString(R.string.search_folders))
            dirs.forEach { h -> row(null, R.drawable.ic_folder, h.name, parentOf(h, base)) { openHit(h) } }
        }
        if (fls.isNotEmpty()) {
            header(a.getString(R.string.search_files))
            fls.forEach { h -> row(null, R.drawable.ic_file, h.name, parentOf(h, base)) { openHit(h) } }
        }
    }

    private fun parentOf(h: FileHit, base: String): String {
        val p = File(h.path).parent ?: return ""
        return p.removePrefix(base).ifEmpty { "/" }
    }

    private fun openHit(h: FileHit) {
        if (!FileIndex.open(a, h)) a.toast(R.string.toast_cannot_open) else a.closeDrawer()
    }

    private fun header(text: String) {
        val t = a.label(text, 12, Theme.muted, true)
        t.setPadding(a.dp(4), a.dp(10), 0, a.dp(4))
        list.addView(t)
    }

    private fun note(text: String) {
        val t = a.label(text, 14, Theme.muted)
        t.setPadding(a.dp(6), a.dp(12), a.dp(6), a.dp(12))
        list.addView(t)
    }

    private fun permissionCard() {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
        box.background = a.rounded(Theme.panel2.withAlpha(170), 14)
        box.addView(a.label(a.getString(R.string.search_need_access), 13, Theme.text))
        val btn = a.label(a.getString(R.string.search_grant_access), 13, Theme.bg, true)
        btn.gravity = Gravity.CENTER
        btn.background = a.rounded(Theme.accentColor, 14)
        btn.setPadding(a.dp(16), 0, a.dp(16), 0)
        btn.setOnClickListener { a.requestFileAccess() }
        val lp = LinearLayout.LayoutParams(-2, a.dp(38))
        lp.topMargin = a.dp(10)
        box.addView(btn, lp)
        val bp = LinearLayout.LayoutParams(-1, -2)
        bp.topMargin = a.dp(8)
        list.addView(box, bp)
    }

    private fun row(bmp: Bitmap?, iconRes: Int, title: String, subtitle: String, onClick: () -> Unit) {
        if (firstAction == null) firstAction = onClick
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(a.dp(8), 0, a.dp(8), 0)
        r.isClickable = true
        r.isFocusable = true
        a.hoverable(r)
        val icon: ImageView = if (bmp != null) {
            val i = ImageView(a)
            i.setImageBitmap(bmp)
            i
        } else {
            a.iconView(iconRes, Theme.accentColor)
        }
        r.addView(icon, llp(a.dp(30), a.dp(30)))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        val title1 = a.label(title, 14, Theme.text)
        title1.maxLines = 1
        title1.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title1)
        if (subtitle.isNotEmpty()) {
            val sub = a.label(subtitle, 11, Theme.muted)
            sub.maxLines = 1
            sub.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            col.addView(sub)
        }
        val cp = LinearLayout.LayoutParams(0, -2, 1f)
        cp.leftMargin = a.dp(12)
        r.addView(col, cp)
        r.setOnClickListener { onClick() }
        list.addView(r, LinearLayout.LayoutParams(-1, a.dp(52)))
    }
}
