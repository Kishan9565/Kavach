package com.kishan.kavach.agent.planner

import com.kishan.kavach.domain.agent.Route
import com.kishan.kavach.domain.tools.ToolCall

/**
 * Regex handlers migrated from Sprint 1-3: fast, offline, no model. Now emit registry ToolCalls instead of
 * the old unrestricted `intent` step. Returns null when it does not understand.
 */
object InstantRules {
    private fun call(name: String, vararg kv: Pair<String, Any?>) = ToolCall(name, mapOf(*kv))
    private fun finish(msg: String) = call("finish", "message" to msg)
    private fun url(u: String) = call("open_url", "url" to u)
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    fun match(raw: String, accessibilityOn: Boolean): Route.Direct? {
        val t = raw.trim()
        val l = t.lowercase()
        val ic = RegexOption.IGNORE_CASE

        Regex("""^(?:yaad rakho|yaad rakh|note karo|note kar|note)\s*[:,-]?\s+(.+)$""", ic).find(t)?.let {
            return Route.Direct("Save note", "Note save kar raha hun.",
                listOf(call("create_note", "title" to "Note", "text" to it.groupValues[1].trim()), finish("Yaad rakh liya.")))
        }
        if (Regex("""^(?:back|peeche)\s+jao$|^back$""").containsMatchIn(l))
            return Route.Direct("Back", "Peeche ja raha hun.", listOf(call("press_back"), finish("Back ho gaya.")))
        if (Regex("""^home(?:\s+jao)?$""").containsMatchIn(l))
            return Route.Direct("Home", "Home ja raha hun.", listOf(call("press_home"), finish("Home aa gaya.")))
        if ("scroll" in l) {
            val dir = if (Regex("""upar|\bup\b""").containsMatchIn(l)) "up" else "down"
            return Route.Direct("Scroll $dir", "Scroll kar raha hun.", listOf(call("scroll", "direction" to dir), finish("Scroll ho gaya.")))
        }

        if ("alarm" in l || "अलार्म" in l) {
            val m = Regex("""(\d{1,2})(?:[:.](\d{2}))?""").find(l) ?: return null
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (Regex("""\bpm\b|shaam|sham|raat|dopahar|शाम|रात|दोपहर""").containsMatchIn(l) && h < 12) h += 12
            if (Regex("""\bam\b""").containsMatchIn(l) && h == 12) h = 0
            if (h > 23 || min > 59) return null
            val hhmm = "%02d:%02d".format(h, min)
            return Route.Direct("Alarm $hhmm", "Alarm laga raha hun.",
                listOf(call("set_alarm", "hour" to h, "minute" to min), finish("$hhmm ka alarm set.")))
        }

        if ("timer" in l || "टाइमर" in l) {
            val m = Regex("""(\d+)\s*(second|sec|min|minute|mint|ghant|hour|hr|सेकंड|मिनट|घंटे|घंटा)""").find(l) ?: return null
            val n = m.groupValues[1].toInt()
            val u = m.groupValues[2]
            val secs = when { u.startsWith("sec") || u.startsWith("सेकंड") -> n; u.startsWith("min") || u.startsWith("मिनट") -> n * 60; else -> n * 3600 }
            return Route.Direct("Timer ${secs}s", "Timer laga raha hun.", listOf(call("set_timer", "seconds" to secs), finish("Timer chal gaya.")))
        }

        if ("youtube" in l) {
            val m = Regex("""youtube.*?(?:aur|and|mein|me|par|pe)\s+(.+?)\s+(?:search|dhundo|chalao|play)""", ic).find(t)
            if (m != null) {
                val q = m.groupValues[1].trim()
                val viaUrl = Route.Direct("YouTube search $q", "YouTube khol raha hun.",
                    listOf(url("https://www.youtube.com/results?search_query=${enc(q)}"), finish("YouTube par '$q' search khul gaya.")))
                if (!accessibilityOn) return viaUrl
                return Route.Direct("YouTube search $q", "YouTube khol raha hun, phir khud search karunga.", listOf(
                    call("open_app", "name" to "YouTube", "expect" to "Search"),
                    call("tap", "text" to "Search"),
                    call("wait", "ms" to 900),
                    call("input_text", "text" to q, "submit" to true, "expect" to q),
                    finish("YouTube par '$q' search ho gaya.")), fallback = viaUrl)
            }
            if (Regex("""kholo|open""").containsMatchIn(l))
                return Route.Direct("Open YouTube", "YouTube khol raha hun.", listOf(call("open_app", "name" to "YouTube"), finish("YouTube khul gaya.")))
        }

        if (Regex("""wi-?fi|वाई-?फाई""").containsMatchIn(l)) return settings("wifi", "Wi-Fi")
        if ("bluetooth" in l || "ब्लूटूथ" in l) return settings("bluetooth", "Bluetooth")
        if ("display" in l || "brightness" in l) return settings("display", "Display")

        Regex("""(?:chrome|google|browser)\s+(?:mein|me|par|pe)\s+['"“]?(.+?)['"”]?\s+search""", ic).find(t)?.let {
            val q = it.groupValues[1].trim()
            return Route.Direct("Web search $q", "Search kar raha hun.",
                listOf(url("https://www.google.com/search?q=${enc(q)}"), finish("'$q' search khul gaya.")))
        }
        Regex("""^(.+?)\s+ka\s+route""", ic).find(t)?.let {
            val q = it.groupValues[1].trim()
            return Route.Direct("Navigate to $q", "Route khol raha hun.",
                listOf(call("maps_search", "query" to q, "navigate" to true), finish("Maps mein route khul gaya.")))
        }
        if (("call" in l || "dial" in l)) Regex("""\b(\d{10,12})\b""").find(l)?.let {
            val n = it.groupValues[1]
            return Route.Direct("Dial $n", "Dialer khol raha hun.", listOf(call("dial_number", "number" to n), finish("Dialer khul gaya, call tum dabao.")))
        }
        Regex("""^(.+?)\s+(?:kholo|open karo|open kar do|open)\s*$""", ic).find(t)?.let {
            val n = it.groupValues[1].trim()
            return Route.Direct("Open $n", "$n khol raha hun.", listOf(call("open_app", "name" to n), finish("$n khul gaya.")))
        }
        return null
    }

    private fun settings(which: String, label: String) = Route.Direct("$label settings", "$label settings khol raha hun.",
        listOf(call("launch_settings", "which" to which), finish("$label settings khul gayi.")))
}
