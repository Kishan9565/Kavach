package com.kishan.kavach.presentation.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kishan.kavach.agent.state.ConditionEvaluator
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.domain.model.Automation
import com.kishan.kavach.domain.model.TriggerType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutomationState(val items: List<Automation> = emptyList(), val loading: Boolean = true) : UiState
sealed interface AutomationIntent : UiIntent {
    data class Save(val a: Automation) : AutomationIntent
    data class Toggle(val a: Automation, val on: Boolean) : AutomationIntent
    data class RunNow(val id: Long) : AutomationIntent
    data class Delete(val a: Automation) : AutomationIntent
}
sealed interface AutomationEffect : UiEffect { data class Toast(val message: String) : AutomationEffect }

class AutomationViewModel(private val c: AppContainer) : MviViewModel<AutomationState, AutomationIntent, AutomationEffect>(AutomationState()) {
    init { viewModelScope.launch { c.automations.observe().collect { l -> setState { copy(items = l, loading = false) } } } }
    override fun onIntent(intent: AutomationIntent) {
        viewModelScope.launch {
            when (intent) {
                is AutomationIntent.Save -> {
                    val a = intent.a
                    when {
                        a.name.isBlank() || a.actionText.isBlank() -> emit(AutomationEffect.Toast("Name and action are required"))
                        !ConditionEvaluator.isValid(a.conditions) -> emit(AutomationEffect.Toast("Conditions: charging, wifi, battery>30, battery<20, hours:8-22"))
                        a.triggerType == TriggerType.SCHEDULE && (a.triggerParam.toLongOrNull() ?: 0) < 15 -> emit(AutomationEffect.Toast("Schedule interval must be at least 15 minutes"))
                        a.triggerType == TriggerType.NOTIFICATION && a.triggerParam.isBlank() -> emit(AutomationEffect.Toast("Notification trigger needs package and/or keyword"))
                        else -> { val id = c.automations.save(a); c.automationScheduler.sync(a.copy(id = if (a.id == 0L) id else a.id)) }
                    }
                }
                is AutomationIntent.Toggle -> { val n = intent.a.copy(enabled = intent.on); c.automations.save(n); c.automationScheduler.sync(n) }
                is AutomationIntent.RunNow -> { emit(AutomationEffect.Toast(c.automationEngine.run(intent.id, manual = true))) }
                is AutomationIntent.Delete -> { c.automationScheduler.cancel(intent.a.id); c.automations.delete(intent.a.id) }
            }
        }
    }
}

@Composable
fun AutomationScreen() {
    val vm = containerViewModel { AutomationViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    var editing by remember { mutableStateOf<Automation?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Automation?>(null) }
    LaunchedEffect(Unit) { vm.effects.collect { if (it is AutomationEffect.Toast) android.widget.Toast.makeText(ctx, it.message, android.widget.Toast.LENGTH_LONG).show() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Automation")
        Text("Trigger → conditions → action. Actions are normal task sentences, so safety checks and confirmations still apply. Scheduled runs depend on Android battery rules (minimum 15 min).", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { creating = true }, modifier = Modifier.padding(vertical = 8.dp)) { Text("+ New automation") }
        if (s.loading) CircularProgressIndicator()
        else if (s.items.isEmpty()) EmptyState("No automations", "Example: every 60 min, if charging → \"meri battery kitni hai\".")
        else LazyColumn { items(s.items, key = { it.id }) { a ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(a.enabled, { vm.onIntent(AutomationIntent.Toggle(a, it)) })
                }
                Text("${a.triggerType.name.lowercase()}${if (a.triggerParam.isNotBlank()) " (${a.triggerParam})" else ""}${if (a.conditions.isNotBlank()) " if ${a.conditions}" else ""}", style = MaterialTheme.typography.bodySmall)
                Text("→ ${a.actionText}", style = MaterialTheme.typography.bodyMedium)
                if (a.lastRunAt != null) Text("Last run ${fmt.format(Date(a.lastRunAt))}: ${a.lastResult}", style = MaterialTheme.typography.labelSmall)
                Row { TextButton(onClick = { vm.onIntent(AutomationIntent.RunNow(a.id)) }) { Text("Run now") }; TextButton(onClick = { editing = a }) { Text("Edit") }; TextButton(onClick = { deleting = a }) { Text("Delete") } }
            } }
        } }
    }

    if (creating || editing != null) {
        val e = editing
        var name by remember(e) { mutableStateOf(e?.name.orEmpty()) }
        var type by remember(e) { mutableStateOf(e?.triggerType ?: TriggerType.MANUAL) }
        var param by remember(e) { mutableStateOf(e?.triggerParam.orEmpty()) }
        var cond by remember(e) { mutableStateOf(e?.conditions.orEmpty()) }
        var action by remember(e) { mutableStateOf(e?.actionText.orEmpty()) }
        AlertDialog(onDismissRequest = { creating = false; editing = null }, title = { Text(if (e == null) "New automation" else "Edit automation") },
            text = { Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(Modifier.padding(vertical = 6.dp)) { TriggerType.values().forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.lowercase()) }, modifier = Modifier.padding(end = 4.dp)) } }
                if (type != TriggerType.MANUAL) OutlinedTextField(param, { param = it }, singleLine = true, label = { Text(if (type == TriggerType.SCHEDULE) "Every N minutes (≥15)" else "package|keyword") })
                OutlinedTextField(cond, { cond = it }, singleLine = true, label = { Text("Conditions (optional)") }, placeholder = { Text("charging,wifi,hours:8-22") })
                OutlinedTextField(action, { action = it }, label = { Text("Action (task sentence)") })
            } },
            confirmButton = { Button(onClick = {
                vm.onIntent(AutomationIntent.Save(Automation(id = e?.id ?: 0, name = name.trim(), enabled = e?.enabled ?: false, triggerType = type, triggerParam = if (type == TriggerType.MANUAL) "" else param.trim(), conditions = cond.trim(), actionText = action.trim(), createdAt = e?.createdAt ?: System.currentTimeMillis(), lastRunAt = e?.lastRunAt, lastResult = e?.lastResult.orEmpty())))
                creating = false; editing = null }) { Text("Save") } },
            dismissButton = { OutlinedButton(onClick = { creating = false; editing = null }) { Text("Cancel") } })
    }
    deleting?.let { d -> ConfirmDialog("Delete automation", "Delete \"${d.name}\"?", onApprove = { vm.onIntent(AutomationIntent.Delete(d)); deleting = null }, onDeny = { deleting = null }) }
}
