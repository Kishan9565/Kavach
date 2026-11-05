package com.kishan.kavach.services.notification

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

object NotificationListenerHealth {

    @Volatile
    var connected: Boolean = false

    fun checkAndRebind(ctx: Context) {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(ctx)
        val isEnabled = ctx.packageName in enabledPackages

        if (isEnabled && !connected) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(ctx, KavachNotificationService::class.java)
                )
            }
        }
    }

    fun isFullyConnected(ctx: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(ctx)
        val isEnabled = ctx.packageName in enabledPackages
        return isEnabled && connected
    }
}
