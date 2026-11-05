package com.kishan.kavach.domain.tools

enum class Risk { LOW, MEDIUM, HIGH }

enum class ParamType(val label: String) { STRING("str"), INTEGER("int"), NUMBER("num"), BOOLEAN("bool") }

/** Tool groups let the prompt builder show Gemma only the tools relevant to the task (small context budget). */
enum class ToolGroup { CORE, SCREEN, COMMS, DEVICE, PRODUCTIVITY, UTILITY }

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String> = emptyEmptyList(),
    val maxLength: Int = 200,
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE,
)

private fun emptyEmptyList() = emptyList<String>()

data class ToolCall(val name: String, val args: Map<String, Any?> = emptyMap()) {
    fun brief(maxLen: Int = 120): String =
        (name + args.entries.joinToString(prefix = "(", postfix = ")") { "${it.key}=${it.value}" }).take(maxLen)
}

/** Structured result every tool returns (spec section 5). */
sealed interface ToolResult {
    val success: Boolean
    val message: String
    val errorCode: ErrorCode get() = ErrorCode.NONE
    val retryable: Boolean get() = false
    val permission: String? get() = null
    val data: Map<String, Any?> get() = emptyMap()

    data class Ok(
        val saw: String? = null,
        val next: String? = null,
        val mapData: Map<String, String> = emptyMap()
    ) : ToolResult {
        override val success: Boolean get() = true
        override val message: String
            get() = saw ?: mapData.entries.joinToString { "${it.key}=${it.value}" }
        override val data: Map<String, Any?> get() = mapData
    }

    data class Denied(
        val what: String,
        val hint: String
    ) : ToolResult {
        override val success: Boolean get() = false
        override val message: String get() = "$what: $hint"
        override val errorCode: ErrorCode get() = ErrorCode.BLOCKED
    }

    data class Failed(
        val reason: String,
        val next: String? = null,
        val code: ErrorCode = ErrorCode.EXECUTION_FAILED,
        val isRetryable: Boolean = false,
        val reqPermission: String? = null
    ) : ToolResult {
        override val success: Boolean get() = false
        override val message: String get() = reason
        override val errorCode: ErrorCode get() = code
        override val retryable: Boolean get() = isRetryable
        override val permission: String? get() = reqPermission
    }

    fun observation(maxLen: Int = 400): String {
        val head = if (success) "OK" else "FAILED(${errorCode.name})"
        val body = message.ifBlank { data.entries.joinToString { "${it.key}=${it.value}" } }
        return "$head: $body".take(maxLen)
    }

    companion object {
        fun ok(message: String = "", data: Map<String, Any?> = emptyMap()): ToolResult =
            Ok(saw = message.ifBlank { null }, mapData = data.mapValues { it.value?.toString().orEmpty() })

        fun fail(code: ErrorCode, message: String, retryable: Boolean = false, permission: String? = null): ToolResult =
            Failed(reason = message, code = code, isRetryable = retryable, reqPermission = permission)
    }
}

/** Validated, typed argument accessor handed to tool handlers. */
class Args(val map: Map<String, Any?>) {
    fun str(k: String): String? = map[k]?.toString()
    fun string(k: String, default: String = ""): String = str(k) ?: default
    fun int(k: String): Int? = (map[k] as? Number)?.toInt() ?: map[k]?.toString()?.toIntOrNull()
    fun long(k: String): Long? = (map[k] as? Number)?.toLong() ?: map[k]?.toString()?.toLongOrNull()
    fun bool(k: String, default: Boolean = false): Boolean = (map[k] as? Boolean) ?: map[k]?.toString()?.toBooleanStrictOrNull() ?: default
    fun has(k: String): Boolean = map.containsKey(k) && map[k] != null
}

/** Android permission strings, or the pseudo permissions "accessibility" and "notification_access". */

class SimpleTool(
    override val name: String,
    override val description: String,
    override val group: ToolGroup,
    override val params: List<ParamSpec> = emptyList(),
    override val risk: Risk = Risk.LOW,
    override val permissions: List<String> = emptyList(),
    override val timeoutMs: Long = 10_000,
    private val confirm: ((Args) -> String)? = null,
    private val handler: suspend (Args) -> ToolResult,
) : Tool {
    override fun confirmationText(args: Args): String = confirm?.invoke(args) ?: (description + args.map.entries.joinToString(prefix = ": ") { "${it.key}=${it.value}" })
    override suspend fun execute(args: Args): ToolResult = handler(args)
}

interface Tool {
    val name: String
    val description: String
    val group: ToolGroup
    val params: List<ParamSpec>
    val risk: Risk
    val permissions: List<String>
    val timeoutMs: Long
    fun confirmationText(args: Args): String
    suspend fun execute(args: Args): ToolResult
    fun signature(): String = "$name(${params.joinToString { "${it.name}:${it.type.label}${if (it.required) "" else "?"}" }}) - $description"
}

enum class ErrorCode {
    NONE,
    UNKNOWN_TOOL,
    INVALID_ARGS,
    PERMISSION_REQUIRED,
    BLOCKED,
    DENIED_BY_USER,
    TIMEOUT,
    NOT_AVAILABLE,
    EXECUTION_FAILED,
    ELEMENT_NOT_FOUND,
    SCREEN_UNAVAILABLE,
    CANCELLED
}
