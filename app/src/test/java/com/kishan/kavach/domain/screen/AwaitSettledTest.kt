package com.kishan.kavach.domain.screen

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AwaitSettledTest {

    @Test
    fun `settles quickly when screen does not change`() = runBlocking {
        var currentTime = 0L
        val fp = Fingerprint("com.app", 10, 100)

        val result = awaitSettled(
            read = { fp },
            now = { currentTime },
            pause = { ms -> currentTime += ms },
            quietMs = 250,
            maxMs = 2500
        )

        assertEquals(fp, result)
        // 80ms * 4 = 320ms >= 250ms quietMs
        assertEquals(320L, currentTime)
    }

    @Test
    fun `returns at maxMs if screen never settles`() = runBlocking {
        var currentTime = 0L
        var counter = 0

        val result = awaitSettled(
            read = { Fingerprint("com.app", 10, counter++) },
            now = { currentTime },
            pause = { ms -> currentTime += ms },
            quietMs = 250,
            maxMs = 1000
        )

        // Elapsed time must reach maxMs
        assertTrue(currentTime >= 1000L)
        assertEquals("com.app", result.pkg)
    }

    @Test
    fun `changes once then settles`() = runBlocking {
        var currentTime = 0L
        val fp1 = Fingerprint("com.app", 10, 100)
        val fp2 = Fingerprint("com.app", 10, 200)

        // First 2 calls return fp1, subsequent return fp2
        var callCount = 0
        val readFn = {
            callCount++
            if (callCount <= 2) fp1 else fp2
        }

        val result = awaitSettled(
            read = readFn,
            now = { currentTime },
            pause = { ms -> currentTime += ms },
            quietMs = 250,
            maxMs = 2500
        )

        assertEquals(fp2, result)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
