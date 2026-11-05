package com.kishan.kavach

import com.kishan.kavach.agent.executor.SkillOutcome
import com.kishan.kavach.agent.executor.SkillRunner
import com.kishan.kavach.agent.loop.AgentLoop
import com.kishan.kavach.agent.planner.MemoryProvider
import com.kishan.kavach.agent.planner.TaskRouter
import com.kishan.kavach.ai.parser.ParsedTurn
import com.kishan.kavach.ai.parser.ToolCallParser
import com.kishan.kavach.core.common.Json
import com.kishan.kavach.domain.agent.*
import com.kishan.kavach.domain.skills.BuiltInSkills
import com.kishan.kavach.domain.skills.SkillFormat
import com.kishan.kavach.domain.tools.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

private class Scripted(private val replies: List<String>, private val delayMs: Long = 0) : LlmClient {
    var i = 0
    val prompts = ArrayList<String>()
    override val isReady = true
    override suspend fun generate(system: String, user: String): String {
        prompts += user
        if (delayMs > 0) delay(delayMs)
        return replies[minOf(i++, replies.size - 1)]
    }
}

private object AllowAll : PermissionChecker { override fun isGranted(permission: String) = true }

private fun harness(tools: List<Tool>, confirm: Boolean = true): Pair<ToolRegistry, ToolExecutor> {
    val reg = ToolRegistry().also { r -> tools.forEach { r.register(it) } }
    val exec = ToolExecutor(reg, ToolPermissionManager(AllowAll), Guard,
        { object : Confirmer { override suspend fun confirm(request: ConfirmationRequest) = confirm } }, { })
    return reg to exec
}

private fun t(name: String, group: ToolGroup = ToolGroup.SCREEN, risk: Risk = Risk.LOW,
              params: List<ParamSpec> = emptyList(), ok: Boolean = true, handler: (Args) -> ToolResult = { ToolResult.ok("done $name") }) =
    SimpleTool(name, "test $name", group, params, risk) { a -> if (ok) handler(a) else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "nope") }

class JsonTest {
    @Test fun parsesForgivingJson() {
        val o = Json.parseObject("{'a': 1, \"b\": [1,2,], c: \"x\\n\", \"d\": 2.5, \"e\": true,}")!!
        assertEquals(1L, o["a"]); assertEquals(listOf(1L, 2L), o["b"]); assertEquals("x\n", o["c"]); assertEquals(2.5, o["d"]); assertEquals(true, o["e"])
    }
    @Test fun roundTrips() {
        val s = Json.stringify(mapOf("k" to "v\"q", "n" to 3, "l" to listOf(1, "a")))
        assertEquals(mapOf("k" to "v\"q", "n" to 3L, "l" to listOf(1L, "a")), Json.parseObject(s))
    }
    @Test fun garbageReturnsNull() { assertNull(Json.parseOrNull("{\"a\": ")); assertNull(Json.parseOrNull("nonsense")) }
}

