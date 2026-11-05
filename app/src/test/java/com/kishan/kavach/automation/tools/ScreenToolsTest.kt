package com.kishan.kavach.automation.tools

import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.automation.accessibility.ScreenController
import com.kishan.kavach.domain.agent.ScreenSnapshot
import com.kishan.kavach.domain.screen.OutlineRow
import com.kishan.kavach.domain.screen.UiNode
import com.kishan.kavach.domain.tools.Args
import com.kishan.kavach.domain.tools.ErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenToolsTest {

    private class FakeController : ScreenController {
        override val connected: Boolean = true
        override val screenWidth: Int = 1080
        override val screenHeight: Int = 2400
        override suspend fun snapshot(): ScreenSnapshot = ScreenSnapshot("com.test.app", emptyList())
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
        ScreenBridge.controller = FakeController()
    }

    @Test
    fun `tap with empty registry fails with INVALID_ARGS`() = runBlocking {
        val tool = ScreenTools.tap
        val args = Args(mapOf("element_id" to 0))
        
        // Ensure registry is empty
        ScreenTools.eidRegistry.replace(emptyList())

        val result = tool.execute(args)
        assertEquals(false, result.success)
        assertEquals(ErrorCode.INVALID_ARGS, result.errorCode)
        assertTrue(result.message.contains("No outline yet"))
    }

    @Test
    fun `tap looks up eid from registry`() {
        val node = UiNode(
            pkg = "com.app",
            viewId = "app:id/btn",
            cls = "android.widget.Button",
            text = "Normal Action",
            desc = null,
            left = 10, top = 10, right = 100, bottom = 100,
            clickable = true, editable = false, scrollable = false, depth = 0
        )
        ScreenTools.eidRegistry.replace(listOf(OutlineRow(5, "e5", node, "e5* Button Normal Action (55,55)")))

        val retrieved = ScreenTools.eidRegistry.lookup(5)
        assertEquals(node, retrieved)
    }
}
