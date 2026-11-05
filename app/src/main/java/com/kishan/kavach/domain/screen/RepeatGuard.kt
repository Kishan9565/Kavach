package com.kishan.kavach.domain.screen

/**
 * Prevents the agent from endlessly tapping the exact same screen coordinates within a short time window.
 * Only coordinate-based/guessed taps call this; taps targeting a specific element ID are exempt.
 */
class RepeatGuard(
    private val clock: () -> Long = System::currentTimeMillis,
    private val radius: Int = 48,
    private val windowMs: Long = 20_000,
    private val maxTries: Int = 3
) {
    private val hits = HashMap<String, Int>()
    private var windowStart = 0L

    @Synchronized
    fun check(x: Int, y: Int) {
        val now = clock()
        if (now - windowStart > windowMs) {
            hits.clear()
            windowStart = now
        }

        val key = "${x / radius},${y / radius}"
        val n = (hits[key] ?: 0) + 1
        hits[key] = n

        if (n > maxTries) {
            throw IllegalStateException("Refused: 4th tap on the same spot. Stop and ask the user.")
        }
    }

    @Synchronized
    fun reset() {
        hits.clear()
        windowStart = 0L
    }
}
