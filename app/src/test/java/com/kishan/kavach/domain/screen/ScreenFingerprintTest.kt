package com.kishan.kavach.domain.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFingerprintTest {

    @Test
    fun `pkg difference triggers changed`() {
        val f1 = Fingerprint("com.app.a", 10, 12345)
        val f2 = Fingerprint("com.app.b", 10, 12345)
        assertTrue(ScreenDiff.changed(f1, f2))
    }

    @Test
    fun `labelHash difference triggers changed`() {
        val f1 = Fingerprint("com.app", 10, 12345)
        val f2 = Fingerprint("com.app", 10, 67890)
        assertTrue(ScreenDiff.changed(f1, f2))
    }

    @Test
    fun `node count difference of 3 or more triggers changed`() {
        val f1 = Fingerprint("com.app", 10, 12345)
        val f2 = Fingerprint("com.app", 13, 12345)
        assertTrue(ScreenDiff.changed(f1, f2))
    }

    @Test
    fun `node count difference less than 3 does not trigger changed if hash same`() {
        val f1 = Fingerprint("com.app", 10, 12345)
        val f2 = Fingerprint("com.app", 12, 12345)
        assertFalse(ScreenDiff.changed(f1, f2))
    }

    @Test
    fun `clock labels are ignored when computing fingerprint`() {
        val node1 = createNode("10:45 AM")
        val node2 = createNode("Home")

        val fp1 = ScreenDiff.compute("com.app", listOf(node1, node2))

        val node1Updated = createNode("10:46 AM")
        val fp2 = ScreenDiff.compute("com.app", listOf(node1Updated, node2))

        assertFalse(ScreenDiff.changed(fp1, fp2))
    }

    private fun createNode(text: String) = UiNode(
        pkg = "com.app", viewId = null, cls = "TextView", text = text, desc = null,
        left = 0, top = 0, right = 100, bottom = 50, clickable = false, editable = false, scrollable = false, depth = 0
    )
}
