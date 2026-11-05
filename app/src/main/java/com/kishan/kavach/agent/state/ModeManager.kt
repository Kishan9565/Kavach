package com.kishan.kavach.agent.state

import com.kishan.kavach.domain.model.Mode
import com.kishan.kavach.domain.tools.Origin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModeManager {

    private val _activeMode = MutableStateFlow<Mode?>(null)
    val activeMode: StateFlow<Mode?> = _activeMode

    fun adopt(mode: Mode, origin: Origin): Boolean {
        // Notification-driven and IDLE turns can never create or enter a mode
        if (origin == Origin.NOTIFICATION || origin == Origin.IDLE || origin == Origin.EXTERNAL) {
            return false
        }
        _activeMode.value = mode.copy(adoptedAt = System.currentTimeMillis())
        return true
    }

    fun exit() {
        _activeMode.value = null
    }

    fun activePromptText(): String? {
        return _activeMode.value?.toPromptInstruction()
    }
}
