package com.droiddesk.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

/**
 * Desktop widgets: the real Android widgets of the apps installed on this device, hosted with
 * [AppWidgetHost]. Add them from the picker, resize them with the corner handle, move or remove them
 * from the long-press / right-click menu.
 */
class DesktopWidgets(private val a: MainActivity) {
    val scroll = ScrollView(a)
    private val column = LinearLayout(a)
    var editMode = false
        private set

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(a)
    private val host = AppWidgetHost(a, HOST_ID)
    private val cards = LinkedHashMap<Int, FrameLayout>()
    private val hostViews = HashMap<Int, AppWidgetHostView>()
    private var listening = false
    private var pendingId = -1

    companion object {
        const val HOST_ID = 0x44D5
        const val REQ_BIND = 7101
        const val REQ_CONFIG = 7102
        private const val DEFAULT_W = 300
    }

    init {
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> a.invalidateBackdrops() }
        column.orientation = LinearLayout.VERTICAL
        scroll.addView(column, FrameLayout.LayoutParams(-2, -2))
    }

    // ---- host lifecycle (called by MainActivity)

    fun start() {
        if (listening) return
        try {
            host.startListening()
            listening = true
        } catch (e: Exception) {
            // Some ROMs throw when the widget service is unavailable; widgets simply stay static.
        }
    }

    fun stop() {
        if (!listening) return
        try { host.stopListening() } catch (e: Exception) { }
        listening = false
    }

    // ---- state

    private fun ids(): List<Int> = a.prefs.widgetIdList()

    fun hasAny(): Boolean = ids().isNotEmpty()

    private fun infoOf(id: Int): AppWidgetProviderInfo? =
        try { manager.getAppWidgetInfo(id) } catch (e: Exception) { null }

    private fun minWDp(info: AppWidgetProviderInfo?): Int =
        if (info == null) 0 else (info.minWidth / a.resources.displayMetrics.density).toInt()

    private fun minHDp(info: AppWidgetProviderInfo?): Int =
        if (info == null) 0 else (info.minHeight / a.resources.displayMetrics.density).toInt()

    private fun widthDp(id: Int): Int = a.prefs.widgetW(id).let { if (it <= 0) DEFAULT_W else it }

    /** Saved height, or one that keeps the widget's own aspect ratio at the chosen width. */
    private fun heightDp(id: Int, info: AppWidgetProviderInfo?): Int {
        val saved = a.prefs.widgetH(id)
        if (saved > 0) return saved
        val mw = minWDp(info)
        val mh = minHDp(info)
        if (mw <= 0 || mh <= 0) return 160
        return (widthDp(id).toFloat() * mh / mw).toInt().coerceIn(90, 480)
    }

    private fun maxWidthPx(): Int = a.widgetAreaWidth()

    private fun cardWidthPx(id: Int): Int = minOf(a.dp(widthDp(id)), maxWidthPx())

    // ---- building

    fun rebuild() {
        for (c in cards.values) a.removeBackdropsOf(c)
        column.removeAllViews()
        cards.clear()
        hostViews.clear()
        if (editMode) column.addView(doneChip())
        for (id in ids()) {
            val info = infoOf(id)
            if (info == null) { drop(id); continue }
            val view: AppWidgetHostView = try {
                host.createView(a, id, info)
            } catch (e: Exception) {
                continue
            }
            view.setPadding(0, 0, 0, 0)
            val card = a.glassCard(view, 18)
            val hDp = heightDp(id, info)
            val lp = LinearLayout.LayoutParams(cardWidthPx(id), a.dp(hDp))
            lp.bottomMargin = a.dp(12)
            if (editMode) addHandle(card, id)
            column.addView(card, lp)
            cards[id] = card
            hostViews[id] = view
            reportSize(view, cardWidthPx(id), a.dp(hDp))
        }
    }

    /** Tell the widget how big it is, so responsive widgets pick the right layout. */
    @Suppress("DEPRECATION")
    private fun reportSize(view: AppWidgetHostView, wPx: Int, hPx: Int) {
        val d = a.resources.displayMetrics.density
        val w = (wPx / d).toInt()
        val h = (hPx / d).toInt()
        try { view.updateAppWidgetSize(Bundle(), w, h, w, h) } catch (e: Exception) { }
    }

    /** Called when the available width changes (rotation, window size) without rebuilding the widgets. */
    fun applyWidth() {
        for ((id, card) in cards) {
            val lp = card.layoutParams as? LinearLayout.LayoutParams ?: continue
            val w = cardWidthPx(id)
            if (lp.width != w) {
                lp.width = w
                card.layoutParams = lp
                hostViews[id]?.let { reportSize(it, w, lp.height) }
            }
        }
    }

    // ---- editing

    fun setEdit(on: Boolean) {
        editMode = on
        rebuild()
        a.onRootResized()
    }

    private fun drop(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (e: Exception) { }
        a.prefs.removeWidget(id)
    }

    fun remove(id: Int) {
        drop(id)
        rebuild()
        a.onRootResized()
    }

    fun removeAll() {
        for (id in ids()) drop(id)
        editMode = false
        rebuild()
        a.onRootResized()
    }

    fun move(id: Int, dir: Int) {
        a.prefs.moveWidget(id, dir)
        rebuild()
    }

    fun preset(id: Int, wDp: Int) {
        a.prefs.setWidgetSize(id, wDp, 0)
        rebuild()
        a.onRootResized()
    }

    fun resetSize(id: Int) = preset(id, 0)

    fun idAt(x: Float, y: Float): Int {
        if (scroll.visibility != View.VISIBLE) return 0
        for ((id, card) in cards) {
            if (isOverView(card, x, y)) return id
        }
        return 0
    }

    private fun doneChip(): View {
        val chip = a.label(a.getString(R.string.widget_done), 13, Theme.bg, true)
        chip.gravity = Gravity.CENTER
        chip.background = a.rounded(Theme.accentColor, 16)
        chip.setPadding(a.dp(14), 0, a.dp(14), 0)
        chip.setOnClickListener { setEdit(false) }
        val lp = LinearLayout.LayoutParams(-2, a.dp(34))
        lp.bottomMargin = a.dp(10)
        chip.layoutParams = lp
        return chip
    }

    /** Drag the corner handle to resize a widget freely. */
    private fun addHandle(card: FrameLayout, id: Int) {
        val h = a.circleIcon(R.drawable.ic_maximize, Theme.accentColor, Theme.bg, 14)
        card.addView(h, FrameLayout.LayoutParams(a.dp(30), a.dp(30), Gravity.BOTTOM or Gravity.END))
        var sx = 0f
        var sy = 0f
        var w0 = 0
        var h0 = 0
        var cw = 0
        var ch = 0
        h.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = ev.rawX
                    sy = ev.rawY
                    w0 = card.width
                    h0 = card.height
                    cw = w0
                    ch = h0
                    scroll.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    cw = (w0 + (ev.rawX - sx)).toInt().coerceIn(a.dp(120), maxOf(a.dp(120), minOf(a.dp(560), maxWidthPx())))
                    ch = (h0 + (ev.rawY - sy)).toInt().coerceIn(a.dp(60), a.dp(640))
                    val lp = card.layoutParams as LinearLayout.LayoutParams
                    lp.width = cw
                    lp.height = ch
                    card.layoutParams = lp
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val d = a.resources.displayMetrics.density
                    a.prefs.setWidgetSize(id, (cw / d).toInt(), (ch / d).toInt())
                    rebuild()
                    a.onRootResized()
                }
            }
            true
        }
    }

    // ---- adding: picker -> bind -> configure -> commit

    private class Entry(val info: AppWidgetProviderInfo, val label: String, val app: String)

    @Suppress("DEPRECATION")
    fun showPicker() {
        val pm = a.packageManager
        val all: List<AppWidgetProviderInfo> = try { manager.installedProviders } catch (e: Exception) { emptyList() }
        val entries = all.mapNotNull { info ->
            try {
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { info.provider.packageName }
                val app = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                } catch (e: Exception) { info.provider.packageName }
                Entry(info, label, app)
            } catch (e: Exception) { null }
        }.sortedWith(compareBy({ it.app.lowercase(Locale.getDefault()) }, { it.label.lowercase(Locale.getDefault()) }))

        if (entries.isEmpty()) {
            a.toast(R.string.widget_none_available)
            return
        }

        val d = a.resources.displayMetrics.density
        val root = LinearLayout(a)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(a.dp(14), a.dp(14), a.dp(14), a.dp(8))
        root.background = a.rounded(Theme.panel, 20)

        root.addView(a.label(a.getString(R.string.widget_pick_title), 18, Theme.text, true))
        val search = EditText(a)
        search.setSingleLine(true)
        search.hint = a.getString(R.string.widget_pick_search)
        search.setTextColor(Theme.text)
        search.setHintTextColor(Theme.muted)
        search.textSize = 14f
        search.background = a.rounded(Theme.panel2, 12)
        search.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
        val sp = LinearLayout.LayoutParams(-1, -2)
        sp.topMargin = a.dp(10)
        sp.bottomMargin = a.dp(8)
        root.addView(search, sp)

        val list = LinearLayout(a)
        list.orientation = LinearLayout.VERTICAL
        val sv = MaxHeightScrollView(a)
        sv.maxHeightPx = (a.resources.displayMetrics.heightPixels * .6f).toInt()
        sv.addView(list, FrameLayout.LayoutParams(-1, -2))
        root.addView(sv, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(a).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(0))

        val previewViews = ArrayList<Pair<ImageView, Entry>>()
        fun fill(q: String) {
            list.removeAllViews()
            previewViews.clear()
            val f = q.trim().lowercase(Locale.getDefault())
            var lastApp = ""
            for (e in entries) {
                if (f.isNotEmpty() && !e.label.lowercase(Locale.getDefault()).contains(f) &&
                    !e.app.lowercase(Locale.getDefault()).contains(f)
                ) continue
                if (e.app != lastApp) {
                    lastApp = e.app
                    val head = a.label(e.app, 12, Theme.accentColor, true)
                    head.setPadding(a.dp(4), a.dp(10), 0, a.dp(4))
                    list.addView(head)
                }
                val row = LinearLayout(a)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8))
                row.background = a.rounded(Theme.panel2.withAlpha(120), 12)
                val iv = ImageView(a)
                iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
                row.addView(iv, LinearLayout.LayoutParams(a.dp(84), a.dp(60)))
                val col = LinearLayout(a)
                col.orientation = LinearLayout.VERTICAL
                col.setPadding(a.dp(12), 0, 0, 0)
                col.addView(a.label(e.label, 14, Theme.text, true))
                val cw = ((minWDp(e.info) + 30) / 70).coerceAtLeast(1)
                val chh = ((minHDp(e.info) + 30) / 70).coerceAtLeast(1)
                col.addView(a.label("$cw × $chh", 11, Theme.muted))
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                row.setOnClickListener {
                    dialog.dismiss()
                    startAdd(e.info)
                }
                val rl = LinearLayout.LayoutParams(-1, -2)
                rl.bottomMargin = a.dp(6)
                list.addView(row, rl)
                previewViews.add(iv to e)
            }
            loadPreviews(previewViews.toList(), dialog)
        }
        search.addTextChangedListener(SimpleTextWatcher { fill(it) })
        fill("")
        dialog.show()
        dialog.window?.setLayout((a.resources.displayMetrics.widthPixels * .92f).toInt().coerceAtMost((520 * d).toInt()), -2)
    }

    /** Previews can be large; load them one by one off the main thread. */
    private fun loadPreviews(items: List<Pair<ImageView, Entry>>, dialog: AlertDialog) {
        Thread {
            for ((iv, e) in items) {
                if (!dialog.isShowing) return@Thread
                val dr: Drawable? = try {
                    e.info.loadPreviewImage(a, 0) ?: e.info.loadIcon(a, 0)
                } catch (ex: Exception) { null }
                if (dr != null) a.runOnUiThread { if (dialog.isShowing) iv.setImageDrawable(dr) }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun startAdd(info: AppWidgetProviderInfo) {
        val id = try { host.allocateAppWidgetId() } catch (e: Exception) { a.toast(R.string.widget_add_failed); return }
        val ok = try { manager.bindAppWidgetIdIfAllowed(id, info.provider) } catch (e: Exception) { false }
        if (ok) {
            afterBind(id, info)
            return
        }
        // The system asks the user once for permission to bind this widget.
        pendingId = id
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        try {
            a.startActivityForResult(i, REQ_BIND)
        } catch (e: Exception) {
            cancelPending()
            a.toast(R.string.widget_add_failed)
        }
    }

    private fun afterBind(id: Int, info: AppWidgetProviderInfo?) {
        if (info == null) { try { host.deleteAppWidgetId(id) } catch (e: Exception) { }; a.toast(R.string.widget_add_failed); return }
        if (info.configure != null) {
            pendingId = id
            try {
                host.startAppWidgetConfigureActivityForResult(a, id, 0, REQ_CONFIG, null)
                return
            } catch (e: Exception) {
                // Configuration screen cannot be opened: add the widget with its defaults.
            }
        }
        commit(id)
    }

    private fun commit(id: Int) {
        pendingId = -1
        a.prefs.addWidget(id)
        rebuild()
        a.onRootResized()
    }

    private fun cancelPending() {
        if (pendingId != -1) {
            try { host.deleteAppWidgetId(pendingId) } catch (e: Exception) { }
            pendingId = -1
        }
    }

    /** Result of the bind-permission dialog or a widget's configuration screen. Returns true when handled. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            REQ_BIND -> {
                val id = pendingId
                if (resultCode == Activity.RESULT_OK && id != -1) afterBind(id, infoOf(id))
                else cancelPending()
                return true
            }
            REQ_CONFIG -> {
                val id = pendingId
                if (resultCode == Activity.RESULT_OK && id != -1) commit(id) else cancelPending()
                return true
            }
        }
        return false
    }
}
