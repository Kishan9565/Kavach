package com.kishan.kavach.automation.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.core.permissions.withPermission
import com.kishan.kavach.data.preferences.Prefs
import com.kishan.kavach.domain.tools.*
import com.kishan.kavach.services.accessibility.KavachAccess
import kotlinx.coroutines.delay

class CommsTools(private val app: Context, private val prefs: Prefs) {
    fun all(): List<Tool> = listOf(getContacts, searchContacts, sendSms, createDraft, sendMessage, autoReply)

    /** (name, number) pairs; needs READ_CONTACTS. */
    private fun lookup(name: String, limit: Int): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        app.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null,
        )?.use { c -> while (c.moveToNext() && out.size < limit) out.putIfAbsent(c.getString(0).orEmpty(), c.getString(1).orEmpty()) }
        return out.toList()
    }

    private fun hasContacts() = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Resolves a destination number from explicit number, or a unique contact match. */
    private fun resolveNumber(number: String?, contact: String?): String? {
        number?.filter { it.isDigit() }?.takeIf { it.length >= 10 }?.let { return it }
        if (contact.isNullOrBlank() || !hasContacts()) return null
        val hits = lookup(contact, 3)
        return hits.singleOrNull()?.second?.filter { it.isDigit() }?.takeIf { it.length >= 10 }
    }

    private fun draftIntent(appName: String, number: String?, text: String): Intent =
        if (appName == "sms") Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${number.orEmpty()}")).putExtra("sms_body", text)
        else if (number != null) Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}")).setPackage("com.whatsapp")
        else Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp").putExtra(Intent.EXTRA_TEXT, text)

    private fun start(i: Intent): Boolean = try { (KavachAccess.instance ?: app).startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }

    private val msgParams = listOf(
        ParamSpec("app", ParamType.STRING, "messaging app", enumValues = listOf("whatsapp", "sms")),
        ParamSpec("text", ParamType.STRING, "message text", maxLength = 500),
        ParamSpec("contact", ParamType.STRING, "contact name", required = false, maxLength = 60),
        ParamSpec("number", ParamType.STRING, "phone number", required = false, maxLength = 16))

    val getContacts = SimpleTool("get_contacts", "Look up contacts by name (max 5 results; names and numbers only)", ToolGroup.COMMS,
        listOf(ParamSpec("query", ParamType.STRING, "name part", maxLength = 40)), permissions = listOf(Manifest.permission.READ_CONTACTS)) { a ->
        withPermission(app, Manifest.permission.READ_CONTACTS, "Contacts") {
            val l = lookup(a.string("query"), 5)
            if (l.isEmpty()) ToolResult.ok("No contact found") else ToolResult.ok(l.joinToString("\n") { "${it.first}: ${it.second}" }, mapOf("count" to l.size.toString()))
        }
    }

    val searchContacts = SimpleTool("search_contacts", "Search contacts by query (returns names and count)", ToolGroup.COMMS,
        listOf(ParamSpec("query", ParamType.STRING, "name part", maxLength = 40)), permissions = listOf(Manifest.permission.READ_CONTACTS)) { a ->
        withPermission(app, Manifest.permission.READ_CONTACTS, "Contacts") {
            val l = lookup(a.string("query"), 10)
            val namesOnly = l.map { it.first }
            if (namesOnly.isEmpty()) ToolResult.ok("No contact found")
            else ToolResult.ok("${namesOnly.size} contact(s) found: ${namesOnly.joinToString(", ")}", mapOf("count" to namesOnly.size.toString()))
        }
    }

    val sendSms = SimpleTool("send_sms", "Send an SMS message after confirmation", ToolGroup.COMMS,
        listOf(ParamSpec("number", ParamType.STRING, "phone number", maxLength = 16), ParamSpec("text", ParamType.STRING, "SMS body", maxLength = 160)),
        risk = Risk.HIGH, permissions = listOf(Manifest.permission.SEND_SMS), timeoutMs = 15_000,
        confirm = { "Send SMS to ${it.string("number")}:\n\"${it.string("text")}\"" }) { a ->
        withPermission(app, Manifest.permission.SEND_SMS, "SMS") {
            val number = a.string("number").filter { it.isDigit() || it == '+' }
            val text = a.string("text")
            if (number.length < 3 || text.isBlank()) {
                return@withPermission ToolResult.fail(ErrorCode.INVALID_ARGS, "Invalid number or empty SMS text")
            }
            try {
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(number, null, text, null, null)
                ToolResult.ok("SMS sent to $number")
            } catch (e: Exception) {
                ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Failed to send SMS: ${e.message}")
            }
        }
    }

    val createDraft = SimpleTool("create_message_draft", "Open a message draft with the text filled in. Does NOT send.", ToolGroup.COMMS, msgParams) { a ->
        val n = resolveNumber(a.str("number"), a.str("contact"))
        if (start(draftIntent(a.string("app"), n, a.string("text")))) ToolResult.ok(if (n != null || a.string("app") == "sms") "Draft ready. Press Send yourself." else "Draft ready in WhatsApp: choose the chat '${a.string("contact")}' and press Send.")
        else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "${a.string("app")} could not be opened")
    }

    val sendMessage = SimpleTool("send_message", "Send a WhatsApp message AFTER the user confirms. Needs a number or a unique contact match.", ToolGroup.COMMS,
        msgParams.map { if (it.name == "app") it.copy(enumValues = listOf("whatsapp")) else it },
        risk = Risk.HIGH, permissions = listOf("accessibility"), timeoutMs = 30_000,
        confirm = { "Send WhatsApp message to ${it.str("contact") ?: it.str("number")}:\n\"${it.string("text")}\"" }) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool ToolResult.fail(ErrorCode.PERMISSION_REQUIRED, "Accessibility is off", permission = "accessibility")
        val text = a.string("text")
        val n = resolveNumber(a.str("number"), a.str("contact"))
            ?: return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Number nahi mila. Number batao ya Contacts permission do (unique naam chahiye).")
        if (!start(draftIntent("whatsapp", n, text))) return@SimpleTool ToolResult.fail(ErrorCode.EXECUTION_FAILED, "WhatsApp could not be opened")
        if (!c.screenContains(text.take(20), 8000)) return@SimpleTool ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Chat did not open with the draft", retryable = true)
        delay(500)
        if (!c.pressSendButton()) return@SimpleTool ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "Send button not found. Draft is open: press Send yourself.")
        delay(900)
        // verification: the draft text must no longer sit in the input box
        val still = c.snapshot()?.nodes?.any { it.editable && it.label.startsWith(text.take(20)) } == true
        if (still) ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Send tapped but the draft is still in the input box; not confirmed as sent")
        else ToolResult.ok("Message sent to ${a.str("contact") ?: n}")
    }

    val autoReply = SimpleTool("auto_reply", "Configure WhatsApp auto-reply for explicitly listed contacts", ToolGroup.COMMS,
        listOf(ParamSpec("action", ParamType.STRING, "what to do", enumValues = listOf("status", "enable", "disable", "add_contact", "remove_contact")),
            ParamSpec("contact", ParamType.STRING, "exact contact name", required = false, maxLength = 60)),
        risk = Risk.HIGH, permissions = listOf("notification_access"),
        confirm = { "Auto-reply: ${it.string("action")} ${it.string("contact")}. KAVACH will answer this person for you." }) { a ->
        val list = prefs.allowedList().toMutableList()
        val who = a.string("contact").trim().lowercase()
        when (a.string("action")) {
            "status" -> ToolResult.ok("Auto-reply is ${if (prefs.enabled) "ON" else "OFF"} for: ${list.joinToString().ifBlank { "nobody" }}")
            "enable" -> if (list.isEmpty()) ToolResult.fail(ErrorCode.INVALID_ARGS, "Add at least one contact first") else { prefs.enabled = true; ToolResult.ok("Auto-reply ON") }
            "disable" -> { prefs.enabled = false; ToolResult.ok("Auto-reply OFF") }
            "add_contact" -> if (who.isEmpty()) ToolResult.fail(ErrorCode.INVALID_ARGS, "contact required") else { if (who !in list) list += who; prefs.allowed = list.joinToString(","); ToolResult.ok("Added '$who'") }
            else -> if (who.isEmpty()) ToolResult.fail(ErrorCode.INVALID_ARGS, "contact required") else { list.remove(who); prefs.allowed = list.joinToString(","); ToolResult.ok("Removed '$who'") }
        }
    }
}
