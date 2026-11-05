package com.kishan.kavach.automation.tools

import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.automation.accessibility.ScreenController
import com.kishan.kavach.domain.agent.ScreenSnapshot
import com.kishan.kavach.domain.agent.UiNode
import com.kishan.kavach.domain.tools.Args
import com.kishan.kavach.domain.tools.ErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TypeAndSubmitTest {

    private class SensitiveScreenController : ScreenController {
        override val connected: Boolean = true
        override val screenWidth: Int = 1080
        override val screenHeight: Int = 2400
        override suspend fun snapshot(): ScreenSnapshot = ScreenSnapshot(
            packageName = "com.test.app",
            nodes = listOf(
                UiNode(0, "EditText", "Enter OTP Code", false, true, false, 0, 0, 100, 100, password = false)
            )
        )
        override suspend fun tapElement(id: Int): Boolean = true
        override suspend fun tapText(text: String): Boolean = true
        override suspend fun tapAt(x: Int, y: Int): Boolean = true
        override suspend fun longPressElement(id: Int): Boolean = true
        override suspend fun longPressText(text: String): Boolean = true
        override suspend fun longPressAt(x: Int, y: Int): Boolean = true
        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean = true
        override suspend fun scroll(forward: Boolean): Boolean = true
        override suspend fun inputText(text: String, submit: Boolean): Boolean = true
        override suspend fun pressSendButton(): Boolean = true
        override fun back(): Boolean = true
        override fun home(): Boolean = true
        override suspend fun screenContains(text: String, waitMs: Long): Boolean = false
        override suspend fun screenshot(): String? = null
        override fun highlight(text: String) {}
        override fun caption(text: String) {}
    }

    @Before
    fun setup() {
        ScreenBridge.controller = SensitiveScreenController()
    }

    @Test
    fun `typeText denies typing into OTP field`() = runBlocking {
        val tool = ScreenTools.typeText
        val args = Args(mapOf("text" to "123456"))

        val result = tool.execute(args)
        assertFalse(result.success)
        assertEquals(ErrorCode.BLOCKED, result.errorCode)
        assertTrue(result.message.contains("Sensitive field detected"))
    }
}
