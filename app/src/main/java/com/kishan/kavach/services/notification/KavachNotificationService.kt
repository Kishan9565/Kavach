package com.kishan.kavach.services.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kishan.kavach.app.AccessHost
import com.kishan.kavach.app.AccessHostProvider
import com.kishan.kavach.domain.tools.Guard
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Optional Notification Access. Does three things, all local:
 *  1. keeps recent notifications in memory (NotificationStore) for summaries,
 *  2. feeds notification-trigger automations,
 *  3. WhatsApp auto-reply to explicitly allow-listed 1:1 contacts through WhatsApp's own Reply action.
 * Money-app and OTP-like notifications are never stored.
 */
class KavachNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seen = ArrayDeque<String>()
    private val lastReply = HashMap<String, String>()
    private val host: AccessHost? get() = (application as? AccessHostProvider)?.host

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationListenerHealth.connected = true
    }

    override fun onListenerDisconnected() {
        NotificationListenerHealth.connected = false
        requestRebind(ComponentName(this, KavachNotificationService::class.java))
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { NotificationStore.remove(sbn.key) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val hst = host ?: return
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val sensitive = Guard.isMoneyApp(sbn.packageName) || Guard.checkUserText(text) != null || Regex("""(?i)\botp\b|\b\d{4,8}\b is your""").containsMatchIn(text)
        if (!sensitive) {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
            val stored = StoredNotification(sbn.key, sbn.packageName, label, title, text, sbn.postTime)
            NotificationStore.add(stored)
            hst.onNotification(stored)
        }
        if (sbn.packageName == "com.whatsapp" || sbn.packageName == "com.whatsapp.w4b") maybeAutoReply(hst, sbn, title, text)
    }

    private fun maybeAutoReply(hst: AccessHost, sbn: StatusBarNotification, title: String, text: String) {
        val prefs = hst.prefs
        if (!prefs.enabled || text.isBlank()) return
        val n = sbn.notification
        if (n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return          // never groups
        val name = title.replace(Regex("""\s*\(\d+ messages?\)$"""), "").trim()
        if (name.equals("WhatsApp", ignoreCase = true)) return
        if (name.lowercase() !in prefs.allowedList()) return                                // exact-name allowlist
        if (text.startsWith("🤖") || text == lastReply[name]) return                        // don't answer ourselves
        val action = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return
        val key = "$name|$text|${n.`when`}"
        synchronized(seen) {
            if (key in seen) return
            seen.addLast(key); if (seen.size > 50) seen.removeFirst()
        }
        if (ReplyPolicy.isRisky(text)) return                                               // money/OTP/link/emergency: human only
        if (!ReplyPolicy.allowNow(name, System.currentTimeMillis())) return

        scope.launch {
            delay(Random.nextLong(4_000, 9_000))                                            // human-like delay
            val raw = runCatching {
                if (hst.llm.isReady) hst.llm.generate(ReplyPolicy.systemPrompt(prefs.ownerName), ReplyPolicy.userPrompt(name, text)) else null
            }.getOrNull()
            if (!prefs.enabled) return@launch                                               // kill switch re-check
            val reply = ReplyPolicy.finalize(raw, prefs.tagReplies)
            lastReply[name] = reply
            send(action, reply)
        }
    }

    private fun send(a: Notification.Action, reply: String) {
        val inputs = a.remoteInputs ?: return
        val intent = Intent()
        val bundle = Bundle()
        inputs.forEach { bundle.putCharSequence(it.resultKey, reply) }
        RemoteInput.addResultsToIntent(inputs, intent, bundle)
        runCatching { a.actionIntent.send(this, 0, intent) }
    }
}
