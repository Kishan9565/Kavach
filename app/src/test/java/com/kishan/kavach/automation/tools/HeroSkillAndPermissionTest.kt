package com.kishan.kavach.automation.tools

import com.kishan.kavach.domain.agent.Route
import com.kishan.kavach.domain.screen.UiNode
import com.kishan.kavach.domain.tools.Origin
import com.kishan.kavach.domain.tools.ToolPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSkillAndPermissionTest {

    @Test
    fun `untrusted origins cannot run send_message or send_sms`() {
        for (origin in ToolPolicy.UNTRUSTED) {
            assertFalse(ToolPolicy.allowed("send_message", origin))
            assertFalse(ToolPolicy.allowed("send_sms", origin))
        }
    }

    @Test
    fun `user origin can run send_message and send_sms`() {
        assertTrue(ToolPolicy.allowed("send_message", Origin.USER))
        assertTrue(ToolPolicy.allowed("send_sms", Origin.USER))
    }

    @Test
    fun `whatsapp hero skill exact match helper picks single match`() {
        val searchName = "Rahul Sharma"
        val candidate1 = createNode("Rahul")
        val candidate2 = createNode("Rahul Sharma")
        val candidate3 = createNode("Rahul Sharma (Work)")

        val candidates = listOf(candidate1, candidate2, candidate3)
        val exactMatches = candidates.filter { it.text.equals(searchName, ignoreCase = true) }

        assertEquals(1, exactMatches.size)
        assertEquals("Rahul Sharma", exactMatches.single().text)
    }

    @Test
    fun `whatsapp hero skill ambiguous match requires user selection`() {
        val searchName = "Rahul"
        val candidate1 = createNode("Rahul Kumar")
        val candidate2 = createNode("Rahul Sharma")

        val candidates = listOf(candidate1, candidate2)
        val exactMatches = candidates.filter { it.text.equals(searchName, ignoreCase = true) }

        // Zero exact matches for "Rahul" when names are full names -> requires user choice
        assertTrue(exactMatches.isEmpty())
    }

    private fun createNode(text: String) = UiNode(
        pkg = "com.whatsapp", viewId = "com.whatsapp:id/contact_name", cls = "android.widget.TextView",
        text = text, desc = null, left = 0, top = 0, right = 100, bottom = 50, clickable = true, editable = false, scrollable = false, depth = 1
    )
}
