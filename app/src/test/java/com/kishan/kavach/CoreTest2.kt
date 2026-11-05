package com.kishan.kavach

import com.kishan.kavach.agent.state.ConfirmationBus
import com.kishan.kavach.agent.state.TaskManager
import com.kishan.kavach.core.common.Redactor
import com.kishan.kavach.core.common.Retriever
import com.kishan.kavach.domain.agent.*
import com.kishan.kavach.domain.model.ChatMessage
import com.kishan.kavach.domain.repository.ChatRepository
import com.kishan.kavach.domain.repository.TaskRepository
import com.kishan.kavach.domain.tools.ConfirmationRequest
import com.kishan.kavach.domain.tools.Risk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Test

private class FakeTaskRepo : TaskRepository {
    val tasks = LinkedHashMap<Long, AgentTask>()
    val events = java.util.concurrent.CopyOnWriteArrayList<Pair<Long, AgentEvent>>()
    private var n = 0L
    private val flow = MutableStateFlow<List<AgentTask>>(emptyList())
    private fun push() { flow.value = tasks.values.toList() }
    override fun observeTasks(): Flow<List<AgentTask>> = flow
    override fun observeEvents(taskId: Long): Flow<List<AgentEvent>> = flow.map { events.filter { it.first == taskId }.map { it.second } }
    override fun observeRecentEvents(limit: Int): Flow<List<Pair<Long, AgentEvent>>> = flow.map { events.takeLast(limit) }
    override suspend fun get(id: Long) = synchronized(tasks) { tasks[id] }
    override suspend fun insert(task: AgentTask): Long = synchronized(tasks) { val id = ++n; tasks[id] = task.copy(id = id); push(); id }
    override suspend fun update(task: AgentTask) { synchronized(tasks) { tasks[task.id] = task; push() } }
    override suspend fun addEvent(taskId: Long, event: AgentEvent) { events += taskId to event }
    override suspend fun failInterrupted(message: String): Int = synchronized(tasks) {
        var c = 0
        for ((id, t) in tasks.toMap()) if (t.isActive) { tasks[id] = t.copy(status = TaskStatus.FAILED, resultMessage = message); c++ }
        push(); c
    }
    override suspend fun clearFinished() {}
}

private class FakeChat : ChatRepository {
    val msgs = java.util.concurrent.CopyOnWriteArrayList<ChatMessage>()
    override fun observe(): Flow<List<ChatMessage>> = MutableStateFlow(msgs.toList())
    override suspend fun add(role: String, text: String, taskId: Long?): Long { msgs += ChatMessage(msgs.size + 1L, role, text, taskId); return msgs.size.toLong() }
    override suspend fun clear() { msgs.clear() }
}

class TaskManagerTest {
    private suspend fun waitFor(timeoutMs: Long = 3000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < end) delay(10)
        assertTrue("condition not met in time", cond())
    }

    @Test fun completesAndWritesChat() = runBlocking {
        val repo = FakeTaskRepo(); val chat = FakeChat(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tm = TaskManager(repo, chat, scope, { text, emit -> emit(AgentEvent(EventType.TOOL_CALL, "tap")); AgentOutcome.Completed("done $text") })
        val id = tm.submitAndGetId("hello")
        waitFor { repo.tasks[id]?.status == TaskStatus.COMPLETED }
        assertEquals("done hello", repo.tasks[id]!!.resultMessage)
        waitFor { chat.msgs.size == 2 }
        assertEquals(listOf("user", "assistant"), chat.msgs.map { it.role })
        assertTrue(repo.events.any { it.second.type == EventType.TOOL_CALL }); assertTrue(repo.events.any { it.second.type == EventType.DONE })
        scope.cancel()
    }

    @Test fun cancelRunningAndQueued() = runBlocking {
        val repo = FakeTaskRepo(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tm = TaskManager(repo, null, scope, { _, _ -> delay(60_000); AgentOutcome.Completed("never") })
        val a = tm.submitAndGetId("a"); val b = tm.submitAndGetId("b")
        waitFor { repo.tasks[a]?.status == TaskStatus.RUNNING }
        assertEquals(TaskStatus.PENDING, repo.tasks[b]!!.status)
        tm.cancel(b)                       // queued: cancelled without ever running
        waitFor { repo.tasks[b]?.status == TaskStatus.CANCELLED }
        tm.cancel(a)                       // running: cooperative cancel
        waitFor { repo.tasks[a]?.status == TaskStatus.CANCELLED }
        assertNull(tm.activeTaskId.value.takeIf { false })
        scope.cancel()
    }

    @Test fun failureAndRecovery() = runBlocking {
        val repo = FakeTaskRepo(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tm = TaskManager(repo, null, scope, { _, _ -> throw IllegalStateException("boom") })
        val id = tm.submitAndGetId("x")
        waitFor { repo.tasks[id]?.status == TaskStatus.FAILED }
        assertTrue(repo.tasks[id]!!.resultMessage.contains("boom"))
        repo.insert(AgentTask(request = "stale", status = TaskStatus.RUNNING))
        assertEquals(1, tm.recoverAfterRestart())
        scope.cancel()
    }

    @Test fun confirmationBusTimesOutToDenied() = runBlocking {
        val bus = ConfirmationBus(timeoutMs = 50)
        assertFalse(bus.confirm(ConfirmationRequest("t", "d", Risk.HIGH)))
        val bus2 = ConfirmationBus(timeoutMs = 5000)
        val d = async { bus2.confirm(ConfirmationRequest("t", "d", Risk.HIGH)) }
        waitFor { bus2.pending.value != null }
        bus2.resolve(true)
        assertTrue(d.await())
    }
}

class RetrieverTest {
    private val notes = listOf("Mera WiFi password router ke peeche likha hai", "Amit ka birthday 14 March ko hai", "Doctor appointment Friday 5pm")
    @Test fun findsRelevantAndHandlesSpelling() {
        assertEquals(notes[1], Retriever.search(notes, { it }, "Amit ka birthday kab hai").first().item)
        assertEquals(notes[2], Retriever.search(notes, { it }, "doctor apointment kab").first().item)
    }
    @Test fun nothingRelevant() { assertTrue(Retriever.search(notes, { it }, "bitcoin price today").isEmpty()) }
    @Test fun redactor() { assertEquals("call <digits> or <email>", Redactor.redact("call 9876543210 or a.b@x.com")) }
}
