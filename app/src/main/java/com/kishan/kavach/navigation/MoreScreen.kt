package com.kishan.kavach.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kishan.kavach.core.ui.ScreenTitle

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        Triple(Routes.MEMORY, "🧠 Notes & Memory", "Notes, approved memories, chat history controls"),
        Triple(Routes.NOTIFICATIONS, "🔔 Notifications", "Optional access, local summaries, reminders"),
        Triple(Routes.AUTOMATION, "⚙ Automation", "Trigger → conditions → action workflows"),
        Triple(Routes.MODELS, "🤖 Gemma model", "Import, select, load/unload, compatibility"),
        Triple(Routes.DEVELOPER, "🛠 Developer mode", "Agent events, tool calls, timings"),
        Triple(Routes.SETTINGS, "⚙ Settings & setup", "Permissions, voice, auto-reply, external automation"),
    )
    Column(Modifier.padding(16.dp)) {
        ScreenTitle("More")
        items.forEach { (route, title, sub) ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onNavigate(route) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
