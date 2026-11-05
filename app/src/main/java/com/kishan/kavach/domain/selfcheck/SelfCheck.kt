package com.kishan.kavach.domain.selfcheck

enum class Level { OK, NOTE, WARN, FAIL }

data class Finding(
    val id: String,
    val level: Level,
    val title: String,
    val fix: String?,
    val settingsAction: String? = null
)

fun needsAttention(f: Finding): Boolean = f.level == Level.FAIL || f.level == Level.WARN

data class SelfCheckSnapshot(
    val modelImported: Boolean,
    val modelLoaded: Boolean,
    val accessibilityConnected: Boolean,
    val inputMethodSupported: Boolean,
    val inputMethodConnected: Boolean,
    val notificationAccessEnabled: Boolean,
    val notificationListenerConnected: Boolean,
    val micPermissionGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val canScheduleExactAlarms: Boolean,
    val foregroundServiceRunning: Boolean
)

object SelfCheckEvaluator {

    fun evaluate(s: SelfCheckSnapshot): List<Finding> {
        val list = mutableListOf<Finding>()

        // 1. Model check
        if (!s.modelImported || !s.modelLoaded) {
            list.add(
                Finding(
                    id = "model_load",
                    level = Level.FAIL,
                    title = "Gemma model is not loaded",
                    fix = "Import a valid .litertlm file in Models settings",
                    settingsAction = "models"
                )
            )
        } else {
            list.add(Finding("model_load", Level.OK, "Gemma model loaded", null))
        }

        // 2. Accessibility service
        if (!s.accessibilityConnected) {
            list.add(
                Finding(
                    id = "accessibility",
                    level = Level.FAIL,
                    title = "Accessibility service is off",
                    fix = "Enable Draivo Agent in System Settings > Accessibility",
                    settingsAction = "android.settings.ACCESSIBILITY_SETTINGS"
                )
            )
        } else {
            list.add(Finding("accessibility", Level.OK, "Accessibility service connected", null))
        }

        // 3. Typing channel (A11yInput vs ACTION_SET_TEXT)
        if (s.inputMethodSupported && s.inputMethodConnected) {
            list.add(Finding("typing_channel", Level.OK, "A11yInput keyboard channel active", null))
        } else if (s.accessibilityConnected) {
            list.add(
                Finding(
                    id = "typing_channel",
                    level = Level.NOTE,
                    title = "Using fallback ACTION_SET_TEXT typing channel",
                    fix = "API 33+ input method flag inactive; fallback typing active",
                    settingsAction = null
                )
            )
        } else {
            list.add(
                Finding(
                    id = "typing_channel",
                    level = Level.WARN,
                    title = "No typing channel available",
                    fix = "Connect Accessibility Service to enable typing",
                    settingsAction = "android.settings.ACCESSIBILITY_SETTINGS"
                )
            )
        }

        // 4. Notification listener
        if (s.notificationAccessEnabled && !s.notificationListenerConnected) {
            list.add(
                Finding(
                    id = "notif_listener",
                    level = Level.WARN,
                    title = "Notification listener is disconnected",
                    fix = "Tap to rebind notification listener or check Notification Access",
                    settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                )
            )
        } else if (s.notificationAccessEnabled) {
            list.add(Finding("notif_listener", Level.OK, "Notification listener connected", null))
        }

        // 5. Mic permission
        if (!s.micPermissionGranted) {
            list.add(
                Finding(
                    id = "mic_permission",
                    level = Level.NOTE,
                    title = "Microphone permission not granted",
                    fix = "Grant Microphone permission for voice commands and wake word",
                    settingsAction = "android.settings.APPLICATION_DETAILS_SETTINGS"
                )
            )
        } else {
            list.add(Finding("mic_permission", Level.OK, "Microphone permission granted", null))
        }

        // 6. Battery optimization
        if (!s.ignoringBatteryOptimizations) {
            list.add(
                Finding(
                    id = "battery_opt",
                    level = Level.WARN,
                    title = "Battery optimization is active",
                    fix = "Exclude Draivo from battery optimizations to prevent background kills",
                    settingsAction = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                )
            )
        } else {
            list.add(Finding("battery_opt", Level.OK, "Ignoring battery optimizations", null))
        }

        // 7. Exact alarms
        if (!s.canScheduleExactAlarms) {
            list.add(
                Finding(
                    id = "exact_alarms",
                    level = Level.NOTE,
                    title = "Exact alarms permission missing",
                    fix = "Allow exact alarms in Settings for precise reminder timing",
                    settingsAction = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
                )
            )
        } else {
            list.add(Finding("exact_alarms", Level.OK, "Exact alarms permitted", null))
        }

        // 8. Foreground service
        if (!s.foregroundServiceRunning) {
            list.add(
                Finding(
                    id = "fg_service",
                    level = Level.WARN,
                    title = "Background agent service is stopped",
                    fix = "Start Accessibility service to activate agent",
                    settingsAction = "android.settings.ACCESSIBILITY_SETTINGS"
                )
            )
        } else {
            list.add(Finding("fg_service", Level.OK, "Foreground service active", null))
        }

        return list
    }
}
