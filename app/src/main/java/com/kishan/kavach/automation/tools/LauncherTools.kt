package com.kishan.kavach.automation.tools

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.domain.tools.*
import com.kishan.kavach.services.accessibility.KavachAccess

/** Tools that fire fixed, well-known Android intents. The model can never supply an arbitrary Intent. */
class LauncherTools(private val app: Context) {
    // The accessibility service context is allowed to start activities from the background.
    private fun launch(i: Intent): ToolResult = try {
        (KavachAccess.instance ?: app).startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ToolResult.ok("Opened")
    } catch (e: Exception) { ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Is phone par ye action nahi chala: ${e.message}".take(150)) }

    fun all(): List<Tool> = listOf(openApp, launchSettings, openUrl, setAlarm, setTimer, dialNumber, mapsSearch, copyToClipboard)

    val openApp = SimpleTool("open_app", "Launch an installed app by its name", ToolGroup.CORE,
        listOf(ParamSpec("name", ParamType.STRING, "app label", maxLength = 60),
            ParamSpec("expect", ParamType.STRING, "text that must be visible after launch", required = false, maxLength = 80)),
        timeoutMs = 15_000) { a ->
        val pm = app.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val q = a.string("name").lowercase().trim()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return@SimpleTool ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "'${a.string("name")}' naam ka app nahi mila.")
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return@SimpleTool ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Launch nahi ho paya")
        val r = launch(i)
        val c = ScreenBridge.controller
        val expect = a.str("expect")
        if (r.success && !expect.isNullOrBlank() && c != null && !c.screenContains(expect, 4000))
            ToolResult.fail(ErrorCode.EXECUTION_FAILED, "App opened but '$expect' not visible", retryable = true)
        else ToolResult.ok("Opened ${hit.loadLabel(pm)}")
    }

    val launchSettings = SimpleTool("launch_settings", "Open a system settings page", ToolGroup.UTILITY,
        listOf(ParamSpec("which", ParamType.STRING, "settings page", enumValues = listOf("wifi", "bluetooth", "display", "sound", "battery", "storage", "accessibility", "notifications", "apps", "main")))) { a ->
        launch(Intent(when (a.string("which")) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }))
    }

    val openUrl = SimpleTool("open_url", "Open an http(s) link in the browser/app", ToolGroup.UTILITY,
        listOf(ParamSpec("url", ParamType.STRING, "http or https URL", maxLength = 500))) { a ->
        val u = a.string("url")
        if (!Regex("(?i)^https?://\\S+$").matches(u)) ToolResult.fail(ErrorCode.INVALID_ARGS, "Only http/https URLs")
        else launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    val setAlarm = SimpleTool("set_alarm", "Set an alarm in the clock app", ToolGroup.UTILITY,
        listOf(ParamSpec("hour", ParamType.INTEGER, "0-23", min = 0.0, max = 23.0), ParamSpec("minute", ParamType.INTEGER, "0-59", min = 0.0, max = 59.0)),
        permissions = listOf("com.android.alarm.permission.SET_ALARM")) { a ->
        launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.int("hour")!!).putExtra(AlarmClock.EXTRA_MINUTES, a.int("minute")!!))
    }

    val setTimer = SimpleTool("set_timer", "Start a countdown timer", ToolGroup.UTILITY,
        listOf(ParamSpec("seconds", ParamType.INTEGER, "1-86400", min = 1.0, max = 86400.0)),
        permissions = listOf("com.android.alarm.permission.SET_ALARM")) { a ->
        launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, a.int("seconds")!!))
    }

    val dialNumber = SimpleTool("dial_number", "Open the dialer with a number (does not call)", ToolGroup.COMMS,
        listOf(ParamSpec("number", ParamType.STRING, "digits, + allowed", maxLength = 16))) { a ->
        val n = a.string("number").filter { it.isDigit() || it == '+' }
        if (n.length < 3) ToolResult.fail(ErrorCode.INVALID_ARGS, "Invalid number") else launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))
    }

    val mapsSearch = SimpleTool("maps_search", "Search a place in Maps, or start navigation", ToolGroup.UTILITY,
        listOf(ParamSpec("query", ParamType.STRING, "place", maxLength = 120), ParamSpec("navigate", ParamType.BOOLEAN, "start navigation", required = false))) { a ->
        val q = Uri.encode(a.string("query"))
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(if (a.bool("navigate")) "google.navigation:q=$q" else "geo:0,0?q=$q")))
    }

    val copyToClipboard = SimpleTool("copy_to_clipboard", "Copy text to the clipboard", ToolGroup.UTILITY,
        listOf(ParamSpec("text", ParamType.STRING, "text", maxLength = 2000))) { a ->
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("KAVACH", a.string("text")))
        ToolResult.ok("Copied")
    }
}
