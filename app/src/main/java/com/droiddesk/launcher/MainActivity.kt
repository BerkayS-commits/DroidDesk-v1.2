package com.droiddesk.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * DroidDesk 1.4 - the launcher shell. It owns the root layout, overlays (app drawer, notification centre,
 * settings, context menus) and input routing, and delegates the rest to Taskbar, DesktopWidgets,
 * NotificationCenter, SearchResults, SettingsPanel and FilesWindow.
 */
class MainActivity : ComponentActivity() {

    internal lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    internal val handler = Handler(Looper.getMainLooper())

    private var glassMode = true
    private var gradientMode = "bluepurple"
    private var drawerHeightFrac = .68f

    internal var apps: List<AppEntry> = emptyList()
    internal var appMap: Map<String, AppEntry> = emptyMap()
    private var filtered: List<AppEntry> = emptyList()
    private var shortcuts = mutableListOf<String>()
    internal var pinned = mutableListOf<String>()
    private var recents = mutableListOf<String>()
    private val sessionOpened = LinkedHashSet<String>()
    private var homePackages: Set<String> = emptySet()
    internal var insets = intArrayOf(0, 0, 0, 0)

    private var drawerOpen = false
    private var notifOpen = false
    private var menuOpen = false
    private var launchCount = 0
    private var reloadToken = 0
    private var backdropBitmap: Bitmap? = null
    private val backdrops = mutableListOf<GlassBackdrop>()

    internal var lastTouchX = 0f
    private var lastTouchY = 0f
    private var longPressRunnable: Runnable? = null
    private var longFired = false
    private var suppressTap = false
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var menuCard: View? = null
    private var menuX = 0f
    private var menuY = 0f
    private var lastClickPkg = ""
    private var lastClickTime = 0L
    private var selectedTile: View? = null

    private var pageCols = 4
    private var perPage = 20
    private var currentPage = 0

    internal lateinit var root: FrameLayout
    private lateinit var gradientView: View
    private lateinit var wallpaperView: ImageView
    private lateinit var desktop: LinearLayout
    private lateinit var desktopArea: FrameLayout
    private lateinit var desktopGrid: GridLayout
    private lateinit var desktopHint: TextView
    private lateinit var scrim: View
    private lateinit var drawerCard: FrameLayout
    private lateinit var drawerSearch: EditText
    private lateinit var drawerPager: ViewPager2
    private lateinit var dotsRow: LinearLayout

