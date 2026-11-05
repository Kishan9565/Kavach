package com.kishan.kavach.presentation.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kishan.kavach.ai.model.ModelState
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.permissions.AndroidPermissionChecker
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.domain.agent.AgentTask
import com.kishan.kavach.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class HomeState(
    val model: ModelState = ModelState.NotLoaded, val hasModel: Boolean = false, val accessibility: Boolean = false,
    val notificationAccess: Boolean = false, val activeTask: AgentTask? = null, val toolCount: Int = 0, val skillCount: Int = 0,
) : UiState
sealed interface HomeIntent : UiIntent { data object Refresh : HomeIntent; data object StopTask : HomeIntent; data class Quick(val text: String) : HomeIntent }
sealed interface HomeEffect : UiEffect

class HomeViewModel(private val c: AppContainer) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    init {
        viewModelScope.launch { c.gemma.state.collect { m -> setState { copy(model = m) } } }
        viewModelScope.launch { c.taskManager.tasks.map { l -> l.firstOrNull { it.isActive } }.collect { t -> setState { copy(activeTask = t) } } }
        viewModelScope.launch { while (true) { refresh(); delay(2000) } }   // service state has no callback: cheap poll while visible
    }
    private fun refresh() = setState {
        copy(hasModel = c.models.selectedModel() != null, accessibility = ScreenBridge.controller?.connected == true,
            notificationAccess = AndroidPermissionChecker.notificationAccessEnabled(c.app), toolCount = c.registry.all().size, skillCount = c.skills.size)
    }
    override fun onIntent(intent: HomeIntent) = when (intent) {
        HomeIntent.Refresh -> refresh()
        HomeIntent.StopTask -> c.taskManager.cancelActive()
        is HomeIntent.Quick -> c.taskManager.submit(intent.text, "chat")
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val vm = containerViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("🛡 KAVACH")
        Text("On-device phone agent. Gemma only. No cloud.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))

        s.activeTask?.let { t ->
            SectionCard("Working on it") {
                Text(t.displayText, style = MaterialTheme.typography.bodyLarge)
                StatusChip(t.status)
                LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                if (t.currentAction.isNotBlank()) Text(t.currentAction, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.onIntent(HomeIntent.StopTask) }) { Text("■ Stop") }
            }
        }

        SectionCard("Gemma model") {
            val line = when {
                !s.hasModel -> "No model imported."
                s.model is ModelState.Ready -> "Ready: ${(s.model as ModelState.Ready).model} on ${(s.model as ModelState.Ready).backend}"
                s.model is ModelState.Loading -> "Loading…"
                s.model is ModelState.Error -> "Error: ${(s.model as ModelState.Error).message}"
                else -> "Model file present (loads on first use)."
            }
            Text(line)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { onNavigate(Routes.MODELS) }) { Text(if (s.hasModel) "Manage model" else "Import Gemma model") }
        }

        SectionCard("Access") {
            Text(if (s.accessibility) "✅ Accessibility service on" else "⚠ Accessibility service off: screen tools unavailable")
            if (!s.accessibility) OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Accessibility settings") }
            Spacer(Modifier.height(6.dp))
            Text(if (s.notificationAccess) "✅ Notification access on (optional)" else "Notification access off (optional)")
        }

        SectionCard("Try") {
            listOf("Wi-Fi settings kholo", "20 minute ka timer lagao", "meri battery kitni hai", "morning briefing do").forEach { q ->
                OutlinedButton(onClick = { vm.onIntent(HomeIntent.Quick(q)); onNavigate(Routes.CHAT) }, modifier = Modifier.fillMaxWidth()) { Text(q) }
            }
        }
        Text("${s.toolCount} tools · ${s.skillCount} skills registered", style = MaterialTheme.typography.labelMedium)
    }
}
