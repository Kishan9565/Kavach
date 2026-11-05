package com.kishan.kavach.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class KavachApp : Application(), AccessHostProvider {
    lateinit var container: AppContainer
        private set
    override val host: AccessHost get() = container

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH))
    }
}
