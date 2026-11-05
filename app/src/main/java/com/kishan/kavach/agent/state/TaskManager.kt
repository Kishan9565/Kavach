package com.kishan.kavach.agent.state

import com.kishan.kavach.domain.agent.*
import com.kishan.kavach.domain.repository.ChatRepository
import com.kishan.kavach.domain.repository.TaskRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Task pipeline: PENDING -> RUNNING -> (WAITING_CONFIRMATION) -> COMPLETED | FAILED | CANCELLED.
 * One task at a time (the screen and the model are single-user resources), others queue.
 * State lives in the repository, so the UI, the bubble and automations all see the same truth and
 * nothing depends on a ViewModel lifetime.
 */
class TaskManager(
    private val repo: TaskRepository,
    private val chat: ChatRepository?,
    private val scope: CoroutineScope,
    /** Runs a request end-to-end. Production: TaskRunner::run. */
    private val runner: suspend (text: String, emit: (AgentEvent) -> Unit) -> AgentOutcome,
    private val onFinished: (AgentTask, String) -> Unit = { _, _ -> },
) {
    companion object { val QUIET_ECHO = setOf("automation", "external", "skill"); val QUIET_RESULT = setOf("automation", "external") }

    private val queue = Channel<Long>(Channel.UNLIMITED)
    private var current: Pair<Long, Job>? = null
    private val _activeId = MutableStateFlow<Long?>(null)
    val activeTaskId: StateFlow<Long?> = _activeId

    val tasks: Flow<List<AgentTask>> get() = repo.observeTasks()
    fun events(taskId: Long): Flow<List<AgentEvent>> = repo.observeEvents(taskId)

    init { scope.launch { for (id in queue) runOne(id) } }

    suspend fun recoverAfterRestart(): Int = repo.failInterrupted("Interrupted because the app restarted. Tap Retry to run it again.")

    /** Fire-and-forget submit (UI, bubble, automations). */
    fun submit(text: String, source: String = "chat") {
        require(text.isNotBlank()) { "empty task" }
        scope.launch { submitAndGetId(text, source) }
    }

    /** Suspending variant that returns the real id. */
    suspend fun submitAndGetId(text: String, source: String = "chat"): Long {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "empty task" }
        val id = repo.insert(AgentTask(request = clean.take(500), source = source))
        if (chat != null && source !in QUIET_ECHO) chat.add("user", clean, id)
        queue.send(id)
        return id
    }

    fun cancel(taskId: Long) {
        val c = current
        if (c != null && c.first == taskId) c.second.cancel()
        else scope.launch {
            repo.get(taskId)?.takeIf { it.isActive }?.let {
                repo.update(it.copy(status = TaskStatus.CANCELLED, currentAction = "", finishedAt = now()))
            }
        }
    }

    fun cancelActive() { current?.second?.cancel() }

    fun retry(taskId: Long) {
        scope.launch { repo.get(taskId)?.let { submitAndGetId(it.request, it.source) } }
    }

    fun setWaitingConfirmation(waiting: Boolean) {
        val id = _activeId.value ?: return
        scope.launch {
            val t = repo.get(id) ?: return@launch
            if (waiting && t.status == TaskStatus.RUNNING) repo.update(t.copy(status = TaskStatus.WAITING_CONFIRMATION))
            if (!waiting && t.status == TaskStatus.WAITING_CONFIRMATION) repo.update(t.copy(status = TaskStatus.RUNNING))
        }
    }

    private fun now() = System.currentTimeMillis()

    private suspend fun runOne(id: Long) {
        val task = repo.get(id) ?: return
        if (task.status != TaskStatus.PENDING) return          // cancelled while queued
        var t = task.copy(status = TaskStatus.RUNNING, progress = 5, currentAction = "Starting")
        repo.update(t)
        _activeId.value = id
        var toolCalls = 0
        val job = scope.launch {
            var outcome: AgentOutcome? = null
            var cancelled = false
            // Events are persisted sequentially by one consumer: no racing writes, and nothing lost on cancel.
            val events = Channel<AgentEvent>(Channel.UNLIMITED)
            val consumer = launch(NonCancellable) {
                for (ev in events) {
                    repo.addEvent(id, ev)
                    if (ev.type == EventType.TOOL_CALL) {
                        toolCalls++
                        t = t.copy(progress = minOf(95, 10 + 12 * toolCalls), currentAction = ev.text.take(80))
                        repo.update(t)
                    }
                }
            }
            try {
                outcome = runner(t.request) { ev -> events.trySend(ev) }
            } catch (e: CancellationException) {
                cancelled = true
            } catch (e: Exception) {
                outcome = AgentOutcome.Failed("${e.javaClass.simpleName}: ${e.message}".take(200))
            }
            events.close()
            withContext(NonCancellable) { consumer.join(); finish(id, t, outcome, cancelled) }
        }
        current = id to job
        job.join()
        current = null
        _activeId.value = null
    }

    private suspend fun finish(id: Long, t: AgentTask, outcome: AgentOutcome?, cancelled: Boolean) {
        val fresh = repo.get(id) ?: t
        val (status, msg) = when {
            cancelled -> TaskStatus.CANCELLED to "Ruk gaya (Stop)."
            outcome is AgentOutcome.Completed -> TaskStatus.COMPLETED to outcome.message
            outcome is AgentOutcome.Answer -> TaskStatus.COMPLETED to outcome.message
            outcome is AgentOutcome.Blocked -> TaskStatus.FAILED to outcome.reason
            outcome is AgentOutcome.Failed -> TaskStatus.FAILED to outcome.reason
            else -> TaskStatus.FAILED to "Unknown error"
        }
        val done = fresh.copy(status = status, progress = if (status == TaskStatus.COMPLETED) 100 else fresh.progress,
            currentAction = "", resultMessage = msg, finishedAt = now())
        repo.update(done)
        repo.addEvent(id, AgentEvent(when (status) { TaskStatus.COMPLETED -> EventType.DONE; TaskStatus.CANCELLED -> EventType.CANCELLED; else -> EventType.FAILED }, msg.take(300)))
        if (chat != null && done.source !in QUIET_RESULT) {
            chat.add("assistant", if (status == TaskStatus.FAILED && outcome !is AgentOutcome.Blocked) "⚠ $msg" else msg, id)
        }
        onFinished(done, msg)
    }
}
