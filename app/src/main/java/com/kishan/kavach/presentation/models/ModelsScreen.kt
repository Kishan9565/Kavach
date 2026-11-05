package com.kishan.kavach.presentation.models

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kishan.kavach.ai.model.*
import com.kishan.kavach.ai.gemma.ModelManager
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ModelsState(
    val files: List<ModelFile> = emptyList(), val compat: Map<String, Compat> = emptyMap(), val selected: String? = null,
    val storage: StorageInfo = StorageInfo(0, 0), val engine: ModelState = ModelState.NotLoaded, val importing: Boolean = false,
    val importedBytes: Long = 0, val error: String? = null, val useGpu: Boolean = false, val ramGb: Double = 0.0,
) : UiState
sealed interface ModelsIntent : UiIntent {
    data class Import(val uri: Uri, val name: String?) : ModelsIntent
    data object CancelImport : ModelsIntent
    data class Select(val name: String) : ModelsIntent
    data class Delete(val name: String) : ModelsIntent
    data object Load : ModelsIntent
    data object Unload : ModelsIntent
    data class Gpu(val on: Boolean) : ModelsIntent
}
sealed interface ModelsEffect : UiEffect { data class Toast(val message: String) : ModelsEffect }

class ModelsViewModel(private val c: AppContainer) : MviViewModel<ModelsState, ModelsIntent, ModelsEffect>(ModelsState()) {
    private var importJob: Job? = null
    init {
        refresh()
        viewModelScope.launch { c.gemma.state.collect { m -> setState { copy(engine = m) }; refresh() } }
    }
    private fun refresh() {
        val m: ModelManager = c.models
        val files = m.list()
        setState { copy(files = files, compat = files.associate { it.name to m.compatibility(it) }, selected = m.selectedModel()?.name, storage = m.storage(), useGpu = c.prefs.useGpu, ramGb = m.totalRamBytes() / 1_073_741_824.0) }
    }
    override fun onIntent(intent: ModelsIntent) {
        when (intent) {
            is ModelsIntent.Import -> {
                setState { copy(importing = true, importedBytes = 0, error = null) }
                importJob = viewModelScope.launch {
                    val r = c.models.import(intent.uri, intent.name) { b -> setState { copy(importedBytes = b) } }
                    r.onFailure { e -> setState { copy(error = e.message ?: "Import failed") } }
                    setState { copy(importing = false) }
                    refresh()
                }
            }
            ModelsIntent.CancelImport -> { importJob?.cancel(); setState { copy(importing = false) }; refresh() }
            is ModelsIntent.Select -> { c.models.select(intent.name); viewModelScope.launch { c.gemma.unload() }; refresh() }
            is ModelsIntent.Delete -> viewModelScope.launch { c.gemma.unload(); c.models.delete(intent.name); refresh() }
            ModelsIntent.Load -> viewModelScope.launch { c.gemma.warmUp(); refresh() }
            ModelsIntent.Unload -> viewModelScope.launch { c.gemma.unload() }
            is ModelsIntent.Gpu -> { c.prefs.useGpu = intent.on; viewModelScope.launch { c.gemma.unload() }; refresh() }
        }
    }
}

private fun mb(b: Long) = "%.0f MB".format(b / 1_048_576.0)
private fun gb(b: Long) = "%.2f GB".format(b / 1_073_741_824.0)

@Composable
fun ModelsScreen() {
    val vm = containerViewModel { ModelsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var deleting by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            vm.onIntent(ModelsIntent.Import(uri, name))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Gemma model")
        Text("Gemma is the only AI model KAVACH uses. It runs on this phone through LiteRT-LM. The app has no internet permission, so download a .litertlm file yourself and import it here.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        s.error?.let { ErrorBanner(it) }

        SectionCard("Status") {
            Text(when (val e = s.engine) {
                ModelState.NotLoaded -> "Not loaded (loads on first use)"
                ModelState.Loading -> "Loading…"
                is ModelState.Ready -> "Ready · ${e.model} · ${e.backend} backend · loaded in ${e.loadMs} ms"
                is ModelState.Error -> "Error: ${e.message}"
            })
            Text("Phone RAM: %.1f GB · models use ${gb(s.storage.modelsBytes)} · free storage ${gb(s.storage.freeBytes)}".format(s.ramGb), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(s.useGpu, { vm.onIntent(ModelsIntent.Gpu(it)) }); Spacer(Modifier.width(8.dp))
                Text("Try GPU backend (falls back to CPU if it fails)", style = MaterialTheme.typography.bodySmall)
            }
            Row { Button(onClick = { vm.onIntent(ModelsIntent.Load) }, enabled = s.selected != null && s.engine !is ModelState.Loading) { Text("Load now") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { vm.onIntent(ModelsIntent.Unload) }, enabled = s.engine is ModelState.Ready) { Text("Unload") } }
        }

        SectionCard("Imported models") {
            if (s.files.isEmpty()) Text("None yet.")
            s.files.forEach { f ->
                val cp = s.compat[f.name]
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${f.name} · ${gb(f.sizeBytes)}${if (f.name == s.selected) "  ✓ selected" else ""}", style = MaterialTheme.typography.titleSmall)
                    cp?.let { Text(it.text, style = MaterialTheme.typography.bodySmall, color = if (it.level == CompatLevel.OK) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
                    Row { if (f.name != s.selected) TextButton(onClick = { vm.onIntent(ModelsIntent.Select(f.name)) }) { Text("Use this") }; TextButton(onClick = { deleting = f.name }) { Text("Delete") } }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (s.importing) {
                Text("Importing… ${mb(s.importedBytes)}")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedButton(onClick = { vm.onIntent(ModelsIntent.CancelImport) }) { Text("Cancel import") }
            } else Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .litertlm file") }
        }

        SectionCard("Where to get Gemma") {
            ModelManager.CATALOG.forEach { Text("${it.name} · ~${it.sizeGb} GB · ${it.minRamGb} GB+ RAM recommended\n${it.url}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp)) }
        }
    }
    deleting?.let { n -> ConfirmDialog("Delete model", "Delete $n from this phone?", onApprove = { vm.onIntent(ModelsIntent.Delete(n)); deleting = null }, onDeny = { deleting = null }) }
}
