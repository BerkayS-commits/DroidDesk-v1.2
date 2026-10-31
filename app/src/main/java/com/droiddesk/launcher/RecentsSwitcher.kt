package com.droiddesk.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout

class RecentItem(val pkg: String, val label: String, val icon: Bitmap, val active: Boolean)

/** Implemented by whoever shows the switcher (the launcher, or the accessibility overlay on top of other apps). */
interface RecentsHost {
    fun open(item: RecentItem)
    fun close(item: RecentItem)
    fun closeAll(items: List<RecentItem>)
    fun dismiss()
}

/**
 * Builds the list of recent apps. Android gives apps no direct list of recent tasks, so this uses
 * usage events (when "Usage access" is granted) plus the apps launched from DroidDesk.
 */
object RecentsData {
    private val dismissedAt = HashMap<String, Long>()

    fun markDismissed(pkg: String) { dismissedAt[pkg] = System.currentTimeMillis() }
    fun clearDismissed(pkg: String) { dismissedAt.remove(pkg) }

    fun load(ctx: Context, prefs: Prefs, limit: Int = 15): List<RecentItem> {
        val pm = ctx.packageManager
        val skip = HashSet<String>()
        skip.add(ctx.packageName)
        skip.add("com.android.systemui")
        skip.addAll(AppRepository(ctx).homePackages())

        val order = LinkedHashMap<String, Long>()
        if (ActiveApps.hasAccess(ctx)) {
            for ((p, t) in ActiveApps.recentOrder(ctx, 24 * 3_600_000L)) {
                if (t > (dismissedAt[p] ?: 0L)) order[p] = t
            }
        }
        for (p in prefs.recents) {
            if (!order.containsKey(p) && !dismissedAt.containsKey(p)) order[p] = 0L
        }
        val fg = DroidBridge.foregroundPkg
        val ordered = ArrayList<String>()
        if (fg.isNotEmpty() && !skip.contains(fg)) ordered.add(fg)
        for (p in order.keys) if (p != fg) ordered.add(p)

        val size = maxOf(64, ctx.dp(56))
        val out = ArrayList<RecentItem>()
        for (pkg in ordered) {
            if (out.size >= limit) break
            if (skip.contains(pkg)) continue
            val launch = try { pm.getLaunchIntentForPackage(pkg) } catch (e: Exception) { null } ?: continue
            if (launch.component == null) continue
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = IconStyler.render(pm.getApplicationIcon(pkg), size, prefs.iconShape)
                out.add(RecentItem(pkg, label, icon, pkg == fg))
            } catch (e: Exception) {
                // App uninstalled or hidden; skip it.
            }
        }
        return out
    }

    fun launchIntent(ctx: Context, pkg: String): Intent? =
        try { ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } catch (e: Exception) { null }
}

/**
 * The switcher UI: square app blocks, a close button on each block and a "Close all" button underneath.
 * Keyboard: Tab / arrows to move, Enter to open, Delete to close one, Esc to leave.
 */
