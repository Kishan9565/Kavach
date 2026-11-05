package com.kishan.kavach.ai.parser

import com.kishan.kavach.core.common.Json
import com.kishan.kavach.domain.tools.ToolCall

sealed interface ParsedTurn {
    data class Call(val call: ToolCall, val thought: String) : ParsedTurn
    /** No tool call found: plain text reply. */
    data class Text(val text: String) : ParsedTurn
    data class Invalid(val reason: String) : ParsedTurn
}

/**
 * Forgiving parser for small-model output. Accepts:
 *  {"tool":"tap","args":{...}} | {"name":..,"arguments":{..}} | flat {"tool":"tap","text":".."}
 *  <tool_call>{...}</tool_call> | ```json fences | tool_name{...} | Gemma native  call:tap{text:<|"|>x<|"|>}
 * It only PARSES. Validation happens in ToolValidator, execution rights in ToolExecutor.
 */
object ToolCallParser {
    private val nameKeys = listOf("tool", "name", "action", "function", "tool_name")
    private val argKeys = listOf("args", "arguments", "parameters", "params", "input")
    private val tagged = Regex("""<tool_call>(.*?)</tool_call>""", RegexOption.DOT_MATCHES_ALL)
    private val native = Regex("""call:([a-z][a-z0-9_]*)\s*(\{.*\})""", RegexOption.DOT_MATCHES_ALL)
    private val nameThenBrace = Regex("""^\s*([a-z][a-z0-9_]*)\s*(\{.*\})\s*$""", RegexOption.DOT_MATCHES_ALL)
    private val thinkBlock = Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(raw: String): ParsedTurn {
        var text = raw.replace(thinkBlock, "").replace("<|\"|>", "\"").trim()
        if (text.isEmpty()) return ParsedTurn.Invalid("empty reply")
        text = text.replace(Regex("```[a-zA-Z]*"), "").replace("```", "").trim()

        native.find(text)?.let { m ->
            val args = Json.parseObject(balance(m.groupValues[2]))
            if (args != null) return ParsedTurn.Call(ToolCall(m.groupValues[1], args), text.substring(0, m.range.first).trim())
        }
        val tag = tagged.find(text)?.groupValues?.get(1)?.trim()
        for (src in listOfNotNull(tag, text)) {
            nameThenBrace.find(src)?.let { m ->
                Json.parseObject(balance(m.groupValues[2]))?.let { return ParsedTurn.Call(ToolCall(m.groupValues[1], it), "") }
            }
        }
        val candidate = tag ?: findObject(text)
        if (candidate != null) {
            val obj = Json.parseObject(balance(candidate))
                ?: return ParsedTurn.Invalid("could not parse JSON")
            val nameKey = nameKeys.firstOrNull { obj[it] is String }
                ?: return ParsedTurn.Invalid("JSON has no \"tool\" field")
            val name = (obj[nameKey] as String).trim()
            val argKey = argKeys.firstOrNull { obj[it] is Map<*, *> }
            @Suppress("UNCHECKED_CAST")
            val args: Map<String, Any?> = if (argKey != null) obj[argKey] as Map<String, Any?>
                else obj.filterKeys { it != nameKey && it !in argKeys && it != "thought" && it != "reason" }
            val thought = (obj["thought"] ?: obj["reason"])?.toString().orEmpty()
            return ParsedTurn.Call(ToolCall(name, args), thought)
        }
        return ParsedTurn.Text(text)
    }

    /** First balanced {...} (string-aware). If the model got truncated, closes missing braces. */
    internal fun findObject(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr: Char? = null
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr != null) {
                if (c == '\\') i++ else if (c == inStr) inStr = null
            } else when (c) {
                '"', '\'' -> inStr = c
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) }
            }
            i++
        }
        return s.substring(start)
    }

    private fun balance(s: String): String {
        val open = s.count { it == '{' }
        val close = s.count { it == '}' }
        return if (open > close) s + "}".repeat(open - close) else s
    }
}
