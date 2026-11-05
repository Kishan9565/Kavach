package com.kishan.kavach.automation.tools

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.kishan.kavach.core.permissions.withPermission
import com.kishan.kavach.domain.repository.NoteRepository
import com.kishan.kavach.domain.repository.ProductivityRepository
import com.kishan.kavach.domain.tools.*
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun interface ReminderScheduler { fun schedule(id: Long, text: String, triggerAt: Long) }

class ProductivityTools(
    private val app: Context,
    private val notes: NoteRepository,
    private val prod: ProductivityRepository,
    private val scheduler: ReminderScheduler,
) {
    fun all(): List<Tool> = listOf(createNote, searchNotes, updateNote, deleteNote, createReminder, listReminders, createTask, updateTask, calendarEvents, addCalendarEvent)

    private val fmt get() = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    val createNote = SimpleTool("create_note", "Save a note", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("title", ParamType.STRING, "short title", required = false, maxLength = 80), ParamSpec("text", ParamType.STRING, "note body", maxLength = 2000))) { a ->
        val id = notes.add(a.string("title").ifBlank { "Note" }, a.string("text"))
        ToolResult.ok("Note #$id saved", mapOf("id" to id))
    }

    val searchNotes = SimpleTool("search_notes", "Find notes related to a query", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("query", ParamType.STRING, "what to look for", maxLength = 200))) { a ->
        val hits = notes.search(a.string("query"), 5)
        if (hits.isEmpty()) ToolResult.ok("No matching notes") else ToolResult.ok(hits.joinToString("\n") { "#${it.id} ${it.title}: ${it.text.take(150)}" }, mapOf("count" to hits.size.toString()))
    }

    val updateNote = SimpleTool("update_note", "Change a note's title or text by id", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("id", ParamType.INTEGER, "note id"), ParamSpec("title", ParamType.STRING, "new title", required = false, maxLength = 80),
            ParamSpec("text", ParamType.STRING, "new body", required = false, maxLength = 2000)), risk = Risk.MEDIUM) { a ->
        if (!a.has("title") && !a.has("text")) return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Give title or text")
        if (notes.update(a.long("id")!!, a.str("title"), a.str("text"))) ToolResult.ok("Note updated") else ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "No note with that id")
    }

    val deleteNote = SimpleTool("delete_note", "Delete a note by id (asks the user first)", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("id", ParamType.INTEGER, "note id")), risk = Risk.HIGH,
        confirm = { "Delete note #${it.int("id")}?" }) { a ->
        if (notes.delete(a.long("id")!!)) ToolResult.ok("Note deleted") else ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "No note with that id")
    }

    val createReminder = SimpleTool("create_reminder", "Remind the user later. Give minutes_from_now OR at (yyyy-MM-ddTHH:mm local time)", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("text", ParamType.STRING, "what to remind", maxLength = 200),
            ParamSpec("minutes_from_now", ParamType.INTEGER, "1-525600", required = false, min = 1.0, max = 525600.0),
            ParamSpec("at", ParamType.STRING, "local datetime like 2026-11-05T09:30", required = false, maxLength = 20))) { a ->
        val now = System.currentTimeMillis()
        val at = when {
            a.has("minutes_from_now") -> now + a.long("minutes_from_now")!! * 60_000
            a.has("at") -> runCatching { LocalDateTime.parse(a.string("at")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                .getOrElse { return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "'at' must look like 2026-11-05T09:30") }
            else -> return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Give minutes_from_now or at")
        }
        if (at <= now) return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Reminder time is in the past")
        val id = prod.addReminder(a.string("text"), at)
        scheduler.schedule(id, a.string("text"), at)
        ToolResult.ok("Reminder set for ${fmt.format(Date(at))}", mapOf("id" to id.toString(), "at" to at.toString()))
    }

    val listReminders = SimpleTool("list_reminders", "Upcoming reminders", ToolGroup.PRODUCTIVITY) {
        val l = prod.reminders(false)
        if (l.isEmpty()) ToolResult.ok("No upcoming reminders") else ToolResult.ok(l.joinToString("\n") { "#${it.id} ${fmt.format(Date(it.triggerAt))}: ${it.text}" }, mapOf("count" to l.size.toString()))
    }

    val createTask = SimpleTool("create_task", "Add a to-do item", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("title", ParamType.STRING, "task title", maxLength = 150),
            ParamSpec("due_in_hours", ParamType.INTEGER, "optional due time", required = false, min = 1.0, max = 8760.0))) { a ->
        val due = a.long("due_in_hours")?.let { System.currentTimeMillis() + it * 3_600_000 }
        val id = prod.addTodo(a.string("title"), due)
        ToolResult.ok("Task #$id added", mapOf("id" to id.toString()))
    }

    val updateTask = SimpleTool("update_task", "Rename or complete a to-do by id", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("id", ParamType.INTEGER, "task id"), ParamSpec("title", ParamType.STRING, "new title", required = false, maxLength = 150),
            ParamSpec("done", ParamType.BOOLEAN, "mark done/undone", required = false))) { a ->
        if (!a.has("title") && !a.has("done")) return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Give title or done")
        if (prod.updateTodo(a.long("id")!!, a.str("title"), if (a.has("done")) a.bool("done") else null)) ToolResult.ok("Task updated")
        else ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "No task with that id")
    }

    val calendarEvents = SimpleTool("get_calendar_events", "Upcoming calendar events (read only)", ToolGroup.PRODUCTIVITY,
        listOf(ParamSpec("days", ParamType.INTEGER, "look-ahead days, default 1", required = false, min = 1.0, max = 14.0)),
        permissions = listOf(Manifest.permission.READ_CALENDAR), timeoutMs = 15_000) { a ->
        withPermission(app, Manifest.permission.READ_CALENDAR, "Calendar") {
            val begin = System.currentTimeMillis()
            val end = begin + (a.long("days") ?: 1L) * 86_400_000
            val out = ArrayList<String>()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(begin.toString()).appendPath(end.toString()).build()
            app.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.size < 15) out += "${fmt.format(Date(c.getLong(1)))}: ${c.getString(0).orEmpty()}"
            }
            if (out.isEmpty()) ToolResult.ok("No events in the next ${a.long("days") ?: 1} day(s)") else ToolResult.ok(out.joinToString("\n"), mapOf("count" to out.size.toString()))
        }
    }

    val addCalendarEvent = SimpleTool("add_calendar_event", "Add an event to the system calendar after confirmation", ToolGroup.PRODUCTIVITY,
        listOf(
            ParamSpec("title", ParamType.STRING, "event title", maxLength = 120),
            ParamSpec("minutes_from_now", ParamType.INTEGER, "when event starts in minutes", required = false, min = 0.0, max = 525600.0),
            ParamSpec("duration_minutes", ParamType.INTEGER, "duration in minutes, default 60", required = false, min = 1.0, max = 1440.0)
        ),
        risk = Risk.HIGH, permissions = listOf(Manifest.permission.WRITE_CALENDAR), timeoutMs = 15_000,
        confirm = { "Add calendar event '${it.string("title")}' starting in ${it.int("minutes_from_now") ?: 0} min?" }) { a ->
        withPermission(app, Manifest.permission.WRITE_CALENDAR, "Calendar") {
            val title = a.string("title")
            val startMs = System.currentTimeMillis() + (a.long("minutes_from_now") ?: 0L) * 60_000
            val endMs = startMs + (a.long("duration_minutes") ?: 60L) * 60_000

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.CALENDAR_ID, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = app.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                ToolResult.ok("Calendar event '$title' created for ${fmt.format(Date(startMs))}")
            } else {
                ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Failed to create calendar event")
            }
        }
    }
}
