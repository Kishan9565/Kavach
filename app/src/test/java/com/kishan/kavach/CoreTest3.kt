package com.kishan.kavach

import com.kishan.kavach.agent.state.*
import com.kishan.kavach.automation.external.ExternalAutomationContract as C
import com.kishan.kavach.domain.agent.*
import com.kishan.kavach.domain.model.*
import com.kishan.kavach.domain.repository.AutomationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test

class ConditionTest {
    private val s = DeviceState(charging = true, batteryPercent = 55, wifi = false, hour = 23)
    @Test fun evaluates() {
        assertNull(ConditionEvaluator.failing("", s)); assertNull(ConditionEvaluator.failing("charging, battery>30, hours:22-6", s))
        assertEquals("wifi", ConditionEvaluator.failing("charging,wifi", s)); assertEquals("battery<20", ConditionEvaluator.failing("battery<20", s))
        assertEquals("hours:8-22", ConditionEvaluator.failing("hours:8-22", s)); assertEquals("nonsense", ConditionEvaluator.failing("nonsense", s))
        assertTrue(ConditionEvaluator.isValid("charging,wifi,battery>20,hours:8-22")); assertFalse(ConditionEvaluator.isValid("moon"))
    }
}

class ExternalContractTest {
    private fun v(enabled: Boolean = true, token: String? = "tok", action: String? = C.ACTION_RUN_TASK, task: String? = "open youtube", req: String? = "r1",
                  sender: String? = "net.dinglisch.android.taskerm", verifiable: Boolean = true, cb: String? = null, last: Long = 0, now: Long = 100_000) =
        C.validate(enabled, "tok", action, token, task, req, sender, listOf("net.dinglisch.android.taskerm"), verifiable, cb, last, now)
    @Test fun acceptsGoodRequest() { assertEquals(C.Decision.Accept, v()) }
    @Test fun rejectsEverythingElse() {
        assertTrue(v(enabled = false) is C.Decision.Reject); assertTrue(v(token = "bad") is C.Decision.Reject); assertTrue(v(token = null) is C.Decision.Reject)
        assertTrue(v(action = "evil") is C.Decision.Reject); assertTrue(v(sender = "com.evil") is C.Decision.Reject); assertTrue(v(sender = null) is C.Decision.Reject)
        assertTrue(v(req = "bad id!") is C.Decision.Reject); assertTrue(v(task = " ") is C.Decision.Reject); assertTrue(v(task = "x".repeat(501)) is C.Decision.Reject)
        assertTrue(v(cb = "com.evil") is C.Decision.Reject); assertTrue(v(last = 99_000) is C.Decision.Reject)
    }
    @Test fun unverifiableSenderStillNeedsToken() { assertEquals(C.Decision.Accept, v(sender = null, verifiable = false)); assertTrue(v(sender = null, verifiable = false, token = "x") is C.Decision.Reject) }
}

private class FakeAutoRepo(val items: MutableList<Automation>) : AutomationRepository {
    val runs = ArrayList<Pair<Long, String>>()
    override fun observe(): Flow<List<Automation>> = flowOf(items)
    override suspend fun all() = items.toList()
    override suspend fun get(id: Long) = items.firstOrNull { it.id == id }
    override suspend fun save(a: Automation) = a.id
    override suspend fun delete(id: Long) { items.removeAll { it.id == id } }
    override suspend fun recordRun(id: Long, at: Long, result: String) { runs += id to result }
}

class AutomationEngineTest {
    @Test fun conditionsAndNotificationTrigger() = runBlocking {
        val repo = FakeAutoRepo(mutableListOf(
            Automation(1, "wifi only", true, TriggerType.MANUAL, "", "wifi", "battery batao"),
            Automation(2, "otp watcher", true, TriggerType.NOTIFICATION, "com.whatsapp|rahul", "", "note karo hi"),
            Automation(3, "disabled", false, TriggerType.MANUAL, "", "", "x")))
        val taskRepoSubmitted = ArrayList<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tm = TaskManager(object : com.kishan.kavach.domain.repository.TaskRepository {
            var n = 0L
            override fun observeTasks() = flowOf(emptyList<AgentTask>())
            override fun observeEvents(taskId: Long) = flowOf(emptyList<AgentEvent>())
            override fun observeRecentEvents(limit: Int) = flowOf(emptyList<Pair<Long, AgentEvent>>())
            override suspend fun get(id: Long): AgentTask? = null
            override suspend fun insert(task: AgentTask): Long { taskRepoSubmitted += task.request; return ++n }
            override suspend fun update(task: AgentTask) {}
            override suspend fun addEvent(taskId: Long, event: AgentEvent) {}
            override suspend fun failInterrupted(message: String) = 0
            override suspend fun clearFinished() {}
        }, null, scope, { _, _ -> AgentOutcome.Completed("") })
        var now = 1_000_000L
        val engine = AutomationEngine(repo, tm, { DeviceState(true, 80, false, 12) }, { now })
        assertTrue(engine.run(1, manual = false).contains("condition 'wifi'"))
        assertEquals("skipped: disabled", engine.run(3, manual = false))
        engine.onNotification("com.whatsapp", "Rahul: hi"); engine.onNotification("com.whatsapp", "Rahul: hi again")   // cooldown
        engine.onNotification("com.other", "Rahul: hi"); engine.onNotification("com.whatsapp", "Amit: hi")
        assertEquals(listOf("note karo hi"), taskRepoSubmitted)
        now += 61_000; engine.onNotification("com.whatsapp", "rahul again")
        assertEquals(2, taskRepoSubmitted.size)
        scope.cancel()
    }
}
