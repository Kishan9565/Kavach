package com.kishan.kavach.ai.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetsTest {

    @Test
    fun `budget constants adhere to Gemma limits`() {
        assertEquals(6, Budgets.MAX_LOOP_STEPS)
        assertEquals(60, Budgets.MAX_OUTLINE_ROWS)
        assertEquals(1500, Budgets.MAX_OUTLINE_TOKENS)
        assertEquals(300, Budgets.MAX_NOTIFICATION_CHARS)
        assertEquals(300, Budgets.MAX_TRIAGE_TOKENS)
        assertEquals(5, Budgets.MAX_HISTORY_TURNS)
    }
}
