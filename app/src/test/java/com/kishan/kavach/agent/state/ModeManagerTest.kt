package com.kishan.kavach.agent.state

import com.kishan.kavach.domain.model.Mode
import com.kishan.kavach.domain.tools.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeManagerTest {

    @Test
    fun `user origin can adopt mode`() {
        val manager = ModeManager()
        val focusMode = Mode.PROPOSALS.first { it.name == "Focus" }

        val adopted = manager.adopt(focusMode, Origin.USER)
        assertTrue(adopted)
        assertEquals(focusMode.name, manager.activeMode.value?.name)
        assertNotNull(manager.activePromptText())
    }

    @Test
    fun `notification or idle origin cannot adopt mode`() {
        val manager = ModeManager()
        val drivingMode = Mode.PROPOSALS.first { it.name == "Driving" }

        val adoptedNotif = manager.adopt(drivingMode, Origin.NOTIFICATION)
        assertFalse(adoptedNotif)
        assertNull(manager.activeMode.value)

        val adoptedIdle = manager.adopt(drivingMode, Origin.IDLE)
        assertFalse(adoptedIdle)
        assertNull(manager.activeMode.value)
    }

    @Test
    fun `exit mode clears active mode`() {
        val manager = ModeManager()
        val nightMode = Mode.PROPOSALS.first { it.name == "Night" }

        manager.adopt(nightMode, Origin.USER)
        assertNotNull(manager.activeMode.value)

        manager.exit()
        assertNull(manager.activeMode.value)
    }

    private fun assertNotNull(actual: Any?) {
        org.junit.Assert.assertNotNull(actual)
    }
}
