package com.kishan.kavach.automation.external

/**
 * Optional entry point for Tasker / MacroDroid / Locale (spec section 11). Pure Kotlin so the security rules are unit-tested.
 * Security model: disabled by default; needs the user's secret token; caller package must be on the user's allow-list when
 * Android can tell us who sent the broadcast (API 34+); requests are rate limited; the task then goes through the normal
 * router, safety validator and confirmation flow (confirmations are never auto-approved).
 */
object ExternalAutomationContract {
    const val ACTION_RUN_TASK = "com.kishan.kavach.action.RUN_TASK"
    const val ACTION_RUN_CHAT = "com.kishan.kavach.action.RUN_CHAT"
    const val ACTION_STATUS = "com.kishan.kavach.action.STATUS"          // sent back to the callback package
    const val EXTRA_TOKEN = "token"
    const val EXTRA_TASK = "task"
    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_CALLBACK_PACKAGE = "callback_package"
    const val EXTRA_STATUS = "status"        // ACCEPTED | COMPLETED | FAILED | CANCELLED | REJECTED
    const val EXTRA_MESSAGE = "message"
    const val MAX_TASK_LEN = 500
    const val MIN_GAP_MS = 5_000L

    sealed interface Decision {
        data object Accept : Decision
        data class Reject(val reason: String) : Decision
    }

    fun validate(
        enabled: Boolean, expectedToken: String, action: String?, token: String?, task: String?, requestId: String?,
        senderPackage: String?, allowedPackages: List<String>, senderVerifiable: Boolean, callbackPackage: String?,
        lastAcceptedAt: Long, now: Long,
    ): Decision {
        if (!enabled) return Decision.Reject("external automation is disabled")
        if (action != ACTION_RUN_TASK && action != ACTION_RUN_CHAT) return Decision.Reject("unknown action")
        if (token == null || !constantTimeEquals(token, expectedToken)) return Decision.Reject("bad token")
        if (senderVerifiable && (senderPackage == null || senderPackage !in allowedPackages)) return Decision.Reject("caller not allowed")
        if (requestId.isNullOrBlank() || requestId.length > 64 || !requestId.matches(Regex("[A-Za-z0-9._:-]+"))) return Decision.Reject("bad request_id")
        if (task.isNullOrBlank() || task.length > MAX_TASK_LEN) return Decision.Reject("task missing or too long")
        if (callbackPackage != null && callbackPackage !in allowedPackages) return Decision.Reject("callback package not allowed")
        if (now - lastAcceptedAt < MIN_GAP_MS) return Decision.Reject("rate limited")
        return Decision.Accept
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}
