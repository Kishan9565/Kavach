package com.kishan.kavach.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineBuilderTest {

    @Test
    fun `empty screen produces empty outline`() {
        val outline = OutlineBuilder.build(emptyList(), 1080, 2400)
        assertTrue(outline.rows.isEmpty())
        assertEquals("", outline.text)
    }

    @Test
    fun `contiguous ids generated e0 to eN`() {
        val nodes = listOf(
            createNode(text = "Item 1"),
            createNode(text = "Item 2"),
            createNode(text = "Item 3")
        )
        val outline = OutlineBuilder.build(nodes, 1000, 1000)
        assertEquals(3, outline.rows.size)
        assertEquals("e0", outline.rows[0].idStr)
        assertEquals("e1", outline.rows[1].idStr)
        assertEquals("e2", outline.rows[2].idStr)
    }

    @Test
    fun `label fallback uses text then desc then viewId tail`() {
        val node1 = createNode(text = "  Text Label  ", desc = "Desc", viewId = "app:id/btn")
        assertEquals("Text Label", node1.label)

        val node2 = createNode(text = "", desc = "Desc Label", viewId = "app:id/btn")
        assertEquals("Desc Label", node2.label)

        val node3 = createNode(text = null, desc = null, viewId = "com.app:id/submit_button")
        assertEquals("submit_button", node3.label)
    }

    @Test
    fun `indentation capped at 4 levels`() {
        val nodeDeep = createNode(text = "Deep Node", depth = 10)
        val outline = OutlineBuilder.build(listOf(nodeDeep), 1000, 1000)
        val line = outline.rows[0].formattedLine
        
        // 4 levels of indentation = 8 spaces
        assertTrue(line.contains("  ".repeat(4)))
        assertFalse(line.contains("  ".repeat(5)))
    }

    @Test
    fun `trimming keeps clickable and editable nodes first`() {
        val interactiveNodes = (1..50).map { i ->
            createNode(text = "Clickable $i", clickable = true, left = 0, top = i, right = 10, bottom = i + 1)
        }
        val passiveNodes = (1..30).map { i ->
            createNode(text = "Passive $i", clickable = false, left = 0, top = 100 + i, right = 50, bottom = 100 + i + 10)
        }
        
        val outline = OutlineBuilder.build(interactiveNodes + passiveNodes, 1000, 1000, maxRows = 60)
        assertEquals(60, outline.rows.size)
        
        // All 50 interactive nodes must be present
        val interactiveCount = outline.rows.count { it.node.clickable }
        assertEquals(50, interactiveCount)
        
        // Contiguous IDs
        assertEquals("e0", outline.rows.first().idStr)
        assertEquals("e59", outline.rows.last().idStr)
    }

    @Test
    fun `offscreen or empty nodes dropped`() {
        val offscreen = createNode(text = "Offscreen", left = -100, top = -100, right = 0, bottom = 0)
        val emptyArea = createNode(text = "Empty", left = 10, top = 10, right = 10, bottom = 10)
        val valid = createNode(text = "Valid", left = 10, top = 10, right = 100, bottom = 100)

        val outline = OutlineBuilder.build(listOf(offscreen, emptyArea, valid), 1000, 1000)
        assertEquals(1, outline.rows.size)
        assertEquals("Valid", outline.rows[0].node.text)
    }

    private fun createNode(
        text: String? = null,
        desc: String? = null,
        viewId: String? = null,
        cls: String = "android.widget.Button",
        left: Int = 0,
        top: Int = 0,
        right: Int = 100,
        bottom: Int = 100,
        clickable: Boolean = true,
        editable: Boolean = false,
        scrollable: Boolean = false,
        depth: Int = 0
    ) = UiNode(
        pkg = "com.test.app",
        viewId = viewId,
        cls = cls,
        text = text,
        desc = desc,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        clickable = clickable,
        editable = editable,
        scrollable = scrollable,
        depth = depth
    )
}
