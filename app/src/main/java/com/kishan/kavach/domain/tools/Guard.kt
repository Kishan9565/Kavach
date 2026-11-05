package com.kishan.kavach.domain.tools

data class Verdict(val blocked: Boolean, val reason: String?)

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
    private val danger = Regex("""(?i)\b(send|submit|pay|delete|install|apply|buy|order|confirm|transfer|bhejo|remove|purchase|checkout)\b""")
    
    // Strict whole-word dangerous actions (English, Hinglish, Devanagari).
    private val strictDanger = setOf(
        "send", "pay", "place order", "buy", "confirm", "transfer", "delete", "remove", 
        "install", "subscribe", "post", "publish", "submit", "checkout", "allow", "grant", 
        "factory reset", "bhejo", "kharido", "hatao",
        "भेज", "खरीदो", "हटाओ", "ऑर्डर", "पेमेंट", "सबमिट", "डिलीट"
    )
    
    // Contextual dangerous actions (blocked only on checkout/payment screens).
    private val contextualDanger = setOf("continue", "proceed", "next", "आगे बढ़ें", "आगे")
    
    private val moneyApps = Regex("""(?i)(paisa|phonepe|paytm|npci|bank|upi|wallet|gpay|googlepay|bhim)""")
    private val checkoutKeywords = Regex("""(?i)\b(checkout|payment|pay|order|cart|billing)\b""")
    
    private val allowedUrl = Regex("""(?i)^https?://[^\s]+$""")

    private fun normalize(text: String): String {
        // Lowercase and strip punctuation/emoji, but keep spaces between words
        return text.lowercase().replace(Regex("""[^\w\s\u0900-\u097F]"""), " ").trim().replace(Regex("""\s+"""), " ")
    }

    fun checkAction(label: String, pkg: String, screenTitle: String?): Verdict {
        val normLabel = normalize(label)
        
        // 1. Hard block if the action is in the strict danger list
        if (strictDanger.contains(normLabel)) {
            return Verdict(true, "Hard stop: '$label' jaisa button main nahi dabata.")
        }
        
        // 2. Contextual block for "Continue", "Next", etc. if on a money/checkout screen
        if (contextualDanger.contains(normLabel)) {
            val isMoneyApp = isMoneyApp(pkg)
            val isCheckoutScreen = screenTitle != null && checkoutKeywords.containsMatchIn(screenTitle)
            if (isMoneyApp || isCheckoutScreen) {
                return Verdict(true, "Hard stop: Payment/Checkout screen par '$label' allowed nahi hai.")
            }
        }
        
        return Verdict(false, null)
    }

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
            val t = args.string("text") ?: ""
            // Fallback for older regex check just in case, but rely primarily on the strict checkAction
            val verdict = checkAction(t, "", null) // We might not have pkg/title here, but strict words will be blocked
            if (verdict.blocked) return verdict.reason
            
            // Retain existing substring safety net just in case, but avoid blocking "Sender profile"
            // The existing danger regex uses \b boundary, so it shouldn't block "Sender profile" anyway, 
            // but we've explicitly handled exact matches above.
            if (danger.containsMatchIn(t)) return "Hard stop: '$t' jaisa button main nahi dabata. Ye tum khud dabao."
        }
        if (tool.name == "open_url" && !allowedUrl.matches(args.string("url") ?: ""))
            return "Sirf http/https links khul sakte hain."
        if (tool.name == "open_app" && isMoneyApp(args.string("name")))
            return "Paisa/bank wali app main nahi kholta."
        return null
    }
}
