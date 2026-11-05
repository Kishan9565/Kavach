package com.kishan.kavach.presentation.developer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.Redactor
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.domain.agent.AgentEvent
import com.kishan.kavach.domain.tools.ToolLogEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DevState(val events: List<Pair<Long, AgentEvent>> = emptyList(), val tools: List<ToolLogEntry> = emptyList(), val tab: Int = 0, val redact: Boolean = true, val toolCount: Int = 0) : UiState
sealed interface DevIntent : UiIntent { data class Tab(val i: Int) : DevIntent; data class Redact(val on: Boolean) : DevIntent }
sealed interface DevEffect : UiEffect

class DeveloperViewModel(private val c: AppContainer) : MviViewModel<DevState, DevIntent, DevEffect>(DevState(redact = c.prefs.redactLogs, toolCount = c.registry.all().size)) {
    init {
        viewModelScope.launch { c.taskRepo.observeRecentEvents(200).collect { e -> setState { copy(events = e) } } }
        viewModelScope.launch { c.toolLog.collect { t -> setState { copy(tools = t.reversed()) } } }
    }
    override fun onIntent(intent: DevIntent) {
        when (intent) { is DevIntent.Tab -> setState { copy(tab = intent.i) }; is DevIntent.Redact -> { c.prefs.redactLogs = intent.on; setState { copy(redact = intent.on) } } }
    }
}

@Composable
fun DeveloperScreen() {
    val vm = containerViewModel { DeveloperViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    fun r(t: String) = if (s.redact) Redactor.redact(t) else t
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Developer mode")
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(s.redact, { vm.onIntent(DevIntent.Redact(it)) }); Spacer(Modifier.width(8.dp)); Text("Redact digits/emails in logs", style = MaterialTheme.typography.bodySmall) }
        Text("${s.toolCount} tools registered", style = MaterialTheme.typography.labelMedium)
        TabRow(selectedTabIndex = s.tab) {
            Tab(s.tab == 0, { vm.onIntent(DevIntent.Tab(0)) }, text = { Text("Agent events") })
            Tab(s.tab == 1, { vm.onIntent(DevIntent.Tab(1)) }, text = { Text("Tool calls") })
        }
        if (s.tab == 0) {
            if (s.events.isEmpty()) EmptyState("No events", "Run a task and its loop events (route, tool calls, retries, verification) appear here.")
            LazyColumn { items(s.events) { (id, e) -> Text("${fmt.format(Date(e.time))} #$id ${e.type.name.lowercase()}: ${r(e.text)}".take(220), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) } }
        } else {
            if (s.tools.isEmpty()) EmptyState("No tool calls yet", "Every tool call shows its arguments, result, error code and duration.")
            LazyColumn { items(s.tools) { t ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    Text("${fmt.format(Date(t.time))} ${if (t.success) "✅" else "❌"} ${r(t.args)}  (${t.durationMs} ms)", style = MaterialTheme.typography.bodySmall)
                    if (!t.success) Text("   ${t.errorCode}: ${r(t.message)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } }
        }
    }
}
