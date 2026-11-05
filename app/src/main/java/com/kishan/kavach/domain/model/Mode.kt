package com.kishan.kavach.domain.model

data class Mode(
    val id: Long,
    val name: String,
    val goal: String,
    val rules: List<String>,
    val adoptedAt: Long
) {
    fun toPromptInstruction(): String {
        val rulesText = rules.joinToString("; ").take(300)
        return "ACTIVE MODE [$name]: Goal: $goal. Rules: $rulesText"
    }

    companion object {
        val PROPOSALS = listOf(
            Mode(
                id = 1,
                name = "Focus",
                goal = "Minimize distractions",
                rules = listOf("TELL only urgent notifications", "Do not interrupt unless high priority"),
                adoptedAt = 0L
            ),
            Mode(
                id = 2,
                name = "Driving",
                goal = "Hands-free navigation and voice assistance",
                rules = listOf("Voice responses only", "Do not tap on screen tools or open heavy apps"),
                adoptedAt = 0L
            ),
            Mode(
                id = 3,
                name = "Night",
                goal = "Quiet night time rest",
                rules = listOf("IGNORE everything except incoming calls", "Do not speak or trigger alarms"),
                adoptedAt = 0L
            )
        )
    }
}
