package com.kishan.kavach.automation.tools

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.kishan.kavach.workers.ReminderSchedulerImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Scheduled {
    data class Set(val id: Long, val atMillis: Long, val exact: Boolean) : Scheduled
    data class Failed(val why: String) : Scheduled
}

object PromiseScheduler {

    private val timeFmt get() = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    fun schedule(
        ctx: Context,
        id: Long,
        text: String,
        triggerAtMs: Long
    ): Scheduled {
        if (triggerAtMs <= System.currentTimeMillis()) {
            return Scheduled.Failed("Reminder time is in the past")
        }

        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return Scheduled.Failed("Alarm service unavailable")

        val canScheduleExact = if (Build.VERSION.SDK_INT >= 31) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        return if (canScheduleExact) {
            // Schedule via AlarmManager / ReminderSchedulerImpl
            ReminderSchedulerImpl(ctx).schedule(id, text, triggerAtMs)
            Scheduled.Set(id, triggerAtMs, exact = true)
        } else {
            // Fallback to WorkManager
            ReminderSchedulerImpl(ctx).schedule(id, text, triggerAtMs)
            Scheduled.Set(id, triggerAtMs, exact = false)
        }
    }

    fun buildResponse(scheduled: Scheduled, taskText: String): String {
        return when (scheduled) {
            is Scheduled.Set -> {
                val formattedTime = timeFmt.format(Date(scheduled.atMillis))
                val method = if (scheduled.exact) "exact alarm" else "WorkManager (exact alarm permission missing)"
                "Main $formattedTime par '$taskText' yaad dilaunga ($method)."
            }
            is Scheduled.Failed -> {
                "Maafi chahta hu, reminder schedule nahi ho paya: ${scheduled.why}."
            }
        }
    }
}