    internal lateinit var taskbar: Taskbar
    internal lateinit var widgets: DesktopWidgets
    internal lateinit var notif: NotificationCenter
    private lateinit var search: SearchResults
    private lateinit var filesWin: FilesWindow
    private var settings: SettingsPanel? = null
    internal val currentSettings: SettingsPanel? get() = settings

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

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            FileIndex.invalidate()
            if (::search.isInitialized) search.onResume()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
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
                if (menuOpen) dismissMenu()
                else if (settings != null) closeSettings()
                else if (drawerOpen) closeDrawer()
                else if (notifOpen) closeNotifications()
            }
        })
        registerPackageReceiver()
        DroidBridge.onNotificationsChanged = { handler.post { onNotificationsChanged() } }
        reloadApps()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        ticker.run()
        if (::notif.isInitialized) notif.syncSliders()
        if (::search.isInitialized) search.onResume()
        handlePendingActions()
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
        closeNotifications()
        if (settings != null) closeSettings()
        handlePendingActions()
        if (DroidBridge.consumeStartRequest()) toggleDrawer() else closeDrawer()
    }

    override fun onDestroy() {
        DroidBridge.onNotificationsChanged = null
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(packageReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    /** The accessibility service can ask us to relaunch the app that was in front in a window. */
    private fun handlePendingActions() {
        val pkg = DroidBridge.consumeShrink() ?: return
        val app = appMap[pkg]
        if (app != null) launchApp(app, null, true)
    }

    private fun loadPrefs() {
        shortcuts = prefs.shortcuts
        pinned = prefs.pinned
        recents = prefs.recents
        Theme.accentColor = Theme.accentFor(prefs.accent, prefs.accentCustom)
        glassMode = prefs.glass
        gradientMode = prefs.gradient
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

    internal fun reloadApps() {
        val token = ++reloadToken
        val shape = prefs.iconShape
        val pack = prefs.iconPack
        Thread {
            val list = try { repo.load(shape, pack) } catch (e: Exception) { emptyList() }
            val homes = repo.homePackages()
            runOnUiThread {
                if (isDestroyed || token != reloadToken) return@runOnUiThread
                apps = list
                appMap = list.associateBy { it.pkg }
                homePackages = homes
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
        taskbar.render()
        applyFilter()
        tick()
    }

    private fun tick() {
        if (!::taskbar.isInitialized) return
        taskbar.updateTray()
        widgets.update()
        updateActive()
        if (notifOpen && notif.tab == 0) notif.refresh()
    }

    private fun onNotificationsChanged() {
        if (!::taskbar.isInitialized) return
        taskbar.updateTray()
        if (notifOpen && notif.tab == 0) notif.refresh()
    }

    /** Which apps get a light on the taskbar: foreground / recently used (usage access) or opened this session. */
    internal fun updateActive() {
        if (!::taskbar.isInitialized) return
        val mode = prefs.activeMode
        val m = HashMap<String, Int>()
        if (mode != 0) {
            if (ActiveApps.hasAccess(this)) {
                val window = when (mode) {
                    2 -> 30 * 60_000L
                    3 -> 120 * 60_000L
                    else -> 6 * 3_600_000L
                }
                for ((p, v) in ActiveApps.compute(this, window)) {
                    if (mode == 1 && v < 2) continue
                    m[p] = v
                }
            } else {
                val fg = DroidBridge.foregroundPkg
                if (fg.isNotEmpty()) m[fg] = 2
            }
            if (mode != 1) {
                for (p in sessionOpened) if (!m.containsKey(p)) m[p] = 1
            }
            m.remove(packageName)
            m.remove("com.android.systemui")
            for (h in homePackages) m.remove(h)
        }
        taskbar.setActive(m)
    }

    internal fun reorderPinned(order: List<String>) {
        val reordered = order.filter { pinned.contains(it) }
        val missing = pinned.filter { !reordered.contains(it) }
        pinned = (reordered + missing).toMutableList()
        prefs.pinned = pinned
        taskbar.render()
    }

    internal fun resetLayout() {
        shortcuts.clear()
        pinned.clear()
        prefs.shortcuts = shortcuts
        prefs.pinned = pinned
        renderDesktop()
        taskbar.render()
    }

    // ------------------------------------------------------------------ system state helpers

    internal fun networkConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork) != null
        } catch (e: Exception) {
            false
        }
    }

    internal fun bluetoothOn(): Boolean {
        return try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    internal fun batteryPercent(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    // ------------------------------------------------------------------ UI construction

    internal fun rebuildUi() {
        val sp = settings
        val reopen = sp != null
        val tab = sp?.tab ?: 0
        val sy = sp?.scrollY() ?: 0
        buildUi()
        if (reopen) showSettingsPanel(tab, sy)
    }

    internal fun onAppearanceChanged() {
        loadPrefs()
        rebuildUi()
    }

    private fun buildUi() {
        drawerOpen = false
        notifOpen = false
        menuOpen = false
        menuCard = null
        settings = null
        selectedTile = null
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

        taskbar = Taskbar(this)
        widgets = DesktopWidgets(this)
        notif = NotificationCenter(this)
        search = SearchResults(this)
        filesWin = FilesWindow(this)

        desktop = LinearLayout(this)
        desktop.orientation = LinearLayout.VERTICAL
        root.addView(desktop, FrameLayout.LayoutParams(-1, -1))

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
        body.addView(widgets.scroll, LinearLayout.LayoutParams(-2, -1))
        desktop.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        desktop.addView(taskbar.build(), LinearLayout.LayoutParams(-1, dp(prefs.taskbarSize)))
        widgets.rebuild()

        scrim = View(this)
        scrim.setBackgroundColor(0x44000000)
        scrim.visibility = View.GONE
        scrim.setOnClickListener { dismissMenu(); closeDrawer(); closeNotifications(); closeSettings() }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val nc = notif.build()
        nc.elevation = dp(24).toFloat()
        nc.visibility = View.GONE
        root.addView(nc, FrameLayout.LayoutParams(-2, -2))
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

    internal fun onRootResized() {
        if (!::root.isInitialized || !::taskbar.isInitialized || root.width == 0) return
        widgets.scroll.visibility = if (root.width >= dp(720) && widgets.hasAny()) View.VISIBLE else View.GONE
        taskbar.expanded = root.width >= dp(640)
        taskbar.layout()
        updateOverlayLayout()
        renderDesktop()
        rebuildBackdrop()
        filesWin.apply()
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
    internal fun glassCard(content: View, radiusDp: Int): FrameLayout {
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

    internal fun invalidateBackdrops() { backdrops.forEach { it.invalidate() } }

    internal fun removeBackdropsOf(card: View) { backdrops.removeAll { it.parent === card } }

    private fun rebuildBackdrop() {
        if (!blurSupported() || !::root.isInitialized || root.width == 0 || root.height == 0) return
        val s = 0.25f
        val bmp = Bitmap.createBitmap(maxOf(1, (root.width * s).toInt()), maxOf(1, (root.height * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        gradientView.draw(c)
        if (wallpaperView.visibility == View.VISIBLE) wallpaperView.draw(c)
        backdropBitmap = bmp
        invalidateBackdrops()
    }

    // ------------------------------------------------------------------ overlay geometry

    internal fun bottomOffset(): Int = insets[3] + dp(prefs.taskbarSize) + dp(20)

    internal fun updateOverlayLayout() {
        if (!::root.isInitialized || !::drawerCard.isInitialized || !::notif.isInitialized || root.width == 0) return
        val availH = root.height - bottomOffset() - insets[1] - dp(8)

        val dw = minOf((root.width * prefs.drawerWidth).toInt(), root.width - insets[0] - insets[2], dp(720))
        val dh = minOf((root.height * drawerHeightFrac).toInt(), availH)
        val grav = when (prefs.drawerAlign) {
            0 -> Gravity.BOTTOM or Gravity.START
            2 -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val dlp = FrameLayout.LayoutParams(dw, maxOf(dh, dp(260)), grav)
        dlp.bottomMargin = bottomOffset()
        if (prefs.drawerAlign == 0) dlp.leftMargin = insets[0] + dp(8)
        if (prefs.drawerAlign == 2) dlp.rightMargin = insets[2] + dp(8)
        drawerCard.layoutParams = dlp

        val nw = when (prefs.npWidth) {
            0 -> dp(340)
            2 -> dp(480)
            else -> dp(400)
        }
        val nlp = FrameLayout.LayoutParams(minOf(nw, root.width - insets[0] - insets[2] - dp(16)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        nlp.bottomMargin = bottomOffset()
        nlp.rightMargin = insets[2] + dp(8)
        notif.card.layoutParams = nlp
        notif.setMaxBody(maxOf(dp(160), minOf(availH - dp(90), dp(520))))

        val sp = settings
        if (sp != null) {
            val sw = minOf(root.width - insets[0] - insets[2] - dp(16), dp(780))
            val slp = FrameLayout.LayoutParams(sw, maxOf(minOf(availH, dp(640)), dp(300)), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            slp.bottomMargin = bottomOffset()
            sp.card.layoutParams = slp
        }
        recomputePages()
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

    internal fun applyDrawerPrefs() {
        drawerHeightFrac = prefs.drawerHeight
        updateOverlayLayout()
        pageAdapter.notifyDataSetChanged()
        rebuildDots()
    }

    internal fun applyIconPrefs() {
        renderDesktop()
        pageAdapter.notifyDataSetChanged()
    }

    // ------------------------------------------------------------------ desktop icons

    private fun iconDims(): IntArray = when (prefs.iconSize) {
        0 -> intArrayOf(70, 92, 34)
        2 -> intArrayOf(96, 120, 52)
        else -> intArrayOf(82, 106, 42)
    } // tile width, tile height, icon (dp)

    internal fun renderDesktop() {
        if (!::desktopGrid.isInitialized) return
        desktopGrid.removeAllViews()
        selectedTile = null
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

    private fun buildTile(iconDp: Int): LinearLayout {
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
        name.visibility = if (prefs.showLabels) View.VISIBLE else View.GONE
        tile.addView(name, LinearLayout.LayoutParams(-1, dp(34)))
        hoverable(tile)
        return tile
    }

    private fun bindTile(tile: LinearLayout, app: AppEntry, onDesktop: Boolean) {
        (tile.getChildAt(0) as ImageView).setImageBitmap(app.icon)
        (tile.getChildAt(1) as TextView).text = app.label
        tile.contentDescription = app.label
        tile.setOnClickListener { onTileClick(tile, app, onDesktop) }
        tile.setOnLongClickListener { showAppMenu(app, onDesktop); true }
        tile.setOnContextClickListener { onTileContext(app, onDesktop); true } // mouse right-click
    }

    /** Single click opens; with "double click to open" the first click only selects (Windows style). */
    private fun onTileClick(tile: View, app: AppEntry, onDesktop: Boolean) {
        if (!onDesktop || prefs.iconOpen == 0) {
            launchApp(app)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (lastClickPkg == app.pkg && now - lastClickTime < 400L) {
            lastClickPkg = ""
            selectedTile?.background = null
            selectedTile = null
            launchApp(app)
        } else {
            lastClickPkg = app.pkg
            lastClickTime = now
            selectedTile?.background = null
            tile.background = rounded(Theme.accentColor.withAlpha(70), 12)
            selectedTile = tile
        }
    }

    private fun onTileContext(app: AppEntry, onDesktop: Boolean) {
        when (if (onDesktop) prefs.rcIcon else 0) {
            1 -> launchApp(app)
            2 -> appInfo(app)
            else -> showAppMenu(app, onDesktop)
        }
    }

    private fun appInfo(app: AppEntry) {
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
    }

    // ------------------------------------------------------------------ app drawer

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

        // Grabber: drag up/down to resize the drawer.
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
        box.addView(iconView(R.drawable.ic_search, Theme.muted), llp(dp(20), dp(20)))
        drawerSearch = EditText(this)
        drawerSearch.hint = getString(R.string.search_hint)
        drawerSearch.textSize = 15f
        drawerSearch.setSingleLine(true)
        drawerSearch.imeOptions = EditorInfo.IME_ACTION_GO
        drawerSearch.setTextColor(Theme.text)
        drawerSearch.setHintTextColor(Theme.muted)
        drawerSearch.background = null
        drawerSearch.setPadding(dp(10), 0, 0, 0)
        drawerSearch.addTextChangedListener(SimpleTextWatcher { applyFilter() })
        // Enter opens the first result (an app, folder or file).
        drawerSearch.setOnEditorActionListener { _, _, _ ->
            if (!search.openFirst()) filtered.firstOrNull()?.let { launchApp(it) }
            true
        }
        box.addView(drawerSearch, LinearLayout.LayoutParams(0, -1, 1f))
        val bp = LinearLayout.LayoutParams(-1, dp(46))
        bp.bottomMargin = dp(8)
        content.addView(box, bp)

        val holder = FrameLayout(this)
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
        holder.addView(drawerPager, FrameLayout.LayoutParams(-1, -1))
        holder.addView(search.view, FrameLayout.LayoutParams(-1, -1))
        content.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))

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
        val cols = if (prefs.drawerCols != 0) prefs.drawerCols else maxOf(4, (w - dp(32)) / dp(92))
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
        if (n <= 1 || drawerPager.visibility != View.VISIBLE) {
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
            d.setColor(if (i == currentPage) Theme.accentColor else Theme.muted.withAlpha(110))
            dotsRow.getChildAt(i).background = d
        }
    }

    private fun applyFilter() {
        if (!::drawerSearch.isInitialized) return
        val q = drawerSearch.text.toString().trim()
        if (q.isEmpty()) {
            filtered = apps
            search.view.visibility = View.GONE
            drawerPager.visibility = View.VISIBLE
            pageAdapter.notifyDataSetChanged()
            currentPage = 0
            if (drawerPager.currentItem != 0) drawerPager.setCurrentItem(0, false)
            rebuildDots()
        } else {
            filtered = apps.filter { it.label.contains(q, ignoreCase = true) }
            drawerPager.visibility = View.GONE
            dotsRow.visibility = View.INVISIBLE
            search.view.visibility = View.VISIBLE
            search.update(q)
        }
    }

    internal fun openDrawer(focusSearch: Boolean) {
        if (!::drawerCard.isInitialized) return
        if (drawerOpen) {
            if (focusSearch) focusSearchField(true)
            return
        }
        dismissMenu(false)
        closeNotifications()
        drawerOpen = true
        drawerSearch.setText("")
        applyFilter()
        drawerCard.visibility = View.VISIBLE
        drawerCard.bringToFront()
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

    internal fun closeDrawer() {
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

    internal fun toggleDrawer() { if (drawerOpen) closeDrawer() else openDrawer(false) }

    // ------------------------------------------------------------------ notification centre + settings overlays

    internal fun openNotifications(tab: Int) {
        if (!::notif.isInitialized) return
        dismissMenu(false)
        closeDrawer()
        notifOpen = true
        notif.select(tab)
        notif.card.visibility = View.VISIBLE
        notif.card.bringToFront()
        updateScrim()
    }

    internal fun closeNotifications() {
        if (!::notif.isInitialized) return
        notifOpen = false
        notif.card.visibility = View.GONE
        updateScrim()
    }

    internal fun toggleNotifications(tab: Int) { if (notifOpen) closeNotifications() else openNotifications(tab) }

    internal fun showSettingsPanel(tab: Int, scrollY: Int = 0) {
        dismissMenu(false)
        closeDrawer()
        closeNotifications()
        var sp = settings
        if (sp == null) {
            sp = SettingsPanel(this)
            val card = sp.build()
            card.elevation = dp(26).toFloat()
            settings = sp
            root.addView(card, FrameLayout.LayoutParams(-2, -2))
            updateOverlayLayout()
            card.bringToFront()
        }
        sp.select(tab, scrollY)
        updateScrim()
    }

    internal fun closeSettings() {
        val sp = settings ?: return
        root.removeView(sp.card)
        removeBackdropsOf(sp.card)
        settings = null
        updateScrim()
    }

    internal fun updateScrim() {
        if (!::scrim.isInitialized) return
        scrim.visibility = if (drawerOpen || notifOpen || menuOpen || settings != null) View.VISIBLE else View.GONE
        if (::taskbar.isInitialized) {
            taskbar.startView?.background = if (drawerOpen) rounded(Theme.accentColor.withAlpha(70), 12) else null
        }
    }

    // ------------------------------------------------------------------ context menus

    private fun s(res: Int): String = getString(res)

    private fun sep() = MenuEntry(0, "", false) { }

    internal fun showMenu(x: Float, y: Float, entries: List<MenuEntry>) {
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
            if (e.iconRes != 0) row.addView(iconView(e.iconRes, Theme.text), llp(dp(18), dp(18)))
            val t = label(e.label, 13, Theme.text)
            t.setPadding(if (e.iconRes != 0) dp(12) else dp(2), 0, 0, 0)
            row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            if (e.sub) row.addView(iconView(R.drawable.ic_chevron_right, Theme.muted), llp(dp(16), dp(16)))
            hoverable(row)
            row.setOnClickListener {
                dismissMenu()
                e.action()
            }
            col.addView(row, LinearLayout.LayoutParams(-1, dp(40)))
        }
        val card = FrameLayout(this)
        card.background = rounded(Color.argb(242, 25, 36, 57), 14, Color.argb(50, 255, 255, 255), 1)
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

    internal fun dismissMenu(updateUi: Boolean = true) {
        val c = menuCard
        if (c != null) root.removeView(c)
        menuCard = null
        menuOpen = false
        if (updateUi) updateScrim()
    }

    private fun checked(on: Boolean, text: String): String = if (on) "✓  $text" else "      $text"

    private fun showDesktopMenu(x: Float, y: Float) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(R.drawable.ic_image, s(R.string.menu_wallpaper), false) { showSettingsPanel(0) })
        e.add(MenuEntry(R.drawable.ic_view, s(R.string.menu_view), true) { showViewMenu() })
        e.add(MenuEntry(R.drawable.ic_sort, s(R.string.menu_sort), true) { showSortMenu() })
        e.add(MenuEntry(R.drawable.ic_refresh, s(R.string.menu_refresh), false) {
            reloadApps()
            widgets.refreshWeather(true)
        })
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_widgets, s(R.string.menu_add_widget), true) { showWidgetToggleMenu() })
        e.add(MenuEntry(R.drawable.ic_apps, s(R.string.menu_open_drawer), false) { openDrawer(false) })
        e.add(MenuEntry(R.drawable.ic_folder, s(R.string.menu_files_window), false) { filesWin.show() })
        e.add(MenuEntry(R.drawable.ic_tune, s(R.string.droiddesk_settings), false) { showSettingsPanel(0) })
        showMenu(x, y, e)
    }

    private fun showViewMenu() {
        val names = listOf(R.string.view_small, R.string.view_medium, R.string.view_large)
        val e = mutableListOf<MenuEntry>()
        names.forEachIndexed { i, res ->
            e.add(MenuEntry(0, checked(prefs.iconSize == i, s(res)), false) {
                prefs.iconSize = i
                renderDesktop()
            })
        }
        showMenu(menuX, menuY, e)
    }

    private fun showSortMenu() {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(0, s(R.string.sort_name), false) { sortShortcuts(false) })
        e.add(MenuEntry(0, s(R.string.sort_recent), false) { sortShortcuts(true) })
        showMenu(menuX, menuY, e)
    }

    private fun sortShortcuts(byRecent: Boolean) {
        if (appMap.isEmpty()) return
        val valid = shortcuts.filter { appMap[it] != null }
        val sorted = if (byRecent) {
            valid.sortedBy { val i = recents.indexOf(it); if (i < 0) Int.MAX_VALUE else i }
        } else {
            valid.sortedBy { appMap[it]?.label?.lowercase(java.util.Locale.getDefault()) ?: "" }
        }
        shortcuts = sorted.toMutableList()
        prefs.shortcuts = shortcuts
        renderDesktop()
    }

    private fun showWidgetToggleMenu() {
        val items = listOf(
            1 to R.string.widget_clock, 2 to R.string.widget_calendar,
            4 to R.string.widget_system, 8 to R.string.widget_quote
        )
        val e = mutableListOf<MenuEntry>()
        items.forEach { (bit, res) ->
            e.add(MenuEntry(0, checked((prefs.widgetMask and bit) != 0, s(res)), false) {
                prefs.widgetMask = prefs.widgetMask xor bit
                widgets.rebuild()
                onRootResized()
                showWidgetToggleMenu()
            })
        }
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_close, s(R.string.widget_remove_all), false) {
            prefs.widgetMask = 0
            widgets.rebuild()
            onRootResized()
        })
        e.add(MenuEntry(R.drawable.ic_refresh, s(R.string.widget_restore), false) { widgets.restoreDefaults() })
        showMenu(menuX, menuY, e)
    }

    private fun showWidgetItemMenu(bit: Int, x: Float, y: Float) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(R.drawable.ic_maximize, s(R.string.widget_resize), false) { widgets.setEdit(true) })
        e.add(MenuEntry(0, s(R.string.size_small), false) { widgets.preset(bit, 220) })
        e.add(MenuEntry(0, s(R.string.size_medium), false) { widgets.preset(bit, 300) })
        e.add(MenuEntry(0, s(R.string.size_large), false) { widgets.preset(bit, 420) })
        e.add(MenuEntry(0, s(R.string.size_reset), false) { widgets.resetSize(bit) })
        e.add(sep())
        e.add(MenuEntry(0, s(R.string.move_up), false) { widgets.move(bit, -1) })
        e.add(MenuEntry(0, s(R.string.move_down), false) { widgets.move(bit, 1) })
        if (bit == 1) e.add(MenuEntry(R.drawable.ic_widgets, s(R.string.set_weather), false) { widgets.askCity() })
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_close, s(R.string.widget_remove), false) { widgets.remove(bit) })
        showMenu(x, y, e)
    }

    private fun showTaskbarMenu(x: Float, y: Float) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(R.drawable.ic_tune, s(R.string.taskbar_settings), false) { showSettingsPanel(1) })
        e.add(MenuEntry(0, s(if (prefs.showSearch) R.string.menu_hide_search else R.string.menu_show_search), false) {
            prefs.showSearch = !prefs.showSearch
            taskbar.render()
        })
        e.add(MenuEntry(0, s(if (prefs.showTaskView) R.string.menu_hide_taskview else R.string.menu_show_taskview), false) {
            prefs.showTaskView = !prefs.showTaskView
            taskbar.render()
        })
        e.add(MenuEntry(0, s(if (prefs.taskbarAlign == 1) R.string.align_left_action else R.string.align_center_action), false) {
            prefs.taskbarAlign = if (prefs.taskbarAlign == 1) 0 else 1
            taskbar.layout()
        })
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_bell, s(R.string.tab_notifications), false) { openNotifications(0) })
        e.add(MenuEntry(R.drawable.ic_settings, s(R.string.android_settings), false) { openAndroidSettings() })
        showMenu(x, y, e)
    }

    /** Right-click on the start button: quick links, like the Windows "power user" menu. */
    private fun showStartMenu(x: Float, y: Float) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(R.drawable.ic_apps, s(R.string.apps), false) { openDrawer(false) })
        e.add(MenuEntry(R.drawable.ic_search, s(R.string.search_tooltip), false) { openDrawer(true) })
        e.add(MenuEntry(R.drawable.ic_folder, s(R.string.menu_files_window), false) { filesWin.show() })
        e.add(MenuEntry(R.drawable.ic_recents, s(R.string.task_view), false) { altTab() })
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_bell, s(R.string.tab_notifications), false) { openNotifications(0) })
        e.add(MenuEntry(R.drawable.ic_display, s(R.string.tab_quick), false) { openNotifications(1) })
        e.add(MenuEntry(R.drawable.ic_tune, s(R.string.droiddesk_settings), false) { showSettingsPanel(0) })
        e.add(MenuEntry(R.drawable.ic_settings, s(R.string.android_settings), false) { openAndroidSettings() })
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_power, s(R.string.tile_power), false) {
            withAccessibility(s(R.string.tile_power)) {
                it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            }
        })
        showMenu(x, y, e)
    }

    private fun showTrayMenu(x: Float, y: Float) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(R.drawable.ic_wifi, s(R.string.tile_internet), false) { openInternetPanel() })
        e.add(MenuEntry(R.drawable.ic_bluetooth, s(R.string.tile_bluetooth), false) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) })
        e.add(MenuEntry(R.drawable.ic_volume, s(R.string.tile_sound), false) { openVolumePanel() })
        e.add(MenuEntry(R.drawable.ic_battery, s(R.string.tile_battery), false) { openSettings(Intent.ACTION_POWER_USAGE_SUMMARY) })
        e.add(sep())
        e.add(MenuEntry(R.drawable.ic_bell, s(R.string.tab_notifications), false) { openNotifications(0) })
        e.add(MenuEntry(R.drawable.ic_clear_all, s(R.string.notif_clear_all), false) { DroidBridge.listener?.cancelAllNotifications() })
        e.add(MenuEntry(R.drawable.ic_tune, s(R.string.tray_settings), false) { showSettingsPanel(1) })
        showMenu(x, y, e)
    }

    internal fun showAppMenu(app: AppEntry, onDesktop: Boolean) {
        val e = mutableListOf<MenuEntry>()
        e.add(MenuEntry(0, s(R.string.menu_open), false) { launchApp(app) })
        externalDisplayId()?.let { id ->
            e.add(MenuEntry(0, s(R.string.menu_open_external), false) { launchApp(app, id) })
        }
        e.add(MenuEntry(0, s(R.string.menu_open_window), false) { launchApp(app, null, true) })
        val onDesk = shortcuts.contains(app.pkg)
        e.add(MenuEntry(0, s(if (onDesk) R.string.menu_remove_shortcut else R.string.menu_add_shortcut), false) { toggleShortcut(app.pkg) })
        e.add(MenuEntry(0, s(if (pinned.contains(app.pkg)) R.string.menu_unpin else R.string.menu_pin), false) { togglePin(app.pkg) })
        if (onDesktop && onDesk) {
            e.add(MenuEntry(0, s(R.string.menu_move_left), false) { moveShortcut(app.pkg, -1) })
            e.add(MenuEntry(0, s(R.string.menu_move_right), false) { moveShortcut(app.pkg, 1) })
        }
        e.add(sep())
        e.add(MenuEntry(0, s(R.string.menu_app_info), false) { appInfo(app) })
        e.add(MenuEntry(0, s(R.string.menu_uninstall), false) {
            safeStart(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null)))
        })
        showMenu(lastTouchX, lastTouchY, e)
    }

    private fun toggleShortcut(pkg: String) {
        if (!shortcuts.remove(pkg)) shortcuts.add(pkg)
        prefs.shortcuts = shortcuts
        renderDesktop()
    }

    private fun togglePin(pkg: String) {
        if (!pinned.remove(pkg)) pinned.add(pkg)
        prefs.pinned = pinned
        taskbar.render()
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

    // ------------------------------------------------------------------ launching

    internal fun launchApp(app: AppEntry, displayId: Int? = null, freeform: Boolean = false) {
        val intent = packageManager.getLaunchIntentForPackage(app.pkg)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        if (displayId != null) {
            options.setLaunchDisplayId(displayId)
        } else if (freeform || prefs.windowed) {
            // Honoured only on devices with freeform windows enabled; ignored otherwise.
            options.setLaunchBounds(nextBounds())
            if (freeform) {
                if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
                    toast(R.string.toast_no_freeform)
                }
                try {
                    val m = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    m.invoke(options, 5) // WINDOWING_MODE_FREEFORM (hidden API, best effort)
                } catch (e: Throwable) {
                    // Not available on this Android version.
                }
            }
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
        sessionOpened.remove(app.pkg)
        sessionOpened.add(app.pkg)
        updateActive()
        dismissMenu(false)
        closeDrawer()
        closeNotifications()
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

    internal fun altTab() {
        val svc = DroidBridge.accessibility
        if (svc != null) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            return
        }
        // Fallback without the accessibility service: jump to the previously used app.
        val next = recents.getOrNull(1) ?: recents.getOrNull(0) ?: return
        appMap[next]?.let { launchApp(it) }
    }

    internal fun withAccessibility(feature: String, block: (DroidAccessibilityService) -> Unit) {
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

    // ------------------------------------------------------------------ wallpaper

    internal fun pickWallpaper() { wallpaperPicker.launch(arrayOf("image/*")) }

    internal fun loadWallpaper(spec: String?, announce: Boolean) {
        if (spec == null) {
            wallpaperView.setImageDrawable(null)
            wallpaperView.visibility = View.GONE
            rebuildBackdrop()
            return
        }
        val dm = resources.displayMetrics
        val w = maxOf(dm.widthPixels, 1)
        val h = maxOf(dm.heightPixels, 1)
        Thread {
            val bmp = if (spec.startsWith("builtin:")) {
                Wallpapers.render(spec.removePrefix("builtin:").toIntOrNull() ?: 0, w, h)
            } else {
                decodeSampled(Uri.parse(spec), w, h)
            }
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

    // ------------------------------------------------------------------ input: keys, right-click, long-press, double-click

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> if (event.action == KeyEvent.ACTION_DOWN && (drawerOpen || notifOpen || menuOpen || settings != null)) {
                dismissMenu(false)
                closeDrawer()
                closeNotifications()
                closeSettings()
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

    private val T_OVERLAY = 0
    private val T_START = 1
    private val T_TRAY = 2
    private val T_APPCELL = 3
    private val T_TASKBAR = 4
    private val T_TILE = 5
    private val T_EMPTY = 6

    /** What is under the pointer. Widgets return 100 + widget bit. */
    private fun targetAt(x: Float, y: Float): Int {
        if (!::taskbar.isInitialized || drawerOpen || notifOpen || menuOpen || settings != null) return T_OVERLAY
        if (isOverView(taskbar.card, x, y)) {
            return when {
                taskbar.isOverStart(x, y) -> T_START
                taskbar.isOverTray(x, y) -> T_TRAY
                taskbar.isOverApp(x, y) -> T_APPCELL
                else -> T_TASKBAR
            }
        }
        if (filesWin.isOver(x, y)) return T_OVERLAY
        val wb = widgets.bitAt(x, y)
        if (wb != 0) return 100 + wb
        for (i in 0 until desktopGrid.childCount) {
            if (isOverView(desktopGrid.getChildAt(i), x, y)) return T_TILE
        }
        return T_EMPTY
    }

    /** Long-press (touch) or right-click (mouse) on bars, widgets and empty desktop. App icons handle their own. */
    private fun routeContext(x: Float, y: Float): Boolean {
        val t = targetAt(x, y)
        when {
            t == T_START -> showStartMenu(x, y)
            t == T_TRAY -> showTrayMenu(x, y)
            t == T_TASKBAR -> showTaskbarMenu(x, y)
            t >= 100 -> showWidgetItemMenu(t - 100, x, y)
            t == T_EMPTY -> if (prefs.rcDesktop == 0) showDesktopMenu(x, y)
            else -> return false
        }
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.rawX
                lastTouchY = ev.rawY
                longFired = false
                suppressTap = false
                cancelLongPress()
                if ((ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                    if (routeContext(ev.rawX, ev.rawY)) {
                        suppressTap = true
                        return true
                    }
                } else {
                    val r = Runnable {
                        if (routeContext(lastTouchX, lastTouchY)) longFired = true
                    }
                    longPressRunnable = r
                    handler.postDelayed(r, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (abs(ev.rawX - lastTouchX) > slop || abs(ev.rawY - lastTouchY) > slop) cancelLongPress()
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                handleTapUp(ev.rawX, ev.rawY)
            }
            MotionEvent.ACTION_CANCEL -> cancelLongPress()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun cancelLongPress() {
        val r = longPressRunnable
        if (r != null) handler.removeCallbacks(r)
        longPressRunnable = null
    }

    /** Configurable double-click on empty desktop. */
    private fun handleTapUp(x: Float, y: Float) {
        if (longFired || suppressTap) {
            suppressTap = false
            return
        }
        if (prefs.dcEmpty == 0 || targetAt(x, y) != T_EMPTY) {
            lastTapTime = 0L
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastTapTime < 350L && abs(x - lastTapX) < dp(40) && abs(y - lastTapY) < dp(40)) {
            lastTapTime = 0L
            when (prefs.dcEmpty) {
                1 -> openDrawer(false)
                2 -> showDesktopMenu(x, y)
                3 -> openNotifications(0)
            }
        } else {
            lastTapTime = now
            lastTapX = x
            lastTapY = y
        }
    }

    // ------------------------------------------------------------------ small helpers

    internal fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    internal fun safeStart(intent: Intent) {
        try { startActivity(intent) } catch (e: Exception) { toast(R.string.toast_cannot_open) }
    }

    internal fun openSettings(action: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { safeStart(Intent(Settings.ACTION_SETTINGS)) }
    }

    internal fun openAndroidSettings() { openSettings(Settings.ACTION_SETTINGS) }

    internal fun openInternetPanel() {
        if (Build.VERSION.SDK_INT >= 29) openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else openSettings(Settings.ACTION_WIFI_SETTINGS)
    }

    internal fun openVolumePanel() {
        if (Build.VERSION.SDK_INT >= 29) openSettings(Settings.Panel.ACTION_VOLUME)
        else openSettings(Settings.ACTION_SOUND_SETTINGS)
    }

    internal fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                safeStart(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
