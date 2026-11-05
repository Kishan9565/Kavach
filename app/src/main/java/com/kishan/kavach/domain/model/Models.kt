package com.kishan.kavach.domain.model

data class Note(val id: Long = 0, val title: String, val text: String, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
data class MemoryItem(val id: Long = 0, val text: String, val createdAt: Long = System.currentTimeMillis())
data class ChatMessage(val id: Long = 0, val role: String, val text: String, val taskId: Long? = null, val createdAt: Long = System.currentTimeMillis()) {
    val fromUser get() = role == "user"
}
data class Reminder(val id: Long = 0, val text: String, val triggerAt: Long, val done: Boolean = false)
data class Todo(val id: Long = 0, val title: String, val done: Boolean = false, val dueAt: Long? = null, val createdAt: Long = System.currentTimeMillis())

enum class TriggerType { MANUAL, SCHEDULE, NOTIFICATION }

/**
 * Automation = Trigger -> Conditions -> Actions -> Result (spec section 14).
 * triggerParam: SCHEDULE = repeat interval in minutes (>=15, WorkManager limit); NOTIFICATION = "package|keyword".
 * conditions: comma separated: "charging", "wifi", "battery>30", "hours:8-22".
 * actionText: a normal task sentence; it goes through the same router, safety validator and confirmations.
 */
data class Automation(
    val id: Long = 0, val name: String, val enabled: Boolean = false,
    val triggerType: TriggerType = TriggerType.MANUAL, val triggerParam: String = "",
    val conditions: String = "", val actionText: String,
    val createdAt: Long = System.currentTimeMillis(), val lastRunAt: Long? = null, val lastResult: String = "",
)
