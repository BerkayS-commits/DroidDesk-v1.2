package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.provider.Settings
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

private class QTile(val bit: Int, val iconRes: Int, val labelRes: Int, val color: Int, val action: () -> Unit)

private class NItem(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
    val pi: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean
)

/**
 * The bottom-right panel: a Notifications tab (tap to open, dismiss, clear all, grouped by app) and a
 * Quick settings tab. Shortcuts in the header jump to DroidDesk settings and to the device settings.
 */
class NotificationCenter(private val a: MainActivity) {
    lateinit var card: FrameLayout
        private set
    var tab = 0
        private set

    private lateinit var tabNotif: TextView
    private lateinit var tabQuick: TextView
    private lateinit var clearBtn: View
    private lateinit var notifScroll: MaxHeightScrollView
    private lateinit var notifList: LinearLayout
    private lateinit var quickScroll: MaxHeightScrollView
    private lateinit var quickPage: LinearLayout
    private lateinit var volumeSeek: SeekBar
    private lateinit var brightnessSeek: SeekBar
    private val collapsed = HashSet<String>()

    fun build(): FrameLayout {
        val content = LinearLayout(a)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(14))

        val header = LinearLayout(a)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        tabNotif = chip(a.getString(R.string.tab_notifications)) { select(0) }
        tabQuick = chip(a.getString(R.string.tab_quick)) { select(1) }
        header.addView(tabNotif, llp(-2, a.dp(34)))
        header.addView(tabQuick, llp(-2, a.dp(34), a.dp(6)))
        header.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        clearBtn = a.circleIcon(R.drawable.ic_clear_all, Theme.panel2, Theme.text, 18)
        clearBtn.setOnClickListener { DroidBridge.listener?.cancelAllNotifications() }
        clearBtn.tip(a.getString(R.string.notif_clear_all))
        header.addView(clearBtn, llp(a.dp(34), a.dp(34)))
        val tune = a.circleIcon(R.drawable.ic_tune, Theme.panel2, Theme.text, 18)
        tune.setOnClickListener { a.showSettingsPanel(0, 0) }
        tune.tip(a.getString(R.string.droiddesk_settings))
        header.addView(tune, llp(a.dp(34), a.dp(34), a.dp(6)))
        val gear = a.circleIcon(R.drawable.ic_settings, Theme.panel2, Theme.text, 18)
        gear.setOnClickListener { a.openAndroidSettings() }
        gear.tip(a.getString(R.string.android_settings))
        header.addView(gear, llp(a.dp(34), a.dp(34), a.dp(6)))
        content.addView(header, LinearLayout.LayoutParams(-1, a.dp(40)))

        val body = FrameLayout(a)
        notifScroll = MaxHeightScrollView(a)
        notifScroll.isVerticalScrollBarEnabled = false
        notifList = LinearLayout(a)
        notifList.orientation = LinearLayout.VERTICAL
        notifScroll.addView(notifList, FrameLayout.LayoutParams(-1, -2))
        body.addView(notifScroll, FrameLayout.LayoutParams(-1, -2))

        quickScroll = MaxHeightScrollView(a)
        quickScroll.isVerticalScrollBarEnabled = false
        quickPage = LinearLayout(a)
        quickPage.orientation = LinearLayout.VERTICAL
        quickScroll.addView(quickPage, FrameLayout.LayoutParams(-1, -2))
        body.addView(quickScroll, FrameLayout.LayoutParams(-1, -2))
        val bp = LinearLayout.LayoutParams(-1, -2)
        bp.topMargin = a.dp(10)
        content.addView(body, bp)

