package com.droiddesk.launcher

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout

/** A small floating window inside the launcher: drag by the title, minimise, maximise, close, resize. */
class FilesWindow(private val a: MainActivity) {
    private var window: FrameLayout? = null
    private var x = 0
    private var y = 0
    private var w = 0
    private var h = 0
    private var max = false

    private class Folder(val labelRes: Int, val color: Int, val action: () -> Unit)

    fun isOver(px: Float, py: Float): Boolean = isOverView(window, px, py)

    fun reset() { window = null }

    fun show() {
        val existing = window
        if (existing != null) {
            existing.visibility = View.VISIBLE
            existing.bringToFront()
            return
        }
        a.closeDrawer()
        a.closeNotifications()
        val body = LinearLayout(a)
        body.orientation = LinearLayout.VERTICAL

        val titleBar = LinearLayout(a)
        titleBar.orientation = LinearLayout.HORIZONTAL
        titleBar.gravity = Gravity.CENTER_VERTICAL
        titleBar.setPadding(a.dp(14), 0, a.dp(4), 0)
        titleBar.addView(a.iconView(R.drawable.ic_folder, Theme.accentColor), llp(a.dp(18), a.dp(18)))
        val tt = a.label(a.getString(R.string.files_window_title), 14, Theme.text, true)
        tt.setPadding(a.dp(10), 0, 0, 0)
        titleBar.addView(tt, LinearLayout.LayoutParams(0, -2, 1f))
        titleBar.addView(winButton(R.drawable.ic_minimize) { window?.visibility = View.GONE }, llp(a.dp(40), a.dp(40)))
        titleBar.addView(winButton(R.drawable.ic_maximize) { max = !max; apply() }, llp(a.dp(40), a.dp(40)))
        titleBar.addView(winButton(R.drawable.ic_close) { close() }, llp(a.dp(40), a.dp(40)))
        body.addView(titleBar, LinearLayout.LayoutParams(-1, a.dp(46)))

        val folders = listOf(
            Folder(R.string.folder_documents, Color.rgb(59, 130, 246)) {
                a.safeStart(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
            },
            Folder(R.string.folder_downloads, Color.rgb(16, 185, 129)) {
                a.safeStart(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            },
            Folder(R.string.folder_pictures, Color.rgb(139, 92, 246)) {
                a.safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
            },
            Folder(R.string.folder_music, Color.rgb(249, 115, 22)) {
                a.safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC))
            }
        )
        val grid = GridLayout(a)
        grid.columnCount = 4
        grid.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(4))
        folders.forEach { f ->
            val tile = LinearLayout(a)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(0, a.dp(8), 0, a.dp(8))
            tile.isClickable = true
            tile.isFocusable = true
            tile.setOnClickListener { f.action() }
            a.hoverable(tile)
            val circle = a.circleIcon(R.drawable.ic_folder, f.color, Color.WHITE, 24)
            circle.isClickable = false
            circle.isFocusable = false
            tile.addView(circle, LinearLayout.LayoutParams(a.dp(46), a.dp(46)))
            val name = a.label(a.getString(f.labelRes), 11, Theme.text)
            name.gravity = Gravity.CENTER
            tile.addView(name, LinearLayout.LayoutParams(-2, a.dp(24)))
            val p = GridLayout.LayoutParams()
            p.width = 0
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            grid.addView(tile, p)
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
        body.addView(View(a), LinearLayout.LayoutParams(-1, 0, 1f))

        val gripRow = LinearLayout(a)
        gripRow.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        val grip = a.label("◢", 16, Theme.muted)
        grip.setPadding(a.dp(14), 0, a.dp(10), a.dp(2))
        gripRow.addView(grip, LinearLayout.LayoutParams(-2, -1))
        body.addView(gripRow, LinearLayout.LayoutParams(-1, a.dp(30)))

        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        titleBar.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = ev.rawX
                    sy = ev.rawY
                    ox = x
                    oy = y
                }
                MotionEvent.ACTION_MOVE -> if (!max) {
                    x = ox + (ev.rawX - sx).toInt()
                    y = oy + (ev.rawY - sy).toInt()
                    apply()
                }
            }
            true
        }
        var rx = 0f
        var ry = 0f
        var rw = 0
        var rh = 0
        grip.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rx = ev.rawX
                    ry = ev.rawY
                    rw = w
                    rh = h
                }
                MotionEvent.ACTION_MOVE -> if (!max) {
                    w = maxOf(a.dp(280), rw + (ev.rawX - rx).toInt())
                    h = maxOf(a.dp(200), rh + (ev.rawY - ry).toInt())
                    apply()
                }
            }
            true
        }

        val card = a.glassCard(body, 18)
        card.elevation = a.dp(20).toFloat()
        max = false
        w = minOf(a.dp(440), a.root.width - a.dp(24))
        h = a.dp(300)
        x = maxOf(0, (a.root.width - w) / 2)
        y = a.insets[1] + a.dp(40)
        a.root.addView(card, FrameLayout.LayoutParams(w, h))
        window = card
        apply()
    }

    private fun winButton(res: Int, action: () -> Unit): FrameLayout {
        val f = FrameLayout(a)
        f.isClickable = true
        f.isFocusable = true
        f.addView(a.iconView(res, Theme.text), FrameLayout.LayoutParams(a.dp(16), a.dp(16), Gravity.CENTER))
        f.setOnClickListener { action() }
        a.hoverable(f)
        return f
    }

    fun close() {
        val c = window ?: return
        a.root.removeView(c)
        a.removeBackdropsOf(c)
        window = null
    }

    fun apply() {
        val win = window ?: return
        val root = a.root
        if (root.width == 0) return
        val p = FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START)
        if (max) {
            p.width = root.width - a.insets[0] - a.insets[2] - a.dp(16)
            p.height = maxOf(a.dp(200), root.height - a.insets[1] - a.bottomOffset() - a.dp(8))
            p.leftMargin = a.insets[0] + a.dp(8)
            p.topMargin = a.insets[1] + a.dp(8)
        } else {
            w = w.coerceIn(a.dp(280), maxOf(a.dp(280), root.width - a.dp(16)))
            h = h.coerceIn(a.dp(200), maxOf(a.dp(200), root.height - a.insets[1] - a.dp(16)))
            x = x.coerceIn(0, maxOf(0, root.width - w))
            y = y.coerceIn(a.insets[1], maxOf(a.insets[1], root.height - h - a.dp(8)))
            p.width = w
            p.height = h
            p.leftMargin = x
            p.topMargin = y
        }
        win.layoutParams = p
    }
}
