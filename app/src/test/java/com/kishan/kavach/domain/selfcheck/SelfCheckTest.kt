package com.kishan.kavach.domain.selfcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfCheckTest {

    @Test
    fun `all good snapshot produces OK findings`() {
        val snapshot = SelfCheckSnapshot(
            modelImported = true,
            modelLoaded = true,
            accessibilityConnected = true,
            inputMethodSupported = true,
            inputMethodConnected = true,
            notificationAccessEnabled = true,
            notificationListenerConnected = true,
            micPermissionGranted = true,
            ignoringBatteryOptimizations = true,
            canScheduleExactAlarms = true,
            foregroundServiceRunning = true
        )

        val findings = SelfCheckEvaluator.evaluate(snapshot)
        assertTrue(findings.all { it.level == Level.OK })

        val attentionNeeded = findings.filter { needsAttention(it) }
        assertTrue(attentionNeeded.isEmpty())
    }

    @Test
    fun `missing model and accessibility produce FAIL findings`() {
        val snapshot = SelfCheckSnapshot(
            modelImported = false,
            modelLoaded = false,
            accessibilityConnected = false,
            inputMethodSupported = false,
            inputMethodConnected = false,
            notificationAccessEnabled = false,
            notificationListenerConnected = false,
            micPermissionGranted = false,
            ignoringBatteryOptimizations = false,
            canScheduleExactAlarms = false,
            foregroundServiceRunning = false
        )

        val findings = SelfCheckEvaluator.evaluate(snapshot)
        val fails = findings.filter { it.level == Level.FAIL }

        assertEquals(2, fails.size)
        assertTrue(fails.any { it.id == "model_load" })
        assertTrue(fails.any { it.id == "accessibility" })
    }

    @Test
    fun `needsAttention includes only FAIL and WARN`() {
        val failFinding = Finding("1", Level.FAIL, "Fail Title", "Fix")
        val warnFinding = Finding("2", Level.WARN, "Warn Title", "Fix")
        val noteFinding = Finding("3", Level.NOTE, "Note Title", "Fix")
        val okFinding = Finding("4", Level.OK, "OK Title", null)

        assertTrue(needsAttention(failFinding))
        assertTrue(needsAttention(warnFinding))
        assertFalse(needsAttention(noteFinding))
        assertFalse(needsAttention(okFinding))
    }
}
