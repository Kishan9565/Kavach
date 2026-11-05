package com.kishan.kavach.presentation.memory

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
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.domain.model.MemoryItem
import com.kishan.kavach.domain.model.Note
import kotlinx.coroutines.launch

data class MemoryState(val notes: List<Note> = emptyList(), val memories: List<MemoryItem> = emptyList(), val tab: Int = 0, val loading: Boolean = true) : UiState
sealed interface MemoryIntent : UiIntent {
    data class Tab(val i: Int) : MemoryIntent
    data class SaveNote(val id: Long?, val title: String, val text: String) : MemoryIntent
    data class DeleteNote(val id: Long) : MemoryIntent
    data class SaveMemory(val id: Long?, val text: String) : MemoryIntent
    data class DeleteMemory(val id: Long) : MemoryIntent
    data object ClearMemories : MemoryIntent
    data object ClearHistory : MemoryIntent
}
sealed interface MemoryEffect : UiEffect { data class Toast(val message: String) : MemoryEffect }

class MemoryViewModel(private val c: AppContainer) : MviViewModel<MemoryState, MemoryIntent, MemoryEffect>(MemoryState()) {
    init {
        viewModelScope.launch { c.notes.observe().collect { n -> setState { copy(notes = n, loading = false) } } }
        viewModelScope.launch { c.memories.observe().collect { m -> setState { copy(memories = m) } } }
    }
    override fun onIntent(intent: MemoryIntent) {
        viewModelScope.launch {
            when (intent) {
                is MemoryIntent.Tab -> setState { copy(tab = intent.i) }
                is MemoryIntent.SaveNote -> if (intent.text.isBlank()) emit(MemoryEffect.Toast("Note is empty")) else if (intent.id == null) c.notes.add(intent.title, intent.text) else c.notes.update(intent.id, intent.title, intent.text)
                is MemoryIntent.DeleteNote -> c.notes.delete(intent.id)
                is MemoryIntent.SaveMemory -> if (intent.text.isBlank()) emit(MemoryEffect.Toast("Memory is empty")) else if (intent.id == null) c.memories.add(intent.text) else c.memories.update(intent.id, intent.text)
                is MemoryIntent.DeleteMemory -> c.memories.delete(intent.id)
                MemoryIntent.ClearMemories -> c.memories.clear()
                MemoryIntent.ClearHistory -> { c.chat.clear(); emit(MemoryEffect.Toast("Chat history cleared")) }
            }
        }
    }
}

@Composable
fun MemoryScreen() {
    val vm = containerViewModel { MemoryViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.effects.collect { if (it is MemoryEffect.Toast) android.widget.Toast.makeText(ctx, it.message, android.widget.Toast.LENGTH_SHORT).show() } }
    var editNote by remember { mutableStateOf<Note?>(null) }
    var newNote by remember { mutableStateOf(false) }
    var editMem by remember { mutableStateOf<MemoryItem?>(null) }
    var newMem by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Notes & Memory")
        Text("Notes and memories stay on this phone and help KAVACH answer. Nothing is saved silently: you add or approve each item.", style = MaterialTheme.typography.bodySmall)
        TabRow(selectedTabIndex = s.tab) {
            Tab(s.tab == 0, { vm.onIntent(MemoryIntent.Tab(0)) }, text = { Text("Notes (${s.notes.size})") })
            Tab(s.tab == 1, { vm.onIntent(MemoryIntent.Tab(1)) }, text = { Text("Memories (${s.memories.size})") })
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { if (s.tab == 0) newNote = true else newMem = true }) { Text("+ Add") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { confirmText = "Clear all chat history?"; confirm = { vm.onIntent(MemoryIntent.ClearHistory) } }) { Text("Clear chat history") }
            if (s.tab == 1) { Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { confirmText = "Delete ALL memories?"; confirm = { vm.onIntent(MemoryIntent.ClearMemories) } }) { Text("Clear all") } }
        }
        if (s.loading) CircularProgressIndicator()
        else if (s.tab == 0) {
            if (s.notes.isEmpty()) EmptyState("No notes", "Say \"yaad rakho: …\" or tap + Add.")
            LazyColumn { items(s.notes, key = { it.id }) { n ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) {
                    Text(n.title, style = MaterialTheme.typography.titleSmall); Text(n.text.take(300))
                    Row { TextButton(onClick = { editNote = n }) { Text("Edit") }; TextButton(onClick = { confirmText = "Delete note \"${n.title}\"?"; confirm = { vm.onIntent(MemoryIntent.DeleteNote(n.id)) } }) { Text("Delete") } }
                } } } }
        } else {
            if (s.memories.isEmpty()) EmptyState("No memories", "Save useful facts from Chat (Remember) or tap + Add.")
            LazyColumn { items(s.memories, key = { it.id }) { m ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) {
                    Text(m.text)
                    Row { TextButton(onClick = { editMem = m }) { Text("Edit") }; TextButton(onClick = { confirmText = "Delete this memory?"; confirm = { vm.onIntent(MemoryIntent.DeleteMemory(m.id)) } }) { Text("Delete") } }
                } } } }
        }
    }

    if (newNote || editNote != null) {
        val n = editNote
        var title by remember(n) { mutableStateOf(n?.title.orEmpty()) }
        var text by remember(n) { mutableStateOf(n?.text.orEmpty()) }
        AlertDialog(onDismissRequest = { newNote = false; editNote = null }, title = { Text(if (n == null) "New note" else "Edit note") },
            text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true); OutlinedTextField(text, { text = it }, label = { Text("Text") }, modifier = Modifier.padding(top = 6.dp)) } },
            confirmButton = { Button(onClick = { vm.onIntent(MemoryIntent.SaveNote(n?.id, title, text)); newNote = false; editNote = null }) { Text("Save") } },
            dismissButton = { OutlinedButton(onClick = { newNote = false; editNote = null }) { Text("Cancel") } })
    }
    if (newMem || editMem != null) {
        val m = editMem
        var text by remember(m) { mutableStateOf(m?.text.orEmpty()) }
        AlertDialog(onDismissRequest = { newMem = false; editMem = null }, title = { Text(if (m == null) "New memory" else "Edit memory") },
            text = { OutlinedTextField(text, { text = it }, label = { Text("Fact to remember") }) },
            confirmButton = { Button(onClick = { vm.onIntent(MemoryIntent.SaveMemory(m?.id, text)); newMem = false; editMem = null }) { Text("Save") } },
            dismissButton = { OutlinedButton(onClick = { newMem = false; editMem = null }) { Text("Cancel") } })
    }
    confirm?.let { act ->
        ConfirmDialog("Please confirm", confirmText, onApprove = { act(); confirm = null }, onDeny = { confirm = null })
    }
}
