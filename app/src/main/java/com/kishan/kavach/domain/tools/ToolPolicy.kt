package com.kishan.kavach.domain.tools

enum class Origin {
    USER,
    NOTIFICATION,
    EXTERNAL,
    AUTOMATION,
    SKILL,
    IDLE
}

/**
 * Mechanical policy table for tool execution based on the request's Origin.
 * No confirmation can unlock a tool if the policy denies it.
 */
object ToolPolicy {
    val UNTRUSTED = setOf(Origin.NOTIFICATION, Origin.EXTERNAL, Origin.IDLE)
    val DENIED_FOR_UNTRUSTED = setOf(
        "send_message", 
        "send_sms", 
        "auto_reply", 
        "create_mode", 
        "enter_mode", 
        "delete_note", 
        "open_url", 
        "install_app"
    )

    fun allowed(tool: String, origin: Origin): Boolean {
        if (origin in UNTRUSTED && tool in DENIED_FOR_UNTRUSTED) {
            return false
        }
        return true
    }
}
