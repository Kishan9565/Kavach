package com.kishan.kavach.services.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class StoredNotification(val key: String, val pkg: String, val app: String, val title: String, val text: String, val time: Long)

/**
 * In-memory ring buffer of recent notifications. Deliberately NOT persisted: notification content is
 * private, so it vanishes when the process dies. Texts are capped.
 */
object NotificationStore {
    private const val MAX = 60
    private val _items = MutableStateFlow<List<StoredNotification>>(emptyList())
    val items: StateFlow<List<StoredNotification>> = _items

    fun add(n: StoredNotification) {
        val cur = _items.value.filterNot { it.key == n.key }
        _items.value = (cur + n.copy(title = n.title.take(80), text = n.text.take(200))).takeLast(MAX)
    }
    fun remove(key: String) { _items.value = _items.value.filterNot { it.key == key } }
    fun clear() { _items.value = emptyList() }

    /** Compact text for tools / Gemma. */
    fun summaryText(limit: Int = 15): String {
        val l = _items.value.takeLast(limit).reversed()
        if (l.isEmpty()) return "No notifications captured yet."
        return l.joinToString("\n") { "- ${it.app}: ${it.title}: ${it.text}".take(160) }
    }
}
