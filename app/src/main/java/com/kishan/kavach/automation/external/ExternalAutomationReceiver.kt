package com.kishan.kavach.automation.external

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.app.KavachApp
import com.kishan.kavach.automation.external.ExternalAutomationContract as C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Exported on purpose (Tasker/MacroDroid are third-party apps) but every request is validated by [C.validate]. */
class ExternalAutomationReceiver : BroadcastReceiver() {
    companion object { @Volatile private var lastAccepted = 0L; private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as KavachApp).container
        val prefs = container.prefs
        val allowed = prefs.externalPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val sender = if (Build.VERSION.SDK_INT >= 34) sentFromPackage else null
        val reqId = intent.getStringExtra(C.EXTRA_REQUEST_ID)
        val cb = intent.getStringExtra(C.EXTRA_CALLBACK_PACKAGE)
        val now = System.currentTimeMillis()
        val d = synchronized(Companion) {
            C.validate(prefs.externalEnabled, prefs.externalToken(), intent.action, intent.getStringExtra(C.EXTRA_TOKEN),
                intent.getStringExtra(C.EXTRA_TASK), reqId, sender, allowed, Build.VERSION.SDK_INT >= 34, cb, lastAccepted, now)
                .also { if (it is C.Decision.Accept) lastAccepted = now }
        }
        if (d is C.Decision.Reject) { ExternalStatusSender.send(context, cb?.takeIf { it in allowed }, reqId ?: "", "REJECTED", d.reason); return }
        val text = intent.getStringExtra(C.EXTRA_TASK)!!.trim()
        val pending = goAsync()
        scope.launch {
            try {
                val taskText = if (intent.action == C.ACTION_RUN_CHAT) AppContainer.CHAT_PREFIX + text else text
                val id = container.taskManager.submitAndGetId(taskText, "external")
                container.externalPending[id] = reqId!! to cb
                ExternalStatusSender.send(context, cb, reqId, "ACCEPTED", "task #$id queued")
            } finally { pending.finish() }
        }
    }
}

object ExternalStatusSender {
    /** Explicit broadcast to a user-approved package only. No implicit broadcast ever carries task data. */
    fun send(ctx: Context, callbackPackage: String?, requestId: String, status: String, message: String) {
        if (callbackPackage.isNullOrBlank()) return
        val allowed = (ctx.applicationContext as KavachApp).container.prefs.externalPackages.split(",").map { it.trim() }
        if (callbackPackage !in allowed) return
        ctx.sendBroadcast(Intent(C.ACTION_STATUS).setPackage(callbackPackage)
            .putExtra(C.EXTRA_REQUEST_ID, requestId).putExtra(C.EXTRA_STATUS, status).putExtra(C.EXTRA_MESSAGE, message.take(300)))
    }
}
