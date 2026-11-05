package com.kishan.kavach.domain.skills

/** Built-in skills in the same text format users will later write themselves. */
object BuiltInSkills {
    val TEXT = """
skill: search_in_app
description: Open an app, tap its search box, type a query and submit
param: app:str - App name, e.g. YouTube
param: query:str - Text to search for
permission: accessibility
match: (?i)^(?<app>[\w .]+?)\s+(?:mein|me|par|pe|on|in)\s+(?<query>.+?)\s+(?:search|dhundo|khojo|search karo)\s*${'$'}
step: open_app name={app} expect=Search
step: tap text=Search
step: wait ms=900
step: input_text text={query} submit=true
success: Search results for the query are shown in the app
failure: Could not run the search in the app
---
skill: navigate_to_screen
description: Open an app and tap a labelled item to reach a screen
param: app:str - App name
param: label:str - Visible label to tap
permission: accessibility
step: open_app name={app}
step: wait ms=900
step: tap text={label}
success: The requested screen is open
failure: Could not reach the screen
---
skill: read_and_summarize
description: Read the visible screen and summarise it with Gemma
permission: accessibility
match: (?i)screen\s+(?:par|pe|mein|me)\s+kya|summari[sz]e\s+(?:this\s+|the\s+)?screen
step: read_screen as=screen
llm: "Summarize this phone screen in 3 short bullets in the user's language (Hinglish is fine)." input={screen} as=result
success: Summary produced
failure: Could not read or summarise the screen
---
skill: prepare_whatsapp_message
description: Prepare a WhatsApp message draft (does not send)
param: contact:str - Contact name
param: text:str - Message text
match: (?i)^(?<contact>.+?)\s+ko\s+whatsapp\s+(?:par|pe|per|mein|me)?\s*(?<text>.+?)\s+(?:likho|likh do|draft karo)\s*${'$'}
step: create_message_draft app=whatsapp contact={contact} text={text}
success: Draft is ready; you press Send
failure: Could not prepare the draft
---
skill: send_approved_whatsapp
description: Send a WhatsApp message after the user explicitly approves it
param: contact:str - Contact name
param: text:str - Message text
permission: accessibility
match: (?i)^(?<contact>.+?)\s+ko\s+whatsapp\s+(?:par|pe|per|mein|me)?\s*(?<text>.+?)\s+(?:bhejo|bhej do|send karo|bol do)\s*${'$'}
step: send_message app=whatsapp contact={contact} text={text}
success: Message sent after approval
failure: Message was not sent
---
skill: monitor_contact
description: Turn on WhatsApp auto-reply for one explicitly named contact
param: contact:str - Exact WhatsApp contact name
permission: notification_access
match: (?i)^(?:monitor|auto.?reply)\s+(?:on\s+)?(?<contact>.+?)\s*${'$'}
step: auto_reply action=add_contact contact={contact}
step: auto_reply action=enable
success: Auto-reply is on for the contact
failure: Could not configure auto-reply
---
skill: morning_briefing
description: Date, time, battery, calendar, reminders and notifications in one short briefing
match: (?i)morning briefing|subah ki briefing|good morning kavach
step: get_current_date as=date
step: get_current_time as=time
step: get_battery_info as=battery
step: get_calendar_events optional=true as=cal
step: list_reminders optional=true as=rem
step: get_notification_summary optional=true as=notif
llm: "Write a friendly 4-line morning briefing in Hinglish from this data." input="{date} {time}\nBattery: {battery}\nCalendar: {cal}\nReminders: {rem}\nNotifications: {notif}" as=result
success: Briefing ready
failure: Could not prepare the briefing
---
skill: notification_summary
description: Summarise recent notifications and flag important ones
permission: notification_access
match: (?i)notifications?.*(?:summary|summari[sz]e|batao|dikhao)|(?:meri\s+)?notifications?\s+(?:batao|dikhao)
step: get_notification_summary as=notif
llm: "Summarize these notifications in at most 5 short lines. Put IMPORTANT: before urgent or time-sensitive ones." input={notif} as=result
success: Notification summary ready
failure: Could not read notifications
---
skill: storage_overview
description: Free and used storage on the device
match: (?i)storage.*(?:kitna|overview|batao|check|status)|kitni\s+storage
step: get_storage_info as=result
success: Storage figures reported
failure: Could not read storage
---
skill: note_from_screen
description: Read the current screen, extract the key points and save them as a note
permission: accessibility
match: (?i)(?:screen|chat|conversation).*(?:se|ka)?.*\bnote\b.*(?:banao|bana do|karo)|note.*screen
step: read_screen as=screen
llm: "Extract the key points of this text as a short note (max 5 lines), same language." input={screen} as=note
step: create_note title="Screen note" text={note} as=result
success: Note saved
failure: Could not create the note
""".trimIndent()

    fun all(): List<Skill> = SkillFormat.parseAll(TEXT)
}
