package com.kishan.kavach.core.permissions

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import com.kishan.kavach.domain.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionGateTest {

    private open class TestContext(private val granted: Boolean) : ContextWrapper(null) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        override fun checkSelfPermission(permission: String): Int {
            return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
    }

    @Test
    fun `withPermission executes block when granted`() {
        val ctx = TestContext(granted = true)
        val result = withPermission(ctx, "android.permission.READ_CONTACTS", "Contacts") {
            ToolResult.Ok(saw = "Access granted")
        }
        assertTrue(result is ToolResult.Ok)
        assertEquals("Access granted", (result as ToolResult.Ok).saw)
    }

    @Test
    fun `withPermission returns Denied when permission refused`() {
        val ctx = TestContext(granted = false)
        val result = withPermission(ctx, "android.permission.READ_CONTACTS", "Contacts") {
            ToolResult.Ok(saw = "Access granted")
        }
        assertTrue(result is ToolResult.Denied)
        val denied = result as ToolResult.Denied
        assertEquals("Contacts", denied.what)
        assertTrue(denied.hint.contains("Settings > Apps > Draivo > Permissions"))
    }
}