        buildQuick()
        card = a.glassCard(content, 24)
        select(a.prefs.npTab)
        return card
    }

    fun setMaxBody(px: Int) {
        notifScroll.maxHeightPx = px
        quickScroll.maxHeightPx = px
    }

    private fun chip(text: String, onClick: () -> Unit): TextView {
        val t = a.label(text, 13, Theme.text, true)
        t.gravity = Gravity.CENTER
        t.setPadding(a.dp(14), 0, a.dp(14), 0)
        t.setOnClickListener { onClick() }
        return t
    }

    fun select(t: Int) {
        tab = t
        notifScroll.visibility = if (t == 0) View.VISIBLE else View.GONE
        quickScroll.visibility = if (t == 1) View.VISIBLE else View.GONE
        clearBtn.visibility = if (t == 0) View.VISIBLE else View.GONE
        tabNotif.background = a.rounded(if (t == 0) Theme.accentColor else Theme.panel2, 17)
        tabNotif.setTextColor(if (t == 0) Theme.bg else Theme.text)
        tabQuick.background = a.rounded(if (t == 1) Theme.accentColor else Theme.panel2, 17)
        tabQuick.setTextColor(if (t == 1) Theme.bg else Theme.text)
        if (t == 0) refresh() else syncSliders()
    }

    // ------------------------------------------------------------------ notifications tab

    private fun collect(): List<NItem>? {
        val l = DroidBridge.listener ?: return null
        val sbns = (try { l.activeNotifications } catch (e: Exception) { null }) ?: return null
        val out = ArrayList<NItem>()
        for (sbn in sbns) {
            val n = sbn.notification ?: continue
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            if (sbn.isOngoing && !a.prefs.npOngoing) continue
            val ex = n.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (ex?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex?.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            val label = a.appMap[sbn.packageName]?.label ?: sbn.packageName
            out.add(
                NItem(
                    sbn.key, sbn.packageName, label, title, text, sbn.postTime, n.contentIntent,
                    sbn.isClearable, (n.flags and Notification.FLAG_AUTO_CANCEL) != 0
                )
            )
        }
        out.sortByDescending { it.time }
        return out.take(a.prefs.npMax)
    }

    fun refresh() {
        if (!::notifList.isInitialized) return
        notifList.removeAllViews()
        val items = collect()
        if (items == null) {
            tabNotif.text = a.getString(R.string.tab_notifications)
            accessCard()
            return
        }
        tabNotif.text = a.getString(R.string.tab_notifications) + if (items.isNotEmpty()) " (" + items.size + ")" else ""
        if (items.isEmpty()) {
            val t = a.label(a.getString(R.string.notif_empty), 14, Theme.muted)
            t.gravity = Gravity.CENTER
            t.setPadding(0, a.dp(28), 0, a.dp(28))
            notifList.addView(t, LinearLayout.LayoutParams(-1, -2))
            return
        }
        if (a.prefs.npGroup) {
            val groups = LinkedHashMap<String, MutableList<NItem>>()
            items.forEach { groups.getOrPut(it.pkg) { ArrayList() }.add(it) }
            for ((pkg, list) in groups) {
                notifList.addView(groupHeader(pkg, list))
                if (!collapsed.contains(pkg)) list.forEach { notifList.addView(itemRow(it)) }
            }
        } else {
            items.forEach { notifList.addView(itemRow(it)) }
        }
    }

    private fun accessCard() {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14))
        box.background = a.rounded(Theme.panel2.withAlpha(170), 16)
        box.addView(a.label(a.getString(R.string.notif_access_msg), 13, Theme.text))
        val btn = a.label(a.getString(R.string.enable), 13, Theme.bg, true)
        btn.gravity = Gravity.CENTER
        btn.background = a.rounded(Theme.accentColor, 16)
        btn.setPadding(a.dp(18), 0, a.dp(18), 0)
        btn.setOnClickListener { a.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        val lp = LinearLayout.LayoutParams(-2, a.dp(38))
        lp.topMargin = a.dp(10)
        box.addView(btn, lp)
        notifList.addView(box, LinearLayout.LayoutParams(-1, -2))
    }

    private fun appIcon(pkg: String): View {
        val bmp = a.appMap[pkg]?.icon
        return if (bmp != null) {
            val i = ImageView(a)
            i.setImageBitmap(bmp)
            i
        } else {
            a.iconView(R.drawable.ic_bell, Theme.muted)
        }
    }

    private fun groupHeader(pkg: String, list: List<NItem>): View {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(a.dp(4), 0, a.dp(2), 0)
        row.addView(appIcon(pkg), llp(a.dp(18), a.dp(18)))
        val name = a.label(list[0].appLabel + "  ·  " + list.size, 12, Theme.muted, true)
        name.setPadding(a.dp(8), 0, 0, 0)
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val fold = a.circleIcon(R.drawable.ic_chevron_up, 0, Theme.muted, 16)
        fold.rotation = if (collapsed.contains(pkg)) 180f else 0f
        fold.setOnClickListener {
            if (!collapsed.remove(pkg)) collapsed.add(pkg)
            refresh()
        }
        row.addView(fold, llp(a.dp(30), a.dp(30)))
        val clear = a.circleIcon(R.drawable.ic_close, 0, Theme.muted, 14)
        clear.setOnClickListener { list.forEach { DroidBridge.listener?.cancelNotification(it.key) } }
        row.addView(clear, llp(a.dp(30), a.dp(30)))
        val lp = LinearLayout.LayoutParams(-1, a.dp(34))
        lp.topMargin = a.dp(4)
        row.layoutParams = lp
        return row
    }

    private fun itemRow(n: NItem): View {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(a.dp(10), a.dp(8), a.dp(4), a.dp(8))
        row.background = a.rounded(Theme.panel2.withAlpha(150), 12)
        row.isClickable = true
        row.setOnClickListener { open(n) }
        if (!a.prefs.npGroup) row.addView(appIcon(n.pkg), llp(a.dp(28), a.dp(28)))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        val title = a.label(if (n.title.isNotEmpty()) n.title else n.appLabel, 13, Theme.text, true)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)
        if (a.prefs.npPreview && n.text.isNotEmpty()) {
            val body = a.label(n.text, 12, Theme.muted)
            body.maxLines = 2
            body.ellipsize = android.text.TextUtils.TruncateAt.END
            col.addView(body)
        }
        val time = a.label(
            DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
            10, Theme.muted
        )
        col.addView(time)
        val cp = LinearLayout.LayoutParams(0, -2, 1f)
        cp.leftMargin = if (a.prefs.npGroup) 0 else a.dp(10)
        row.addView(col, cp)
        if (n.clearable) {
            val x = a.circleIcon(R.drawable.ic_close, 0, Theme.muted, 14)
            x.setOnClickListener { DroidBridge.listener?.cancelNotification(n.key) }
            row.addView(x, llp(a.dp(32), a.dp(32)))
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = a.dp(6)
        row.layoutParams = lp
        return row
    }

    private fun open(n: NItem) {
        val pi = n.pi
        if (pi != null) {
            try { pi.send() } catch (e: PendingIntent.CanceledException) { a.toast(R.string.toast_cannot_open) }
            a.closeNotifications()
        }
        if (n.autoCancel) DroidBridge.listener?.cancelNotification(n.key)
    }

    // ------------------------------------------------------------------ quick settings tab

    fun buildQuick() {
        quickPage.removeAllViews()
        val mask = a.prefs.qsMask
        val tools = listOf(
            QTile(1, R.drawable.ic_wifi, R.string.tile_internet, Color.rgb(45, 140, 255)) { a.openInternetPanel() },
            QTile(2, R.drawable.ic_bluetooth, R.string.tile_bluetooth, Color.rgb(124, 92, 255)) { a.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            QTile(4, R.drawable.ic_airplane, R.string.tile_airplane, Color.rgb(100, 116, 139)) { a.openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
            QTile(8, R.drawable.ic_display, R.string.tile_display, Color.rgb(139, 92, 246)) { a.openSettings(Settings.ACTION_DISPLAY_SETTINGS) },
            QTile(16, R.drawable.ic_volume, R.string.tile_sound, Color.rgb(249, 115, 22)) { a.openVolumePanel() },
            QTile(32, R.drawable.ic_battery, R.string.tile_battery, Color.rgb(16, 185, 129)) { a.openSettings(Intent.ACTION_POWER_USAGE_SUMMARY) },
            QTile(64, R.drawable.ic_power, R.string.tile_power, Color.rgb(239, 68, 68)) {
                a.withAccessibility(a.getString(R.string.tile_power)) {
                    it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                }
            },
            QTile(128, R.drawable.ic_settings, R.string.tile_settings, Color.rgb(100, 116, 139)) { a.openAndroidSettings() }
        ).filter { (mask and it.bit) != 0 }

        val grid = GridLayout(a)
        grid.columnCount = 4
        tools.forEach { tool ->
            val tile = LinearLayout(a)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(0, a.dp(6), 0, a.dp(6))
            tile.isClickable = true
            tile.isFocusable = true
            tile.setOnClickListener { tool.action() }
            a.hoverable(tile)
            val bg = GradientDrawable()
            if (a.prefs.qsShape == 0) bg.shape = GradientDrawable.OVAL else bg.cornerRadius = a.dp(16).toFloat()
            bg.setColor(tool.color)
            val circle = FrameLayout(a)
            circle.background = bg
            circle.addView(a.iconView(tool.iconRes, Color.WHITE), FrameLayout.LayoutParams(a.dp(26), a.dp(26), Gravity.CENTER))
            tile.addView(circle, LinearLayout.LayoutParams(a.dp(52), a.dp(52)))
            val name = a.label(a.getString(tool.labelRes), 11, Theme.text)
            name.gravity = Gravity.CENTER
            tile.addView(name, LinearLayout.LayoutParams(-2, a.dp(26)))
            val p = GridLayout.LayoutParams()
            p.width = 0
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            grid.addView(tile, p)
        }
        if (tools.isNotEmpty()) quickPage.addView(grid, LinearLayout.LayoutParams(-1, -2))

        val audio = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeSeek = styledSeek(maxOf(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))
        volumeSeek.setOnSeekBarChangeListener(seekListener { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) })
        quickPage.addView(sliderRow(R.drawable.ic_volume, volumeSeek) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            syncSliders()
        }, topMargin(a.dp(10)))

        brightnessSeek = styledSeek(100)
        brightnessSeek.setOnSeekBarChangeListener(seekListener { v ->
            val wp = a.window.attributes
            wp.screenBrightness = maxOf(.05f, v / 100f)
            a.window.attributes = wp
        })
        quickPage.addView(sliderRow(R.drawable.ic_brightness, brightnessSeek) { }, topMargin(a.dp(8)))
    }

    private fun topMargin(px: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = px
        return p
    }

    private fun styledSeek(maxValue: Int): SeekBar {
        val s = SeekBar(a)
        s.max = maxValue
        s.progressTintList = android.content.res.ColorStateList.valueOf(Theme.accentColor)
        s.thumbTintList = android.content.res.ColorStateList.valueOf(Theme.accentColor)
        s.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Theme.panel2)
        return s
    }

    private fun sliderRow(res: Int, seek: SeekBar, onIcon: () -> Unit): LinearLayout {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val circle = a.circleIcon(res, Theme.panel2, Theme.text, 22)
        circle.setOnClickListener { onIcon() }
        row.addView(circle, LinearLayout.LayoutParams(a.dp(44), a.dp(44)))
        val sp = LinearLayout.LayoutParams(0, -2, 1f)
        sp.leftMargin = a.dp(10)
        row.addView(seek, sp)
        return row
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    fun syncSliders() {
        if (!::volumeSeek.isInitialized) return
        val audio = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeSeek.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val w = a.window.attributes.screenBrightness
        brightnessSeek.progress = if (w >= 0f) {
            (w * 100).toInt()
        } else {
            Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 178) * 100 / 255
        }
    }
}
