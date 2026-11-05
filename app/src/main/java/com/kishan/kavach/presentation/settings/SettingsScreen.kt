package com.kishan.kavach.presentation.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import com.kishan.kavach.app.AppContainer
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.core.common.MviViewModel
import com.kishan.kavach.core.common.UiEffect
import com.kishan.kavach.core.common.UiIntent
import com.kishan.kavach.core.common.UiState
import com.kishan.kavach.core.permissions.AndroidPermissionChecker
import com.kishan.kavach.core.ui.*
import com.kishan.kavach.services.accessibility.KavachAccess

data class SettingsState(
    val bubble: Boolean = false, val speak: Boolean = true, val voiceLang: String = "en-IN", val autoReply: Boolean = false, val allowed: String = "",
    val owner: String = "", val tag: Boolean = true, val extEnabled: Boolean = false, val extToken: String = "", val extPackages: String = "",
    val accessibility: Boolean = false, val notifAccess: Boolean = false, val tokenVisible: Boolean = false,
) : UiState
sealed interface SettingsIntent : UiIntent {
    data class Bubble(val on: Boolean) : SettingsIntent
    data class Speak(val on: Boolean) : SettingsIntent
    data class Lang(val code: String) : SettingsIntent
    data class AutoReply(val on: Boolean) : SettingsIntent
    data class Allowed(val v: String) : SettingsIntent
    data class Owner(val v: String) : SettingsIntent
    data class Tag(val on: Boolean) : SettingsIntent
    data class ExtEnabled(val on: Boolean) : SettingsIntent
    data class ExtPackages(val v: String) : SettingsIntent
    data object ResetToken : SettingsIntent
    data object ToggleToken : SettingsIntent
    data object Refresh : SettingsIntent
}
sealed interface SettingsEffect : UiEffect

class SettingsViewModel(private val c: AppContainer) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {
    init { load() }
    private fun load() = setState {
        val p = c.prefs
        copy(bubble = p.bubble, speak = p.speak, voiceLang = p.voiceLang, autoReply = p.enabled, allowed = p.allowed, owner = p.ownerName, tag = p.tagReplies,
            extEnabled = p.externalEnabled, extToken = p.externalToken(), extPackages = p.externalPackages,
            accessibility = ScreenBridge.controller?.connected == true, notifAccess = AndroidPermissionChecker.notificationAccessEnabled(c.app))
    }
    override fun onIntent(intent: SettingsIntent) {
        val p = c.prefs
        when (intent) {
            is SettingsIntent.Bubble -> { p.bubble = intent.on; KavachAccess.instance?.applyPrefs() }
            is SettingsIntent.Speak -> p.speak = intent.on
            is SettingsIntent.Lang -> p.voiceLang = intent.code
            is SettingsIntent.AutoReply -> p.enabled = intent.on && p.allowedList().isNotEmpty()
            is SettingsIntent.Allowed -> p.allowed = intent.v
            is SettingsIntent.Owner -> p.ownerName = intent.v
            is SettingsIntent.Tag -> p.tagReplies = intent.on
            is SettingsIntent.ExtEnabled -> p.externalEnabled = intent.on
            is SettingsIntent.ExtPackages -> p.externalPackages = intent.v
            SettingsIntent.ResetToken -> p.resetExternalToken()
            SettingsIntent.ToggleToken -> { setState { copy(tokenVisible = !tokenVisible) }; return }
            SettingsIntent.Refresh -> {}
        }
        load()
    }
}

@Composable
fun SettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    fun open(action: String) = ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onIntent(SettingsIntent.Refresh) }
    LaunchedEffect(Unit) { vm.onIntent(SettingsIntent.Refresh) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Settings & setup")

        SectionCard("Permissions") {
            Text(if (s.accessibility) "✅ Accessibility (screen tools)" else "⚠ Accessibility off")
            OutlinedButton(onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Accessibility settings") }
            Text(if (s.notifAccess) "✅ Notification access (optional)" else "Notification access off (optional)")
            OutlinedButton(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Notification access settings") }
            Spacer(Modifier.height(6.dp))
            Text("Ask only when needed:", style = MaterialTheme.typography.bodySmall)
            Row {
                OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }) { Text("Mic") }; Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }) { Text("Contacts") }; Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.READ_CALENDAR)) }) { Text("Calendar") }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Allow reminder notifications") }
        }

        SectionCard("Voice & bubble") {
            SwitchRow("Floating bubble (tap to talk, needs accessibility)", s.bubble) { vm.onIntent(SettingsIntent.Bubble(it)) }
            SwitchRow("Speak answers", s.speak) { vm.onIntent(SettingsIntent.Speak(it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speech language: ", style = MaterialTheme.typography.bodySmall)
                FilterChip(s.voiceLang == "en-IN", { vm.onIntent(SettingsIntent.Lang("en-IN")) }, label = { Text("Hinglish / English") }); Spacer(Modifier.width(6.dp))
                FilterChip(s.voiceLang == "hi-IN", { vm.onIntent(SettingsIntent.Lang("hi-IN")) }, label = { Text("हिन्दी") })
            }
            Text("Offline recognition depends on the language pack installed in your phone's speech service.", style = MaterialTheme.typography.labelSmall)
        }

        SectionCard("WhatsApp auto-reply") {
            Text("Replies only to the exact contact names listed, never groups, never money/OTP/link messages. Needs Notification access.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(s.allowed, { vm.onIntent(SettingsIntent.Allowed(it)) }, label = { Text("Contacts (comma separated, exact names)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.owner, { vm.onIntent(SettingsIntent.Owner(it)) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SwitchRow("Prefix replies with 🤖", s.tag) { vm.onIntent(SettingsIntent.Tag(it)) }
            SwitchRow("Auto-reply ON", s.autoReply) { vm.onIntent(SettingsIntent.AutoReply(it)) }
        }

        SectionCard("External automation (Tasker / MacroDroid)") {
            Text("OFF by default. When on, other apps may send RUN_TASK / RUN_CHAT broadcasts that include your secret token. Tasks still pass all safety checks; confirmations are never auto-approved.", style = MaterialTheme.typography.bodySmall)
            SwitchRow("Enable external automation", s.extEnabled) { vm.onIntent(SettingsIntent.ExtEnabled(it)) }
            OutlinedTextField(s.extPackages, { vm.onIntent(SettingsIntent.ExtPackages(it)) }, label = { Text("Allowed caller packages") }, modifier = Modifier.fillMaxWidth())
            Text("Token: ${if (s.tokenVisible) s.extToken else "••••••••••••"}", style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = { vm.onIntent(SettingsIntent.ToggleToken) }) { Text(if (s.tokenVisible) "Hide" else "Show") }; TextButton(onClick = { vm.onIntent(SettingsIntent.ResetToken) }) { Text("Regenerate") } }
            Text("Actions: com.kishan.kavach.action.RUN_TASK / RUN_CHAT · extras: token, task, request_id, callback_package", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}
