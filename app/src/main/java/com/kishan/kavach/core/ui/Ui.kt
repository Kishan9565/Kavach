package com.kishan.kavach.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.domain.agent.TaskStatus

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Creates a ViewModel wired with the app container (manual DI). */
@Composable
inline fun <reified T : ViewModel> containerViewModel(crossinline create: (AppContainer) -> T): T {
    val c = LocalContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

private val Cyan = Color(0xFF22D3EE)
private val Violet = Color(0xFF8B5CF6)
private val DarkScheme = darkColorScheme(primary = Cyan, secondary = Violet, tertiary = Color(0xFF34D399), background = Color(0xFF0B1020), surface = Color(0xFF111833), surfaceVariant = Color(0xFF1A2347))
private val LightScheme = lightColorScheme(primary = Color(0xFF0E7490), secondary = Color(0xFF6D28D9), tertiary = Color(0xFF047857))

@Composable
fun KavachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun StatusChip(status: TaskStatus) {
    val (label, color) = when (status) {
        TaskStatus.PENDING -> "Pending" to Color(0xFF94A3B8)
        TaskStatus.RUNNING -> "Running" to Color(0xFF22D3EE)
        TaskStatus.WAITING_CONFIRMATION -> "Needs approval" to Color(0xFFF59E0B)
        TaskStatus.COMPLETED -> "Completed" to Color(0xFF34D399)
        TaskStatus.FAILED -> "Failed" to Color(0xFFF87171)
        TaskStatus.CANCELLED -> "Cancelled" to Color(0xFF94A3B8)
    }
    Text(label, color = color, style = MaterialTheme.typography.labelLarge)
}

@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConfirmDialog(title: String, detail: String, onApprove: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text(title) }, text = { Text(detail) },
        confirmButton = { Button(onClick = onApprove) { Text("Approve") } },
        dismissButton = { OutlinedButton(onClick = onDeny) { Text("Deny") } },
    )
}
