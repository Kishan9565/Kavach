package com.kishan.kavach.domain.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Independently registerable tools. Adding a tool never touches AgentLoop. */
class ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()

    @Synchronized fun register(tool: Tool): ToolRegistry {
        require(tool.name.matches(Regex("[a-z][a-z0-9_]*"))) { "bad tool name ${tool.name}" }
        tools[tool.name] = tool
        return this
    }
    @Synchronized fun registerAll(vararg t: Tool): ToolRegistry { t.forEach { register(it) }; return this }
    @Synchronized fun get(name: String): Tool? = tools[name]
    @Synchronized fun all(): List<Tool> = tools.values.toList()
    @Synchronized fun byGroups(groups: Set<ToolGroup>): List<Tool> =
        tools.values.filter { it.group in groups || it.group == ToolGroup.CORE }
}

data class ValidationResult(val args: Args?, val error: String? = null) { val ok get() = error == null }

/** Validates every model-generated tool call before it can run. Unknown args are dropped, never forwarded. */
object ToolValidator {
    fun validate(tool: Tool, raw: Map<String, Any?>): ValidationResult {
        val out = LinkedHashMap<String, Any?>()
        for (p in tool.params) {
            val v = raw[p.name]
            if (v == null || (v is String && v.isBlank() && p.type != ParamType.STRING)) {
                if (p.required) return ValidationResult(null, "missing required argument '${p.name}'")
                continue
            }
            val coerced: Any = when (p.type) {
                ParamType.STRING -> {
                    val s = clean(v.toString())
                    if (s.length > p.maxLength) return ValidationResult(null, "'${p.name}' longer than ${p.maxLength} chars")
                    if (p.required && s.isBlank()) return ValidationResult(null, "'${p.name}' is empty")
                    if (p.enumValues.isNotEmpty() && s.lowercase() !in p.enumValues.map { it.lowercase() })
                        return ValidationResult(null, "'${p.name}' must be one of ${p.enumValues.joinToString("|")}")
                    if (p.enumValues.isNotEmpty()) s.lowercase() else s
                }
                ParamType.INTEGER -> {
                    val n = (v as? Number)?.toDouble() ?: v.toString().trim().toDoubleOrNull()
                        ?: return ValidationResult(null, "'${p.name}' must be an integer")
                    if (n % 1.0 != 0.0) return ValidationResult(null, "'${p.name}' must be a whole number")
                    rangeCheck(p, n)?.let { return ValidationResult(null, it) }
                    n.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
                }
                ParamType.NUMBER -> {
                    val n = (v as? Number)?.toDouble() ?: v.toString().trim().toDoubleOrNull()
                        ?: return ValidationResult(null, "'${p.name}' must be a number")
                    rangeCheck(p, n)?.let { return ValidationResult(null, it) }
                    n
                }
                ParamType.BOOLEAN -> when (v.toString().lowercase()) {
                    "true", "yes", "1" -> true
                    "false", "no", "0" -> false
                    else -> return ValidationResult(null, "'${p.name}' must be true or false")
                }
            }
            out[p.name] = coerced
        }
        return ValidationResult(Args(out))
    }

    private fun rangeCheck(p: ParamSpec, n: Double): String? = when {
        p.min != null && n < p.min -> "'${p.name}' must be >= ${p.min}"
        p.max != null && n > p.max -> "'${p.name}' must be <= ${p.max}"
        else -> null
    }

    /** Strip control chars; keep newlines/tabs. */
    private fun clean(s: String) = s.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim()
}

/** Maps required permissions to what is currently granted. Implemented with Android APIs in app code. */
interface PermissionChecker { fun isGranted(permission: String): Boolean }

class ToolPermissionManager(private val checker: PermissionChecker) {
    fun missing(tool: Tool): String? = missing(tool.permissions)
    fun missing(permissions: List<String>): String? = permissions.firstOrNull { !checker.isGranted(it) }
}

/** Safety layer: returns a user-facing reason when the call must be blocked, else null. */
interface SafetyValidator { fun check(tool: Tool, args: Args): String? }

data class ConfirmationRequest(val title: String, val detail: String, val risk: Risk)

interface Confirmer { suspend fun confirm(request: ConfirmationRequest): Boolean }

/** Used for background automation: nothing needing confirmation may run unattended. */
object DenyAllConfirmer : Confirmer { override suspend fun confirm(request: ConfirmationRequest) = false }

data class ToolLogEntry(
    val time: Long, val tool: String, val args: String, val success: Boolean,
    val errorCode: ErrorCode, val message: String, val durationMs: Long,
)

fun interface ToolExecutionLogger { fun log(entry: ToolLogEntry) }

/** Full pipeline: lookup -> validate -> safety -> permission -> confirmation -> timeout-bounded execution -> log. */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val permissions: ToolPermissionManager,
    private val safety: SafetyValidator,
    private val confirmer: () -> Confirmer,
    private val logger: ToolExecutionLogger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(call: ToolCall): ToolResult {
        val t0 = clock()
        val result = run(call)
        logger.log(ToolLogEntry(t0, call.name, call.brief(200), result.success, result.errorCode, result.message.take(200), clock() - t0))
        return result
    }

    private suspend fun run(call: ToolCall): ToolResult {
        val tool = registry.get(call.name)
            ?: return ToolResult.fail(ErrorCode.UNKNOWN_TOOL, "Unknown tool '${call.name}'")
        val v = ToolValidator.validate(tool, call.args)
        if (!v.ok) return ToolResult.fail(ErrorCode.INVALID_ARGS, v.error!!, retryable = true)
        val args = v.args!!
        safety.check(tool, args)?.let { return ToolResult.fail(ErrorCode.BLOCKED, it) }
        permissions.missing(tool)?.let {
            return ToolResult.fail(ErrorCode.PERMISSION_REQUIRED, "Permission needed: $it", permission = it)
        }
        if (tool.risk == Risk.HIGH) {
            val ok = confirmer().confirm(ConfirmationRequest(tool.name, tool.confirmationText(args), tool.risk))
            if (!ok) return ToolResult.fail(ErrorCode.DENIED_BY_USER, "User did not approve '${tool.name}'")
        }
        return try {
            withTimeout(tool.timeoutMs) { tool.execute(args) }
        } catch (e: TimeoutCancellationException) {
            ToolResult.fail(ErrorCode.TIMEOUT, "'${tool.name}' timed out after ${tool.timeoutMs} ms", retryable = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.fail(ErrorCode.EXECUTION_FAILED, "${e.javaClass.simpleName}: ${e.message}".take(200), retryable = true)
        }
    }
}
