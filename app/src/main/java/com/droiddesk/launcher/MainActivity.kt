package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.DownloadManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private class Tool(val iconRes: Int, val labelRes: Int, val color: Int, val action: () -> Unit)
private class MenuEntry(val iconRes: Int, val label: String, val sub: Boolean, val action: () -> Unit)

/** DroidDesk 1.3 - Windows-inspired Android home launcher. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private val handler = Handler(Looper.getMainLooper())

    private var accent = Theme.accents[0].color
    private var glassMode = true
    private var gradientMode = "bluepurple"
    private var taskbarSize = 64
    private var drawerWidthFrac = .88f
    private var drawerHeightFrac = .68f
    private var iconSize = 1
    private var widgetMask = 7
    private var monthOffset = 0

    private var apps: List<AppEntry> = emptyList()
    private var appMap: Map<String, AppEntry> = emptyMap()
    private var filtered: List<AppEntry> = emptyList()
    private var shortcuts = mutableListOf<String>()
    private var pinned = mutableListOf<String>()
    private var recents = mutableListOf<String>()
    private var insets = intArrayOf(0, 0, 0, 0)
    private var drawerOpen = false
    private var quickOpen = false
    private var menuOpen = false
    private var trayExpanded = true
    private var launchCount = 0
    private var reloadToken = 0
    private var backdropBitmap: Bitmap? = null
    private val backdrops = mutableListOf<GlassBackdrop>()

    private var weather: WeatherInfo? = null
    private var weatherFetching = false
    private var lastWeatherAttempt = 0L

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var longPressRunnable: Runnable? = null
    private var menuCard: View? = null
    private var menuX = 0f
    private var menuY = 0f

    private var pageCols = 4
    private var perPage = 20
    private var currentPage = 0

    private var filesWindow: FrameLayout? = null
    private var fwX = 0
    private var fwY = 0
    private var fwW = 0
    private var fwH = 0
    private var fwMax = false

    private lateinit var root: FrameLayout
    private lateinit var gradientView: View
    private lateinit var wallpaperView: ImageView
    private lateinit var desktop: LinearLayout
    private lateinit var desktopArea: FrameLayout
    private lateinit var desktopGrid: GridLayout
    private lateinit var desktopHint: TextView
    private lateinit var widgetScroll: ScrollView
    private lateinit var widgetColumn: LinearLayout
    private lateinit var taskbarCard: FrameLayout
    private lateinit var centerScroll: HorizontalScrollView
    private lateinit var taskbarApps: LinearLayout
    private lateinit var trayBox: LinearLayout
    private lateinit var trayChevron: ImageView
    private lateinit var trayWifi: FrameLayout
    private lateinit var trayBt: FrameLayout
    private lateinit var trayVol: FrameLayout
    private lateinit var trayBell: FrameLayout
    private lateinit var trayBat: FrameLayout
    private lateinit var trayClock: TextView
    private lateinit var scrim: View
    private lateinit var drawerCard: FrameLayout
    private lateinit var drawerSearch: EditText
    private lateinit var drawerPager: ViewPager2
    private lateinit var dotsRow: LinearLayout
    private lateinit var emptyLabel: TextView
    private lateinit var quickCard: FrameLayout
    private lateinit var volumeSeek: SeekBar
    private lateinit var brightnessSeek: SeekBar
    private var startCell: View? = null

    // Widget views (null while the widget is disabled)
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

    private val pageAdapter = PageAdapter()

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Not every provider offers persistable grants.
            }
            prefs.wallpaper = uri.toString()
            loadWallpaper(uri.toString(), true)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateTray()
            handler.postDelayed(this, 15_000L)
        }
    }

    private val reloadRunnable = Runnable { reloadApps() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.removeCallbacks(reloadRunnable)
            handler.postDelayed(reloadRunnable, 600L)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(this)
        loadPrefs()
        setupWindow()
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // A launcher must never finish itself: just close whatever is open.
                if (menuOpen) dismissMenu() else if (drawerOpen) closeDrawer() else if (quickOpen) closeQuick()
            }
        })
        registerPackageReceiver()
        reloadApps()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        ticker.run()
        syncSeekBars()
        if (DroidBridge.consumeStartRequest()) openDrawer(false)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while DroidDesk is already on screen.
        dismissMenu()
        closeQuick()
        if (DroidBridge.consumeStartRequest()) toggleDrawer() else closeDrawer()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(packageReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun loadPrefs() {
        shortcuts = prefs.shortcuts
        pinned = prefs.pinned
        recents = prefs.recents
        accent = Theme.accent(prefs.accent).color
        glassMode = prefs.glass
        gradientMode = prefs.gradient
        taskbarSize = prefs.taskbarSize
        drawerWidthFrac = prefs.drawerWidth
        drawerHeightFrac = prefs.drawerHeight
        iconSize = prefs.iconSize
        widgetMask = prefs.widgetMask
        weather = if (prefs.weatherCity.isBlank()) null else prefs.loadWeather()
    }

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        // targetSdk 35 forces edge-to-edge on Android 15; do the same on older versions so layout is consistent.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    @Suppress("DEPRECATION")
    private fun barInsets(i: WindowInsets): IntArray {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = i.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            )
            intArrayOf(b.left, b.top, b.right, b.bottom)
        } else {
            intArrayOf(i.systemWindowInsetLeft, i.systemWindowInsetTop, i.systemWindowInsetRight, i.systemWindowInsetBottom)
        }
    }

    // ------------------------------------------------------------------ apps

    private fun reloadApps() {
        val token = ++reloadToken
        Thread {
            val list = try { repo.load() } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isDestroyed || token != reloadToken) return@runOnUiThread
                apps = list
                appMap = list.associateBy { it.pkg }
                seedDefaults()
                refreshAll()
            }
        }.start()
    }

    /** First run only: put Files / Chrome / YouTube / Play Store on the desktop when they are installed. */
    private fun seedDefaults() {
        if (prefs.seeded || apps.isEmpty()) return
        prefs.seeded = true
        val files = listOf(
            "com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.android.documentsui",
            "com.mi.android.globalFileexplorer", "com.sec.android.app.myfiles"
        ).firstOrNull { appMap.containsKey(it) }
        val chrome = "com.android.chrome".takeIf { appMap.containsKey(it) }
        val youtube = "com.google.android.youtube".takeIf { appMap.containsKey(it) }
        val play = "com.android.vending".takeIf { appMap.containsKey(it) }
        if (shortcuts.isEmpty()) {
            listOfNotNull(files, chrome, youtube, play).forEach { shortcuts.add(it) }
            prefs.shortcuts = shortcuts
        }
        if (pinned.isEmpty()) {
            listOfNotNull(files, chrome, play).forEach { pinned.add(it) }
            prefs.pinned = pinned
        }
    }

    private fun registerPackageReceiver() {
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_CHANGED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, f)
        }
    }

    private fun refreshAll() {
        renderDesktop()
        renderTaskbar()
        applyFilter()
        updateTray()
    }

    // ------------------------------------------------------------------ UI construction

    private fun buildUi() {
        drawerOpen = false
        quickOpen = false
        menuOpen = false
        menuCard = null
        filesWindow = null
        backdrops.clear()

        root = FrameLayout(this)
        root.setBackgroundColor(Theme.bg)
        gradientView = View(this)
        gradientView.background = gradientDrawable()
        wallpaperView = ImageView(this)
        wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
        wallpaperView.visibility = View.GONE
        root.addView(gradientView, FrameLayout.LayoutParams(-1, -1))
        root.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))

        desktop = LinearLayout(this)
        desktop.orientation = LinearLayout.VERTICAL
        root.addView(desktop, FrameLayout.LayoutParams(-1, -1))
        buildDesktopContent()

        scrim = View(this)
        scrim.setBackgroundColor(0x44000000)
        scrim.visibility = View.GONE
        scrim.setOnClickListener { dismissMenu(); closeDrawer(); closeQuick() }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        buildQuickPanel()
        buildDrawer()

        root.setOnApplyWindowInsetsListener { _, i ->
            insets = barInsets(i)
            applyInsets()
            i
        }
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or2, ob ->
            if (r - l != or2 - ol || b - t != ob - ot) onRootResized()
        }
        setContentView(root)
        applyInsets()
        root.requestApplyInsets()
        loadWallpaper(prefs.wallpaper, false)
        refreshAll()
    }

    private fun applyInsets() {
        if (!::desktop.isInitialized) return
        desktop.setPadding(dp(18) + insets[0], dp(16) + insets[1], dp(18) + insets[2], dp(12) + insets[3])
        onRootResized()
    }

    private fun onRootResized() {
        if (root.width == 0) return
        val wide = root.width >= dp(720)
        widgetScroll.visibility = if (wide && widgetMask != 0) View.VISIBLE else View.GONE
        val wlp = widgetScroll.layoutParams
        wlp.width = minOf(dp(320), (root.width * .34f).toInt())
        widgetScroll.layoutParams = wlp
        trayExpanded = root.width >= dp(640)
        layoutTray()
        updateOverlayLayout()
        recomputePages()
        renderDesktop()
        rebuildBackdrop()
        applyFilesWindow()
    }

    // ------------------------------------------------------------------ desktop (icons + widgets)

    private fun buildDesktopContent() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.HORIZONTAL

        desktopArea = FrameLayout(this)
        desktopGrid = GridLayout(this)
        desktopGrid.orientation = GridLayout.VERTICAL
        desktopArea.addView(desktopGrid, FrameLayout.LayoutParams(-2, -2))
        desktopHint = label(getString(R.string.desktop_empty), 14, Theme.muted)
        desktopHint.setPadding(dp(8), dp(8), dp(8), dp(8))
        desktopHint.visibility = View.GONE
        desktopArea.addView(desktopHint, FrameLayout.LayoutParams(dp(260), -2))
        desktopArea.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) renderDesktop()
        }
        body.addView(desktopArea, LinearLayout.LayoutParams(0, -1, 1f))

        widgetScroll = ScrollView(this)
        widgetScroll.isVerticalScrollBarEnabled = false
        widgetScroll.overScrollMode = View.OVER_SCROLL_NEVER
        widgetScroll.setOnScrollChangeListener { _, _, _, _, _ -> backdrops.forEach { it.invalidate() } }
        widgetColumn = LinearLayout(this)
        widgetColumn.orientation = LinearLayout.VERTICAL
        widgetScroll.addView(widgetColumn, FrameLayout.LayoutParams(-1, -2))
        body.addView(widgetScroll, LinearLayout.LayoutParams(dp(300), -1))

        desktop.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        buildWidgets()
        buildTaskbar()
    }

    private fun hasWidget(bit: Int): Boolean = (widgetMask and bit) != 0

    private fun buildWidgets() {
        clockTime = null; clockDate = null; weatherIcon = null; weatherTemp = null; weatherDesc = null
        calendarTitle = null; calendarGrid = null
        ringBattery = null; ringStorage = null; ringRam = null
        sysBattery = null; sysStorage = null; sysRam = null
        widgetColumn.removeAllViews()
        if (hasWidget(1)) addWidget(buildClockCard())
        if (hasWidget(2)) addWidget(buildCalendarCard())
        if (hasWidget(4)) addWidget(buildSystemCard())
        if (hasWidget(8)) addWidget(buildQuoteCard())
        updateWidgets()
    }

    private fun addWidget(card: View) {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.bottomMargin = dp(12)
        widgetColumn.addView(card, p)
    }

    private fun buildClockCard(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(14), dp(16), dp(14))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        val time = label("", 34, Theme.text, true)
        val date = label("", 14, Theme.muted)
        left.addView(time)
        left.addView(date)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.HORIZONTAL
        right.gravity = Gravity.CENTER_VERTICAL
        val wIcon = label("☁", 26, Theme.text)
        val wCol = LinearLayout(this)
        wCol.orientation = LinearLayout.VERTICAL
        wCol.setPadding(dp(6), 0, 0, 0)
        val wTemp = label("--", 18, Theme.text, true)
        val wDesc = label("", 10, Theme.muted)
        wCol.addView(wTemp)
        wCol.addView(wDesc)
        right.addView(wIcon)
        right.addView(wCol)
        right.isClickable = true
        right.setOnClickListener { askWeatherCity() }
        row.addView(right)

        clockTime = time; clockDate = date
        weatherIcon = wIcon; weatherTemp = wTemp; weatherDesc = wDesc
        return glassCard(row, 18)
    }

    private fun buildCalendarCard(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(10), dp(14), dp(12))
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = label("", 15, Theme.text, true)
        head.addView(title, LinearLayout.LayoutParams(0, dp(34), 1f))
        val prev = circleIcon(R.drawable.ic_chevron_right, 0, Theme.muted, 30, 18)
        prev.rotation = 180f
        prev.setOnClickListener { monthOffset--; renderCalendar() }
        val next = circleIcon(R.drawable.ic_chevron_right, 0, Theme.muted, 30, 18)
        next.setOnClickListener { monthOffset++; renderCalendar() }
        head.addView(prev, llp(dp(30), dp(30)))
        head.addView(next, llp(dp(30), dp(30)))
        col.addView(head)
        val grid = GridLayout(this)
        grid.columnCount = 7
        col.addView(grid, LinearLayout.LayoutParams(-1, -2))
        calendarTitle = title
        calendarGrid = grid
        renderCalendar()
        return glassCard(col, 18)
    }

    private fun renderCalendar() {
        val g = calendarGrid ?: return
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
            addCalCell(g, symbols[day].take(1).uppercase(Locale.getDefault()), Theme.muted, false)
        }
        val lead = (cal.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        for (i in 0 until lead) addCalCell(g, "", Theme.text, false)
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val sameMonth = cal.get(Calendar.YEAR) == ty && cal.get(Calendar.MONTH) == tm
        for (d in 1..days) {
            val isToday = sameMonth && d == td
            addCalCell(g, d.toString(), if (isToday) Theme.bg else Theme.text, isToday)
        }
    }

    private fun addCalCell(g: GridLayout, value: String, color: Int, today: Boolean) {
        val t = label(value, 12, color, today)
        t.gravity = Gravity.CENTER
        if (today) {
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(accent)
            t.background = d
        }
        val p = GridLayout.LayoutParams()
        p.width = dp(28)
        p.height = dp(28)
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        p.setGravity(Gravity.CENTER)
        p.setMargins(0, dp(1), 0, dp(1))
        g.addView(t, p)
    }

    private fun buildSystemCard(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(12), dp(16), dp(14))
        col.addView(label(getString(R.string.system_status), 15, Theme.text, true))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(8), 0, 0)

        val rb = RingView(this)
        rb.configure(Color.rgb(74, 222, 128), Theme.panel2, R.drawable.ic_battery)
        val vb = label("", 12, Theme.text, true)
        val rs = RingView(this)
        rs.configure(Color.rgb(129, 140, 248), Theme.panel2, R.drawable.ic_folder)
        val vs = label("", 12, Theme.text, true)
        val rr = RingView(this)
        rr.configure(Color.rgb(45, 212, 191), Theme.panel2, R.drawable.ic_display)
        val vr = label("", 12, Theme.text, true)
        row.addView(ringColumn(rb, getString(R.string.sys_battery), vb), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ringColumn(rs, getString(R.string.sys_storage), vs), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ringColumn(rr, getString(R.string.sys_ram), vr), LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row)
        ringBattery = rb; ringStorage = rs; ringRam = rr
        sysBattery = vb; sysStorage = vs; sysRam = vr
        return glassCard(col, 18)
    }

    private fun ringColumn(ring: RingView, title: String, value: TextView): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(ring, LinearLayout.LayoutParams(dp(54), dp(54)))
        val t = label(title, 11, Theme.muted)
        t.gravity = Gravity.CENTER
        c.addView(t, LinearLayout.LayoutParams(-2, dp(22)))
        value.gravity = Gravity.CENTER
        c.addView(value, LinearLayout.LayoutParams(-2, dp(18)))
        return c
    }

    private fun buildQuoteCard(): View {
        val quotes = resources.getStringArray(R.array.quotes)
        val index = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % maxOf(1, quotes.size)
        val t = label("“" + quotes[index] + "” ✨", 13, Theme.text)
        t.setPadding(dp(18), dp(14), dp(18), dp(14))
        return glassCard(t, 18)
    }

    private fun updateWidgets() {
        val now = Date()
        val time = android.text.format.DateFormat.getTimeFormat(this).format(now)
        clockTime?.text = time
        clockDate?.text = SimpleDateFormat("d MMMM EEEE", Locale.getDefault()).format(now)
        updateWeatherUi()
        refreshWeather(false)
        updateSystemWidget()
    }

    private fun updateWeatherUi() {
        val icon = weatherIcon ?: return
        val w = weather
        if (prefs.weatherCity.isBlank()) {
            icon.text = "☁"
            weatherTemp?.text = "--"
            weatherDesc?.text = getString(R.string.weather_set_city)
        } else if (w == null) {
            icon.text = "☁"
            weatherTemp?.text = "…"
            weatherDesc?.text = prefs.weatherCity
        } else {
            icon.text = WeatherClient.emoji(w.code)
            weatherTemp?.text = w.tempC.toString() + "°"
            weatherDesc?.text = getString(WeatherClient.descriptionRes(w.code)) + "\n" + w.city
        }
    }

    private fun refreshWeather(force: Boolean) {
        val city = prefs.weatherCity
        if (city.isBlank() || weatherFetching || weatherIcon == null) return
        val now = System.currentTimeMillis()
        val cached = weather
        if (!force) {
            if (cached != null && now - cached.fetchedAt < 30 * 60 * 1000L) return
            if (now - lastWeatherAttempt < 5 * 60 * 1000L) return
        }
        lastWeatherAttempt = now
        weatherFetching = true
        val lang = Locale.getDefault().language
        Thread {
            val result = WeatherClient.fetch(city, lang)
            runOnUiThread {
                weatherFetching = false
                if (isDestroyed) return@runOnUiThread
                if (result != null) {
                    weather = result
                    prefs.saveWeather(result)
                }
                updateWeatherUi()
            }
        }.start()
    }

    private fun askWeatherCity() {
        val input = EditText(this)
        input.setSingleLine(true)
        input.setText(prefs.weatherCity)
        input.hint = getString(R.string.weather_city_hint)
        val wrap = LinearLayout(this)
        wrap.setPadding(dp(20), dp(8), dp(20), 0)
        wrap.addView(input, LinearLayout.LayoutParams(-1, -2))
        AlertDialog.Builder(this)
            .setTitle(R.string.weather_city_title)
            .setMessage(R.string.weather_city_msg)
            .setView(wrap)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.weatherCity = input.text.toString().trim()
                prefs.clearWeather()
                weather = null
                lastWeatherAttempt = 0L
                updateWeatherUi()
                refreshWeather(true)
            }
            .show()
    }

    private fun updateSystemWidget() {
        val rb = ringBattery ?: return
        val pct = batteryPercent()
        rb.setProgress(if (pct >= 0) pct / 100f else 0f)
        sysBattery?.text = if (pct >= 0) "$pct%" else "—"
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.blockCountLong * st.blockSizeLong
            val used = total - st.availableBlocksLong * st.blockSizeLong
            ringStorage?.setProgress(if (total > 0) used.toFloat() / total else 0f)
            sysStorage?.text = gb(used, 0)
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            val ramUsed = mi.totalMem - mi.availMem
            ringRam?.setProgress(if (mi.totalMem > 0) ramUsed.toFloat() / mi.totalMem else 0f)
            sysRam?.text = gb(ramUsed, 1)
        } catch (e: Exception) {
            // Leave previous values in place.
        }
    }

    private fun gb(bytes: Long, decimals: Int): String =
        String.format(Locale.getDefault(), if (decimals == 0) "%.0f GB" else "%.1f GB", bytes / 1073741824.0)

    private fun iconDims(): IntArray = when (iconSize) {
        0 -> intArrayOf(70, 92, 34)
        2 -> intArrayOf(96, 120, 52)
        else -> intArrayOf(82, 106, 42)
    } // tile width, tile height, icon (dp)

    private fun renderDesktop() {
        if (!::desktopGrid.isInitialized) return
        desktopGrid.removeAllViews()
        val d = iconDims()
        val areaH = if (desktopArea.height > 0) desktopArea.height else resources.displayMetrics.heightPixels / 2
        desktopGrid.rowCount = maxOf(1, areaH / dp(d[1] + 6))
        val list = shortcuts.mapNotNull { appMap[it] }
        desktopHint.visibility = if (list.isEmpty() && apps.isNotEmpty()) View.VISIBLE else View.GONE
        list.forEach { app ->
            val tile = buildTile(d[2])
            bindTile(tile, app, true)
            val p = GridLayout.LayoutParams()
            p.width = dp(d[0])
            p.height = dp(d[1])
            p.setMargins(dp(3), dp(3), dp(3), dp(3))
            desktopGrid.addView(tile, p)
        }
    }

    private fun buildTile(iconDp: Int = 42): LinearLayout {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setPadding(dp(3), dp(5), dp(3), dp(4))
        tile.isClickable = true
        tile.isFocusable = true
        val icon = ImageView(this)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        tile.addView(icon, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
        val name = label("", 11, Theme.text)
        name.gravity = Gravity.CENTER
        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        name.setShadowLayer(4f, 0f, 1f, 0xAA000000.toInt())
        tile.addView(name, LinearLayout.LayoutParams(-1, dp(34)))
        hoverable(tile)
        return tile
    }

    private fun bindTile(tile: LinearLayout, app: AppEntry, onDesktop: Boolean) {
        (tile.getChildAt(0) as ImageView).setImageBitmap(app.icon)
        (tile.getChildAt(1) as TextView).text = app.label
        tile.setOnClickListener { launchApp(app) }
        tile.setOnLongClickListener { showAppMenu(app, onDesktop); true }
        tile.setOnContextClickListener { showAppMenu(app, onDesktop); true } // mouse right-click
    }

    private fun hoverable(v: View) {
        v.setOnHoverListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_HOVER_ENTER -> view.background = rounded(accent.withAlpha(40), 12)
                MotionEvent.ACTION_HOVER_EXIT -> view.background = null
            }
            false
        }
    }

    // ------------------------------------------------------------------ taskbar + tray

    private fun buildTaskbar() {
        val bar = FrameLayout(this)

        centerScroll = HorizontalScrollView(this)
        centerScroll.isHorizontalScrollBarEnabled = false
        centerScroll.overScrollMode = View.OVER_SCROLL_NEVER
        taskbarApps = LinearLayout(this)
        taskbarApps.orientation = LinearLayout.HORIZONTAL
        taskbarApps.gravity = Gravity.CENTER_VERTICAL
        centerScroll.addView(taskbarApps, FrameLayout.LayoutParams(-2, -1))
        bar.addView(centerScroll, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER))

        trayBox = LinearLayout(this)
        trayBox.orientation = LinearLayout.HORIZONTAL
        trayBox.gravity = Gravity.CENTER_VERTICAL
        trayBox.setPadding(dp(4), 0, dp(12), 0)

        trayChevron = iconView(R.drawable.ic_chevron_up, Theme.muted, 20)
        trayChevron.setOnClickListener { trayExpanded = !trayExpanded; layoutTray() }
        trayBox.addView(trayChevron, llp(dp(28), dp(28)))

        trayWifi = circleIcon(R.drawable.ic_wifi, Theme.panel2, Theme.text, 34, 18)
        trayWifi.setOnClickListener { openInternetPanel() }
        trayBt = circleIcon(R.drawable.ic_bluetooth, Theme.panel2, Theme.text, 34, 18)
        trayBt.setOnClickListener { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
        trayVol = circleIcon(R.drawable.ic_volume, Theme.panel2, Theme.text, 34, 18)
        trayVol.setOnClickListener { openVolumePanel() }
        trayBell = circleIcon(R.drawable.ic_bell, Theme.panel2, Theme.text, 34, 18)
        trayBell.setOnClickListener { onTrayClicked() }
        trayBat = circleIcon(R.drawable.ic_battery, Theme.panel2, Theme.text, 34, 18)
        trayBat.setOnClickListener { openSettings(Intent.ACTION_POWER_USAGE_SUMMARY) }
        trayBox.addView(trayWifi, llp(dp(34), dp(34), dp(6)))
        trayBox.addView(trayBt, llp(dp(34), dp(34), dp(6)))
        trayBox.addView(trayVol, llp(dp(34), dp(34), dp(6)))
        trayBox.addView(trayBell, llp(dp(34), dp(34), dp(6)))
        trayBox.addView(trayBat, llp(dp(34), dp(34), dp(6)))

        trayClock = label("", 13, Theme.text, true)
        trayClock.gravity = Gravity.CENTER
        trayClock.setPadding(dp(10), 0, 0, 0)
        trayClock.setOnClickListener { toggleQuick() }
        trayBox.addView(trayClock, llp(-2, -1))
        bar.addView(trayBox, FrameLayout.LayoutParams(-2, -1, Gravity.END or Gravity.CENTER_VERTICAL))

        taskbarCard = glassCard(bar, 22)
        desktop.addView(taskbarCard, LinearLayout.LayoutParams(-1, dp(taskbarSize)))
    }

    /** Keeps the centred app group clear of the tray on both sides, and shows/hides the optional tray icons. */
    private fun layoutTray() {
        if (!::trayBox.isInitialized) return
        val vis = if (trayExpanded) View.VISIBLE else View.GONE
        trayBt.visibility = vis
        trayVol.visibility = vis
        trayChevron.rotation = if (trayExpanded) 180f else 0f
        trayBox.measure(0, 0)
        val m = trayBox.measuredWidth + dp(8)
        val p = centerScroll.layoutParams as FrameLayout.LayoutParams
        p.leftMargin = m
        p.rightMargin = m
        centerScroll.layoutParams = p
    }

    private fun renderTaskbar() {
        if (!::taskbarApps.isInitialized) return
        taskbarApps.removeAllViews()

        val start = FrameLayout(this)
        start.isClickable = true
        start.isFocusable = true
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_start_logo)
        start.addView(logo, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        start.setOnClickListener { toggleDrawer() }
        start.contentDescription = getString(R.string.apps)
        hoverable(start)
        startCell = start
        taskbarApps.addView(start, LinearLayout.LayoutParams(dp(48), -1))

        taskbarApps.addView(fixedIcon(R.drawable.ic_search) { openDrawer(true) }, LinearLayout.LayoutParams(dp(48), -1))

        val ordered = pinned + recents.filter { it !in pinned }
        ordered.forEach { pkg ->
            val app = appMap[pkg] ?: return@forEach
            val cell = FrameLayout(this)
            cell.isClickable = true
            cell.setOnClickListener { launchApp(app) }
            cell.setOnLongClickListener { showAppMenu(app, false); true }
            cell.setOnContextClickListener { showAppMenu(app, false); true }
            hoverable(cell)
            val icon = ImageView(this)
            icon.setImageBitmap(app.icon)
            icon.setPadding(dp(3), dp(3), dp(3), dp(3))
            icon.contentDescription = app.label
            cell.addView(icon, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
            if (pkg !in pinned) {
                // Small accent bar = recently used (not pinned).
                val dot = View(this)
                dot.background = rounded(accent, 2)
                cell.addView(dot, FrameLayout.LayoutParams(dp(14), dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            }
            taskbarApps.addView(cell, LinearLayout.LayoutParams(dp(48), -1))
        }

        taskbarApps.addView(fixedIcon(R.drawable.ic_apps) { altTab() }, LinearLayout.LayoutParams(dp(48), -1))
    }

    private fun fixedIcon(res: Int, action: () -> Unit): FrameLayout {
        val cell = FrameLayout(this)
        cell.isClickable = true
        cell.isFocusable = true
        cell.addView(iconView(res, Theme.text, 22), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        cell.setOnClickListener { action() }
        hoverable(cell)
        return cell
    }

    private fun networkConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun bluetoothOn(): Boolean {
        return try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    private fun batteryIntent(): Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun batteryPercent(): Int {
        val i = batteryIntent() ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    private fun updateTray() {
        if (!::trayClock.isInitialized) return
        trayClock.text = android.text.format.DateFormat.getTimeFormat(this).format(Date())
        val online = networkConnected()
        setCircle(trayWifi, if (online) accent else Theme.panel2, if (online) Theme.bg else Theme.text)
        val bt = bluetoothOn()
        setCircle(trayBt, if (bt) accent else Theme.panel2, if (bt) Theme.bg else Theme.text)
        val n = DroidBridge.notificationCount
        trayBell.visibility = if (DroidBridge.listenerConnected || n > 0) View.VISIBLE else View.GONE
        setCircle(trayBell, if (n > 0) accent else Theme.panel2, if (n > 0) Theme.bg else Theme.text)
        val pct = batteryPercent()
        setCircle(trayBat, Theme.panel2, if (pct in 0..15) Color.rgb(248, 113, 113) else Theme.text)
        updateWidgets()
    }

    // ------------------------------------------------------------------ glass cards / backdrop

    private fun blurSupported(): Boolean = glassMode && Build.VERSION.SDK_INT >= 31

    private fun panelTint(): Int = when {
        !glassMode -> Theme.panel
        blurSupported() -> Color.argb(150, 25, 36, 57)
        else -> Color.argb(224, 25, 36, 57)
    }

    private fun gradientDrawable(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, Theme.gradient(gradientMode).colors)

    /** Rounded card with an optional blurred-backdrop layer (Android 12+) under a tinted panel. */
    private fun glassCard(content: View, radiusDp: Int): FrameLayout {
        val card = FrameLayout(this)
        val radius = dp(radiusDp).toFloat()
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        card.clipToOutline = true
        if (blurSupported()) {
            val backdrop = GlassBackdrop(this, { root }, { backdropBitmap })
            backdrops.add(backdrop)
            card.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
            card.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> backdrop.invalidate() }
        }
        val stroke = if (glassMode) Color.argb(40, 255, 255, 255) else 0
        content.background = rounded(panelTint(), radiusDp, stroke, if (glassMode) 1 else 0)
        card.addView(content, FrameLayout.LayoutParams(-1, -1))
        return card
    }

    private fun rebuildBackdrop() {
        if (!blurSupported() || !::root.isInitialized || root.width == 0 || root.height == 0) return
        val s = 0.25f
        val bmp = Bitmap.createBitmap(maxOf(1, (root.width * s).toInt()), maxOf(1, (root.height * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        gradientView.draw(c)
        if (wallpaperView.visibility == View.VISIBLE) wallpaperView.draw(c)
        backdropBitmap = bmp
        backdrops.forEach { it.invalidate() }
    }

    // ------------------------------------------------------------------ icon helpers

    private fun iconView(res: Int, color: Int, @Suppress("UNUSED_PARAMETER") sizeDp: Int): ImageView {
        val v = ImageView(this)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setImageResource(res)
        v.setColorFilter(color)
        return v
    }

    private fun circleIcon(res: Int, bg: Int, tint: Int, @Suppress("UNUSED_PARAMETER") outerDp: Int, innerDp: Int): FrameLayout {
        val f = FrameLayout(this)
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(bg)
        f.background = d
        val i = iconView(res, tint, innerDp)
        f.addView(i, FrameLayout.LayoutParams(dp(innerDp), dp(innerDp), Gravity.CENTER))
        f.tag = i
        f.isClickable = true
        f.isFocusable = true
        return f
    }

    private fun setCircle(f: FrameLayout, bg: Int, tint: Int) {
        (f.background as? GradientDrawable)?.setColor(bg)
        (f.tag as? ImageView)?.setColorFilter(tint)
    }

    private fun llp(w: Int, h: Int, left: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.leftMargin = left
        return p
    }

    // ------------------------------------------------------------------ quick settings panel

    private fun buildQuickPanel() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(14), dp(8), dp(14), dp(16))

        val grabber = View(this)
        grabber.background = rounded(Theme.muted.withAlpha(140), 3)
        val gp = LinearLayout.LayoutParams(dp(40), dp(5))
        gp.gravity = Gravity.CENTER_HORIZONTAL
        gp.bottomMargin = dp(12)
        content.addView(grabber, gp)

        val tools = listOf(
            Tool(R.drawable.ic_wifi, R.string.tile_internet, Color.rgb(45, 140, 255)) { openInternetPanel() },
            Tool(R.drawable.ic_bluetooth, R.string.tile_bluetooth, Color.rgb(124, 92, 255)) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Tool(R.drawable.ic_airplane, R.string.tile_airplane, Color.rgb(100, 116, 139)) { openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
            Tool(R.drawable.ic_display, R.string.tile_display, Color.rgb(139, 92, 246)) { openSettings(Settings.ACTION_DISPLAY_SETTINGS) },
            Tool(R.drawable.ic_volume, R.string.tile_sound, Color.rgb(249, 115, 22)) { openVolumePanel() },
            Tool(R.drawable.ic_battery, R.string.tile_battery, Color.rgb(16, 185, 129)) { openSettings(Intent.ACTION_POWER_USAGE_SUMMARY) },
            Tool(R.drawable.ic_power, R.string.tile_power, Color.rgb(239, 68, 68)) {
                withAccessibility(getString(R.string.tile_power)) {
                    it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                }
            },
            Tool(R.drawable.ic_settings, R.string.tile_settings, Color.rgb(100, 116, 139)) { showSettings() }
        )
        val grid = GridLayout(this)
        grid.columnCount = 4
        tools.forEach { tool ->
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(0, dp(6), 0, dp(6))
            tile.isClickable = true
            tile.isFocusable = true
            tile.setOnClickListener { tool.action() }
            hoverable(tile)
            val circle = circleIcon(tool.iconRes, tool.color, Color.WHITE, 52, 26)
            circle.isClickable = false
            circle.isFocusable = false
            tile.addView(circle, LinearLayout.LayoutParams(dp(52), dp(52)))
            val name = label(getString(tool.labelRes), 11, Theme.text)
            name.gravity = Gravity.CENTER
            tile.addView(name, LinearLayout.LayoutParams(-2, dp(26)))
            val p = GridLayout.LayoutParams()
            p.width = 0
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            grid.addView(tile, p)
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeSeek = styledSeek(maxOf(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))
        volumeSeek.setOnSeekBarChangeListener(seekListener { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) })
        val volRow = sliderRow(R.drawable.ic_volume, volumeSeek) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            syncSeekBars()
        }
        content.addView(volRow, llpTop(dp(10)))

        brightnessSeek = styledSeek(100)
        brightnessSeek.setOnSeekBarChangeListener(seekListener { v ->
            val wp = window.attributes
            wp.screenBrightness = maxOf(.05f, v / 100f)
            window.attributes = wp
        })
        content.addView(sliderRow(R.drawable.ic_brightness, brightnessSeek) { }, llpTop(dp(8)))

        quickCard = glassCard(content, 24)
        quickCard.elevation = dp(24).toFloat()
        quickCard.visibility = View.GONE
        root.addView(quickCard, FrameLayout.LayoutParams(-2, -2))
    }

    private fun llpTop(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = top
        return p
    }

    private fun styledSeek(maxValue: Int): SeekBar {
        val s = SeekBar(this)
        s.max = maxValue
        s.progressTintList = ColorStateList.valueOf(accent)
        s.thumbTintList = ColorStateList.valueOf(accent)
        s.progressBackgroundTintList = ColorStateList.valueOf(Theme.panel2)
        return s
    }

    private fun sliderRow(res: Int, seek: SeekBar, onIcon: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val circle = circleIcon(res, Theme.panel2, Theme.text, 44, 22)
        circle.setOnClickListener { onIcon() }
        row.addView(circle, LinearLayout.LayoutParams(dp(44), dp(44)))
        val sp = LinearLayout.LayoutParams(0, -2, 1f)
        sp.leftMargin = dp(10)
        row.addView(seek, sp)
        return row
    }

    private fun syncSeekBars() {
        if (!::volumeSeek.isInitialized) return
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeSeek.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val w = window.attributes.screenBrightness
        brightnessSeek.progress = if (w >= 0f) {
            (w * 100).toInt()
        } else {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 178) * 100 / 255
        }
    }

    private fun openInternetPanel() {
        if (Build.VERSION.SDK_INT >= 29) openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else openSettings(Settings.ACTION_WIFI_SETTINGS)
    }

    private fun openVolumePanel() {
        if (Build.VERSION.SDK_INT >= 29) openSettings(Settings.Panel.ACTION_VOLUME)
        else openSettings(Settings.ACTION_SOUND_SETTINGS)
    }

    // ------------------------------------------------------------------ Start menu / app drawer

    private inner class PageVH(val grid: GridLayout) : RecyclerView.ViewHolder(grid)

    private inner class PageAdapter : RecyclerView.Adapter<PageVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val g = GridLayout(this@MainActivity)
            g.layoutParams = RecyclerView.LayoutParams(-1, -1)
            return PageVH(g)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val g = holder.grid
            g.removeAllViews()
            g.columnCount = pageCols
            filtered.drop(position * perPage).take(perPage).forEach { app ->
                val tile = buildTile(42)
                bindTile(tile, app, false)
                val p = GridLayout.LayoutParams()
                p.width = 0
                p.height = dp(96)
                p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                g.addView(tile, p)
            }
        }

        override fun getItemCount(): Int = maxOf(1, (filtered.size + perPage - 1) / perPage)
    }

    private fun buildDrawer() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(4), dp(16), dp(8))

        // Grabber: drag up/down to resize the Start menu.
        val grab = FrameLayout(this)
        val bar = View(this)
        bar.background = rounded(Theme.muted.withAlpha(140), 3)
        grab.addView(bar, FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER))
        grab.setOnTouchListener { _, e -> handleDrawerResize(e); true }
        content.addView(grab, LinearLayout.LayoutParams(-1, dp(24)))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(14), 0, dp(12), 0)
        box.background = rounded(Theme.panel2.withAlpha(200), 16)
        box.addView(iconView(R.drawable.ic_search, Theme.muted, 20), llp(dp(20), dp(20)))
        drawerSearch = EditText(this)
        drawerSearch.hint = getString(R.string.search_apps)
        drawerSearch.textSize = 15f
        drawerSearch.setSingleLine(true)
        drawerSearch.imeOptions = EditorInfo.IME_ACTION_GO
        drawerSearch.setTextColor(Theme.text)
        drawerSearch.setHintTextColor(Theme.muted)
        drawerSearch.background = null
        drawerSearch.setPadding(dp(10), 0, 0, 0)
        drawerSearch.addTextChangedListener(SimpleTextWatcher { applyFilter() })
        // Enter launches the first match, like the Windows Start menu.
        drawerSearch.setOnEditorActionListener { _, _, _ ->
            filtered.firstOrNull()?.let { launchApp(it) }
            true
        }
        box.addView(drawerSearch, LinearLayout.LayoutParams(0, -1, 1f))
        val bp = LinearLayout.LayoutParams(-1, dp(46))
        bp.bottomMargin = dp(8)
        content.addView(box, bp)

        emptyLabel = label(getString(R.string.no_apps), 14, Theme.muted)
        emptyLabel.visibility = View.GONE
        emptyLabel.setPadding(dp(4), dp(8), 0, 0)
        content.addView(emptyLabel)

        drawerPager = ViewPager2(this)
        drawerPager.adapter = pageAdapter
        (drawerPager.getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
        drawerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateDots()
            }
        })
        drawerPager.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) recomputePages()
        }
        content.addView(drawerPager, LinearLayout.LayoutParams(-1, 0, 1f))

        dotsRow = LinearLayout(this)
        dotsRow.orientation = LinearLayout.HORIZONTAL
        dotsRow.gravity = Gravity.CENTER
        content.addView(dotsRow, LinearLayout.LayoutParams(-1, dp(18)))

        drawerCard = glassCard(content, 24)
        drawerCard.elevation = dp(22).toFloat()
        drawerCard.visibility = View.GONE
        root.addView(drawerCard, FrameLayout.LayoutParams(-2, -2))
    }

    private fun recomputePages() {
        if (!::drawerPager.isInitialized || !::drawerCard.isInitialized) return
        val w = drawerCard.layoutParams?.width ?: return
        if (w <= 0) return
        val cols = maxOf(4, (w - dp(32)) / dp(92))
        val rows = maxOf(2, drawerPager.height / dp(96))
        val per = cols * rows
        if (cols != pageCols || per != perPage) {
            pageCols = cols
            perPage = per
            pageAdapter.notifyDataSetChanged()
            rebuildDots()
        }
    }

    private fun rebuildDots() {
        if (!::dotsRow.isInitialized) return
        dotsRow.removeAllViews()
        val n = pageAdapter.itemCount
        if (n <= 1) {
            dotsRow.visibility = View.INVISIBLE
            return
        }
        dotsRow.visibility = View.VISIBLE
        for (i in 0 until n) dotsRow.addView(View(this), llp(dp(7), dp(7), if (i == 0) 0 else dp(7)))
        updateDots()
    }

    private fun updateDots() {
        for (i in 0 until dotsRow.childCount) {
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(if (i == currentPage) accent else Theme.muted.withAlpha(110))
            dotsRow.getChildAt(i).background = d
        }
    }

    private fun applyFilter() {
        if (!::drawerSearch.isInitialized) return
        val q = drawerSearch.text.toString().trim()
        filtered = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
        pageAdapter.notifyDataSetChanged()
        currentPage = 0
        if (drawerPager.currentItem != 0) drawerPager.setCurrentItem(0, false)
        emptyLabel.visibility = if (filtered.isEmpty() && apps.isNotEmpty()) View.VISIBLE else View.GONE
        rebuildDots()
    }

    private fun bottomOffset(): Int = insets[3] + dp(taskbarSize) + dp(20)

    private fun updateOverlayLayout() {
        if (!::root.isInitialized || !::drawerCard.isInitialized || !::quickCard.isInitialized || root.width == 0) return
        val availH = root.height - bottomOffset() - insets[1] - dp(8)
        val dw = minOf((root.width * drawerWidthFrac).toInt(), root.width - insets[0] - insets[2], dp(720))
        val dh = minOf((root.height * drawerHeightFrac).toInt(), availH)
        val dlp = FrameLayout.LayoutParams(dw, maxOf(dh, dp(260)), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        dlp.bottomMargin = bottomOffset()
        drawerCard.layoutParams = dlp

        val qw = minOf((root.width * .92f).toInt(), dp(400))
        val qlp = FrameLayout.LayoutParams(qw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        qlp.bottomMargin = bottomOffset()
        qlp.rightMargin = insets[2] + dp(12)
        quickCard.layoutParams = qlp
    }

    private fun handleDrawerResize(e: MotionEvent) {
        val a = e.actionMasked
        if (a != MotionEvent.ACTION_DOWN && a != MotionEvent.ACTION_MOVE && a != MotionEvent.ACTION_UP) return
        if (root.height == 0) return
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val topY = e.rawY - loc[1]
        val bottomEdge = (root.height - bottomOffset()).toFloat()
        drawerHeightFrac = ((bottomEdge - topY) / root.height).coerceIn(.30f, .82f)
        updateOverlayLayout()
        if (a == MotionEvent.ACTION_UP) prefs.drawerHeight = drawerHeightFrac
    }

    private fun openDrawer(focusSearch: Boolean) {
        if (!::drawerCard.isInitialized) return
        if (drawerOpen) {
            if (focusSearch) focusSearchField(true)
            return
        }
        dismissMenu(false)
        closeQuick()
        drawerOpen = true
        drawerSearch.setText("")
        applyFilter()
        drawerCard.visibility = View.VISIBLE
        drawerCard.bringToFront()
        // With a hardware keyboard attached, start typing immediately (without popping the soft keyboard).
        if (focusSearch) {
            focusSearchField(true)
        } else if (resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS) {
            focusSearchField(false)
        }
        updateScrim()
    }

    private fun focusSearchField(showKeyboard: Boolean) {
        drawerSearch.requestFocus()
        if (showKeyboard) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            drawerSearch.post { imm.showSoftInput(drawerSearch, InputMethodManager.SHOW_IMPLICIT) }
        }
    }

    private fun closeDrawer() {
        if (!::drawerCard.isInitialized) return
        val wasOpen = drawerOpen
        drawerOpen = false
        drawerCard.visibility = View.GONE
        if (wasOpen) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(drawerSearch.windowToken, 0)
            drawerSearch.clearFocus()
        }
        updateScrim()
    }

    private fun toggleDrawer() { if (drawerOpen) closeDrawer() else openDrawer(false) }

    private fun openQuick() {
        if (quickOpen || !::quickCard.isInitialized) return
        dismissMenu(false)
        closeDrawer()
        quickOpen = true
        syncSeekBars()
        updateTray()
        quickCard.visibility = View.VISIBLE
        quickCard.bringToFront()
        updateScrim()
    }

    private fun closeQuick() {
        if (!::quickCard.isInitialized) return
        quickOpen = false
        quickCard.visibility = View.GONE
        updateScrim()
    }

    private fun toggleQuick() { if (quickOpen) closeQuick() else openQuick() }

    private fun updateScrim() {
        if (!::scrim.isInitialized) return
        scrim.visibility = if (drawerOpen || quickOpen || menuOpen) View.VISIBLE else View.GONE
        startCell?.background = if (drawerOpen) rounded(accent.withAlpha(70), 12) else null
    }

    // ------------------------------------------------------------------ context menus

    private fun showMenu(x: Float, y: Float, entries: List<MenuEntry>) {
        dismissMenu(false)
        menuX = x
        menuY = y
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(6), dp(6), dp(6), dp(6))
        entries.forEach { e ->
            if (e.label.isEmpty()) {
                val line = View(this)
                line.setBackgroundColor(Color.argb(40, 255, 255, 255))
                val lp = LinearLayout.LayoutParams(-1, dp(1))
                lp.setMargins(dp(8), dp(4), dp(8), dp(4))
                col.addView(line, lp)
                return@forEach
            }
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), 0, dp(10), 0)
            row.isClickable = true
            row.isFocusable = true
            if (e.iconRes != 0) row.addView(iconView(e.iconRes, Theme.text, 18), llp(dp(18), dp(18)))
            val t = label(e.label, 13, Theme.text)
            t.setPadding(if (e.iconRes != 0) dp(12) else dp(2), 0, 0, 0)
            row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            if (e.sub) row.addView(iconView(R.drawable.ic_chevron_right, Theme.muted, 16), llp(dp(16), dp(16)))
            hoverable(row)
            row.setOnClickListener {
                dismissMenu()
                e.action()
            }
            col.addView(row, LinearLayout.LayoutParams(-1, dp(40)))
        }
        val card = FrameLayout(this)
        card.background = rounded(Color.argb(240, 25, 36, 57), 14, Color.argb(50, 255, 255, 255), 1)
        card.elevation = dp(18).toFloat()
        card.addView(col, FrameLayout.LayoutParams(-1, -2))
        val w = dp(250)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = card.measuredHeight
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        var left = (x - loc[0]).toInt()
        var top = (y - loc[1]).toInt()
        left = maxOf(minOf(left, root.width - w - insets[2] - dp(8)), insets[0] + dp(8))
        top = maxOf(minOf(top, root.height - h - insets[3] - dp(8)), insets[1] + dp(8))
        val p = FrameLayout.LayoutParams(w, -2, Gravity.TOP or Gravity.START)
        p.leftMargin = left
        p.topMargin = top
        root.addView(card, p)
        menuCard = card
        menuOpen = true
        updateScrim()
    }

    private fun dismissMenu(updateUi: Boolean = true) {
        val c = menuCard
        if (c != null) root.removeView(c)
        menuCard = null
        menuOpen = false
        if (updateUi) updateScrim()
    }

    private fun checked(on: Boolean, text: String): String = if (on) "✓  $text" else "      $text"

    private fun showDesktopMenu(x: Float, y: Float) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(R.drawable.ic_image, getString(R.string.menu_wallpaper), false) { wallpaperPicker.launch(arrayOf("image/*")) })
        e.add(MenuEntry(R.drawable.ic_view, getString(R.string.menu_view), true) { showViewMenu() })
        e.add(MenuEntry(R.drawable.ic_sort, getString(R.string.menu_sort), true) { showSortMenu() })
        e.add(MenuEntry(R.drawable.ic_refresh, getString(R.string.menu_refresh), false) {
            reloadApps()
            refreshWeather(true)
        })
        e.add(MenuEntry(0, "", false) { })
        e.add(MenuEntry(R.drawable.ic_widgets, getString(R.string.menu_add_widget), true) { showWidgetMenu() })
        e.add(MenuEntry(R.drawable.ic_apps, getString(R.string.menu_open_drawer), false) { openDrawer(false) })
        e.add(MenuEntry(R.drawable.ic_folder, getString(R.string.menu_files_window), false) { showFilesWindow() })
        e.add(MenuEntry(R.drawable.ic_settings, getString(R.string.menu_settings), false) { showSettings() })
        showMenu(x, y, e)
    }

    private fun showViewMenu() {
        val names = listOf(R.string.view_small, R.string.view_medium, R.string.view_large)
        val e = mutableListOf<MenuEntry>()
        names.forEachIndexed { i, res ->
            e.add(MenuEntry(0, checked(iconSize == i, getString(res)), false) {
                iconSize = i
                prefs.iconSize = i
                renderDesktop()
            })
        }
        showMenu(menuX, menuY, e)
    }

    private fun showSortMenu() {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(0, getString(R.string.sort_name), false) { sortShortcuts(false) })
        e.add(MenuEntry(0, getString(R.string.sort_recent), false) { sortShortcuts(true) })
        showMenu(menuX, menuY, e)
    }

    private fun sortShortcuts(byRecent: Boolean) {
        if (appMap.isEmpty()) return
        val valid = shortcuts.filter { appMap[it] != null }
        val sorted = if (byRecent) {
            valid.sortedBy { val i = recents.indexOf(it); if (i < 0) Int.MAX_VALUE else i }
        } else {
            valid.sortedBy { appMap[it]?.label?.lowercase(Locale.getDefault()) ?: "" }
        }
        shortcuts = sorted.toMutableList()
        prefs.shortcuts = shortcuts
        renderDesktop()
    }

    private fun showWidgetMenu() {
        val items = listOf(
            1 to R.string.widget_clock, 2 to R.string.widget_calendar,
            4 to R.string.widget_system, 8 to R.string.widget_quote
        )
        val e = mutableListOf<MenuEntry>()
        items.forEach { (bit, res) ->
            e.add(MenuEntry(0, checked(hasWidget(bit), getString(res)), false) {
                widgetMask = widgetMask xor bit
                prefs.widgetMask = widgetMask
                buildWidgets()
                onRootResized()
                showWidgetMenu()
            })
        }
        showMenu(menuX, menuY, e)
    }

    private fun showAppMenu(app: AppEntry, onDesktop: Boolean) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(0, getString(R.string.menu_open), false) { launchApp(app) })
        externalDisplayId()?.let { id ->
            e.add(MenuEntry(0, getString(R.string.menu_open_external), false) { launchApp(app, id) })
        }
        val onDesk = shortcuts.contains(app.pkg)
        e.add(MenuEntry(0, getString(if (onDesk) R.string.menu_remove_shortcut else R.string.menu_add_shortcut), false) { toggleShortcut(app.pkg) })
        e.add(MenuEntry(0, getString(if (pinned.contains(app.pkg)) R.string.menu_unpin else R.string.menu_pin), false) { togglePin(app.pkg) })
        if (onDesktop && onDesk) {
            e.add(MenuEntry(0, getString(R.string.menu_move_left), false) { moveShortcut(app.pkg, -1) })
            e.add(MenuEntry(0, getString(R.string.menu_move_right), false) { moveShortcut(app.pkg, 1) })
        }
        e.add(MenuEntry(0, "", false) { })
        e.add(MenuEntry(0, getString(R.string.menu_app_info), false) {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
        })
        e.add(MenuEntry(0, getString(R.string.menu_uninstall), false) {
            safeStart(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null)))
        })
        showMenu(lastTouchX, lastTouchY, e)
    }

    // ------------------------------------------------------------------ floating "Files" window

    private class Folder(val labelRes: Int, val color: Int, val action: () -> Unit)

    private fun showFilesWindow() {
        val existing = filesWindow
        if (existing != null) {
            existing.visibility = View.VISIBLE
            existing.bringToFront()
            return
        }
        closeDrawer()
        closeQuick()

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL

        val titleBar = LinearLayout(this)
        titleBar.orientation = LinearLayout.HORIZONTAL
        titleBar.gravity = Gravity.CENTER_VERTICAL
        titleBar.setPadding(dp(14), 0, dp(4), 0)
        titleBar.addView(iconView(R.drawable.ic_folder, accent, 18), llp(dp(18), dp(18)))
        val tt = label(getString(R.string.files_window_title), 14, Theme.text, true)
        tt.setPadding(dp(10), 0, 0, 0)
        titleBar.addView(tt, LinearLayout.LayoutParams(0, -2, 1f))
        titleBar.addView(winButton(R.drawable.ic_minimize) { filesWindow?.visibility = View.GONE }, llp(dp(40), dp(40)))
        titleBar.addView(winButton(R.drawable.ic_maximize) { fwMax = !fwMax; applyFilesWindow() }, llp(dp(40), dp(40)))
        titleBar.addView(winButton(R.drawable.ic_close) { closeFilesWindow() }, llp(dp(40), dp(40)))
        body.addView(titleBar, LinearLayout.LayoutParams(-1, dp(46)))

        val folders = listOf(
            Folder(R.string.folder_documents, Color.rgb(59, 130, 246)) {
                safeStart(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
            },
            Folder(R.string.folder_downloads, Color.rgb(16, 185, 129)) {
                safeStart(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            },
            Folder(R.string.folder_pictures, Color.rgb(139, 92, 246)) {
                safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
            },
            Folder(R.string.folder_music, Color.rgb(249, 115, 22)) {
                safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC))
            }
        )
        val grid = GridLayout(this)
        grid.columnCount = 4
        grid.setPadding(dp(10), dp(8), dp(10), dp(4))
        folders.forEach { f ->
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(0, dp(8), 0, dp(8))
            tile.isClickable = true
            tile.isFocusable = true
            tile.setOnClickListener { f.action() }
            hoverable(tile)
            val circle = circleIcon(R.drawable.ic_folder, f.color, Color.WHITE, 46, 24)
            circle.isClickable = false
            circle.isFocusable = false
            tile.addView(circle, LinearLayout.LayoutParams(dp(46), dp(46)))
            val name = label(getString(f.labelRes), 11, Theme.text)
            name.gravity = Gravity.CENTER
            tile.addView(name, LinearLayout.LayoutParams(-2, dp(24)))
            val p = GridLayout.LayoutParams()
            p.width = 0
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            grid.addView(tile, p)
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
        body.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        val gripRow = LinearLayout(this)
        gripRow.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        val grip = label("◢", 16, Theme.muted)
        grip.setPadding(dp(14), 0, dp(10), dp(2))
        gripRow.addView(grip, LinearLayout.LayoutParams(-2, -1))
        body.addView(gripRow, LinearLayout.LayoutParams(-1, dp(30)))

        // Drag by the title bar.
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        titleBar.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = ev.rawX
                    sy = ev.rawY
                    ox = fwX
                    oy = fwY
                }
                MotionEvent.ACTION_MOVE -> if (!fwMax) {
                    fwX = ox + (ev.rawX - sx).toInt()
                    fwY = oy + (ev.rawY - sy).toInt()
                    applyFilesWindow()
                }
            }
            true
        }
        // Resize by the bottom-right grip.
        var rx = 0f
        var ry = 0f
        var rw = 0
        var rh = 0
        grip.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rx = ev.rawX
                    ry = ev.rawY
                    rw = fwW
                    rh = fwH
                }
                MotionEvent.ACTION_MOVE -> if (!fwMax) {
                    fwW = maxOf(dp(280), rw + (ev.rawX - rx).toInt())
                    fwH = maxOf(dp(200), rh + (ev.rawY - ry).toInt())
                    applyFilesWindow()
                }
            }
            true
        }

        val card = glassCard(body, 18)
        card.elevation = dp(20).toFloat()
        fwMax = false
        fwW = minOf(dp(440), root.width - dp(24))
        fwH = dp(300)
        fwX = maxOf(0, (root.width - fwW) / 2)
        fwY = insets[1] + dp(40)
        root.addView(card, FrameLayout.LayoutParams(fwW, fwH))
        filesWindow = card
        applyFilesWindow()
    }

    private fun winButton(res: Int, action: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.isClickable = true
        f.isFocusable = true
        f.addView(iconView(res, Theme.text, 16), FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        f.setOnClickListener { action() }
        hoverable(f)
        return f
    }

    private fun closeFilesWindow() {
        val c = filesWindow ?: return
        root.removeView(c)
        backdrops.removeAll { it.parent === c }
        filesWindow = null
    }

    private fun applyFilesWindow() {
        val w = filesWindow ?: return
        if (root.width == 0) return
        val p = FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START)
        if (fwMax) {
            p.width = root.width - insets[0] - insets[2] - dp(16)
            p.height = maxOf(dp(200), root.height - insets[1] - bottomOffset() - dp(8))
            p.leftMargin = insets[0] + dp(8)
            p.topMargin = insets[1] + dp(8)
        } else {
            fwW = fwW.coerceIn(dp(280), maxOf(dp(280), root.width - dp(16)))
            fwH = fwH.coerceIn(dp(200), maxOf(dp(200), root.height - insets[1] - dp(16)))
            fwX = fwX.coerceIn(0, maxOf(0, root.width - fwW))
            fwY = fwY.coerceIn(insets[1], maxOf(insets[1], root.height - fwH - dp(8)))
            p.width = fwW
            p.height = fwH
            p.leftMargin = fwX
            p.topMargin = fwY
        }
        w.layoutParams = p
    }

    // ------------------------------------------------------------------ launching

    private fun launchApp(app: AppEntry, displayId: Int? = null) {
        val intent = packageManager.getLaunchIntentForPackage(app.pkg)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        if (displayId != null) {
            options.setLaunchDisplayId(displayId)
        } else {
            // Only honoured when the device supports freeform windows; otherwise ignored.
            options.setLaunchBounds(nextBounds())
        }
        val started = try {
            startActivity(intent, options.toBundle())
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            try { startActivity(intent); true } catch (e2: Exception) { false }
        }
        if (!started) {
            toast(R.string.toast_cannot_open)
            return
        }
        recents.remove(app.pkg)
        recents.add(0, app.pkg)
        while (recents.size > 6) recents.removeAt(recents.size - 1)
        prefs.recents = recents
        renderTaskbar()
        dismissMenu(false)
        closeDrawer()
        closeQuick()
    }

    private fun nextBounds(): Rect {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val step = (launchCount++ % 4) * dp(24)
        val bw = (w * .78f).toInt()
        val bh = (h * .66f).toInt()
        val left = minOf((w * .05f).toInt() + step, w - bw)
        val top = minOf((h * .08f).toInt() + step, h - bh)
        return Rect(left, top, left + bw, top + bh)
    }

    private fun externalDisplayId(): Int? {
        return try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.displayId
        } catch (e: Exception) {
            null
        }
    }

    private fun toggleShortcut(pkg: String) {
        if (!shortcuts.remove(pkg)) shortcuts.add(pkg)
        prefs.shortcuts = shortcuts
        renderDesktop()
    }

    private fun togglePin(pkg: String) {
        if (!pinned.remove(pkg)) pinned.add(pkg)
        prefs.pinned = pinned
        renderTaskbar()
    }

    private fun moveShortcut(pkg: String, dir: Int) {
        val visible = shortcuts.filter { appMap[it] != null }
        val other = visible.getOrNull(visible.indexOf(pkg) + dir) ?: return
        val a = shortcuts.indexOf(pkg)
        val b = shortcuts.indexOf(other)
        if (a < 0 || b < 0) return
        shortcuts[a] = other
        shortcuts[b] = pkg
        prefs.shortcuts = shortcuts
        renderDesktop()
    }

    private fun altTab() {
        val s = DroidBridge.accessibility
        if (s != null) {
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            return
        }
        // Fallback without the accessibility service: jump to the previously used app.
        val next = recents.getOrNull(1) ?: recents.getOrNull(0) ?: return
        appMap[next]?.let { launchApp(it) }
    }

    // ------------------------------------------------------------------ accessibility / notifications

    private fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun onTrayClicked() {
        if (!listenerEnabled()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.notif_access_title)
                .setMessage(R.string.notif_access_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.enable) { _, _ -> safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .show()
        } else {
            withAccessibility(getString(R.string.tray_notifications)) {
                it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
        }
    }

    private fun withAccessibility(feature: String, block: (DroidAccessibilityService) -> Unit) {
        val service = DroidBridge.accessibility
        if (service != null) {
            block(service)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.a11y_needed_title)
            .setMessage(getString(R.string.a11y_needed_msg, feature))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.enable) { _, _ -> safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .show()
    }

    // ------------------------------------------------------------------ settings

    private fun showSettings() {
        val titles = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        fun add(res: Int, action: () -> Unit) {
            titles.add(getString(res))
            actions.add(action)
        }
        add(R.string.set_accent) { chooseAccent() }
        add(R.string.set_gradient) { chooseGradient() }
        add(R.string.set_wallpaper) { wallpaperPicker.launch(arrayOf("image/*")) }
        add(R.string.set_glass) {
            glassMode = !glassMode
            prefs.glass = glassMode
            buildUi()
        }
        add(R.string.set_taskbar) {
            sizeDialog(R.string.set_taskbar, taskbarSize.toFloat(), 54f, 82f) { v ->
                taskbarSize = v.toInt()
                prefs.taskbarSize = taskbarSize
                buildUi()
            }
        }
        add(R.string.set_drawer_w) {
            sizeDialog(R.string.set_drawer_w, drawerWidthFrac, .65f, .98f) { v ->
                drawerWidthFrac = v
                prefs.drawerWidth = v
                updateOverlayLayout()
            }
        }
        add(R.string.set_drawer_h) {
            sizeDialog(R.string.set_drawer_h, drawerHeightFrac, .30f, .82f) { v ->
                drawerHeightFrac = v
                prefs.drawerHeight = v
                updateOverlayLayout()
            }
        }
        add(R.string.set_weather) { askWeatherCity() }
        add(R.string.set_clear) {
            AlertDialog.Builder(this)
                .setMessage(R.string.confirm_clear)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear) { _, _ ->
                    shortcuts.clear()
                    prefs.shortcuts = shortcuts
                    renderDesktop()
                }
                .show()
        }
        add(R.string.set_keyboard) { safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        add(R.string.set_notifications) { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        add(R.string.set_default_launcher) { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) }
        add(R.string.set_android) { openSettings(Settings.ACTION_SETTINGS) }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setItems(titles.toTypedArray()) { _, i -> actions[i]() }
            .show()
    }

    private fun sizeDialog(titleRes: Int, current: Float, minV: Float, maxV: Float, onSave: (Float) -> Unit) {
        val seek = SeekBar(this)
        seek.max = 100
        seek.progress = (((current - minV) / (maxV - minV)) * 100).toInt().coerceIn(0, 100)
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(20), dp(12), dp(20), dp(4))
        wrap.addView(seek)
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(wrap)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ -> onSave(minV + (maxV - minV) * seek.progress / 100f) }
            .show()
    }

    private fun chooseAccent() {
        val labels = Theme.accents.map { getString(it.labelRes) }.toTypedArray()
        val current = Theme.accents.indexOfFirst { it.color == accent }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.set_accent)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val preset = Theme.accents[which]
                accent = preset.color
                prefs.accent = preset.key
                dialog.dismiss()
                buildUi()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseGradient() {
        val labels = Theme.gradients.map { getString(it.labelRes) }.toTypedArray()
        val current = Theme.gradients.indexOfFirst { it.key == gradientMode }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.set_gradient)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                gradientMode = Theme.gradients[which].key
                prefs.gradient = gradientMode
                prefs.wallpaper = null
                dialog.dismiss()
                buildUi()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ wallpaper

    private fun loadWallpaper(uriText: String?, announce: Boolean) {
        if (uriText == null) {
            wallpaperView.setImageDrawable(null)
            wallpaperView.visibility = View.GONE
            rebuildBackdrop()
            return
        }
        val dm = resources.displayMetrics
        val w = maxOf(dm.widthPixels, 1)
        val h = maxOf(dm.heightPixels, 1)
        Thread {
            val bmp = decodeSampled(Uri.parse(uriText), w, h)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (bmp != null) {
                    wallpaperView.setImageBitmap(bmp)
                    wallpaperView.visibility = View.VISIBLE
                    rebuildBackdrop()
                    if (announce) toast(R.string.toast_wallpaper_ok)
                } else if (announce) {
                    toast(R.string.toast_wallpaper_fail)
                }
            }
        }.start()
    }

    /** Decodes a down-sampled bitmap so large photos cannot exhaust memory. */
    private fun decodeSampled(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------------ input: keys, long-press / right-click

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> if (event.action == KeyEvent.ACTION_DOWN && (drawerOpen || quickOpen || menuOpen)) {
                dismissMenu(false)
                closeDrawer()
                closeQuick()
                updateScrim()
                return true
            }
            // When the accessibility service is on it handles Win / Alt+Tab globally; these are the fallbacks.
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT ->
                if (event.action == KeyEvent.ACTION_UP && DroidBridge.accessibility == null) {
                    toggleDrawer()
                    return true
                }
            KeyEvent.KEYCODE_TAB -> if (event.isAltPressed && DroidBridge.accessibility == null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) altTab()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.rawX
                lastTouchY = ev.rawY
                cancelLongPress()
                if ((ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                    // Mouse right-click on empty desktop.
                    if (canShowDesktopMenu(ev.rawX, ev.rawY)) {
                        showDesktopMenu(ev.rawX, ev.rawY)
                        return true
                    }
                } else {
                    val r = Runnable {
                        if (canShowDesktopMenu(lastTouchX, lastTouchY)) showDesktopMenu(lastTouchX, lastTouchY)
                    }
                    longPressRunnable = r
                    handler.postDelayed(r, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (abs(ev.rawX - lastTouchX) > slop || abs(ev.rawY - lastTouchY) > slop) cancelLongPress()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPress()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun cancelLongPress() {
        val r = longPressRunnable
        if (r != null) handler.removeCallbacks(r)
        longPressRunnable = null
    }

    private fun isOver(v: View?, x: Float, y: Float): Boolean {
        if (v == null || v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
    }

    /** The desktop menu opens only on empty desktop: not over icons, widgets, the taskbar or a window. */
    private fun canShowDesktopMenu(x: Float, y: Float): Boolean {
        if (!::taskbarCard.isInitialized || drawerOpen || quickOpen || menuOpen) return false
        if (isOver(taskbarCard, x, y) || isOver(filesWindow, x, y)) return false
        if (widgetScroll.visibility == View.VISIBLE) {
            for (i in 0 until widgetColumn.childCount) {
                if (isOver(widgetColumn.getChildAt(i), x, y)) return false
            }
        }
        for (i in 0 until desktopGrid.childCount) {
            if (isOver(desktopGrid.getChildAt(i), x, y)) return false
        }
        return true
    }

    // ------------------------------------------------------------------ small helpers

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    private fun safeStart(intent: Intent) {
        try { startActivity(intent) } catch (e: Exception) { toast(R.string.toast_cannot_open) }
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { safeStart(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun Int.withAlpha(alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = value
        t.textSize = size.toFloat()
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        t.gravity = Gravity.CENTER_VERTICAL
        return t
    }
}
