package com.kishan.kavach.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelocatorTest {

    @Test
    fun `empty EidRegistry lookup returns null`() {
        val registry = EidRegistry()
        assertTrue(registry.isEmpty())
        assertNull(registry.lookup(0))
    }

    @Test
    fun `EidRegistry lookup returns registered node`() {
        val registry = EidRegistry()
        val node = createNode("Submit", viewId = "app:id/submit")
        val row = OutlineRow(0, "e0", node, "e0* Button Submit (500,500)")

        registry.replace(listOf(row))
        assertEquals(node, registry.lookup(0))
        assertNull(registry.lookup(1))
    }

    @Test
    fun `relocate matches list shifted down by 200px`() {
        val oldNode = createNode("Item 1", viewId = "app:id/item", top = 100, bottom = 150)
        
        // Live screen list shifted down by 200px
        val liveNode1 = createNode("Header", viewId = "app:id/header", top = 0, bottom = 50)
        val liveNode2 = createNode("Item 1", viewId = "app:id/item", top = 300, bottom = 350)

        val located = Relocator.relocate(oldNode, listOf(liveNode1, liveNode2))
        assertTrue(located is Located.Found)
        assertEquals(liveNode2, (located as Located.Found).node)
    }

    @Test
    fun `relocate chooses nearest node when duplicate viewIds exist`() {
        val oldNode = createNode("Option", viewId = "app:id/option", top = 500, bottom = 550)

        val farCandidate = createNode("Option", viewId = "app:id/option", top = 100, bottom = 150)
        val nearCandidate = createNode("Option", viewId = "app:id/option", top = 480, bottom = 530)

        val located = Relocator.relocate(oldNode, listOf(farCandidate, nearCandidate))
        assertTrue(located is Located.Found)
        assertEquals(nearCandidate, (located as Located.Found).node)
    }

    @Test
    fun `relocate returns Remembered coordinates when element vanishes`() {
        val oldNode = createNode("Disappeared", viewId = "app:id/gone", left = 200, top = 400, right = 400, bottom = 500)
        val liveList = listOf(createNode("Other", viewId = "app:id/other"))

        val located = Relocator.relocate(oldNode, liveList)
        assertTrue(located is Located.Remembered)
        val remembered = located as Located.Remembered
        assertEquals(300, remembered.cx)
        assertEquals(450, remembered.cy)
    }

    private fun createNode(
        text: String,
        pkg: String = "com.test.app",
        viewId: String? = null,
        cls: String = "android.widget.TextView",
        left: Int = 0,
        top: Int = 0,
        right: Int = 100,
        bottom: Int = 100
    ) = UiNode(
        pkg = pkg,
        viewId = viewId,
        cls = cls,
        text = text,
        desc = null,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        clickable = true,
        editable = false,
        scrollable = false,
        depth = 1
    )
}
