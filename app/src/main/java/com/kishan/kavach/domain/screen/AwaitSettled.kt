package com.kishan.kavach.domain.screen

/**
 * Polls the screen fingerprint until it stays unchanged for [quietMs] consecutive milliseconds
 * or until [maxMs] time limit is reached. Injects [now] and [pause] for deterministic unit testing.
 */
suspend fun awaitSettled(
    read: () -> Fingerprint,
    now: () -> Long,
    pause: suspend (Long) -> Unit,
    quietMs: Long = 250,
    maxMs: Long = 2500
): Fingerprint {
    var last = read()
    var since = now()
    val end = since + maxMs

    while (now() < end) {
        pause(80)
        val cur = read()
        if (!ScreenDiff.changed(last, cur)) {
            if (now() - since >= quietMs) {
                return cur
            }
        } else {
            last = cur
            since = now()
        }
    }

    return last
}
