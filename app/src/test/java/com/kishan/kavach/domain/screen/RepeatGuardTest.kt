package com.kishan.kavach.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RepeatGuardTest {

    @Test
    fun `allows taps up to maxTries`() {
        val currentTime = 1000L
        val guard = RepeatGuard(clock = { currentTime }, radius = 48, windowMs = 20_000, maxTries = 3)

        // 1st, 2nd, 3rd tap at same coordinates should succeed
        guard.check(100, 200)
        guard.check(100, 200)
        guard.check(100, 200)
    }

    @Test
    fun `refuses 4th tap on same spot within window`() {
        var currentTime = 1000L
        val guard = RepeatGuard(clock = { currentTime }, radius = 48, windowMs = 20_000, maxTries = 3)

        guard.check(100, 200)
        guard.check(100, 200)
        guard.check(100, 200)

        try {
            guard.check(100, 200)
            fail("Expected IllegalStateException on 4th tap")
        } catch (e: IllegalStateException) {
            assertEquals("Refused: 4th tap on the same spot. Stop and ask the user.", e.message)
        }
    }

    @Test
    fun `resets hit count after windowMs expires`() {
        var currentTime = 1000L
        val guard = RepeatGuard(clock = { currentTime }, radius = 48, windowMs = 20_000, maxTries = 3)

        guard.check(100, 200)
        guard.check(100, 200)
        guard.check(100, 200)

        // Advance time past 20 seconds
        currentTime += 21_000L

        // Should succeed as window reset
        guard.check(100, 200)
    }
}
