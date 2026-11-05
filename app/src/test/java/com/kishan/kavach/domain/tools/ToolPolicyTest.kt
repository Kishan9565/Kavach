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
        val externalAndNotif = setOf(Origin.NOTIFICATION, Origin.EXTERNAL)
        for (origin in externalAndNotif) {
            for (tool in ToolPolicy.DENIED_FOR_UNTRUSTED) {
                assertFalse("Failed for $tool with origin $origin", ToolPolicy.allowed(tool, origin))
            }
        }
    }

    @Test
    fun `idle origin allows only allow-listed tools`() {
        val allowedTools = ToolPolicy.IDLE_ALLOW_LIST
        val forbiddenTools = setOf("tap", "send_message", "send_sms", "open_app", "scroll", "swipe")

        for (tool in allowedTools) {
            assertTrue("Expected $tool to be allowed for IDLE", ToolPolicy.allowed(tool, Origin.IDLE))
        }

        for (tool in forbiddenTools) {
            assertFalse("Expected $tool to be denied for IDLE", ToolPolicy.allowed(tool, Origin.IDLE))
        }
    }
}
