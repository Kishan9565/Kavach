package com.kishan.kavach.domain.skills

data class SkillParam(val name: String, val type: String = "str", val required: Boolean = true, val description: String = "")

data class SkillStep(
    val tool: String,                 // tool name, or "llm" for a Gemma transform step
    val args: Map<String, String> = emptyMap(),
    val instruction: String = "",     // llm steps only
    val saveAs: String? = null,
    val optional: Boolean = false,
) { val isLlm get() = tool == "llm" }

/**
 * A reusable workflow made of generic tools (spec section 8). Human-readable text format, see [SkillFormat].
 */
data class Skill(
    val name: String,
    val description: String,
    val params: List<SkillParam>,
    val permissions: List<String>,
    val steps: List<SkillStep>,
    val success: String,
    val failure: String,
    val matchers: List<Regex> = emptyList(),
    val builtIn: Boolean = true,
)

class SkillFormatException(msg: String) : Exception(msg)

/**
 * Text format (one skill per block), designed so users can write their own skills later:
 *
 *   skill: search_in_app
 *   description: Open an app and search inside it
 *   param: app:str - App name
 *   param: note:str? - optional param
 *   permission: accessibility
 *   match: (?i)^(?<app>.+?)\s+mein\s+(?<query>.+?)\s+search
 *   step: open_app name={app} expect=Search
 *   step: tap text=Search optional=true as=tapped
 *   llm: "Summarize in 3 bullets" input={screen} as=result
 *   success: Search results are visible
 *   failure: Could not complete the search
 *
 * Blocks are separated by a line containing only `---`.
 */
object SkillFormat {
    private val kv = Regex("""(\w+)=("([^"]*)"|\S+)""")

    fun parseAll(text: String): List<Skill> =
        text.split(Regex("(?m)^---\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }.map { parse(it) }

    fun parse(block: String): Skill {
        var name = ""; var desc = ""; var success = "Done."; var failure = "Could not complete the task."
        val params = ArrayList<SkillParam>(); val perms = ArrayList<String>()
        val steps = ArrayList<SkillStep>(); val matchers = ArrayList<Regex>()
        for (raw in block.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val idx = line.indexOf(':')
            if (idx < 0) throw SkillFormatException("bad line: $line")
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "skill" -> name = value
                "description" -> desc = value
                "success" -> success = value
                "failure" -> failure = value
                "permission" -> perms += value
                "match" -> matchers += Regex(value)
                "param" -> {
                    val (decl, d) = value.split(" - ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    val (n, t) = decl.split(":", limit = 2).let { it[0].trim() to it.getOrElse(1) { "str" }.trim() }
                    params += SkillParam(n, t.removeSuffix("?"), !t.endsWith("?"), d.trim())
                }
                "step" -> {
                    val tool = value.substringBefore(' ').trim()
                    val args = LinkedHashMap<String, String>()
                    var saveAs: String? = null; var optional = false
                    for (m in kv.findAll(value.substringAfter(' ', ""))) {
                        val v = m.groups[3]?.value ?: m.groupValues[2]
                        when (m.groupValues[1]) { "as" -> saveAs = v; "optional" -> optional = v == "true"; else -> args[m.groupValues[1]] = v }
                    }
                    steps += SkillStep(tool, args, saveAs = saveAs, optional = optional)
                }
                "llm" -> {
                    val instr = Regex("""^"([^"]*)"""").find(value)?.groupValues?.get(1) ?: throw SkillFormatException("llm step needs a quoted instruction")
                    var input = ""; var saveAs: String? = null
                    for (m in kv.findAll(value.substringAfter('"' + instr + '"'))) {
                        val v = m.groups[3]?.value ?: m.groupValues[2]
                        when (m.groupValues[1]) { "input" -> input = v; "as" -> saveAs = v }
                    }
                    steps += SkillStep("llm", mapOf("input" to input), instruction = instr, saveAs = saveAs)
                }
                else -> throw SkillFormatException("unknown key '$key'")
            }
        }
        if (name.isBlank()) throw SkillFormatException("skill has no name")
        if (steps.isEmpty()) throw SkillFormatException("skill '$name' has no steps")
        return Skill(name, desc, params, perms, steps, success, failure, matchers)
    }

    /** Group names declared in a pattern, e.g. (?<app>..)(?<query>..) -> [app, query]. */
    fun groupNames(r: Regex): List<String> = Regex("""\(\?<([a-zA-Z][a-zA-Z0-9]*)>""").findAll(r.pattern).map { it.groupValues[1] }.toList()

    fun extract(skill: Skill, text: String): Map<String, String>? {
        for (r in skill.matchers) {
            val m = r.find(text) ?: continue
            val named = m.groups as? MatchNamedGroupCollection
            val out = LinkedHashMap<String, String>()
            for (g in groupNames(r)) named?.get(g)?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { out[g] = it }
            if (skill.params.filter { it.required }.all { it.name in out }) return out
        }
        return null
    }
}
