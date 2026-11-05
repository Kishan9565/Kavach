package com.kishan.kavach.agent.loop

import com.kishan.kavach.domain.tools.ErrorCode
import com.kishan.kavach.domain.tools.ToolCall
import com.kishan.kavach.domain.tools.ToolResult

enum class StuckKind { REPEATED_CALL, REPEATED_ERRORS, UNCHANGED_SCREEN, NO_PROGRESS }
data class StuckVerdict(val kind: StuckKind, val reason: String)

/** Detects repeated tool calls, repeated errors, unchanged screens and time-outs (spec sections 7 + 9). */
class StuckDetector(
    private val maxSame: Int = 3,
    private val maxErrors: Int = 3,
    private val maxUnchanged: Int = 2,
) {
    private val recent = ArrayDeque<String>()
    private var errors = 0
    private var unchanged = 0
    private var lastSig: String? = null
    private var lastWasScreenAction = false

    companion object {
        val SCREEN_CHANGING = setOf("tap", "swipe", "long_press", "input_text", "press_back", "scroll", "open_app", "launch_settings", "open_url")
    }

    fun record(call: ToolCall, result: ToolResult, screenSignature: String?): StuckVerdict? {
        val key = call.name + call.args.toString()
        recent.addLast(key)
        while (recent.size > maxSame) recent.removeFirst()

        errors = if (result.success) 0 else errors + 1

        if (screenSignature != null) {
            if (lastWasScreenAction && screenSignature == lastSig) unchanged++ else if (screenSignature != lastSig) unchanged = 0
            lastSig = screenSignature
        }
        lastWasScreenAction = call.name in SCREEN_CHANGING && result.success

        return when {
            recent.size == maxSame && recent.all { it == key } ->
                StuckVerdict(StuckKind.REPEATED_CALL, "same action '${call.name}' repeated $maxSame times")
            errors >= maxErrors ->
                StuckVerdict(StuckKind.REPEATED_ERRORS, "$errors failures in a row (last: ${result.errorCode})")
            unchanged >= maxUnchanged ->
                StuckVerdict(StuckKind.UNCHANGED_SCREEN, "screen did not change after $unchanged actions")
            result.errorCode == ErrorCode.TIMEOUT && errors >= 2 ->
                StuckVerdict(StuckKind.NO_PROGRESS, "repeated time-outs")
            else -> null
        }
    }

    /** Called after a soft recovery hint so the same verdict does not fire immediately again. */
    fun softReset() { recent.clear(); errors = 0; unchanged = 0 }
}