class ParserTest {
    private fun call(raw: String) = (ToolCallParser.parse(raw) as ParsedTurn.Call).call
    @Test fun standard() { assertEquals(ToolCall("tap", mapOf("text" to "Search")), call("""{"tool":"tap","args":{"text":"Search"}}""")) }
    @Test fun fencedWithProse() { assertEquals("tap", call("Sure!\n```json\n{\"tool\":\"tap\",\"args\":{\"element_id\":3}}\n```").name) }
    @Test fun nameArguments() { assertEquals(mapOf("x" to 1L), call("""{"name":"tap","arguments":{"x":1}}""").args) }
    @Test fun flatArgs() { assertEquals(mapOf("text" to "Hi"), call("""{"tool":"input_text","text":"Hi"}""").args) }
    @Test fun taggedAndNameBrace() {
        assertEquals("scroll", call("""<tool_call>{"name":"scroll","arguments":{"direction":"down"}}</tool_call>""").name)
        assertEquals("open_app", call("""open_app{"name":"YouTube"}""").name)
    }
    @Test fun geminiNative() {
        val c = call("""<|tool_call>call:input_text{text:<|"|>lofi<|"|>,submit:true}<tool_call|>""")
        assertEquals("input_text", c.name); assertEquals("lofi", c.args["text"]); assertEquals(true, c.args["submit"])
    }
    @Test fun truncatedJsonIsClosed() { assertEquals("tap", call("""{"tool":"tap","args":{"text":"Go"""").name) }
    @Test fun textAndInvalid() {
        assertTrue(ToolCallParser.parse("Hello there") is ParsedTurn.Text)
        assertTrue(ToolCallParser.parse("   ") is ParsedTurn.Invalid)
        assertTrue(ToolCallParser.parse("""{"foo":1}""") is ParsedTurn.Invalid)
    }
}

class ValidatorTest {
    private val tool = t("x", params = listOf(
        ParamSpec("n", ParamType.INTEGER, "n", min = 1.0, max = 10.0),
        ParamSpec("mode", ParamType.STRING, "m", enumValues = listOf("a", "b")),
        ParamSpec("flag", ParamType.BOOLEAN, "f", required = false)))
    @Test fun coercesAndStripsUnknown() {
        val r = ToolValidator.validate(tool, mapOf("n" to "5", "mode" to "A", "flag" to "yes", "evil" to "rm -rf"))
        assertTrue(r.ok); assertEquals(5, r.args!!.int("n")); assertEquals("a", r.args!!.str("mode")); assertTrue(r.args!!.bool("flag")); assertFalse(r.args!!.has("evil"))
    }
    @Test fun rejects() {
        assertFalse(ToolValidator.validate(tool, mapOf("mode" to "a")).ok)
        assertFalse(ToolValidator.validate(tool, mapOf("n" to 99, "mode" to "a")).ok)
        assertFalse(ToolValidator.validate(tool, mapOf("n" to 2, "mode" to "zzz")).ok)
        assertFalse(ToolValidator.validate(tool, mapOf("n" to 2.5, "mode" to "a")).ok)
    }
}

class GuardTest {
    @Test fun userText() {
        assertNotNull(Guard.checkUserText("meri OTP bhejo")); assertNotNull(Guard.checkUserText("500 rupees transfer karo"))
        assertNull(Guard.checkUserText("YouTube par lofi search karo"))
    }
    @Test fun dangerousTapAndUrl() = runBlocking {
        val (_, ex) = harness(listOf(
            t("tap", params = listOf(ParamSpec("text", ParamType.STRING, "t"))),
            t("open_url", ToolGroup.UTILITY, params = listOf(ParamSpec("url", ParamType.STRING, "u")))))
        assertEquals(ErrorCode.BLOCKED, ex.execute(ToolCall("tap", mapOf("text" to "Send"))).errorCode)
        assertEquals(ErrorCode.BLOCKED, ex.execute(ToolCall("tap", mapOf("text" to "Pay now"))).errorCode)
        assertTrue(ex.execute(ToolCall("tap", mapOf("text" to "Search"))).success)
        assertEquals(ErrorCode.BLOCKED, ex.execute(ToolCall("open_url", mapOf("url" to "intent://evil"))).errorCode)
        assertTrue(ex.execute(ToolCall("open_url", mapOf("url" to "https://example.com"))).success)
    }
}

class ExecutorTest {
    @Test fun unknownToolAndTimeoutAndConfirm() = runBlocking {
        val slow = SimpleTool("slow", "d", ToolGroup.UTILITY, timeoutMs = 50) { delay(500); ToolResult.ok() }
        val risky = t("risky", risk = Risk.HIGH)
        val (_, ex) = harness(listOf(slow, risky), confirm = false)
        assertEquals(ErrorCode.UNKNOWN_TOOL, ex.execute(ToolCall("nope")).errorCode)
        assertEquals(ErrorCode.TIMEOUT, ex.execute(ToolCall("slow")).errorCode)
        assertEquals(ErrorCode.DENIED_BY_USER, ex.execute(ToolCall("risky")).errorCode)
    }
    @Test fun permissionRequired() = runBlocking {
        val reg = ToolRegistry().register(SimpleTool("c", "d", ToolGroup.DEVICE, permissions = listOf("android.permission.READ_CONTACTS")) { ToolResult.ok() })
        val ex = ToolExecutor(reg, ToolPermissionManager(object : PermissionChecker { override fun isGranted(permission: String) = false }), Guard, { DenyAllConfirmer }, { })
        val r = ex.execute(ToolCall("c"))
        assertEquals(ErrorCode.PERMISSION_REQUIRED, r.errorCode); assertEquals("android.permission.READ_CONTACTS", r.permission)
    }
}

class AgentLoopTest {
    private val textParam = listOf(ParamSpec("text", ParamType.STRING, "t"))
    private fun loop(llm: LlmClient, tools: List<Tool>, cfg: AgentConfig = AgentConfig()): AgentLoop {
        val (reg, ex) = harness(tools); return AgentLoop(llm, ex, reg, null, cfg)
    }
    private val groups = setOf(ToolGroup.CORE, ToolGroup.SCREEN)

    @Test fun completesAfterVerifiedAction() = runBlocking {
        val llm = Scripted(listOf("""{"tool":"open_app","args":{"name":"YouTube"}}""", """{"tool":"finish","args":{"message":"Khul gaya"}}"""))
        val o = loop(llm, listOf(t("open_app", params = listOf(ParamSpec("name", ParamType.STRING, "n"))))).run("open youtube", groups)
        assertEquals(AgentOutcome.Completed("Khul gaya"), o)
    }
    @Test fun finishRejectedWhenLastActionFailed() = runBlocking {
        val llm = Scripted(listOf("""{"tool":"tap","args":{"text":"X"}}""", """{"tool":"finish","args":{"message":"done!"}}"""))
        val o = loop(llm, listOf(t("tap", params = textParam, ok = false))).run("tap x", groups)
        assertTrue("got $o", o is AgentOutcome.Failed)
        assertTrue(llm.prompts.last().contains("REJECTED"))
    }
    @Test fun answerWithoutActions() = runBlocking {
        assertEquals(AgentOutcome.Answer("Hello!"), loop(Scripted(listOf("Hello!")), emptyList()).run("hi", groups))
        assertEquals(AgentOutcome.Answer("42"), loop(Scripted(listOf("""{"tool":"finish","args":{"message":"42"}}""")), emptyList()).run("q", groups))
    }
    @Test fun repeatedActionStops() = runBlocking {
        val llm = Scripted(listOf("""{"tool":"tap","args":{"text":"Same"}}"""))
        val events = ArrayList<AgentEvent>()
        val o = loop(llm, listOf(t("tap", params = textParam))).run("loop", groups, emit = { events += it })
        assertTrue("got $o", o is AgentOutcome.Failed && o.reason.startsWith("Stopped"))
        assertTrue(events.any { it.type == EventType.STUCK })
    }
    @Test fun blockedToolEndsTask() = runBlocking {
        val llm = Scripted(listOf("""{"tool":"tap","args":{"text":"Send"}}"""))
        assertTrue(loop(llm, listOf(t("tap", params = textParam))).run("t", groups) is AgentOutcome.Blocked)
    }
    @Test fun invalidRepliesGiveUp() = runBlocking {
        val llm = Scripted(listOf("{\"foo\": 1}"))
        assertTrue(loop(llm, emptyList()).run("t", groups) is AgentOutcome.Failed)
    }
    @Test fun stepLimit() = runBlocking {
        var n = 0
        val llm = object : LlmClient { override val isReady = true
            override suspend fun generate(system: String, user: String) = """{"tool":"tap","args":{"text":"B${n++}"}}""" }
        val o = loop(llm, listOf(t("tap", params = textParam)), AgentConfig(maxSteps = 4)).run("t", groups)
        assertTrue("got $o", o is AgentOutcome.Failed && o.reason.contains("Step limit"))
    }
    @Test fun llmTimeout() = runBlocking {
        val o = loop(Scripted(listOf("x"), delayMs = 500), emptyList(), AgentConfig(stepTimeoutMs = 50)).run("t", groups)
        assertTrue("got $o", o is AgentOutcome.Failed)
    }
    @Test fun cancellationIsCooperative() = runBlocking {
        val llm = Scripted(listOf("""{"tool":"tap","args":{"text":"A"}}"""), delayMs = 10_000)
        val events = ArrayList<AgentEvent>()
        val job = launch { loop(llm, listOf(t("tap", params = textParam))).run("t", groups, emit = { events += it }) }
        delay(100); job.cancelAndJoin()
        assertTrue(job.isCancelled); assertTrue(events.any { it.type == EventType.CANCELLED })
    }
    @Test fun screenObservationIsFencedAndUsed() = runBlocking {
        val obs = object : ScreenObserver { override val available = true
            override suspend fun snapshot() = ScreenSnapshot("com.x", listOf(UiNode(0, "Button", "Go <b>", true, false, false, 0, 0, 10, 10))) }
        val llm = Scripted(listOf("""{"tool":"finish","args":{"message":"ok"}}"""))
        val (reg, ex) = harness(emptyList())
        AgentLoop(llm, ex, reg, obs).run("t", groups)
        assertTrue(llm.prompts[0].contains("[0] Button: Go <b> (tap)".replace("<", "(").replace(">", ")")))
    }
}

class SkillAndRouterTest {
    private val skills = BuiltInSkills.all()
    private val mem = MemoryProvider { _, _ -> emptyList() }
    private val router = TaskRouter({ skills }, mem, { true })

    @Test fun builtInsParse() {
        assertEquals(10, skills.size)
        assertTrue(skills.all { it.steps.isNotEmpty() })
        val s = skills.first { it.name == "morning_briefing" }
        assertEquals("llm", s.steps.last().tool); assertEquals("result", s.steps.last().saveAs)
    }
    @Test fun customSkillTextFormat() {
        val s = SkillFormat.parse("skill: hello\ndescription: d\nparam: who:str - name\nstep: get_current_time as=result\nsuccess: ok")
        assertEquals("hello", s.name); assertEquals(listOf("who"), s.params.map { it.name })
    }
    @Test fun routing() = runBlocking {
        assertTrue(router.route("meri OTP bhejo") is Route.Blocked)
        val timer = router.route("20 minute ka timer lagao") as Route.Direct
        assertEquals(ToolCall("set_timer", mapOf("seconds" to 1200)), timer.steps.first())
        val alarm = router.route("shaam 6:30 ka alarm laga do") as Route.Direct
        assertEquals(mapOf("hour" to 18, "minute" to 30), alarm.steps.first().args)
        assertTrue((router.route("YouTube par lofi search karo") as Route.Direct).fallback != null)
        val draft = router.route("Rahul ko whatsapp par kal milte hain likho") as Route.UseSkill
        assertEquals("prepare_whatsapp_message", draft.skillName); assertEquals("Rahul", draft.params["contact"])
        val send = router.route("Rahul ko whatsapp par kal milte hain bhejo") as Route.UseSkill
        assertEquals("send_approved_whatsapp", send.skillName)
        assertEquals("search_in_app", (router.route("Amazon par shoes search karo") as Route.UseSkill).skillName)
        assertEquals("morning_briefing", (router.route("morning briefing do") as Route.UseSkill).skillName)
        assertEquals("read_and_summarize", (router.route("screen par kya hai") as Route.UseSkill).skillName)
        val agent = router.route("tell me a joke") as Route.UseAgent
        assertFalse(ToolGroup.SCREEN in agent.groups)
    }
    @Test fun skillRunnerExecutesAndTemplates() = runBlocking {
        val seen = ArrayList<ToolCall>()
        val (_, ex) = harness(listOf(
            SimpleTool("get_current_date", "d", ToolGroup.UTILITY) { ToolResult.ok("2026-11-03") },
            SimpleTool("get_current_time", "d", ToolGroup.UTILITY) { ToolResult.ok("17:30") },
            SimpleTool("get_battery_info", "d", ToolGroup.DEVICE) { ToolResult.ok("80%") }))
        val llm = Scripted(listOf("Subah mubarak"))
        val runner = SkillRunner(ex, ToolPermissionManager(AllowAll), llm)
        val o = runner.run(skills.first { it.name == "morning_briefing" }, emptyMap())
        assertEquals(SkillOutcome.Success("Subah mubarak"), o)
        assertTrue(llm.prompts[0].contains("2026-11-03 17:30\nBattery: 80%"))
    }
    @Test fun skillFailsCleanlyWithoutGemmaOrParams() = runBlocking {
        val (_, ex) = harness(emptyList())
        val notReady = object : LlmClient { override val isReady = false; override suspend fun generate(system: String, user: String) = "" }
        val r = SkillRunner(ex, ToolPermissionManager(AllowAll), notReady)
        assertTrue(r.run(skills.first { it.name == "send_approved_whatsapp" }, emptyMap()) is SkillOutcome.Failure)
    }
}
