package com.kishan.kavach.automation.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseSchedulerTest {

    @Test
    fun `successful exact set produces promise sentence`() {
        val triggerAt = System.currentTimeMillis() + 3_600_000L
        val scheduled = Scheduled.Set(id = 1, atMillis = triggerAt, exact = true)

        val response = PromiseScheduler.buildResponse(scheduled, "Call Maa")
        assertTrue(response.contains("yaad dilaunga"))
        assertTrue(response.contains("exact alarm"))
        assertFalse(response.contains("Maafi chahta hu"))
    }

    @Test
    fun `fallback non-exact set mentions WorkManager`() {
        val triggerAt = System.currentTimeMillis() + 3_600_000L
        val scheduled = Scheduled.Set(id = 2, atMillis = triggerAt, exact = false)

        val response = PromiseScheduler.buildResponse(scheduled, "Call Maa")
        assertTrue(response.contains("yaad dilaunga"))
        assertTrue(response.contains("WorkManager"))
    }

    @Test
    fun `failed schedule produces apology and no promise`() {
        val scheduled = Scheduled.Failed("Time is in the past")

        val response = PromiseScheduler.buildResponse(scheduled, "Call Maa")
        assertTrue(response.contains("Maafi chahta hu"))
        assertFalse(response.contains("yaad dilaunga"))
    }
}
