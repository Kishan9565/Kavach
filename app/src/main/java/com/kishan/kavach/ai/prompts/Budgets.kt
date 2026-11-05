package com.kishan.kavach.ai.prompts

import android.util.Log

/**
 * Single source of truth for Gemma context and loop limits (H15).
 * Gemma is a small model, so budget enforcement is critical.
 */
object Budgets {
    const val MAX_OUTLINE_ROWS = 60
    const val MAX_OUTLINE_TOKENS = 1500
    const val MAX_LOOP_STEPS = 6
    const val MAX_NOTIFICATION_CHARS = 300
    const val MAX_TRIAGE_TOKENS = 300
    const val MAX_HISTORY_TURNS = 5

    fun logPromptSize(tag: String, promptText: String) {
        val chars = promptText.length
        val approxTokens = chars / 4
        Log.d("Budgets", "[$tag] Prompt chars: $chars (~$approxTokens tokens)")
    }
}
