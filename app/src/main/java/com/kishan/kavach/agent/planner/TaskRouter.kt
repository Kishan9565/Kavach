package com.kishan.kavach.agent.planner

import com.kishan.kavach.domain.agent.Route
import com.kishan.kavach.domain.skills.Skill
import com.kishan.kavach.domain.skills.SkillFormat
import com.kishan.kavach.domain.tools.Guard
import com.kishan.kavach.domain.tools.ToolGroup

fun interface MemoryProvider {
    /** Relevant notes/memories for the query (already filtered by a score threshold). */
    suspend fun relevant(query: String, k: Int): List<String>
}

/**
 * Hybrid routing (spec section 19): Guard -> deterministic rules -> skill match -> notes Q&A -> Gemma agent.
 * Gemma is only used when nothing reliable matches.
 */
class TaskRouter(
    private val skills: () -> List<Skill>,
    private val memory: MemoryProvider,
    private val accessibilityOn: () -> Boolean,
) {
    suspend fun route(text: String): Route {
        Guard.checkUserText(text)?.let { return Route.Blocked(it) }
        InstantRules.match(text, accessibilityOn())?.let { return it }
        for (s in skills()) {
            SkillFormat.extract(s, text)?.let { return Route.UseSkill(s.name, it) }
            // parameterless skills: matched by pattern alone
            if (s.params.none { it.required } && s.matchers.any { it.containsMatchIn(text) }) return Route.UseSkill(s.name, emptyMap())
        }
        val hits = memory.relevant(text, 3)
        if (hits.isNotEmpty() && !TaskClassifier.groups(text).contains(ToolGroup.SCREEN))
            return Route.UseAgent(setOf(ToolGroup.CORE, ToolGroup.UTILITY), hits.joinToString("\n") { "- $it" }, chatOnly = true)
        return Route.UseAgent(TaskClassifier.groups(text), hits.joinToString("\n") { "- $it" })
    }
}
