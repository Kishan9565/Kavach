package com.kishan.kavach.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi

/**
 * Thin Android adapter for committing text through AccessibilityInputConnection (API 33+).
 * Acts like a keyboard via InputConnection, keeping the user's normal soft keyboard active.
 */
object A11yInput {

    private fun conn(s: AccessibilityService): InputMethod.AccessibilityInputConnection? {
        if (Build.VERSION.SDK_INT < 33) return null
        val im = s.inputMethod ?: return null
        if (!im.currentInputStarted) return null
        return im.currentInputConnection
    }

    suspend fun awaitConnection(s: AccessibilityService, timeoutMs: Long = 400L): InputMethod.AccessibilityInputConnection? {
        if (Build.VERSION.SDK_INT < 33) return null
        val end = SystemClock.uptimeMillis() + timeoutMs
        do {
            val c = conn(s)
            if (c != null) return c
            kotlinx.coroutines.delay(50)
        } while (SystemClock.uptimeMillis() < end)
        return null
    }

    /**
     * Replaces text in the focused editor using AccessibilityInputConnection.
     * Returns null if channel is unavailable (caller may fall back).
     * Returns non-null string containing the read-back result if committed.
     */
    suspend fun replace(s: AccessibilityService, text: String): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        val c = awaitConnection(s, 400L) ?: return null

        clear(c)
        c.commitText(text, 1, null)
        return readBack(c, text)
    }

    @RequiresApi(33)
    private fun clear(c: InputMethod.AccessibilityInputConnection) {
        val st = c.getSurroundingText(8192, 8192, 0) ?: return
        val len = st.text.length
        if (len == 0) return
        if (st.selectionStart >= 0 && st.selectionEnd >= 0) {
            c.deleteSurroundingText(st.selectionStart, len - st.selectionEnd)
        } else {
            c.setSelection(st.offset, st.offset + len)
        }
    }

    @RequiresApi(33)
    private suspend fun readBack(
        c: InputMethod.AccessibilityInputConnection,
        expectedText: String,
        maxPollMs: Long = 600L,
        pollIntervalMs: Long = 60L
    ): String {
        val end = SystemClock.uptimeMillis() + maxPollMs
        do {
            val st = c.getSurroundingText(8192, 8192, 0)
            val currentText = st?.text?.toString().orEmpty()
            if (currentText == expectedText) return currentText
            kotlinx.coroutines.delay(pollIntervalMs)
        } while (SystemClock.uptimeMillis() < end)

        val stFinal = c.getSurroundingText(8192, 8192, 0)
        return stFinal?.text?.toString().orEmpty()
    }
}
