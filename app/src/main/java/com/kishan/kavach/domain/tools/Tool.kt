package com.kishan.kavach.domain.tools

/** HIGH = always needs explicit user confirmation before running. MEDIUM = allowed, but logged prominently. */
enum class Risk { LOW, MEDIUM, HIGH }

enum class ParamType(val label: String) { STRING("str"), INTEGER("int"), NUMBER("num"), BOOLEAN("bool") }

/** Tool groups let the prompt builder show Gemma only the tools relevant to the task (small context budget). */
enum class ToolGroup { CORE, SCREEN, COMMS, DEVICE, PRODUCTIVITY, UTILITY }

enum class ErrorCode {
    NONE, INVALID_ARGS, UNKNOWN_TOOL, PERMISSION_REQUIRED, BLOCKED, DENIED_BY_USER,
    TIMEOUT, NOT_AVAILABLE, EXECUTION_FAILED, ELEMENT_NOT_FOUND, SCREEN_UNAVAILABLE, CANCELLED
}

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val maxLength: Int = 600,
)

data class ToolCall(val name: String, val args: Map<String, Any?> = emptyMap()) {
    /** Short single-line form for logs / history. */
    fun brief(maxLen: Int = 120): String =
        (name + args.entries.joinToString(prefix = "(", postfix = ")") { "${it.key}=${it.value}" }).take(maxLen)
}

/** Structured result every tool returns (spec section 5). */
data class ToolResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val errorCode: ErrorCode = ErrorCode.NONE,
    val message: String = "",
    val retryable: Boolean = false,
    val permission: String? = null,
) {
    fun observation(maxLen: Int = 400): String {
        val head = if (success) "OK" else "FAILED(${errorCode.name})"
        val body = message.ifBlank { data.entries.joinToString { "${it.key}=${it.value}" } }
        return "$head: $body".take(maxLen)
    }

    companion object {
        fun ok(message: String = "", data: Map<String, Any?> = emptyMap()) = ToolResult(true, data, ErrorCode.NONE, message)
        fun fail(code: ErrorCode, message: String, retryable: Boolean = false, permission: String? = null) =
            ToolResult(false, emptyMap(), code, message, retryable, permission)
    }
}

/** Validated, typed argument accessor handed to tool handlers. */
class Args(val map: Map<String, Any?>) {
    fun str(k: String): String? = map[k]?.toString()
    fun string(k: String, default: String = ""): String = str(k) ?: default
    fun int(k: String): Int? = (map[k] as? Number)?.toInt() ?: map[k]?.toString()?.toIntOrNull()
    fun long(k: String): Long? = (map[k] as? Number)?.toLong() ?: map[k]?.toString()?.toLongOrNull()
    fun double(k: String): Double? = (map[k] as? Number)?.toDouble() ?: map[k]?.toString()?.toDoubleOrNull()
    fun bool(k: String, default: Boolean = false): Boolean =
        (map[k] as? Boolean) ?: map[k]?.toString()?.lowercase()?.let { it == "true" || it == "yes" } ?: default
    fun has(k: String) = map[k] != null
}

/** Generic tool contract (spec section 6). */
interface Tool {
    val name: String
    val description: String
    val group: ToolGroup
    val params: List<ParamSpec>
    val risk: Risk get() = Risk.LOW
    /** Android permission strings, or the pseudo-permissions "accessibility" / "notification_access". */
    val permissions: List<String> get() = emptyList()
    val timeoutMs: Long get() = 10_000
    /** Human readable result schema, shown in the developer screen and used for docs. */
    val resultSchema: String get() = "{success, message, data}"
    /** Text shown to the user when this tool needs confirmation. */
    fun confirmationText(args: Args): String = "$name ${args.map}"
    suspend fun execute(args: Args): ToolResult

    /** Compact signature for the prompt, e.g. `tap(element_id:int?, text:str?) - Tap a UI element`. */
    fun signature(): String {
        val p = params.joinToString(", ") { sp ->
            val t = if (sp.enumValues.isNotEmpty()) sp.enumValues.joinToString("|") else sp.type.label
            "${sp.name}:$t${if (sp.required) "" else "?"}"
        }
        return "$name($p) - $description"
    }

    /** JSON-schema style description (developer screen, docs). */
    fun jsonSchema(): Map<String, Any?> = mapOf(
        "name" to name, "description" to description, "risk" to risk.name,
        "permissions" to permissions, "timeout_ms" to timeoutMs, "result" to resultSchema,
        "parameters" to mapOf(
            "type" to "object",
            "properties" to params.associate { sp ->
                sp.name to buildMap<String, Any?> {
                    put("type", when (sp.type) { ParamType.STRING -> "string"; ParamType.INTEGER -> "integer"; ParamType.NUMBER -> "number"; ParamType.BOOLEAN -> "boolean" })
                    put("description", sp.description)
                    if (sp.enumValues.isNotEmpty()) put("enum", sp.enumValues)
                }
            },
            "required" to params.filter { it.required }.map { it.name },
        ),
    )
}

/** Lambda-backed tool: lets us register many small tools without a class each. */
class SimpleTool(
    override val name: String,
    override val description: String,
    override val group: ToolGroup,
    override val params: List<ParamSpec> = emptyList(),
    override val risk: Risk = Risk.LOW,
    override val permissions: List<String> = emptyList(),
    override val timeoutMs: Long = 10_000,
    override val resultSchema: String = "{success, message, data}",
    private val confirm: ((Args) -> String)? = null,
    private val handler: suspend (Args) -> ToolResult,
) : Tool {
    override fun confirmationText(args: Args) = confirm?.invoke(args) ?: super.confirmationText(args)
    override suspend fun execute(args: Args): ToolResult = handler(args)
}
