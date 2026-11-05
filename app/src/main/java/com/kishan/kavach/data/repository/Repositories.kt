package com.kishan.kavach.data.repository

import com.kishan.kavach.core.common.Retriever
import com.kishan.kavach.data.local.*
import com.kishan.kavach.domain.agent.AgentEvent
import com.kishan.kavach.domain.agent.AgentTask
import com.kishan.kavach.domain.agent.EventType
import com.kishan.kavach.domain.agent.TaskStatus
import com.kishan.kavach.domain.model.*
import com.kishan.kavach.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl(private val dao: NoteDao) : NoteRepository {
    private fun NoteEntity.toModel() = Note(id, title, text, createdAt, updatedAt)
    override fun observe(): Flow<List<Note>> = dao.observe().map { l -> l.map { it.toModel() } }
    override suspend fun all() = dao.all().map { it.toModel() }
    override suspend fun add(title: String, text: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(NoteEntity(title = title.trim().ifBlank { "Note" }.take(80), text = text.trim(), createdAt = now, updatedAt = now))
    }
    override suspend fun update(id: Long, title: String?, text: String?): Boolean {
        val e = dao.get(id) ?: return false
        return dao.update(e.copy(title = title?.take(80) ?: e.title, text = text ?: e.text, updatedAt = System.currentTimeMillis())) > 0
    }
    override suspend fun delete(id: Long) = dao.delete(id) > 0
    override suspend fun search(query: String, k: Int): List<Note> =
        Retriever.search(all(), { it.title + " " + it.text }, query, k).map { it.item }
}

class MemoryRepositoryImpl(private val dao: MemoryDao) : MemoryRepository {
    private fun MemoryEntity.toModel() = MemoryItem(id, text, createdAt)
    override fun observe(): Flow<List<MemoryItem>> = dao.observe().map { l -> l.map { it.toModel() } }
    override suspend fun add(text: String) = dao.insert(MemoryEntity(text = text.trim().take(500), createdAt = System.currentTimeMillis()))
    override suspend fun update(id: Long, text: String) { dao.update(id, text.trim().take(500)) }
    override suspend fun delete(id: Long) { dao.delete(id) }
    override suspend fun clear() { dao.clear() }
    override suspend fun search(query: String, k: Int): List<MemoryItem> =
        Retriever.search(dao.all().map { it.toModel() }, { it.text }, query, k).map { it.item }
}

class ChatRepositoryImpl(private val dao: MessageDao) : ChatRepository {
    override fun observe(): Flow<List<ChatMessage>> = dao.observe().map { l -> l.map { ChatMessage(it.id, it.role, it.text, it.taskId, it.createdAt) } }
    override suspend fun add(role: String, text: String, taskId: Long?) = dao.insert(MessageEntity(role = role, text = text.take(4000), taskId = taskId, createdAt = System.currentTimeMillis()))
    override suspend fun clear() { dao.clear() }
}

class TaskRepositoryImpl(private val dao: TaskDao) : TaskRepository {
    private fun TaskEntity.toModel() = AgentTask(id, request, TaskStatus.valueOf(status), progress, currentAction, resultMessage, source, createdAt, finishedAt)
    private fun AgentTask.toEntity() = TaskEntity(id, request, status.name, progress, currentAction, resultMessage, source, createdAt, finishedAt)
    private fun TaskEventEntity.toModel() = AgentEvent(runCatching { EventType.valueOf(type) }.getOrDefault(EventType.INFO), text, time)

    override fun observeTasks(): Flow<List<AgentTask>> = dao.observe().map { l -> l.map { it.toModel() } }
    override fun observeEvents(taskId: Long): Flow<List<AgentEvent>> = dao.observeEvents(taskId).map { l -> l.map { it.toModel() } }
    override fun observeRecentEvents(limit: Int): Flow<List<Pair<Long, AgentEvent>>> = dao.observeRecentEvents(limit).map { l -> l.map { it.taskId to it.toModel() } }
    override suspend fun get(id: Long) = dao.get(id)?.toModel()
    override suspend fun insert(task: AgentTask) = dao.insert(task.toEntity().copy(id = 0))
    override suspend fun update(task: AgentTask) { dao.update(task.toEntity()) }
    override suspend fun addEvent(taskId: Long, event: AgentEvent) { dao.insertEvent(TaskEventEntity(taskId = taskId, type = event.type.name, text = event.text.take(400), time = event.time)); dao.pruneEvents() }
    override suspend fun failInterrupted(message: String) = dao.failInterrupted(message, System.currentTimeMillis())
    override suspend fun clearFinished() { dao.clearFinished(); dao.pruneOrphanEvents() }
}

class ProductivityRepositoryImpl(private val dao: ProductivityDao) : ProductivityRepository {
    override suspend fun addReminder(text: String, triggerAt: Long) = dao.insertReminder(ReminderEntity(text = text.take(200), triggerAt = triggerAt, done = false))
    override suspend fun reminders(includeDone: Boolean) = dao.reminders(if (includeDone) 1 else 0).map { Reminder(it.id, it.text, it.triggerAt, it.done) }
    override suspend fun markReminderDone(id: Long) { dao.markReminderDone(id) }
    override fun observeReminders(): Flow<List<Reminder>> = dao.observeReminders().map { l -> l.map { Reminder(it.id, it.text, it.triggerAt, it.done) } }
    override suspend fun addTodo(title: String, dueAt: Long?) = dao.insertTodo(TodoEntity(title = title.take(150), done = false, dueAt = dueAt, createdAt = System.currentTimeMillis()))
    override suspend fun updateTodo(id: Long, title: String?, done: Boolean?): Boolean {
        val e = dao.getTodo(id) ?: return false
        return dao.updateTodo(e.copy(title = title?.take(150) ?: e.title, done = done ?: e.done)) > 0
    }
    override suspend fun todos() = dao.todos().map { Todo(it.id, it.title, it.done, it.dueAt, it.createdAt) }
    override fun observeTodos(): Flow<List<Todo>> = dao.observeTodos().map { l -> l.map { Todo(it.id, it.title, it.done, it.dueAt, it.createdAt) } }
}

class AutomationRepositoryImpl(private val dao: AutomationDao) : AutomationRepository {
    private fun AutomationEntity.toModel() = Automation(id, name, enabled, runCatching { TriggerType.valueOf(triggerType) }.getOrDefault(TriggerType.MANUAL), triggerParam, conditions, actionText, createdAt, lastRunAt, lastResult)
    private fun Automation.toEntity() = AutomationEntity(id, name, enabled, triggerType.name, triggerParam, conditions, actionText, createdAt, lastRunAt, lastResult)
    override fun observe(): Flow<List<Automation>> = dao.observe().map { l -> l.map { it.toModel() } }
    override suspend fun all() = dao.all().map { it.toModel() }
    override suspend fun get(id: Long) = dao.get(id)?.toModel()
    override suspend fun save(a: Automation) = dao.upsert(a.toEntity())
    override suspend fun delete(id: Long) { dao.delete(id) }
    override suspend fun recordRun(id: Long, at: Long, result: String) { dao.recordRun(id, at, result.take(200)) }
}
