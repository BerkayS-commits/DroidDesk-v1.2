package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class Tool(val glyph: String, val labelRes: Int, val action: () -> Unit)

/* DroidDesk 1.2 - Windows-inspired Android home launcher. */
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

    private var apps: List<AppEntry> = emptyList()
    private var appMap: Map<String, AppEntry> = emptyMap()
    private var filtered: List<AppEntry> = emptyList()
    private var shortcuts = mutableListOf<String>()
    private var pinned = mutableListOf<String>()
    private var recents = mutableListOf<String>()
    private var insets = intArrayOf(0, 0, 0, 0)
    private var drawerOpen = false
    private var quickOpen = false
    private var launchCount = 0
    private var reloadToken = 0
    private var backdropBitmap: Bitmap? = null
    private val backdrops = mutableListOf<GlassBackdrop>()

    private lateinit var root: FrameLayout
    private lateinit var gradientView: View
    private lateinit var wallpaperView: ImageView
    private lateinit var desktop: LinearLayout
    private lateinit var desktopGrid: GridLayout
    private lateinit var headerClock: TextView
    private lateinit var startButton: TextView
    private lateinit var taskbarApps: LinearLayout
    private lateinit var trayStatus: TextView
    private lateinit var trayClock: TextView
    private lateinit var scrim: View
    private lateinit var drawerCard: FrameLayout
    private lateinit var drawerSearch: EditText
    private lateinit var drawerList: RecyclerView
    private lateinit var stripBox: LinearLayout
    private lateinit var emptyLabel: TextView
    private lateinit var quickCard: FrameLayout
    private lateinit var quickDate: TextView
    private lateinit var quickTime: TextView
    private lateinit var volumeSeek: SeekBar
    private lateinit var brightnessSeek: SeekBar

    private val drawerAdapter = DrawerAdapter()

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
        buildUi()
        setupWindow()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // A launcher must never finish itself: just close whatever is open.
                if (drawerOpen) closeDrawer() else if (quickOpen) closeQuick()
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
        if (DroidBridge.consumeStartRequest()) openDrawer()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while DroidDesk is already on screen.
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
                refreshAll()
            }
        }.start()
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
        scrim.setBackgroundColor(0x55000000)
        scrim.visibility = View.GONE
        scrim.setOnClickListener { closeDrawer(); closeQuick() }
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
        desktop.setPadding(dp(22) + insets[0], dp(18) + insets[1], dp(22) + insets[2], dp(12) + insets[3])
        onRootResized()
    }

    private fun onRootResized() {
        if (root.width == 0) return
        trayStatus.visibility = if (root.width >= dp(620)) View.VISIBLE else View.GONE
        updateOverlayLayout()
        updateDrawerSpan()
        renderDesktop()
        rebuildBackdrop()
    }

    private fun buildDesktopContent() {
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(label("DroidDesk", 25, Theme.text, true), LinearLayout.LayoutParams(0, dp(44), 1f))
        headerClock = label("", 14, Theme.muted)
        top.addView(headerClock)
        desktop.addView(top)
        val hint = label(getString(R.string.desktop_hint), 13, Theme.muted)
        hint.setPadding(0, 0, 0, dp(12))
        desktop.addView(hint)
        val title = label(getString(R.string.desktop_title), 11, Theme.muted, true)
        title.letterSpacing = .14f
        desktop.addView(title)

        val scroll = ScrollView(this)
        desktopGrid = GridLayout(this)
        desktopGrid.setPadding(0, dp(10), 0, dp(10))
        scroll.addView(desktopGrid)
        desktop.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        buildTaskbar()
    }

    private fun buildTaskbar() {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(7), dp(5), dp(7), dp(5))

        startButton = button("⊞", 22) { toggleDrawer() }
        startButton.contentDescription = getString(R.string.apps)
        bar.addView(startButton, LinearLayout.LayoutParams(dp(52), -1))

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isFillViewport = true
        taskbarApps = LinearLayout(this)
        taskbarApps.orientation = LinearLayout.HORIZONTAL
        taskbarApps.gravity = Gravity.CENTER_VERTICAL
        scroll.addView(taskbarApps, FrameLayout.LayoutParams(-2, -1))
        val sp = LinearLayout.LayoutParams(0, -1, 1f)
        sp.leftMargin = dp(6)
        bar.addView(scroll, sp)

        trayStatus = label("", 12, Theme.text)
        trayStatus.gravity = Gravity.CENTER
        trayStatus.setPadding(dp(8), 0, dp(8), 0)
        trayStatus.setOnClickListener { onTrayClicked() }
        bar.addView(trayStatus, LinearLayout.LayoutParams(-2, -1))

        trayClock = label("", 12, Theme.text, true)
        trayClock.gravity = Gravity.CENTER
        trayClock.setPadding(dp(8), 0, dp(8), 0)
        trayClock.setOnClickListener { toggleQuick() }
        bar.addView(trayClock, LinearLayout.LayoutParams(-2, -1))

        val gear = button("⚙", 20) { toggleQuick() }
        gear.contentDescription = getString(R.string.quick_settings)
        bar.addView(gear, LinearLayout.LayoutParams(dp(46), -1))

        desktop.addView(glassCard(bar, 22), LinearLayout.LayoutParams(-1, dp(taskbarSize)))
    }

    private fun buildQuickPanel() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(14))
        content.addView(label(getString(R.string.quick_settings), 20, Theme.text, true))
        quickDate = label("", 13, Theme.muted)
        content.addView(quickDate)
        quickTime = label("", 30, Theme.text, true)
        quickTime.setPadding(0, 0, 0, dp(10))
        content.addView(quickTime)

        val tools = listOf(
            Tool("⚙", R.string.tile_settings) { showSettings() },
            Tool("◷", R.string.tile_calendar) { openCalendar() },
            Tool("▣", R.string.tile_files) { safeStart(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) },
            Tool("⌁", R.string.tile_internet) {
                if (Build.VERSION.SDK_INT >= 29) openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else openSettings(Settings.ACTION_WIFI_SETTINGS)
            },
            Tool("ᛒ", R.string.tile_bluetooth) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Tool("▤", R.string.tile_display) { openSettings(Settings.ACTION_DISPLAY_SETTINGS) },
            Tool("♪", R.string.tile_sound) {
                if (Build.VERSION.SDK_INT >= 29) openSettings(Settings.Panel.ACTION_VOLUME)
                else openSettings(Settings.ACTION_SOUND_SETTINGS)
            },
            Tool("▦", R.string.tile_storage) { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
            Tool("⏻", R.string.tile_power) {
                withAccessibility(getString(R.string.tile_power)) {
                    it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                }
            }
        )
        val grid = GridLayout(this)
        grid.columnCount = 3
        tools.forEach { tool ->
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.setPadding(dp(4), dp(8), dp(4), dp(8))
            tile.background = rounded(accent.withAlpha(48), 16)
            tile.isClickable = true
            tile.isFocusable = true
            tile.setOnClickListener { tool.action() }
            val glyph = label(tool.glyph, 23, accent, true)
            glyph.gravity = Gravity.CENTER
            tile.addView(glyph, LinearLayout.LayoutParams(-1, dp(30)))
            val name = label(getString(tool.labelRes), 11, Theme.text, true)
            name.gravity = Gravity.CENTER
            tile.addView(name, LinearLayout.LayoutParams(-1, dp(24)))
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            grid.addView(tile, lp)
        }
        content.addView(grid)

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = maxOf(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        val volLabel = label(getString(R.string.volume), 13, Theme.muted, true)
        volLabel.setPadding(dp(2), dp(8), 0, 0)
        content.addView(volLabel)
        volumeSeek = SeekBar(this)
        volumeSeek.max = maxVolume
        volumeSeek.setOnSeekBarChangeListener(seekListener { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) })
        content.addView(volumeSeek)

        val brLabel = label(getString(R.string.brightness), 13, Theme.muted, true)
        brLabel.setPadding(dp(2), dp(5), 0, 0)
        content.addView(brLabel)
        brightnessSeek = SeekBar(this)
        brightnessSeek.max = 100
        brightnessSeek.setOnSeekBarChangeListener(seekListener { v ->
            val lp = window.attributes
            lp.screenBrightness = maxOf(.05f, v / 100f)
            window.attributes = lp
        })
        content.addView(brightnessSeek)

        quickCard = glassCard(content, 24)
        quickCard.elevation = dp(24).toFloat()
        quickCard.visibility = View.GONE
        root.addView(quickCard, FrameLayout.LayoutParams(-2, -2))
    }

    private fun buildDrawer() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(4), dp(18), dp(12))

        val grab = FrameLayout(this)
        val bar = View(this)
        bar.background = rounded(accent, 3)
        grab.addView(bar, FrameLayout.LayoutParams(dp(56), dp(5), Gravity.CENTER))
        grab.setOnTouchListener { _, e -> handleDrawerResize(e); true }
        content.addView(grab, LinearLayout.LayoutParams(-1, dp(24)))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(label(getString(R.string.apps), 22, Theme.text, true), LinearLayout.LayoutParams(0, dp(40), 1f))
        header.addView(button("—", 18) { closeDrawer() }, LinearLayout.LayoutParams(dp(44), dp(36)))
        content.addView(header)

        drawerSearch = EditText(this)
        drawerSearch.hint = getString(R.string.search_apps)
        drawerSearch.textSize = 15f
        drawerSearch.setSingleLine(true)
        drawerSearch.imeOptions = EditorInfo.IME_ACTION_GO
        drawerSearch.setTextColor(Theme.text)
        drawerSearch.setHintTextColor(Theme.muted)
        drawerSearch.setPadding(dp(14), 0, dp(14), 0)
        drawerSearch.background = rounded(Theme.panel2, 14)
        drawerSearch.addTextChangedListener(SimpleTextWatcher { applyFilter() })
        drawerSearch.setOnEditorActionListener { _, _, _ ->
            filtered.firstOrNull()?.let { launchApp(it) }
            true
        }
        val sp = LinearLayout.LayoutParams(-1, dp(46))
        sp.topMargin = dp(8)
        sp.bottomMargin = dp(6)
        content.addView(drawerSearch, sp)

        stripBox = LinearLayout(this)
        stripBox.orientation = LinearLayout.VERTICAL
        content.addView(stripBox)

        emptyLabel = label(getString(R.string.no_apps), 14, Theme.muted)
        emptyLabel.visibility = View.GONE
        emptyLabel.setPadding(dp(4), dp(12), 0, 0)
        content.addView(emptyLabel)

        drawerList = RecyclerView(this)
        drawerList.layoutManager = GridLayoutManager(this, 5)
        drawerList.adapter = drawerAdapter
        drawerList.setHasFixedSize(true)
        drawerList.overScrollMode = View.OVER_SCROLL_NEVER
        content.addView(drawerList, LinearLayout.LayoutParams(-1, 0, 1f))

        drawerCard = glassCard(content, 24)
        drawerCard.elevation = dp(22).toFloat()
        drawerCard.visibility = View.GONE
        root.addView(drawerCard, FrameLayout.LayoutParams(-2, -2))
    }

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
            card.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> backdrop.invalidate() }
        }
        val stroke = if (glassMode) Color.argb(40, 255, 255, 255) else 0
        content.background = rounded(panelTint(), radiusDp, stroke, if (glassMode) 1 else 0)
        card.addView(content, FrameLayout.LayoutParams(-1, -1))
        return card
    }

    private fun blurSupported(): Boolean = glassMode && Build.VERSION.SDK_INT >= 31

    private fun panelTint(): Int = when {
        !glassMode -> Theme.panel
        blurSupported() -> Color.argb(150, 25, 36, 57)
        else -> Color.argb(224, 25, 36, 57)
    }

    private fun gradientDrawable(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, Theme.gradient(gradientMode).colors)

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

    private fun bottomOffset(): Int = insets[3] + dp(taskbarSize) + dp(20)

    private fun updateOverlayLayout() {
        if (!::root.isInitialized || !::drawerCard.isInitialized || !::quickCard.isInitialized || root.width == 0) return
        val availH = root.height - bottomOffset() - insets[1] - dp(8)
        val dw = minOf((root.width * drawerWidthFrac).toInt(), root.width - insets[0] - insets[2])
        val dh = minOf((root.height * drawerHeightFrac).toInt(), availH)
        val dlp = FrameLayout.LayoutParams(dw, maxOf(dh, dp(240)), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        dlp.bottomMargin = bottomOffset()
        drawerCard.layoutParams = dlp

        val qw = minOf((root.width * .92f).toInt(), dp(380))
        val qlp = FrameLayout.LayoutParams(qw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        qlp.bottomMargin = bottomOffset()
        qlp.rightMargin = insets[2] + dp(12)
        quickCard.layoutParams = qlp
    }

    private fun updateDrawerSpan() {
        if (!::drawerList.isInitialized || !::drawerCard.isInitialized) return
        val w = drawerCard.layoutParams?.width ?: return
        if (w <= 0) return
        (drawerList.layoutManager as? GridLayoutManager)?.spanCount = maxOf(3, (w - dp(36)) / dp(86))
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

    private fun renderDesktop() {
        if (!::desktopGrid.isInitialized) return
        desktopGrid.removeAllViews()
        val total = if (root.width > 0) root.width else resources.displayMetrics.widthPixels
        val cols = maxOf(3, (total - desktop.paddingLeft - desktop.paddingRight) / dp(88))
        desktopGrid.columnCount = cols
        val list = shortcuts.mapNotNull { appMap[it] }
        if (list.isEmpty()) {
            val hint = label(getString(R.string.desktop_empty), 15, Theme.muted)
            hint.setPadding(dp(8), dp(18), dp(8), dp(18))
            val lp = GridLayout.LayoutParams()
            lp.width = -1
            lp.columnSpec = GridLayout.spec(0, cols)
            desktopGrid.addView(hint, lp)
            return
        }
        list.forEach { app ->
            val tile = buildTile()
            bindTile(tile, app, true)
            val lp = GridLayout.LayoutParams()
            lp.width = dp(82)
            lp.height = dp(104)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            desktopGrid.addView(tile, lp)
        }
    }

    private fun renderTaskbar() {
        if (!::taskbarApps.isInitialized) return
        taskbarApps.removeAllViews()
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
            icon.setPadding(dp(7), dp(7), dp(7), dp(7))
            icon.contentDescription = app.label
            cell.addView(icon, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
            if (pkg !in pinned) {
                val dot = View(this)
                dot.background = rounded(accent, 2)
                cell.addView(dot, FrameLayout.LayoutParams(dp(14), dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            }
            taskbarApps.addView(cell, LinearLayout.LayoutParams(dp(48), -1))
        }
    }

    private fun buildTile(): LinearLayout {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setPadding(dp(3), dp(5), dp(3), dp(4))
        tile.isClickable = true
        tile.isFocusable = true
        val icon = ImageView(this)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        tile.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
        val name = label("", 11, Theme.text)
        name.gravity = Gravity.CENTER
        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        tile.addView(name, LinearLayout.LayoutParams(-1, dp(34)))
        hoverable(tile)
        return tile
    }

    private fun bindTile(tile: LinearLayout, app: AppEntry, onDesktop: Boolean) {
        (tile.getChildAt(0) as ImageView).setImageBitmap(app.icon)
        (tile.getChildAt(1) as TextView).text = app.label
        tile.setOnClickListener { launchApp(app) }
        tile.setOnLongClickListener { showAppMenu(app, onDesktop); true }
        tile.setOnContextClickListener { showAppMenu(app, onDesktop); true }
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

    private inner class VH(val tile: LinearLayout) : RecyclerView.ViewHolder(tile)

    private inner class DrawerAdapter : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tile = buildTile()
            tile.layoutParams = RecyclerView.LayoutParams(-1, dp(98))
            return VH(tile)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            bindTile(holder.tile, filtered[position], false)
        }

        override fun getItemCount(): Int = filtered.size
    }

    private fun applyFilter() {
        if (!::drawerSearch.isInitialized) return
        val q = drawerSearch.text.toString().trim()
        filtered = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
        drawerAdapter.notifyDataSetChanged()
        emptyLabel.visibility = if (filtered.isEmpty() && apps.isNotEmpty()) View.VISIBLE else View.GONE
        renderStrip()
    }

    private fun renderStrip() {
        if (!::stripBox.isInitialized) return
        stripBox.removeAllViews()
        if (drawerSearch.text.isNotEmpty()) {
            stripBox.visibility = View.GONE
            return
        }
        val pin = pinned.mapNotNull { appMap[it] }
        val rec = recents.filter { it !in pinned }.mapNotNull { appMap[it] }
        if (pin.isEmpty() && rec.isEmpty()) {
            stripBox.visibility = View.GONE
            return
        }
        stripBox.visibility = View.VISIBLE
        if (pin.isNotEmpty()) addStripRow(getString(R.string.pinned), pin)
        if (rec.isNotEmpty()) addStripRow(getString(R.string.recent), rec)
        val all = label(getString(R.string.all_apps), 12, Theme.muted, true)
        all.setPadding(dp(2), dp(8), 0, dp(2))
        stripBox.addView(all)
    }

    private fun addStripRow(title: String, list: List<AppEntry>) {
        val t = label(title, 12, Theme.muted, true)
        t.setPadding(dp(2), dp(6), 0, dp(2))
        stripBox.addView(t)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        list.forEach { app ->
            val icon = ImageView(this)
            icon.setImageBitmap(app.icon)
            icon.setPadding(dp(8), dp(8), dp(8), dp(8))
            icon.contentDescription = app.label
            icon.isClickable = true
            icon.setOnClickListener { launchApp(app) }
            icon.setOnLongClickListener { showAppMenu(app, false); true }
            icon.setOnContextClickListener { showAppMenu(app, false); true }
            hoverable(icon)
            row.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)))
        }
        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        sv.addView(row)
        stripBox.addView(sv, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun openDrawer() {
        if (drawerOpen || !::drawerCard.isInitialized) return
        closeQuick()
        drawerOpen = true
        drawerSearch.setText("")
        applyFilter()
        drawerCard.visibility = View.VISIBLE
        drawerCard.bringToFront()
        if (resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS) drawerSearch.requestFocus()
        updateScrim()
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

    private fun toggleDrawer() { if (drawerOpen) closeDrawer() else openDrawer() }

    private fun openQuick() {
        if (quickOpen || !::quickCard.isInitialized) return
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
        scrim.visibility = if (drawerOpen || quickOpen) View.VISIBLE else View.GONE
        startButton.background = rounded(if (drawerOpen) accent.withAlpha(90) else Theme.panel2, 14)
    }

    private fun launchApp(app: AppEntry, displayId: Int? = null) {
        val intent = packageManager.getLaunchIntentForPackage(app.pkg)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        if (displayId != null) {
            options.setLaunchDisplayId(displayId)
        } else {
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
        renderStrip()
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

    private fun showAppMenu(app: AppEntry, onDesktop: Boolean) {
        val titles = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        fun add(res: Int, action: () -> Unit) {
            titles.add(getString(res))
            actions.add(action)
        }
        add(R.string.menu_open) { launchApp(app) }
        externalDisplayId()?.let { id -> add(R.string.menu_open_external) { launchApp(app, id) } }
        val onDesk = shortcuts.contains(app.pkg)
        add(if (onDesk) R.string.menu_remove_shortcut else R.string.menu_add_shortcut) { toggleShortcut(app.pkg) }
        add(if (pinned.contains(app.pkg)) R.string.menu_unpin else R.string.menu_pin) { togglePin(app.pkg) }
        if (onDesktop && onDesk) {
            add(R.string.menu_move_left) { moveShortcut(app.pkg, -1) }
            add(R.string.menu_move_right) { moveShortcut(app.pkg, 1) }
        }
        add(R.string.menu_app_info) {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
        }
        add(R.string.menu_uninstall) {
            safeStart(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null)))
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(titles.toTypedArray()) { _, i -> actions[i]() }
            .show()
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
        renderStrip()
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

    private fun updateTray() {
        if (!::trayClock.isInitialized) return
        val now = Date()
        val time = android.text.format.DateFormat.getTimeFormat(this).format(now)
        trayClock.text = time + "\n" + SimpleDateFormat("d MMM", Locale.getDefault()).format(now)
        headerClock.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now) + " • " + time
        quickDate.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        quickTime.text = time

        val sb = StringBuilder("📶 ").append(networkName())
        val battery = batteryIntent()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            sb.append("   🔋 ").append(if (charging) "⚡" else "").append(level * 100 / scale).append('%')
        }
        val n = DroidBridge.notificationCount
        if (n > 0) sb.append("   🔔 ").append(n)
        trayStatus.text = sb.toString()
    }

    private fun batteryIntent(): Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun networkName(): String {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> getString(R.string.net_none)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getString(R.string.net_wifi)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getString(R.string.net_mobile)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> getString(R.string.net_eth)
                else -> getString(R.string.net_other)
            }
        } catch (e: Exception) {
            getString(R.string.net_none)
        }
    }

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
                updateDrawerSpan()
            }
        }
        add(R.string.set_drawer_h) {
            sizeDialog(R.string.set_drawer_h, drawerHeightFrac, .30f, .82f) { v ->
                drawerHeightFrac = v
                prefs.drawerHeight = v
                updateOverlayLayout()
            }
        }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> if (event.action == KeyEvent.ACTION_DOWN && (drawerOpen || quickOpen)) {
                closeDrawer()
                closeQuick()
                return true
            }
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT ->
                if (event.action == KeyEvent.ACTION_UP && DroidBridge.accessibility == null) {
                    toggleDrawer()
                    return true
                }
            KeyEvent.KEYCODE_TAB -> if (event.isAltPressed && DroidBridge.accessibility == null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) altTabFallback()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun altTabFallback() {
        val next = recents.getOrNull(1) ?: recents.getOrNull(0) ?: return
        appMap[next]?.let { launchApp(it) }
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    private fun safeStart(intent: Intent) {
        try { startActivity(intent) } catch (e: Exception) { toast(R.string.toast_cannot_open) }
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { safeStart(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openCalendar() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
        } catch (e: Exception) {
            openSettings(Settings.ACTION_DATE_SETTINGS)
        }
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

    private fun button(value: String, size: Int, action: () -> Unit): TextView {
        val t = TextView(this)
        t.text = value
        t.textSize = size.toFloat()
        t.setTextColor(Theme.text)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(4), dp(8), dp(4))
        t.background = rounded(Theme.panel2, 14)
        t.isFocusable = true
        t.isClickable = true
        t.setOnClickListener { action() }
        return t
    }
}
