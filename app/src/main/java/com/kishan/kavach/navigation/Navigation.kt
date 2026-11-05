package com.kishan.kavach.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kishan.kavach.core.ui.ConfirmDialog
import com.kishan.kavach.core.ui.LocalContainer
import com.kishan.kavach.presentation.automation.AutomationScreen
import com.kishan.kavach.presentation.chat.ChatScreen
import com.kishan.kavach.presentation.developer.DeveloperScreen
import com.kishan.kavach.presentation.home.HomeScreen
import com.kishan.kavach.presentation.memory.MemoryScreen
import com.kishan.kavach.presentation.models.ModelsScreen
import com.kishan.kavach.presentation.notifications.NotificationsScreen
import com.kishan.kavach.presentation.settings.SettingsScreen
import com.kishan.kavach.presentation.skills.SkillsScreen
import com.kishan.kavach.presentation.tasks.TasksScreen

object Routes {
    const val HOME = "home"; const val CHAT = "chat"; const val TASKS = "tasks"; const val SKILLS = "skills"; const val MORE = "more"
    const val MEMORY = "memory"; const val NOTIFICATIONS = "notifications"; const val AUTOMATION = "automation"
    const val MODELS = "models"; const val DEVELOPER = "developer"; const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: String)
private val tabs = listOf(Tab(Routes.HOME, "Home", "🏠"), Tab(Routes.CHAT, "Chat", "💬"), Tab(Routes.TASKS, "Tasks", "✅"), Tab(Routes.SKILLS, "Skills", "🧩"), Tab(Routes.MORE, "More", "⋯"))

@Composable
fun KavachRoot() {
    val container = LocalContainer.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val pending by container.confirmationBus.pending.collectAsStateWithLifecycle()

    // Consequential actions (send, delete, auto-reply) are approved here, on any screen.
    pending?.let { p ->
        ConfirmDialog(p.request.title, p.request.detail, onApprove = { container.confirmationBus.resolve(true) }, onDeny = { container.confirmationBus.resolve(false) })
    }

    fun go(r: String) = nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }

    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEach { t ->
                val selected = route == t.route || (t.route == Routes.MORE && route in setOf(Routes.MEMORY, Routes.NOTIFICATIONS, Routes.AUTOMATION, Routes.MODELS, Routes.DEVELOPER, Routes.SETTINGS))
                NavigationBarItem(selected = selected, onClick = { go(t.route) }, icon = { Text(t.icon) }, label = { Text(t.label) })
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) { HomeScreen(onNavigate = ::go) }
            composable(Routes.CHAT) { ChatScreen() }
            composable(Routes.TASKS) { TasksScreen() }
            composable(Routes.SKILLS) { SkillsScreen() }
            composable(Routes.MORE) { MoreScreen(onNavigate = ::go) }
            composable(Routes.MEMORY) { MemoryScreen() }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onOpenChat = { go(Routes.CHAT) }) }
            composable(Routes.AUTOMATION) { AutomationScreen() }
            composable(Routes.MODELS) { ModelsScreen() }
            composable(Routes.DEVELOPER) { DeveloperScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
