package com.kishan.kavach.services.notification

enum class Triage { ACT, TELL, IGNORE, ASK }

data class TriageResult(val action: Triage, val reason: String)

object TriageParser {

    private val triageRegex = Regex("""^(ACT|TELL|IGNORE) *:\s*(.{0,80})$""", RegexOption.IGNORE_CASE)

    fun parse(modelReply: String): TriageResult {
        val firstLine = modelReply.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        val match = triageRegex.find(firstLine)

        if (match == null) {
            return TriageResult(Triage.ASK, "Could not parse model response")
        }

        val typeStr = match.groupValues[1].uppercase()
        val reason = match.groupValues[2].trim()

        val triage = when (typeStr) {
            "ACT" -> Triage.ACT
            "TELL" -> Triage.TELL
            "IGNORE" -> Triage.IGNORE
            else -> Triage.ASK
        }

        return TriageResult(triage, reason)
    }
}
