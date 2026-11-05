package com.kishan.kavach.services.notification

data class NotifIn(
    val pkg: String,
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val postedAt: Long
)

sealed interface Gate {
    data class Skip(val why: String) : Gate
    object Offer : Gate
}

class NotificationGate(
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: Set<String>,
    private val maxPerHour: Int = 20,
    private val maxAgeMs: Long = 600_000
) {
    private val stamps = ArrayDeque<Long>()

    @Synchronized
    fun decide(n: NotifIn): Gate {
        val now = clock()
        if (n.pkg !in scope) return Gate.Skip("out of scope")
        if (n.ongoing || n.groupSummary) return Gate.Skip("structural")
        if (now - n.postedAt > maxAgeMs) return Gate.Skip("too old")

        while (stamps.isNotEmpty() && now - stamps.first() > 3_600_000) {
            stamps.removeFirst()
        }

        if (stamps.size >= maxPerHour) return Gate.Skip("hourly cap")

        stamps.addLast(now)
        return Gate.Offer
    }
}