class RecentsUi(
    private val ctx: Context,
    private var items: List<RecentItem>,
    private val host: RecentsHost,
    private val widthPx: Int,
    private val heightPx: Int
) {
    val view = FrameLayout(ctx)
    private var selected = 0
    private var cols = 4
    private val tiles = ArrayList<View>()

    init { build() }

    private fun build() {
        view.removeAllViews()
        tiles.clear()
        val accent = Theme.accentColor
        view.setBackgroundColor(0xBF050B18.toInt())
        view.isClickable = true
        view.setOnClickListener { host.dismiss() }

        val tileDp = 134
        cols = ((widthPx - ctx.dp(72)) / ctx.dp(tileDp)).coerceIn(2, 6)

        val panel = LinearLayout(ctx)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(ctx.dp(20), ctx.dp(16), ctx.dp(20), ctx.dp(16))
        panel.background = ctx.rounded(Color.argb(240, 18, 28, 48), 26, Color.argb(60, 255, 255, 255), 1)
        panel.isClickable = true
        panel.elevation = ctx.dp(24).toFloat()

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(ctx.label(ctx.getString(R.string.recents_title), 18, Theme.text, true), LinearLayout.LayoutParams(0, ctx.dp(36), 1f))
        if (items.isNotEmpty()) head.addView(ctx.label(items.size.toString(), 14, Theme.muted))
        panel.addView(head)

        if (items.isEmpty()) {
            val empty = ctx.label(ctx.getString(R.string.recents_empty), 15, Theme.muted)
            empty.gravity = Gravity.CENTER
            panel.addView(empty, LinearLayout.LayoutParams(ctx.dp(260), ctx.dp(120)))
        } else {
            val scroll = MaxHeightScrollView(ctx)
            scroll.maxHeightPx = (heightPx * 0.62f).toInt()
            scroll.isVerticalScrollBarEnabled = false
            val grid = GridLayout(ctx)
            grid.columnCount = cols
            items.forEachIndexed { i, item ->
                val tile = tile(item, i, tileDp, accent)
                tiles.add(tile)
                val p = GridLayout.LayoutParams()
                p.width = ctx.dp(tileDp)
                p.height = ctx.dp(tileDp)
                p.setMargins(ctx.dp(5), ctx.dp(5), ctx.dp(5), ctx.dp(5))
                grid.addView(tile, p)
            }
            scroll.addView(grid, FrameLayout.LayoutParams(-2, -2))
            panel.addView(scroll, LinearLayout.LayoutParams(-2, -2))
        }

        val foot = LinearLayout(ctx)
        foot.orientation = LinearLayout.VERTICAL
        foot.gravity = Gravity.CENTER_HORIZONTAL
        val closeAll = LinearLayout(ctx)
        closeAll.orientation = LinearLayout.HORIZONTAL
        closeAll.gravity = Gravity.CENTER
        closeAll.setPadding(ctx.dp(22), 0, ctx.dp(22), 0)
        closeAll.background = ctx.rounded(Color.rgb(239, 68, 68), 22)
        closeAll.addView(ctx.iconView(R.drawable.ic_clear_all, Color.WHITE), llp(ctx.dp(20), ctx.dp(20)))
        val ct = ctx.label(ctx.getString(R.string.recents_close_all), 14, Color.WHITE, true)
        ct.setPadding(ctx.dp(8), 0, 0, 0)
        closeAll.addView(ct)
        closeAll.isClickable = true
        closeAll.isFocusable = true
        ctx.hoverable(closeAll, 22, 1.04f)
        closeAll.setOnClickListener {
            host.closeAll(items)
            items = emptyList()
            host.dismiss()
        }
        val cp = LinearLayout.LayoutParams(-2, ctx.dp(44))
        cp.topMargin = ctx.dp(12)
        foot.addView(closeAll, cp)
        val hint = ctx.label(ctx.getString(R.string.recents_hint), 11, Theme.muted)
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, ctx.dp(8), 0, 0)
        foot.addView(hint)
        panel.addView(foot, LinearLayout.LayoutParams(-1, -2))

        view.addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        selected = selected.coerceIn(0, maxOf(0, items.size - 1))
        paintSelection()
    }

    private fun tile(item: RecentItem, index: Int, sizeDp: Int, accent: Int): View {
        val t = FrameLayout(ctx)
        t.isClickable = true
        t.isFocusable = true
        t.background = ctx.rounded(Theme.panel2.withAlpha(200), 18)
        t.tip(item.label)
        val icon = ImageView(ctx)
        icon.setImageBitmap(item.icon)
        t.addView(icon, FrameLayout.LayoutParams(ctx.dp(58), ctx.dp(58), Gravity.CENTER_HORIZONTAL or Gravity.TOP).also { it.topMargin = ctx.dp(22) })
        val name = ctx.label(item.label, 12, Theme.text)
        name.gravity = Gravity.CENTER
        name.maxLines = 2
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        t.addView(name, FrameLayout.LayoutParams(-1, ctx.dp(36), Gravity.BOTTOM).also {
            it.bottomMargin = ctx.dp(10)
            it.leftMargin = ctx.dp(6)
            it.rightMargin = ctx.dp(6)
        })
        if (item.active) {
            val bar = View(ctx)
            bar.background = ctx.rounded(accent, 2)
            t.addView(bar, FrameLayout.LayoutParams(ctx.dp(22), ctx.dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = ctx.dp(4) })
        }
        val x = ctx.circleIcon(R.drawable.ic_close, Color.argb(120, 0, 0, 0), Theme.text, 12)
        x.setOnClickListener {
            host.close(item)
            items = items.filter { it !== item }
            build()
        }
        t.addView(x, FrameLayout.LayoutParams(ctx.dp(26), ctx.dp(26), Gravity.TOP or Gravity.END).also {
            it.topMargin = ctx.dp(6)
            it.rightMargin = ctx.dp(6)
        })
        t.setOnClickListener { host.open(item) }
        t.setOnHoverListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    selected = index
                    paintSelection()
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                }
                MotionEvent.ACTION_HOVER_EXIT -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return t
    }

    private fun paintSelection() {
        tiles.forEachIndexed { i, t ->
            t.background = ctx.rounded(
                Theme.panel2.withAlpha(if (i == selected) 255 else 190), 18,
                if (i == selected) Theme.accentColor else 0, if (i == selected) 3 else 0
            )
        }
    }

    fun move(delta: Int) {
        if (items.isEmpty()) return
        selected = (selected + delta + items.size) % items.size
        paintSelection()
    }

    private fun moveRows(dy: Int) {
        if (items.isEmpty()) return
        selected = (selected + dy * cols).coerceIn(0, items.size - 1)
        paintSelection()
    }

    fun activate() {
        val item = items.getOrNull(selected)
        if (item != null) host.open(item) else host.dismiss()
    }

    private fun closeSelected() {
        val item = items.getOrNull(selected) ?: return
        host.close(item)
        items = items.filter { it !== item }
        build()
    }

    /** Returns true if the key was used. */
    fun onKey(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return false
        when (e.keyCode) {
            KeyEvent.KEYCODE_TAB -> move(if (e.isShiftPressed) -1 else 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveRows(1)
            KeyEvent.KEYCODE_DPAD_UP -> moveRows(-1)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> activate()
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> closeSelected()
            KeyEvent.KEYCODE_ESCAPE -> host.dismiss()
            else -> return false
        }
        return true
    }
}
