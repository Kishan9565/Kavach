package com.kishan.kavach.presentation.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kishan.kavach.domain.agent.AgentTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TasksState(val tasks: List<AgentTask> = emptyList(), val expandedId: Long? = null, val events: List<AgentEvent> = emptyList(), val loading: Boolean = true) : UiState
sealed interface TasksIntent : UiIntent {
    data class Toggle(val id: Long) : TasksIntent
    data class Cancel(val id: Long) : TasksIntent
    data class Retry(val id: Long) : TasksIntent
    data object ClearFinished : TasksIntent
}
sealed interface TasksEffect : UiEffect

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(private val c: AppContainer) : MviViewModel<TasksState, TasksIntent, TasksEffect>(TasksState()) {
    private val expanded = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    init {
        viewModelScope.launch { c.taskManager.tasks.collect { t -> setState { copy(tasks = t, loading = false) } } }
        viewModelScope.launch {
            expanded.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.taskManager.events(id) }
                .collect { ev -> setState { copy(events = ev) } }
        }
    }
    override fun onIntent(intent: TasksIntent) {
        when (intent) {
            is TasksIntent.Toggle -> { val n = if (current.expandedId == intent.id) null else intent.id; expanded.value = n; setState { copy(expandedId = n) } }
            is TasksIntent.Cancel -> c.taskManager.cancel(intent.id)
            is TasksIntent.Retry -> c.taskManager.retry(intent.id)
            TasksIntent.ClearFinished -> viewModelScope.launch { c.taskRepo.clearFinished() }
        }
    }
}

@Composable
fun TasksScreen() {
    val vm = containerViewModel { TasksViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val redact = LocalContainer.current.prefs.redactLogs
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            ScreenTitle("Tasks", Modifier.weight(1f))
            TextButton(onClick = { vm.onIntent(TasksIntent.ClearFinished) }) { Text("Clear finished") }
        }
        when {
            s.loading -> CircularProgressIndicator()
            s.tasks.isEmpty() -> EmptyState("No tasks yet", "Anything you ask KAVACH to do appears here with live progress and history.")
            else -> LazyColumn {
                items(s.tasks, key = { it.id }) { t ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.onIntent(TasksIntent.Toggle(t.id)) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.displayText, style = MaterialTheme.typography.titleSmall)
                            Row { StatusChip(t.status); Spacer(Modifier.width(8.dp)); Text("${t.source} · ${fmt.format(Date(t.createdAt))}", style = MaterialTheme.typography.labelSmall) }
                            if (t.isActive) {
                                LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                if (t.currentAction.isNotBlank()) Text(t.currentAction, style = MaterialTheme.typography.bodySmall)
                            }
                            if (t.resultMessage.isNotBlank()) Text(t.resultMessage.take(300), style = MaterialTheme.typography.bodySmall)
                            Row {
                                if (t.isActive) OutlinedButton(onClick = { vm.onIntent(TasksIntent.Cancel(t.id)) }) { Text("Cancel") }
                                else OutlinedButton(onClick = { vm.onIntent(TasksIntent.Retry(t.id)) }) { Text("Retry") }
                            }
                            if (s.expandedId == t.id) {
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                if (s.events.isEmpty()) Text("No events recorded", style = MaterialTheme.typography.bodySmall)
                                s.events.forEach { e -> Text("${e.type.name.lowercase()}: ${if (redact) Redactor.redact(e.text) else e.text}".take(200), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}
