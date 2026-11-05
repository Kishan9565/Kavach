package com.kishan.kavach.domain.agent

import com.kishan.kavach.domain.tools.ToolGroup

enum class TaskStatus { PENDING, RUNNING, WAITING_CONFIRMATION, COMPLETED, FAILED, CANCELLED }

data class AgentTask(
    val id: Long = 0,
    val request: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Int = 0,
    val currentAction: String = "",
    val resultMessage: String = "",
    val source: String = "chat",
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
) {
    /** Request text without internal routing markers (skill / chat prefixes). */
    val displayText: String get() = when {
        request.startsWith("\u0001SKILL\u0001") -> "Skill: " + request.removePrefix("\u0001SKILL\u0001").substringBefore('\n')
        request.startsWith("\u0001CHAT\u0001") -> request.removePrefix("\u0001CHAT\u0001")
        else -> request
    }
    val isActive get() = status == TaskStatus.PENDING || status == TaskStatus.RUNNING || status == TaskStatus.WAITING_CONFIRMATION
}

enum class EventType { ROUTE, THINK, TOOL_CALL, TOOL_RESULT, OBSERVATION, RETRY, STUCK, CONFIRM, VERIFY, INFO, DONE, FAILED, CANCELLED }

data class AgentEvent(val type: EventType, val text: String, val time: Long = System.currentTimeMillis())

sealed interface AgentOutcome {
    /** Task finished AND the finish claim passed verification. */
    data class Completed(val message: String) : AgentOutcome
    /** Question answered without any action being taken (nothing to verify). */
    data class Answer(val message: String) : AgentOutcome
    data class Failed(val reason: String) : AgentOutcome
    data class Blocked(val reason: String) : AgentOutcome
}

class LlmUnavailableException(message: String) : Exception(message)

/** The only LLM contract. The sole production implementation is the on-device Gemma engine. */
interface LlmClient {
    val isReady: Boolean
    /** Stateless: one system prompt + one user prompt -> one reply. */
    suspend fun generate(system: String, user: String): String
}

data class UiNode(
    val id: Int, val role: String, val label: String,
    val clickable: Boolean, val editable: Boolean, val scrollable: Boolean,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val password: Boolean = false,
) {
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
}

data class ScreenSnapshot(
    val packageName: String,
    val nodes: List<UiNode>,
    val keyboardVisible: Boolean = false,
    val dialogLikely: Boolean = false,
) {
    /** Stable fingerprint used for unchanged-screen detection. */
    val signature: String get() = packageName + "|" + nodes.joinToString("|") { "${it.role}:${it.label}" }.hashCode()

    /** Concise, bounded text for the model. Passwords are never included. */
    fun toPromptText(maxNodes: Int = 40, maxChars: Int = 1400): String {
        val sb = StringBuilder("app=$packageName")
        if (keyboardVisible) sb.append(" keyboard=visible")
        if (dialogLikely) sb.append(" dialog=likely")
        sb.append('\n')
        for (n in nodes.take(maxNodes)) {
            val act = when { n.editable -> " (edit)"; n.clickable -> " (tap)"; n.scrollable -> " (scroll)"; else -> "" }
            val line = "[${n.id}] ${n.role}: ${if (n.password) "••••" else n.label.take(60)}$act\n"
            if (sb.length + line.length > maxChars) { sb.append("…(truncated)\n"); break }
            sb.append(line)
        }
        if (nodes.isEmpty()) sb.append("(no readable elements)\n")
        return sb.toString()
    }
}

interface ScreenObserver {
    val available: Boolean
    suspend fun snapshot(): ScreenSnapshot?
}

data class AgentConfig(
    val maxSteps: Int = 12,
    val stepTimeoutMs: Long = 120_000,
    val maxInvalidReplies: Int = 3,
    val maxRecoveries: Int = 2,
)

/** What the router decided. */
sealed interface Route {
    data class Blocked(val reason: String) : Route
    /** A short deterministic plan: no Gemma needed. `fallback` runs if a step fails. */
    data class Direct(val goal: String, val reply: String, val steps: List<com.kishan.kavach.domain.tools.ToolCall>, val fallback: Direct? = null) : Route
    data class UseSkill(val skillName: String, val params: Map<String, String>) : Route
    data class UseAgent(val groups: Set<ToolGroup>, val memory: String = "", val chatOnly: Boolean = false) : Route
}
