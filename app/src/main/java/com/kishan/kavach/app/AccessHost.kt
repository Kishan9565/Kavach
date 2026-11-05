package com.kishan.kavach.app

import com.kishan.kavach.agent.state.TaskManager
import com.kishan.kavach.data.preferences.Prefs
import com.kishan.kavach.domain.agent.AgentTask
import com.kishan.kavach.domain.agent.LlmClient
import com.kishan.kavach.services.notification.StoredNotification
import kotlinx.coroutines.flow.SharedFlow

/** Narrow view of the app container for the Android services (keeps them decoupled from Room/Compose). */
interface AccessHost {
    val taskManager: TaskManager
    val prefs: Prefs
    val llm: LlmClient
    val finishedTasks: SharedFlow<Pair<AgentTask, String>>
    /** Asks the OS to show the app's UI (used when a confirmation is pending and the app is not visible). */
    fun surfaceUi()
    /** Notification-trigger hook for automations. */
    fun onNotification(n: StoredNotification)
}

interface AccessHostProvider { val host: AccessHost }
