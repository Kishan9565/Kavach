package com.kishan.kavach.automation.accessibility

import com.kishan.kavach.domain.agent.ScreenObserver
import com.kishan.kavach.domain.agent.ScreenSnapshot

/** What tools may ask of the phone screen. Implemented by the AccessibilityService; framework-free so tools stay testable. */
interface ScreenController {
    val connected: Boolean
    val screenWidth: Int
    val screenHeight: Int
    suspend fun snapshot(): ScreenSnapshot?
    suspend fun tapElement(id: Int): Boolean
    suspend fun tapText(text: String): Boolean
    suspend fun tapAt(x: Int, y: Int): Boolean
    suspend fun longPressElement(id: Int): Boolean
    suspend fun longPressText(text: String): Boolean
    suspend fun longPressAt(x: Int, y: Int): Boolean
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean
    suspend fun scroll(forward: Boolean): Boolean
    /** Sets text in the focused (or first) editable field. Never touches password fields. */
    suspend fun inputText(text: String, submit: Boolean): Boolean
    /** Presses the messaging app's own Send control. Only send_message may call this, after confirmation. */
    suspend fun pressSendButton(): Boolean
    fun back(): Boolean
    fun home(): Boolean
    suspend fun screenContains(text: String, waitMs: Long): Boolean
    /** API 30+ and screenshot capability enabled; returns a file path or null. */
    suspend fun screenshot(): String?
    fun highlight(text: String)
    fun caption(text: String)
}

/** Process-wide handle to the connected accessibility service (null when the service is off). */
object ScreenBridge {
    @Volatile var controller: ScreenController? = null
}

class AccessibilityScreenObserver : ScreenObserver {
    override val available: Boolean get() = ScreenBridge.controller?.connected == true
    override suspend fun snapshot(): ScreenSnapshot? = ScreenBridge.controller?.snapshot()
}
