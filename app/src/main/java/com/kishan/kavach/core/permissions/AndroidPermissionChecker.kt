package com.kishan.kavach.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.domain.tools.PermissionChecker

/** Real permission state. Pseudo permissions: "accessibility" and "notification_access". */
class AndroidPermissionChecker(private val ctx: Context) : PermissionChecker {
    override fun isGranted(permission: String): Boolean = when (permission) {
        "accessibility" -> ScreenBridge.controller?.connected == true
        "notification_access" -> notificationAccessEnabled(ctx)
        else -> ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        fun notificationAccessEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { it.startsWith(ctx.packageName + "/") }
        }
    }
}
