package com.droiddesk.launcher

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Desktop widgets: clock + weather (1), calendar (2), system status (4), daily quote (8). */
class DesktopWidgets(private val a: MainActivity) {
    val scroll = ScrollView(a)
    private val column = LinearLayout(a)
    var editMode = false
        private set

    private var weather: WeatherInfo? = null
    private var fetching = false
    private var lastAttempt = 0L
    private var monthOffset = 0
    private val cards = HashMap<Int, View>()

    private var clockTime: TextView? = null
    private var clockDate: TextView? = null
    private var weatherIcon: TextView? = null
    private var weatherTemp: TextView? = null
    private var weatherDesc: TextView? = null
    private var calendarTitle: TextView? = null
    private var calendarGrid: GridLayout? = null
    private var ringBattery: RingView? = null
    private var ringStorage: RingView? = null
    private var ringRam: RingView? = null
    private var sysBattery: TextView? = null
    private var sysStorage: TextView? = null
    private var sysRam: TextView? = null

    init {
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> a.invalidateBackdrops() }
        column.orientation = LinearLayout.VERTICAL
        scroll.addView(column, FrameLayout.LayoutParams(-2, -2))
        weather = if (a.prefs.weatherCity.isBlank()) null else a.prefs.loadWeather()
    }

    private fun widthDp(bit: Int): Int = a.prefs.widgetW(bit).let { if (it <= 0) 300 else it }
    private fun scale(bit: Int): Float = (widthDp(bit) / 300f).coerceIn(.7f, 1.8f)
    private fun sz(base: Int, s: Float): Int = maxOf(8, (base * s).toInt())

    fun visibleBits(): List<Int> = a.prefs.orderedWidgets().filter { (a.prefs.widgetMask and it) != 0 }

    fun hasAny(): Boolean = visibleBits().isNotEmpty()

    fun rebuild() {
        column.removeAllViews()
        cards.clear()
        clockTime = null; clockDate = null; weatherIcon = null; weatherTemp = null; weatherDesc = null
        calendarTitle = null; calendarGrid = null
        ringBattery = null; ringStorage = null; ringRam = null
        sysBattery = null; sysStorage = null; sysRam = null
        if (editMode) column.addView(doneChip())
        for (bit in visibleBits()) {
            val content: View = when (bit) {
                1 -> clockContent()
                2 -> calendarContent()
                4 -> systemContent()
                else -> quoteContent()
            }
            val card = a.glassCard(content, 18)
            val hDp = a.prefs.widgetH(bit)
            val lp = LinearLayout.LayoutParams(a.dp(widthDp(bit)), if (hDp > 0) a.dp(hDp) else -2)
            lp.bottomMargin = a.dp(12)
            if (editMode) addHandle(card, bit)
            column.addView(card, lp)
            cards[bit] = card
        }
        renderCalendar()
        update()
    }

    // ---- editing

    fun setEdit(on: Boolean) {
        editMode = on
        rebuild()
        a.onRootResized()
    }

    fun remove(bit: Int) {
        a.prefs.widgetMask = a.prefs.widgetMask and bit.inv()
        rebuild()
        a.onRootResized()
    }

    fun move(bit: Int, dir: Int) {
        a.prefs.moveWidget(bit, dir)
        rebuild()
    }

    fun preset(bit: Int, wDp: Int) {
        a.prefs.setWidgetSize(bit, wDp, 0)
        rebuild()
        a.onRootResized()
    }

    fun resetSize(bit: Int) = preset(bit, 0)

    fun restoreDefaults() {
        a.prefs.widgetMask = 7
        a.prefs.widgetOrder = "1,2,4,8"
        for (b in listOf(1, 2, 4, 8)) a.prefs.setWidgetSize(b, 0, 0)
        editMode = false
        rebuild()
        a.onRootResized()
    }

    fun bitAt(x: Float, y: Float): Int {
        if (scroll.visibility != View.VISIBLE) return 0
        for ((bit, card) in cards) {
            if (isOverView(card, x, y)) return bit
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
    private fun addHandle(card: FrameLayout, bit: Int) {
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
                    cw = (w0 + (ev.rawX - sx)).toInt().coerceIn(a.dp(180), a.dp(560))
                    ch = (h0 + (ev.rawY - sy)).toInt().coerceIn(a.dp(80), a.dp(640))
                    val lp = card.layoutParams as LinearLayout.LayoutParams
                    lp.width = cw
                    lp.height = ch
                    card.layoutParams = lp
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val d = a.resources.displayMetrics.density
                    a.prefs.setWidgetSize(bit, (cw / d).toInt(), (ch / d).toInt())
                    rebuild()
                    a.onRootResized()
                }
            }
            true
        }
    }

    // ---- content builders

    private fun clockContent(): View {
        val s = scale(1)
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(a.dp(18), a.dp(14), a.dp(16), a.dp(14))
        val left = LinearLayout(a)
        left.orientation = LinearLayout.VERTICAL
        val time = a.label("", sz(34, s), Theme.text, true)
        val date = a.label("", sz(14, s), Theme.muted)
        left.addView(time)
        left.addView(date)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val right = LinearLayout(a)
        right.orientation = LinearLayout.HORIZONTAL
        right.gravity = Gravity.CENTER_VERTICAL
        val wIcon = a.label("☁", sz(26, s), Theme.text)
        val wCol = LinearLayout(a)
        wCol.orientation = LinearLayout.VERTICAL
        wCol.setPadding(a.dp(6), 0, 0, 0)
        val wTemp = a.label("--", sz(18, s), Theme.text, true)
        val wDesc = a.label("", sz(10, s), Theme.muted)
        wCol.addView(wTemp)
        wCol.addView(wDesc)
        right.addView(wIcon)
        right.addView(wCol)
        right.isClickable = true
        right.setOnClickListener { askCity() }
        row.addView(right)
        clockTime = time; clockDate = date
        weatherIcon = wIcon; weatherTemp = wTemp; weatherDesc = wDesc
        return row
    }

    private fun calendarContent(): View {
        val s = scale(2)
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(12))
        val head = LinearLayout(a)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = a.label("", sz(15, s), Theme.text, true)
        head.addView(title, LinearLayout.LayoutParams(0, a.dp(34), 1f))
        val prev = a.circleIcon(R.drawable.ic_chevron_right, 0, Theme.muted, 18)
        prev.rotation = 180f
        prev.setOnClickListener { monthOffset--; renderCalendar() }
        val next = a.circleIcon(R.drawable.ic_chevron_right, 0, Theme.muted, 18)
        next.setOnClickListener { monthOffset++; renderCalendar() }
        head.addView(prev, llp(a.dp(30), a.dp(30)))
        head.addView(next, llp(a.dp(30), a.dp(30)))
        col.addView(head)
        val grid = GridLayout(a)
        grid.columnCount = 7
        col.addView(grid, LinearLayout.LayoutParams(-1, -2))
        calendarTitle = title
        calendarGrid = grid
        return col
    }

    private fun renderCalendar() {
        val g = calendarGrid ?: return
        val s = scale(2)
        g.removeAllViews()
        val cal = Calendar.getInstance()
        val ty = cal.get(Calendar.YEAR)
        val tm = cal.get(Calendar.MONTH)
        val td = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, monthOffset)
        calendarTitle?.text = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(cal.time)
        val first = cal.firstDayOfWeek
        val symbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
        for (i in 0 until 7) {
            val day = (first - 1 + i) % 7 + 1
            addCalCell(g, symbols[day].take(1).uppercase(Locale.getDefault()), Theme.muted, false, s)
        }
        val lead = (cal.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        for (i in 0 until lead) addCalCell(g, "", Theme.text, false, s)
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val sameMonth = cal.get(Calendar.YEAR) == ty && cal.get(Calendar.MONTH) == tm
        for (d in 1..days) {
            val isToday = sameMonth && d == td
            addCalCell(g, d.toString(), if (isToday) Theme.bg else Theme.text, isToday, s)
        }
    }

    private fun addCalCell(g: GridLayout, value: String, color: Int, today: Boolean, s: Float) {
        val t = a.label(value, sz(12, s), color, today)
        t.gravity = Gravity.CENTER
        if (today) {
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(Theme.accentColor)
            t.background = d
        }
        val p = GridLayout.LayoutParams()
        p.width = a.dp(sz(28, s))
        p.height = a.dp(sz(28, s))
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        p.setGravity(Gravity.CENTER)
        p.setMargins(0, a.dp(1), 0, a.dp(1))
        g.addView(t, p)
    }

    private fun systemContent(): View {
        val s = scale(4)
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(14))
        col.addView(a.label(a.getString(R.string.system_status), sz(15, s), Theme.text, true))
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, a.dp(8), 0, 0)
        val rb = RingView(a)
        rb.configure(Color.rgb(74, 222, 128), Theme.panel2, R.drawable.ic_battery)
        val vb = a.label("", sz(12, s), Theme.text, true)
        val rs = RingView(a)
        rs.configure(Color.rgb(129, 140, 248), Theme.panel2, R.drawable.ic_folder)
        val vs = a.label("", sz(12, s), Theme.text, true)
        val rr = RingView(a)
        rr.configure(Color.rgb(45, 212, 191), Theme.panel2, R.drawable.ic_display)
        val vr = a.label("", sz(12, s), Theme.text, true)
        row.addView(ringColumn(rb, a.getString(R.string.sys_battery), vb, s), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ringColumn(rs, a.getString(R.string.sys_storage), vs, s), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ringColumn(rr, a.getString(R.string.sys_ram), vr, s), LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row)
        ringBattery = rb; ringStorage = rs; ringRam = rr
        sysBattery = vb; sysStorage = vs; sysRam = vr
        return col
    }

    private fun ringColumn(ring: RingView, title: String, value: TextView, s: Float): LinearLayout {
        val c = LinearLayout(a)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(ring, LinearLayout.LayoutParams(a.dp(sz(54, s)), a.dp(sz(54, s))))
        val t = a.label(title, sz(11, s), Theme.muted)
        t.gravity = Gravity.CENTER
        c.addView(t, LinearLayout.LayoutParams(-2, a.dp(22)))
        value.gravity = Gravity.CENTER
        c.addView(value, LinearLayout.LayoutParams(-2, a.dp(18)))
        return c
    }

    private fun quoteContent(): View {
        val s = scale(8)
        val quotes = a.resources.getStringArray(R.array.quotes)
        val index = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % maxOf(1, quotes.size)
        val t = a.label("“" + quotes[index] + "” ✨", sz(13, s), Theme.text)
        t.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14))
        return t
    }

    // ---- live data

    fun update() {
        val now = Date()
        clockTime?.text = android.text.format.DateFormat.getTimeFormat(a).format(now)
        clockDate?.text = SimpleDateFormat("d MMMM EEEE", Locale.getDefault()).format(now)
        updateWeatherUi()
        refreshWeather(false)
        updateSystem()
    }

    private fun updateWeatherUi() {
        val icon = weatherIcon ?: return
        val w = weather
        if (a.prefs.weatherCity.isBlank()) {
            icon.text = "☁"
            weatherTemp?.text = "--"
            weatherDesc?.text = a.getString(R.string.weather_set_city)
        } else if (w == null) {
            icon.text = "☁"
            weatherTemp?.text = "…"
            weatherDesc?.text = a.prefs.weatherCity
        } else {
            icon.text = WeatherClient.emoji(w.code)
            weatherTemp?.text = w.tempC.toString() + "°"
            weatherDesc?.text = a.getString(WeatherClient.descriptionRes(w.code)) + "\n" + w.city
        }
    }

    fun refreshWeather(force: Boolean) {
        val city = a.prefs.weatherCity
        if (city.isBlank() || fetching || weatherIcon == null) return
        val now = System.currentTimeMillis()
        val cached = weather
        if (!force) {
            if (cached != null && now - cached.fetchedAt < 30 * 60 * 1000L) return
            if (now - lastAttempt < 5 * 60 * 1000L) return
        }
        lastAttempt = now
        fetching = true
        val lang = Locale.getDefault().language
        Thread {
            val result = WeatherClient.fetch(city, lang)
            a.runOnUiThread {
                fetching = false
                if (a.isDestroyed) return@runOnUiThread
                if (result != null) {
                    weather = result
                    a.prefs.saveWeather(result)
                }
                updateWeatherUi()
            }
        }.start()
    }

    fun askCity() {
        val input = EditText(a)
        input.setSingleLine(true)
        input.setText(a.prefs.weatherCity)
        input.hint = a.getString(R.string.weather_city_hint)
        val wrap = LinearLayout(a)
        wrap.setPadding(a.dp(20), a.dp(8), a.dp(20), 0)
        wrap.addView(input, LinearLayout.LayoutParams(-1, -2))
        AlertDialog.Builder(a)
            .setTitle(R.string.weather_city_title)
            .setMessage(R.string.weather_city_msg)
            .setView(wrap)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                a.prefs.weatherCity = input.text.toString().trim()
                a.prefs.clearWeather()
                weather = null
                lastAttempt = 0L
                updateWeatherUi()
                refreshWeather(true)
            }
            .show()
    }

    private fun updateSystem() {
        val rb = ringBattery ?: return
        val pct = a.batteryPercent()
        rb.setProgress(if (pct >= 0) pct / 100f else 0f)
        sysBattery?.text = if (pct >= 0) "$pct%" else "—"
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.blockCountLong * st.blockSizeLong
            val used = total - st.availableBlocksLong * st.blockSizeLong
            ringStorage?.setProgress(if (total > 0) used.toFloat() / total else 0f)
            sysStorage?.text = gb(used, 0)
            val mi = ActivityManager.MemoryInfo()
            (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            val ramUsed = mi.totalMem - mi.availMem
            ringRam?.setProgress(if (mi.totalMem > 0) ramUsed.toFloat() / mi.totalMem else 0f)
            sysRam?.text = gb(ramUsed, 1)
        } catch (e: Exception) {
            // Keep previous values.
        }
    }

    private fun gb(bytes: Long, decimals: Int): String =
        String.format(Locale.getDefault(), if (decimals == 0) "%.0f GB" else "%.1f GB", bytes / 1073741824.0)
}
