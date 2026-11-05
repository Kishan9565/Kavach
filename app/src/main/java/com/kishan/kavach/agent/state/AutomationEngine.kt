package com.kishan.kavach.agent.state

import com.kishan.kavach.domain.agent.AgentTask
import com.kishan.kavach.domain.agent.TaskStatus
import com.kishan.kavach.domain.model.Automation
import com.kishan.kavach.domain.model.TriggerType
import com.kishan.kavach.domain.repository.AutomationRepository
import java.util.concurrent.ConcurrentHashMap

/** Snapshot of the device used to evaluate automation conditions. */
data class DeviceState(val charging: Boolean, val batteryPercent: Int, val wifi: Boolean, val hour: Int)

/** Conditions: comma separated: charging, wifi, battery>30, battery<20, hours:8-22. All must hold. */
object ConditionEvaluator {
    /** Returns null when all conditions hold, else a human reason. Unknown condition names fail closed. */
    fun failing(conditions: String, s: DeviceState): String? {
        for (raw in conditions.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }) {
            val ok = when {
                raw == "charging" -> s.charging
                raw == "wifi" -> s.wifi
                raw.startsWith("battery>") -> raw.removePrefix("battery>").toIntOrNull()?.let { s.batteryPercent > it } ?: false
                raw.startsWith("battery<") -> raw.removePrefix("battery<").toIntOrNull()?.let { s.batteryPercent < it } ?: false
                raw.startsWith("hours:") -> raw.removePrefix("hours:").split("-").map { it.toIntOrNull() }.let { p ->
                    if (p.size == 2 && p[0] != null && p[1] != null) { val a = p[0]!!; val b = p[1]!!; if (a <= b) s.hour in a until b else s.hour >= a || s.hour < b } else false }
                else -> false
            }
            if (!ok) return raw
        }
        return null
    }
    fun isValid(conditions: String) = conditions.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.all {
        it == "charging" || it == "wifi" || Regex("battery[<>]\\d{1,3}").matches(it) || Regex("hours:\\d{1,2}-\\d{1,2}").matches(it)
    }
}

/**
 * Trigger -> Conditions -> Action -> Result. The action is an ordinary task sentence, so it passes through the same
 * router, safety validator and confirmation dialogs as anything typed by the user (nothing is bypassed).
 */
class AutomationEngine(
    private val repo: AutomationRepository,
    private val tasks: TaskManager,
    private val state: () -> DeviceState,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = ConcurrentHashMap<Long, Long>()      // taskId -> automationId
    private val lastNotifFire = ConcurrentHashMap<Long, Long>()

    suspend fun run(id: Long, manual: Boolean): String {
        val a = repo.get(id) ?: return "automation not found"
        if (!manual && !a.enabled) return record(a, "skipped: disabled")
        ConditionEvaluator.failing(a.conditions, state())?.let { return record(a, "skipped: condition '$it' not met") }
        val taskId = tasks.submitAndGetId(a.actionText, "automation")
        running[taskId] = a.id
        return record(a, "started task #$taskId")
    }

    private suspend fun record(a: Automation, msg: String): String { repo.recordRun(a.id, clock(), msg); return msg }

    /** Notification triggers: "package|keyword" (either side may be empty). 60 s cooldown per automation. */
    suspend fun onNotification(pkg: String, text: String) {
        for (a in repo.all()) {
            if (!a.enabled || a.triggerType != TriggerType.NOTIFICATION) continue
            val (p, k) = a.triggerParam.split("|", limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
            if (p.isEmpty() && k.isEmpty()) continue
            if (p.isNotEmpty() && !pkg.contains(p, true)) continue
            if (k.isNotEmpty() && !text.contains(k, true)) continue
            val now = clock()
            if (now - (lastNotifFire[a.id] ?: 0L) < 60_000) continue
            lastNotifFire[a.id] = now
            run(a.id, manual = false)
        }
    }

    suspend fun onTaskFinished(task: AgentTask, message: String) {
        val autoId = running.remove(task.id) ?: return
        val verdict = when (task.status) { TaskStatus.COMPLETED -> "ok"; TaskStatus.CANCELLED -> "cancelled"; else -> "failed" }
        repo.recordRun(autoId, clock(), "$verdict: ${message.take(120)}")
    }
}
