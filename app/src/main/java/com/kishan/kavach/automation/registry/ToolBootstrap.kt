package com.kishan.kavach.automation.registry

import android.content.Context
import com.kishan.kavach.automation.tools.*
import com.kishan.kavach.data.preferences.Prefs
import com.kishan.kavach.domain.repository.NoteRepository
import com.kishan.kavach.domain.repository.ProductivityRepository
import com.kishan.kavach.domain.tools.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The only place that knows the full tool list. Add a tool here (or register one elsewhere): AgentLoop never changes. */
object ToolBootstrap {
    fun build(app: Context, notes: NoteRepository, prod: ProductivityRepository, prefs: Prefs, scheduler: ReminderScheduler): ToolRegistry {
        val r = ToolRegistry()
        r.register(SimpleTool("finish", "End the task with a short message. Only after the last action succeeded.", ToolGroup.CORE,
            listOf(ParamSpec("message", ParamType.STRING, "result message", maxLength = 1500))) { ToolResult.ok(it.string("message")) })
        ScreenTools.all().forEach(r::register)
        LauncherTools(app).all().forEach(r::register)
        DeviceTools(app).all().forEach(r::register)
        ProductivityTools(app, notes, prod, scheduler).all().forEach(r::register)
        CommsTools(app, prefs).all().forEach(r::register)
        r.register(SimpleTool("get_current_time", "Current local time", ToolGroup.UTILITY) { ToolResult.ok(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) })
        r.register(SimpleTool("get_current_date", "Current local date and weekday", ToolGroup.UTILITY) { ToolResult.ok(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())) })
        return r
    }
}
