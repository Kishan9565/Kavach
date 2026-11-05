package com.kishan.kavach.domain.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {

    @Test
    fun `trusted origins can use any tool`() {
        val trustedOrigins = setOf(Origin.USER, Origin.AUTOMATION, Origin.SKILL)
        val allTools = ToolPolicy.DENIED_FOR_UNTRUSTED + setOf("tap", "read_screen")

        for (origin in trustedOrigins) {
            for (tool in allTools) {
                assertTrue("Failed for $tool with origin $origin", ToolPolicy.allowed(tool, origin))
            }
        }
    }

    @Test
    fun `untrusted origins cannot use denied tools`() {
        for (origin in ToolPolicy.UNTRUSTED) {
            for (tool in ToolPolicy.DENIED_FOR_UNTRUSTED) {
                assertFalse("Failed for $tool with origin $origin", ToolPolicy.allowed(tool, origin))
            }
        }
    }

    @Test
    fun `untrusted origins can use non-denied tools`() {
        val safeTools = setOf("tap", "read_screen", "scroll", "get_status")
        
        for (origin in ToolPolicy.UNTRUSTED) {
            for (tool in safeTools) {
                assertTrue("Failed for $tool with origin $origin", ToolPolicy.allowed(tool, origin))
            }
        }
    }
}