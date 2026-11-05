package com.kishan.kavach.presentation.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.automation.tools.ReminderScheduler
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.permissions.AndroidPermissionChecker
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.services.notification.NotificationStore
import com.kishan.kavach.services.notification.StoredNotification
import com.kishan.kavach.workers.ReminderSchedulerImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NotificationsState(val access: Boolean = false, val items: List<StoredNotification> = emptyList()) : UiState
sealed interface NotificationsIntent : UiIntent {
    data object Summarize : NotificationsIntent
    data class RemindLater(val n: StoredNotification) : NotificationsIntent
    data object Clear : NotificationsIntent
}
sealed interface NotificationsEffect : UiEffect { data object OpenChat : NotificationsEffect; data class Toast(val message: String) : NotificationsEffect }

class NotificationsViewModel(private val c: AppContainer) : MviViewModel<NotificationsState, NotificationsIntent, NotificationsEffect>(NotificationsState()) {
    private val scheduler: ReminderScheduler = ReminderSchedulerImpl(c.app)
    init {
        viewModelScope.launch { NotificationStore.items.collect { l -> setState { copy(items = l.reversed()) } } }
        viewModelScope.launch { while (true) { setState { copy(access = AndroidPermissionChecker.notificationAccessEnabled(c.app)) }; delay(2000) } }
    }
    override fun onIntent(intent: NotificationsIntent) {
        when (intent) {
            NotificationsIntent.Summarize -> { c.taskManager.submit("meri notifications ka summary batao", "chat"); emit(NotificationsEffect.OpenChat) }
            is NotificationsIntent.RemindLater -> viewModelScope.launch {
                val at = System.currentTimeMillis() + 3_600_000
                val text = "${intent.n.app}: ${intent.n.title}".take(150)
                scheduler.schedule(c.productivity.addReminder(text, at), text, at)
                emit(NotificationsEffect.Toast("Reminder set for 1 hour from now"))
            }
            NotificationsIntent.Clear -> NotificationStore.clear()
        }
    }
}

@Composable
fun NotificationsScreen(onOpenChat: () -> Unit) {
    val vm = containerViewModel { NotificationsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.effects.collect { when (it) { NotificationsEffect.OpenChat -> onOpenChat(); is NotificationsEffect.Toast -> android.widget.Toast.makeText(ctx, it.message, android.widget.Toast.LENGTH_SHORT).show() } } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Notifications")
        if (!s.access) {
            SectionCard("Notification access is off") {
                Text("Optional. When on, KAVACH keeps recent notifications in memory only (never saved to disk), summarises them locally with Gemma and can suggest reminders. Banking/OTP notifications are never captured.")
                Spacer(Modifier.height(6.dp))
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Grant access") }
            }
        } else {
            Row { Button(onClick = { vm.onIntent(NotificationsIntent.Summarize) }, enabled = s.items.isNotEmpty()) { Text("Summarize with Gemma") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { vm.onIntent(NotificationsIntent.Clear) }) { Text("Clear") } }
            if (s.items.isEmpty()) EmptyState("Nothing captured yet", "New notifications that arrive while KAVACH has access will show up here.")
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(s.items, key = { it.key + it.time }) { n ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(10.dp)) {
                        Text("${n.app} · ${n.title}", style = MaterialTheme.typography.titleSmall)
                        Text(n.text, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.onIntent(NotificationsIntent.RemindLater(n)) }) { Text("Remind me in 1 h") }
                    } }
                }
            }
        }
    }
}
