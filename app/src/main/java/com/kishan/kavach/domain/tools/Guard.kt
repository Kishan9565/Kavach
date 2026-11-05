package com.kishan.kavach.domain.tools

/**
 * Pure-Kotlin safety layer (migrated from the Sprint 1-3 Guard and extended for the tool registry).
 * Screen text, notes and notifications are untrusted data; this validator only looks at what the model
 * (or user) wants to DO. Returns a user-facing reason when blocked, null when allowed.
 */
object Guard : SafetyValidator {
    private val secrets = Regex("""(?i)\b(otp|password|cvv|upi pin|pin number)\b""")
    private val money = Regex("""(?i)(rupaye|rupay|rupees|paise|paisa|₹)""")
    private val payVerb = Regex("""(?i)\b(bhej|bhejo|send|transfer|pay|payment)\b""")
    private val devSecrets = Regex("ओटीपी|पासवर्ड|यूपीआई|पिन नंबर|सीवीवी")
    private val devMoney = Regex("पैसे|पैसा|रुपये|रुपए")
    private val devPay = Regex("भेज|ट्रांसफर|पेमेंट")
    /** Buttons the generic `tap` tool must never press (sending goes through send_message + confirmation). */
    private val danger = Regex("""(?i)\b(send|submit|pay|delete|install|apply|buy|order|confirm|transfer|bhejo|remove|purchase|checkout)\b""")
    private val moneyApps = Regex("""(?i)(paisa|phonepe|paytm|npci|bank|upi|wallet|gpay|googlepay|bhim)""")
    private val allowedUrl = Regex("""(?i)^https?://[^\s]+$""")

    fun checkUserText(t: String): String? {
        if (secrets.containsMatchIn(t) || devSecrets.containsMatchIn(t) ||
            (money.containsMatchIn(t) && payVerb.containsMatchIn(t)) ||
            (devMoney.containsMatchIn(t) && devPay.containsMatchIn(t)))
            return "Paisa, OTP ya password wala kaam main nahi karta. Ye step tum khud karo."
        return null
    }

    fun isMoneyApp(packageOrLabel: String) = moneyApps.containsMatchIn(packageOrLabel)

    override fun check(tool: Tool, args: Args): String? {
        val joined = args.map.values.joinToString(" ")
        if (tool.name in setOf("tap", "input_text", "open_app", "long_press", "send_message", "create_message_draft") &&
            (secrets.containsMatchIn(joined) || devSecrets.containsMatchIn(joined)))
            return "Hard stop: OTP/password wala step main nahi karunga."
        if (tool.name == "tap" || tool.name == "long_press") {
            val t = args.string("text")
            if (danger.containsMatchIn(t)) return "Hard stop: '$t' jaisa button main nahi dabata. Ye tum khud dabao."
        }
        if (tool.name == "open_url" && !allowedUrl.matches(args.string("url")))
            return "Sirf http/https links khul sakte hain."
        if (tool.name == "open_app" && isMoneyApp(args.string("name")))
            return "Paisa/bank wali app main nahi kholta."
        return null
    }
}
