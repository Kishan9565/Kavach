package com.kishan.kavach.services.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGateTest {

    private val scope = setOf("com.whatsapp", "com.google.android.apps.messaging")

    @Test
    fun `skips notification out of scope`() {
        val gate = NotificationGate(clock = { 1000000L }, scope = scope)
        val notif = NotifIn("com.other.app", "Title", "Text", ongoing = false, groupSummary = false, postedAt = 1000000L)

        val result = gate.decide(notif)
        assertTrue(result is Gate.Skip)
        assertEquals("out of scope", (result as Gate.Skip).why)
    }

    @Test
    fun `skips ongoing or group summary notifications`() {
        val gate = NotificationGate(clock = { 1000000L }, scope = scope)
        val ongoingNotif = NotifIn("com.whatsapp", "Title", "Text", ongoing = true, groupSummary = false, postedAt = 1000000L)
        val summaryNotif = NotifIn("com.whatsapp", "Title", "Text", ongoing = false, groupSummary = true, postedAt = 1000000L)

        val res1 = gate.decide(ongoingNotif)
        assertTrue(res1 is Gate.Skip)
        assertEquals("structural", (res1 as Gate.Skip).why)

        val res2 = gate.decide(summaryNotif)
        assertTrue(res2 is Gate.Skip)
        assertEquals("structural", (res2 as Gate.Skip).why)
    }

    @Test
    fun `skips notifications older than maxAgeMs`() {
        val gate = NotificationGate(clock = { 1000000L }, scope = scope, maxAgeMs = 600_000L)
        val oldNotif = NotifIn("com.whatsapp", "Title", "Text", ongoing = false, groupSummary = false, postedAt = 300_000L)

        val result = gate.decide(oldNotif)
        assertTrue(result is Gate.Skip)
        assertEquals("too old", (result as Gate.Skip).why)
    }

    @Test
    fun `enforces hourly cap and recovers after an hour`() {
        var currentTime = 1000000L
        val gate = NotificationGate(clock = { currentTime }, scope = scope, maxPerHour = 3)
        val notif = NotifIn("com.whatsapp", "Title", "Text", ongoing = false, groupSummary = false, postedAt = 1000000L)

        // First 3 allowed
        assertEquals(Gate.Offer, gate.decide(notif))
        assertEquals(Gate.Offer, gate.decide(notif))
        assertEquals(Gate.Offer, gate.decide(notif))

        // 4th cap reached
        val resCap = gate.decide(notif)
        assertTrue(resCap is Gate.Skip)
        assertEquals("hourly cap", (resCap as Gate.Skip).why)

        // Advance clock past 1 hour (3,600,000 ms)
        currentTime += 3_600_001L
        val notif2 = NotifIn("com.whatsapp", "Title", "Text", ongoing = false, groupSummary = false, postedAt = currentTime)

        // Now recovers
        assertEquals(Gate.Offer, gate.decide(notif2))
    }
}
