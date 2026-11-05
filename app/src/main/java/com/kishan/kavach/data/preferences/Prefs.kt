package com.kishan.kavach.data.preferences

import android.content.Context
import java.security.SecureRandom

/**
 * Small settings store. SharedPreferences (not DataStore) on purpose: the accessibility and notification
 * services must read flags synchronously, and the values are tiny.
 */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("kavach", Context.MODE_PRIVATE)

    var enabled: Boolean                       // WhatsApp auto-reply master switch
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** Comma separated EXACT WhatsApp contact names. Empty = reply to nobody. */
    var allowed: String
        get() = sp.getString("allowed", "") ?: ""
        set(v) = sp.edit().putString("allowed", v).apply()

    var ownerName: String
        get() = sp.getString("owner", "Kishan") ?: "Kishan"
        set(v) = sp.edit().putString("owner", v).apply()

    var tagReplies: Boolean
        get() = sp.getBoolean("tag", true)
        set(v) = sp.edit().putBoolean("tag", v).apply()

    var bubble: Boolean
        get() = sp.getBoolean("bubble", false)
        set(v) = sp.edit().putBoolean("bubble", v).apply()

    /** "en-IN" (Hinglish in Latin letters) or "hi-IN" (Devanagari). */
    var voiceLang: String
        get() = sp.getString("voiceLang", "en-IN") ?: "en-IN"
        set(v) = sp.edit().putString("voiceLang", v).apply()

    var speak: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()

    /** Mask digits/emails in developer logs. */
    var redactLogs: Boolean
        get() = sp.getBoolean("redactLogs", true)
        set(v) = sp.edit().putBoolean("redactLogs", v).apply()

    /** Selected Gemma model file name inside filesDir/models (empty = auto-pick). */
    var modelFile: String
        get() = sp.getString("modelFile", "") ?: ""
        set(v) = sp.edit().putString("modelFile", v).apply()

    var useGpu: Boolean
        get() = sp.getBoolean("useGpu", false)
        set(v) = sp.edit().putBoolean("useGpu", v).apply()

    // ---- External automation (Tasker / MacroDroid): OFF by default, explicit opt-in
    var externalEnabled: Boolean
        get() = sp.getBoolean("extEnabled", false)
        set(v) = sp.edit().putBoolean("extEnabled", v).apply()

    var externalPackages: String        // comma separated allowed caller packages (API 34+ verifies the sender)
        get() = sp.getString("extPkgs", "net.dinglisch.android.taskerm,com.arlosoft.macrodroid") ?: ""
        set(v) = sp.edit().putString("extPkgs", v).apply()

    fun externalToken(): String {
        sp.getString("extToken", null)?.let { return it }
        val b = ByteArray(18).also { SecureRandom().nextBytes(it) }
        val t = b.joinToString("") { "%02x".format(it) }
        sp.edit().putString("extToken", t).apply()
        return t
    }
    fun resetExternalToken(): String { sp.edit().remove("extToken").apply(); return externalToken() }

    fun allowedList(): List<String> = allowed.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
