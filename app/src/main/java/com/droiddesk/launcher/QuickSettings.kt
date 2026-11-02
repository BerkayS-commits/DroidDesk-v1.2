package com.droiddesk.launcher

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * One quick-settings tile. Tap toggles where Android lets a third-party app do that, otherwise it opens the
 * system panel for the setting. Long-press always opens the matching Android settings page.
 */
class QsItem(
    val bit: Int,
    val iconRes: Int,
    val labelRes: Int,
    val color: Int,
    val available: (MainActivity) -> Boolean,
    val isOn: (MainActivity) -> Boolean,
    val onTap: (MainActivity) -> Unit,
    val onLong: (MainActivity) -> Unit
)

object QuickSettings {
    @Volatile var torchOn = false
    private var torchId: String? = null

    fun registerTorch(ctx: Context, onChange: () -> Unit) {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchId = cm.cameraIdList.firstOrNull { id ->
                val ch = cm.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) {
                        torchOn = enabled
                        onChange()
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // No flash or no camera access.
        }
    }

    private fun toggleTorch(a: MainActivity) {
        val id = torchId ?: return
        if (Build.VERSION.SDK_INT < 23) return
        try {
            (a.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, !torchOn)
        } catch (e: Exception) {
            // Camera in use.
        }
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi(a: MainActivity) {
        if (Build.VERSION.SDK_INT <= 28) {
            try {
                val wm = a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.isWifiEnabled = !wm.isWifiEnabled
                return
            } catch (e: Exception) {
                // Fall through to the system panel.
            }
        }
        a.openInternetPanel()
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun toggleBluetooth(a: MainActivity) {
        if (Build.VERSION.SDK_INT <= 30) {
            try {
                val ad = BluetoothAdapter.getDefaultAdapter()
                if (ad != null) {
                    if (ad.isEnabled) ad.disable() else ad.enable()
                    return
                }
            } catch (e: Exception) {
                // Fall through to settings.
            }
        }
        a.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    private fun wifiOn(a: MainActivity): Boolean = try {
        (a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        false
    }

    private fun toggleDnd(a: MainActivity) {
        if (Build.VERSION.SDK_INT < 23) return
        val nm = a.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            a.safeStart(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val all = NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(if (nm.currentInterruptionFilter == all) NotificationManager.INTERRUPTION_FILTER_PRIORITY else all)
    }

    private fun dndOn(a: MainActivity): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        val f = (a.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun rotateOn(a: MainActivity): Boolean =
        Settings.System.getInt(a.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun toggleRotate(a: MainActivity) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(a)) {
            a.safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + a.packageName)))
            return
        }
        try {
            Settings.System.putInt(a.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotateOn(a)) 0 else 1)
        } catch (e: Exception) {
            // Not permitted.
        }
    }

    private fun locationOn(a: MainActivity): Boolean {
        return try {
            val lm = a.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    private fun globalAction(a: MainActivity, nameRes: Int, action: Int) {
        if (Build.VERSION.SDK_INT < 28) {
            a.toast(R.string.toast_needs_android9)
            return
        }
        a.closeNotifications()
        // Wait for the panel to disappear so it is not part of the screenshot.
        a.handler.postDelayed({
            a.withAccessibility(a.getString(nameRes)) { it.performGlobalAction(action) }
        }, 350L)
    }

    fun items(): List<QsItem> = listOf(
        QsItem(1, R.drawable.ic_wifi, R.string.tile_internet, Color.rgb(45, 140, 255), { true },
            { wifiOn(it) }, { toggleWifi(it) }, { it.openSettings(Settings.ACTION_WIFI_SETTINGS) }),
        QsItem(2, R.drawable.ic_bluetooth, R.string.tile_bluetooth, Color.rgb(124, 92, 255), { true },
            { it.bluetoothOn() }, { toggleBluetooth(it) }, { it.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }),
        QsItem(4, R.drawable.ic_airplane, R.string.tile_airplane, Color.rgb(100, 116, 139), { true },
            { Settings.Global.getInt(it.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 },
            { it.openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }, { it.openSettings(Settings.ACTION_WIRELESS_SETTINGS) }),
        QsItem(256, R.drawable.ic_nfc, R.string.tile_nfc, Color.rgb(6, 182, 212),
            { NfcAdapter.getDefaultAdapter(it) != null },
            { NfcAdapter.getDefaultAdapter(it)?.isEnabled == true },
            {
                if (Build.VERSION.SDK_INT >= 29) it.openSettings(Settings.Panel.ACTION_NFC)
                else it.openSettings(Settings.ACTION_NFC_SETTINGS)
            },
            { it.openSettings(Settings.ACTION_NFC_SETTINGS) }),
        QsItem(512, R.drawable.ic_dnd, R.string.tile_dnd, Color.rgb(225, 29, 72), { Build.VERSION.SDK_INT >= 23 },
            { dndOn(it) }, { toggleDnd(it) }, { it.openSettings(Settings.ACTION_SOUND_SETTINGS) }),
        QsItem(1024, R.drawable.ic_torch, R.string.tile_torch, Color.rgb(234, 179, 8),
            { Build.VERSION.SDK_INT >= 23 && it.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) },
            { torchOn }, { toggleTorch(it) }, { it.openAndroidSettings() }),
        QsItem(2048, R.drawable.ic_rotate, R.string.tile_rotate, Color.rgb(20, 184, 166), { true },
            { rotateOn(it) }, { toggleRotate(it) }, { it.openSettings(Settings.ACTION_DISPLAY_SETTINGS) }),
        QsItem(4096, R.drawable.ic_moon, R.string.tile_dark, Color.rgb(99, 102, 241), { true },
            { (it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES },
            { it.openSettings(Settings.ACTION_DISPLAY_SETTINGS) }, { it.openSettings(Settings.ACTION_DISPLAY_SETTINGS) }),
        QsItem(8192, R.drawable.ic_volume_off, R.string.tile_mute, Color.rgb(245, 158, 11), { Build.VERSION.SDK_INT >= 23 },
            { (it.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isStreamMute(AudioManager.STREAM_MUSIC) },
            { (it.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) },
            { it.openSettings(Settings.ACTION_SOUND_SETTINGS) }),
        QsItem(16384, R.drawable.ic_awake, R.string.tile_awake, Color.rgb(132, 204, 22), { true },
            { it.keepAwake }, { it.keepAwake = !it.keepAwake }, { it.openSettings(Settings.ACTION_DISPLAY_SETTINGS) }),
        QsItem(32768, R.drawable.ic_location, R.string.tile_location, Color.rgb(14, 165, 233), { true },
            { locationOn(it) }, { it.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }, { it.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }),
        QsItem(8, R.drawable.ic_display, R.string.tile_display, Color.rgb(139, 92, 246), { true },
            { false }, { it.openSettings(Settings.ACTION_DISPLAY_SETTINGS) }, { it.openSettings(Settings.ACTION_DISPLAY_SETTINGS) }),
        QsItem(16, R.drawable.ic_volume, R.string.tile_sound, Color.rgb(249, 115, 22), { true },
            { false }, { it.openVolumePanel() }, { it.openSettings(Settings.ACTION_SOUND_SETTINGS) }),
        QsItem(32, R.drawable.ic_saver, R.string.tile_saver, Color.rgb(16, 185, 129), { true },
            { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode },
            { it.openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS) }, { it.openSettings(Intent.ACTION_POWER_USAGE_SUMMARY) }),
        QsItem(65536, R.drawable.ic_screenshot, R.string.tile_screenshot, Color.rgb(236, 72, 153), { Build.VERSION.SDK_INT >= 28 },
            { false }, { globalAction(it, R.string.tile_screenshot, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, { it.openAndroidSettings() }),
        QsItem(131072, R.drawable.ic_lock, R.string.tile_lock, Color.rgb(100, 116, 139), { Build.VERSION.SDK_INT >= 28 },
            { false }, { globalAction(it, R.string.tile_lock, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }, { it.openAndroidSettings() }),
        QsItem(64, R.drawable.ic_power, R.string.tile_power, Color.rgb(239, 68, 68), { true },
            { false },
            { it.withAccessibility(it.getString(R.string.tile_power)) { s -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) } },
            { it.openAndroidSettings() }),
        QsItem(128, R.drawable.ic_settings, R.string.tile_settings, Color.rgb(100, 116, 139), { true },
            { false }, { it.openAndroidSettings() }, { it.showSettingsPanel(0) })
    )
}
