package com.kishan.kavach.workers

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.*
import com.kishan.kavach.app.KavachApp
import com.kishan.kavach.automation.tools.ReminderScheduler
import com.kishan.kavach.domain.model.Automation
import com.kishan.kavach.domain.model.TriggerType
import java.util.concurrent.TimeUnit

/** One-shot reminder through WorkManager (inexact by design: respects Doze and battery optimisation). */
class ReminderSchedulerImpl(private val ctx: Context) : ReminderScheduler {
    override fun schedule(id: Long, text: String, triggerAt: Long) {
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to id, "text" to text)).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("reminder_$id", ExistingWorkPolicy.REPLACE, req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", -1)
        val text = inputData.getString("text").orEmpty()
        val n = Notification.Builder(applicationContext, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("KAVACH reminder").setContentText(text)
            .setAutoCancel(true).build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id.toInt(), n)
        if (id >= 0) (applicationContext as KavachApp).container.productivity.markReminderDone(id)
        return Result.success()
    }
}

/** Scheduled automations: periodic work (Android's minimum interval is 15 minutes; actual timing is up to the OS). */
class AutomationScheduler(private val ctx: Context) {
    fun sync(a: Automation) {
        val name = "automation_${a.id}"
        val wm = WorkManager.getInstance(ctx)
        if (!a.enabled || a.triggerType != TriggerType.SCHEDULE) { wm.cancelUniqueWork(name); return }
        val minutes = (a.triggerParam.toLongOrNull() ?: 60L).coerceAtLeast(15L)
        val req = PeriodicWorkRequestBuilder<AutomationWorker>(minutes, TimeUnit.MINUTES).setInputData(workDataOf("id" to a.id)).build()
        wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
    fun cancel(id: Long) { WorkManager.getInstance(ctx).cancelUniqueWork("automation_$id") }
}

class AutomationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", -1)
        if (id < 0) return Result.failure()
        (applicationContext as KavachApp).container.automationEngine.run(id, manual = false)
        return Result.success()
    }
}

/** Nightly review worker: periodic job with charging & idle constraints. Folds conversations into summaries. */
class NightlyReviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? KavachApp ?: return Result.success()
        val container = app.container
        if (!container.prefs.nightlyReviewOptIn) return Result.success()

        // Prune old orphan task events and summarize finished tasks
        container.taskRepo.clearFinished()
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val req = PeriodicWorkRequestBuilder<NightlyReviewWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "nightly_review",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
