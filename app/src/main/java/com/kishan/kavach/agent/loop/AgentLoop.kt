package com.kishan.kavach.agent.loop

import com.kishan.kavach.ai.parser.ParsedTurn
import com.kishan.kavach.ai.parser.ToolCallParser
import com.kishan.kavach.ai.prompts.HistoryItem
import com.kishan.kavach.ai.prompts.PromptBuilder
import com.kishan.kavach.domain.agent.*
import com.kishan.kavach.domain.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Bounded observe -> reason -> act -> verify loop.
 * Never claims success without verification: `finish` is accepted only if the last executed tool succeeded
 * (and tools themselves verify their effect, e.g. `expect` text on screen). Adding tools never touches this class.
 */
class AgentLoop(
    private val llm: LlmClient,
    private val executor: ToolExecutor,
    private val registry: ToolRegistry,
    private val observer: ScreenObserver?,
    private val config: AgentConfig = AgentConfig(),
    private val prompts: PromptBuilder = PromptBuilder(),
) {
    suspend fun run(
        task: String,
        groups: Set<ToolGroup>,
        memory: String = "",
        emit: (AgentEvent) -> Unit = {},
    ): AgentOutcome {
        try {
            return loop(task, groups, memory, emit)
        } catch (e: CancellationException) {
            emit(AgentEvent(EventType.CANCELLED, "Task cancelled"))
            throw e
        }
    }

    private suspend fun loop(task: String, groups: Set<ToolGroup>, memory: String, emit: (AgentEvent) -> Unit): AgentOutcome {
        val tools = registry.byGroups(groups)
        val system = prompts.system(tools)
        val observe = ToolGroup.SCREEN in groups && observer != null
        val history = ArrayList<HistoryItem>()
        val stuck = StuckDetector()
        var invalid = 0; var timeouts = 0; var recoveries = 0; var finishRejects = 0
        var actions = 0
        var lastActionOk: Boolean? = null

        for (step in 1..config.maxSteps) {
            coroutineContext.ensureActive()

            var snapSig: String? = null
            var screenText: String? = null
            if (observe) {
                val snap = if (observer!!.available) observer.snapshot() else null
                screenText = snap?.toPromptText() ?: "(screen unavailable: accessibility off or secure screen)"
                snapSig = snap?.signature
                emit(AgentEvent(EventType.OBSERVATION, screenText.lines().first()))
            }

            val user = prompts.user(task, memory, history, screenText)
            val raw = try {
                withTimeoutOrNull(config.stepTimeoutMs) { llm.generate(system, user) }
            } catch (e: LlmUnavailableException) {
                return AgentOutcome.Failed(e.message ?: "Gemma model is not available")
            }
            if (raw == null) {
                timeouts++
                emit(AgentEvent(EventType.RETRY, "Gemma timed out (attempt $timeouts)"))
                if (timeouts >= 2) return AgentOutcome.Failed("Gemma did not respond in time")
                continue
            }

            when (val turn = ToolCallParser.parse(raw)) {
                is ParsedTurn.Invalid -> {
                    invalid++
                    emit(AgentEvent(EventType.RETRY, "Unusable model reply: ${turn.reason}"))
                    if (invalid > config.maxInvalidReplies) return AgentOutcome.Failed("Model kept returning unusable output")
                    history.add(HistoryItem("(invalid reply)", "Reply with one JSON object: {\"tool\":...,\"args\":{...}}"))
                }
                is ParsedTurn.Text -> {
                    if (actions == 0) return AgentOutcome.Answer(turn.text.take(1500))
                    invalid++
                    emit(AgentEvent(EventType.RETRY, "Plain text after actions; asking for JSON"))
                    if (invalid > config.maxInvalidReplies) return AgentOutcome.Failed("Model did not finish with a tool call")
                    history.add(HistoryItem("(plain text)", "Use a JSON tool call. Call finish when the task is done."))
                }
                is ParsedTurn.Call -> {
                    val call = turn.call
                    if (turn.thought.isNotBlank()) emit(AgentEvent(EventType.THINK, turn.thought.take(200)))

                    if (call.name == "finish") {
                        val msg = (call.args["message"] ?: call.args["answer"] ?: "").toString().trim().take(1500)
                        if (actions > 0 && lastActionOk != true) {
                            finishRejects++
                            emit(AgentEvent(EventType.VERIFY, "finish rejected: last action did not succeed"))
                            if (finishRejects >= 2) return AgentOutcome.Failed("Task could not be verified as done: ${history.lastOrNull()?.observation ?: "last action failed"}")
                            history.add(HistoryItem("finish", "REJECTED: the last action failed. Fix it or explain the failure in finish."))
                            continue
                        }
                        emit(AgentEvent(EventType.VERIFY, if (actions > 0) "last action succeeded: finish accepted" else "answer only, no actions"))
                        return if (actions > 0) AgentOutcome.Completed(msg.ifBlank { "Done." }) else AgentOutcome.Answer(msg.ifBlank { "Done." })
                    }

                    emit(AgentEvent(EventType.TOOL_CALL, call.brief()))
                    var result = executor.execute(call)
                    val tool = registry.get(call.name)
                    if (result.errorCode == ErrorCode.TIMEOUT && tool?.risk == Risk.LOW && tool.group != ToolGroup.COMMS) {
                        emit(AgentEvent(EventType.RETRY, "Auto-retry once after time-out: ${call.name}"))
                        result = executor.execute(call)
                    }
                    emit(AgentEvent(EventType.TOOL_RESULT, result.observation(200)))
                    actions++
                    lastActionOk = result.success
                    history.add(HistoryItem(call.brief(80), result.observation(300)))

                    when (result.errorCode) {
                        ErrorCode.BLOCKED -> return AgentOutcome.Blocked(result.message)
                        ErrorCode.DENIED_BY_USER -> return AgentOutcome.Failed(result.message)
                        ErrorCode.PERMISSION_REQUIRED -> return AgentOutcome.Failed("${result.message}. Grant it in Settings and retry.")
                        else -> {}
                    }

                    stuck.record(call, result, snapSig)?.let { verdict ->
                        emit(AgentEvent(EventType.STUCK, verdict.reason))
                        if (recoveries >= config.maxRecoveries) return AgentOutcome.Failed("Stopped: ${verdict.reason}")
                        recoveries++
                        history.add(HistoryItem("(system)", "You seem stuck: ${verdict.reason}. Try a different approach (press_back, scroll, another element) or finish and explain."))
                        stuck.softReset()
                    }
                }
            }
        }
        return AgentOutcome.Failed("Step limit (${config.maxSteps}) reached without finishing")
    }
}
