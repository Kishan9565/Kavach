package com.kishan.kavach.presentation.skills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.domain.skills.Skill

data class SkillsState(val skills: List<Skill> = emptyList(), val gemmaReady: Boolean = false) : UiState
sealed interface SkillsIntent : UiIntent { data class Run(val skill: Skill, val params: Map<String, String>) : SkillsIntent }
sealed interface SkillsEffect : UiEffect { data class Toast(val message: String) : SkillsEffect }

class SkillsViewModel(private val c: AppContainer) : MviViewModel<SkillsState, SkillsIntent, SkillsEffect>(SkillsState(c.skills, c.gemma.isReady)) {
    override fun onIntent(intent: SkillsIntent) {
        when (intent) {
            is SkillsIntent.Run -> {
                val spec = intent.skill.name + intent.params.entries.joinToString("") { "\n${it.key}=${it.value.replace("\n", " ")}" }
                c.taskManager.submit(AppContainer.SKILL_PREFIX + spec, "skill")
                emit(SkillsEffect.Toast("Started: ${intent.skill.name}. Progress in Tasks."))
            }
        }
    }
}

@Composable
fun SkillsScreen() {
    val vm = containerViewModel { SkillsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<Skill?>(null) }
    LaunchedEffect(Unit) { vm.effects.collect { if (it is SkillsEffect.Toast) android.widget.Toast.makeText(ctx, it.message, android.widget.Toast.LENGTH_SHORT).show() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Skills")
        Text("Reusable workflows built from tools. Matching phrases run them automatically, without asking Gemma to plan from scratch.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(s.skills, key = { it.name }) { sk ->
                SectionCard(sk.name) {
                    Text(sk.description)
                    if (sk.params.isNotEmpty()) Text("Needs: " + sk.params.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
                    if (sk.permissions.isNotEmpty()) Text("Permissions: " + sk.permissions.joinToString(), style = MaterialTheme.typography.bodySmall)
                    Text("Steps: " + sk.steps.joinToString(" → ") { it.tool }, style = MaterialTheme.typography.bodySmall)
                    if (sk.steps.any { it.isLlm } && !s.gemmaReady) Text("Needs the Gemma model for its summary step.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { if (sk.params.isEmpty()) vm.onIntent(SkillsIntent.Run(sk, emptyMap())) else dialog = sk }) { Text("Run") }
                }
            }
        }
    }

    dialog?.let { sk ->
        val values = remember(sk) { mutableStateMapOf<String, String>() }
        AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text(sk.name) },
            text = { Column { sk.params.forEach { p -> OutlinedTextField(values[p.name].orEmpty(), { values[p.name] = it }, label = { Text("${p.name}${if (p.required) "" else " (optional)"}") }, singleLine = true, modifier = Modifier.padding(vertical = 3.dp)) } } },
            confirmButton = { Button(enabled = sk.params.filter { it.required }.all { !values[it.name].isNullOrBlank() }, onClick = { vm.onIntent(SkillsIntent.Run(sk, values.toMap())); dialog = null }) { Text("Run") } },
            dismissButton = { OutlinedButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}
