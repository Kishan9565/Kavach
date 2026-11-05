package com.kishan.kavach.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes") data class NoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val text: String, val createdAt: Long, val updatedAt: Long)
@Entity(tableName = "memories") data class MemoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val createdAt: Long)
@Entity(tableName = "messages") data class MessageEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val role: String, val text: String, val taskId: Long?, val createdAt: Long)
@Entity(tableName = "tasks") data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val request: String, val status: String, val progress: Int,
    val currentAction: String, val resultMessage: String, val source: String, val createdAt: Long, val finishedAt: Long?,
)
@Entity(tableName = "task_events", indices = [Index("taskId")]) data class TaskEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val taskId: Long, val type: String, val text: String, val time: Long)
@Entity(tableName = "reminders") data class ReminderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val triggerAt: Long, val done: Boolean)
@Entity(tableName = "todos") data class TodoEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val done: Boolean, val dueAt: Long?, val createdAt: Long)
@Entity(tableName = "automations") data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val enabled: Boolean, val triggerType: String, val triggerParam: String,
    val conditions: String, val actionText: String, val createdAt: Long, val lastRunAt: Long?, val lastResult: String,
)

@Dao interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun observe(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") suspend fun all(): List<NoteEntity>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: Long): NoteEntity?
    @Insert suspend fun insert(e: NoteEntity): Long
    @Update suspend fun update(e: NoteEntity): Int
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long): Int
}

@Dao interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC") fun observe(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories ORDER BY createdAt DESC") suspend fun all(): List<MemoryEntity>
    @Insert suspend fun insert(e: MemoryEntity): Long
    @Query("UPDATE memories SET text = :text WHERE id = :id") suspend fun update(id: Long, text: String): Int
    @Query("DELETE FROM memories WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("DELETE FROM memories") suspend fun clear(): Int
}

@Dao interface MessageDao {
    @Query("SELECT * FROM (SELECT * FROM messages ORDER BY id DESC LIMIT 200) ORDER BY id ASC") fun observe(): Flow<List<MessageEntity>>
    @Insert suspend fun insert(e: MessageEntity): Long
    @Query("DELETE FROM messages") suspend fun clear(): Int
}

@Dao interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC LIMIT 100") fun observe(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: Long): TaskEntity?
    @Insert suspend fun insert(e: TaskEntity): Long
    @Update suspend fun update(e: TaskEntity): Int
    @Query("UPDATE tasks SET status = 'FAILED', resultMessage = :msg, finishedAt = :now, currentAction = '' WHERE status IN ('PENDING','RUNNING','WAITING_CONFIRMATION')")
    suspend fun failInterrupted(msg: String, now: Long): Int
    @Query("DELETE FROM tasks WHERE status IN ('COMPLETED','FAILED','CANCELLED')") suspend fun clearFinished(): Int
    @Query("SELECT * FROM task_events WHERE taskId = :id ORDER BY id ASC") fun observeEvents(id: Long): Flow<List<TaskEventEntity>>
    @Query("SELECT * FROM task_events ORDER BY id DESC LIMIT :n") fun observeRecentEvents(n: Int): Flow<List<TaskEventEntity>>
    @Insert suspend fun insertEvent(e: TaskEventEntity): Long
    @Query("DELETE FROM task_events WHERE id NOT IN (SELECT id FROM task_events ORDER BY id DESC LIMIT 1500)") suspend fun pruneEvents(): Int
    @Query("DELETE FROM task_events WHERE taskId NOT IN (SELECT id FROM tasks)") suspend fun pruneOrphanEvents(): Int
}

@Dao interface ProductivityDao {
    @Insert suspend fun insertReminder(e: ReminderEntity): Long
    @Query("SELECT * FROM reminders WHERE (:includeDone = 1 OR done = 0) ORDER BY triggerAt ASC") suspend fun reminders(includeDone: Int): List<ReminderEntity>
    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC") fun observeReminders(): Flow<List<ReminderEntity>>
    @Query("UPDATE reminders SET done = 1 WHERE id = :id") suspend fun markReminderDone(id: Long): Int
    @Insert suspend fun insertTodo(e: TodoEntity): Long
    @Query("SELECT * FROM todos WHERE id = :id") suspend fun getTodo(id: Long): TodoEntity?
    @Update suspend fun updateTodo(e: TodoEntity): Int
    @Query("SELECT * FROM todos ORDER BY done ASC, createdAt DESC") suspend fun todos(): List<TodoEntity>
    @Query("SELECT * FROM todos ORDER BY done ASC, createdAt DESC") fun observeTodos(): Flow<List<TodoEntity>>
}

@Dao interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY id DESC") fun observe(): Flow<List<AutomationEntity>>
    @Query("SELECT * FROM automations") suspend fun all(): List<AutomationEntity>
    @Query("SELECT * FROM automations WHERE id = :id") suspend fun get(id: Long): AutomationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: AutomationEntity): Long
    @Query("DELETE FROM automations WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("UPDATE automations SET lastRunAt = :at, lastResult = :result WHERE id = :id") suspend fun recordRun(id: Long, at: Long, result: String): Int
}

@Database(
    entities = [NoteEntity::class, MemoryEntity::class, MessageEntity::class, TaskEntity::class, TaskEventEntity::class,
        ReminderEntity::class, TodoEntity::class, AutomationEntity::class],
    version = 1, exportSchema = false,
)
abstract class KavachDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun memories(): MemoryDao
    abstract fun messages(): MessageDao
    abstract fun tasks(): TaskDao
    abstract fun productivity(): ProductivityDao
    abstract fun automations(): AutomationDao
}
