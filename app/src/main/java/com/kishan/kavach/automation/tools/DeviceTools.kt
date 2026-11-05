package com.kishan.kavach.automation.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.kishan.kavach.domain.tools.*
import com.kishan.kavach.services.notification.NotificationStore
import java.util.Locale

/** Deterministic device info straight from Android APIs: no Accessibility, no Gemma. */
class DeviceTools(private val app: Context) {
    fun all(): List<Tool> = listOf(battery, storage, deviceInfo, androidVersion, installedApps, bluetooth, network, notificationSummary)

    private fun gb(b: Long) = String.format(Locale.US, "%.1f GB", b / 1_073_741_824.0)

    val battery = SimpleTool("get_battery_info", "Battery level, charging state, temperature", ToolGroup.DEVICE) {
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        ToolResult.ok("Battery $pct%${if (charging) ", charging" else ""}, ${temp}°C", mapOf("percent" to pct, "charging" to charging, "temp_c" to temp))
    }

    val storage = SimpleTool("get_storage_info", "Internal storage total/free/used", ToolGroup.DEVICE) {
        val s = StatFs(Environment.getDataDirectory().path)
        val total = s.totalBytes; val free = s.availableBytes
        ToolResult.ok("Storage: ${gb(total - free)} used of ${gb(total)}, ${gb(free)} free (${(free * 100 / total)}% free)",
            mapOf("total_bytes" to total, "free_bytes" to free))
    }

    val deviceInfo = SimpleTool("get_device_info", "Model, manufacturer, RAM, CPU ABI", ToolGroup.DEVICE) {
        val mi = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        ToolResult.ok("${Build.MANUFACTURER} ${Build.MODEL}, RAM ${gb(mi.totalMem)} (${gb(mi.availMem)} free), ABI ${Build.SUPPORTED_ABIS.firstOrNull()}",
            mapOf("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "ram_bytes" to mi.totalMem))
    }

    val androidVersion = SimpleTool("get_android_version", "Android version, API level, security patch", ToolGroup.DEVICE) {
        ToolResult.ok("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}",
            mapOf("release" to Build.VERSION.RELEASE, "api" to Build.VERSION.SDK_INT))
    }

    val installedApps = SimpleTool("get_installed_apps", "Launcher apps installed (names, max 60)", ToolGroup.DEVICE,
        listOf(ParamSpec("filter", ParamType.STRING, "substring filter", required = false, maxLength = 40))) { a ->
        val pm = app.packageManager
        val f = a.string("filter").lowercase()
        val names = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() }.distinct().sorted().filter { f.isEmpty() || it.lowercase().contains(f) }
        ToolResult.ok("${names.size} apps: ${names.take(60).joinToString(", ")}", mapOf("count" to names.size))
    }

    val bluetooth = SimpleTool("get_bluetooth_status", "Whether Bluetooth is on", ToolGroup.DEVICE) {
        val on = Settings.Global.getInt(app.contentResolver, "bluetooth_on", 0) == 1
        ToolResult.ok("Bluetooth is ${if (on) "on" else "off"}", mapOf("enabled" to on))
    }

    val network = SimpleTool("get_network_status", "Connectivity: wifi/cellular/offline", ToolGroup.DEVICE) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val type = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        ToolResult.ok("Network: $type${if (type != "offline") if (validated) ", internet ok" else ", no internet" else ""}", mapOf("type" to type, "validated" to validated))
    }

    val notificationSummary = SimpleTool("get_notification_summary", "Recent notifications captured since app start (needs Notification Access)", ToolGroup.DEVICE,
        permissions = listOf("notification_access")) {
        ToolResult.ok(NotificationStore.summaryText(), mapOf("count" to NotificationStore.items.value.size))
    }
}
