package com.kishan.kavach.presentation.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.kishan.kavach.domain.model.ChatMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ChatState(
    val messages: List<ChatMessage> = emptyList(), val input: String = "", val activeTask: AgentTask? = null,
    val events: List<AgentEvent> = emptyList(), val gemmaReady: Boolean = false, val voiceLang: String = "en-IN",
) : UiState

sealed interface ChatIntent : UiIntent {
    data class Input(val text: String) : ChatIntent
    data object Send : ChatIntent
    data object Stop : ChatIntent
    data class VoiceResult(val text: String) : ChatIntent
    data class Remember(val text: String) : ChatIntent
    data object ClearHistory : ChatIntent
    data object MicClick : ChatIntent
}
sealed interface ChatEffect : UiEffect { data object LaunchVoice : ChatEffect; data class Toast(val message: String) : ChatEffect }

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val c: AppContainer) : MviViewModel<ChatState, ChatIntent, ChatEffect>(ChatState()) {
    init {
        viewModelScope.launch { c.chat.observe().collect { m -> setState { copy(messages = m, gemmaReady = c.gemma.isReady, voiceLang = c.prefs.voiceLang) } } }
        val active = c.taskManager.tasks.map { l -> l.firstOrNull { it.isActive } }
        viewModelScope.launch { active.collect { t -> setState { copy(activeTask = t) } } }
        viewModelScope.launch {
            active.map { it?.id }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.taskManager.events(id) }
                .collect { ev -> setState { copy(events = ev) } }
        }
    }

    override fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Input -> setState { copy(input = intent.text.take(500)) }
            ChatIntent.Send -> send(current.input)
            is ChatIntent.VoiceResult -> send(intent.text)
            ChatIntent.Stop -> c.taskManager.cancelActive()
            ChatIntent.MicClick -> emit(ChatEffect.LaunchVoice)
            is ChatIntent.Remember -> viewModelScope.launch { c.memories.add(intent.text); emit(ChatEffect.Toast("Saved to memory")) }
            ChatIntent.ClearHistory -> viewModelScope.launch { c.chat.clear() }
        }
    }

    private fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        setState { copy(input = "") }
        c.taskManager.submit(t, "chat")
    }
}

@Composable
fun ChatScreen() {
    val vm = containerViewModel { ChatViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val redact = LocalContainer.current.prefs.redactLogs
    val listState = rememberLazyListState()
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.onIntent(ChatIntent.VoiceResult(it)) }
    }
    LaunchedEffect(Unit) {
        vm.effects.collect { e ->
            when (e) {
                ChatEffect.LaunchVoice -> runCatching {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, s.voiceLang))
                }.onFailure { android.widget.Toast.makeText(ctx, "Speech recognition not available on this phone", android.widget.Toast.LENGTH_LONG).show() }
                is ChatEffect.Toast -> android.widget.Toast.makeText(ctx, e.message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Chat", Modifier.weight(1f))
            TextButton(onClick = { vm.onIntent(ChatIntent.ClearHistory) }) { Text("Clear") }
        }
        if (!s.gemmaReady) ErrorBanner("Gemma model not imported. Simple commands (alarm, timer, settings, open app) still work; reasoning needs Gemma (More → Gemma model).", Modifier.padding(horizontal = 16.dp))

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = listState) {
            if (s.messages.isEmpty()) item { EmptyState("Kuch bolo ya likho", "Try: \"YouTube par lofi search karo\", \"meri battery kitni hai\", \"yaad rakho: wifi password 1234\"") }
            items(s.messages, key = { it.id }) { m ->
                val mine = m.fromUser
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(m.text)
                            if (mine) TextButton(onClick = { menuFor = m }) { Text("Remember", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            s.activeTask?.let { t ->
                item {
                    SectionCard("▶ ${t.displayText.take(60)}") {
                        StatusChip(t.status)
                        LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        s.events.takeLast(4).forEach { e -> Text("• ${if (redact) Redactor.redact(e.text) else e.text}".take(120), style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = { vm.onIntent(ChatIntent.Stop) }) { Text("■ Stop") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(s.input, { vm.onIntent(ChatIntent.Input(it)) }, Modifier.weight(1f), placeholder = { Text("Command ya sawaal…") }, maxLines = 3)
            TextButton(onClick = { vm.onIntent(ChatIntent.MicClick) }) { Text("🎤") }
            Button(onClick = { vm.onIntent(ChatIntent.Send) }, enabled = s.input.isNotBlank()) { Text("Send") }
        }
    }

    menuFor?.let { m ->
        AlertDialog(onDismissRequest = { menuFor = null }, title = { Text("Save to memory?") }, text = { Text(m.text.take(200)) },
            confirmButton = { Button(onClick = { vm.onIntent(ChatIntent.Remember(m.text)); menuFor = null }) { Text("Save") } },
            dismissButton = { OutlinedButton(onClick = { menuFor = null }) { Text("Cancel") } })
    }
}
