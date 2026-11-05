package com.kishan.kavach.domain.repository

import com.kishan.kavach.domain.agent.AgentEvent
import com.kishan.kavach.domain.agent.AgentTask
import com.kishan.kavach.domain.model.*
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observe(): Flow<List<Note>>
    suspend fun all(): List<Note>
    suspend fun add(title: String, text: String): Long
    suspend fun update(id: Long, title: String?, text: String?): Boolean
    suspend fun delete(id: Long): Boolean
    suspend fun search(query: String, k: Int): List<Note>
}

interface MemoryRepository {
    fun observe(): Flow<List<MemoryItem>>
    suspend fun add(text: String): Long
    suspend fun update(id: Long, text: String)
    suspend fun delete(id: Long)
    suspend fun clear()
    suspend fun search(query: String, k: Int): List<MemoryItem>
}

interface ChatRepository {
    fun observe(): Flow<List<ChatMessage>>
    suspend fun add(role: String, text: String, taskId: Long? = null): Long
    suspend fun clear()
}

interface TaskRepository {
    fun observeTasks(): Flow<List<AgentTask>>
    fun observeEvents(taskId: Long): Flow<List<AgentEvent>>
    fun observeRecentEvents(limit: Int): Flow<List<Pair<Long, AgentEvent>>>
    suspend fun get(id: Long): AgentTask?
    suspend fun insert(task: AgentTask): Long
    suspend fun update(task: AgentTask)
    suspend fun addEvent(taskId: Long, event: AgentEvent)
    /** Marks tasks left PENDING/RUNNING/WAITING by a killed process as FAILED (never auto-reruns them). */
    suspend fun failInterrupted(message: String): Int
    suspend fun clearFinished()
}

interface ProductivityRepository {
    suspend fun addReminder(text: String, triggerAt: Long): Long
    suspend fun reminders(includeDone: Boolean): List<Reminder>
    suspend fun markReminderDone(id: Long)
    fun observeReminders(): Flow<List<Reminder>>
    suspend fun addTodo(title: String, dueAt: Long?): Long
    suspend fun updateTodo(id: Long, title: String?, done: Boolean?): Boolean
    suspend fun todos(): List<Todo>
    fun observeTodos(): Flow<List<Todo>>
}

interface AutomationRepository {
    fun observe(): Flow<List<Automation>>
    suspend fun all(): List<Automation>
    suspend fun get(id: Long): Automation?
    suspend fun save(a: Automation): Long
    suspend fun delete(id: Long)
    suspend fun recordRun(id: Long, at: Long, result: String)
}
