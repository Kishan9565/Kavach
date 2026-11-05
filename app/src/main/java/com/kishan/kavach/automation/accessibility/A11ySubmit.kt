package com.kishan.kavach.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.inputmethod.EditorInfo

sealed interface SubmitResult {
    data class Submitted(val actionName: String) : SubmitResult
    data object Skipped : SubmitResult
    data object Unavailable : SubmitResult
}

object A11ySubmit {

    fun performSubmit(s: AccessibilityService): SubmitResult {
        if (Build.VERSION.SDK_INT < 33) return SubmitResult.Unavailable
        val im = s.inputMethod ?: return SubmitResult.Unavailable
        if (!im.currentInputStarted) return SubmitResult.Unavailable
        val conn = im.currentInputConnection ?: return SubmitResult.Unavailable
        val editorInfo = im.currentInputEditorInfo ?: return SubmitResult.Unavailable

        val actionId = editorInfo.actionId
        val imeOptions = editorInfo.imeOptions

        val actionToPerform: Int
        val actionName: String

        if (actionId != 0) {
            actionToPerform = actionId
            actionName = "actionId($actionId)"
        } else {
            val maskAction = imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

            if (maskAction == EditorInfo.IME_ACTION_NONE) {
                return SubmitResult.Skipped
            }

            if (noEnterAction) {
                return SubmitResult.Skipped
            }

            actionToPerform = maskAction
            actionName = when (maskAction) {
                EditorInfo.IME_ACTION_GO -> "GO"
                EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
                EditorInfo.IME_ACTION_SEND -> "SEND"
                EditorInfo.IME_ACTION_NEXT -> "NEXT"
                EditorInfo.IME_ACTION_DONE -> "DONE"
                else -> "imeOptions($maskAction)"
            }
        }

        conn.performEditorAction(actionToPerform)
        return SubmitResult.Submitted(actionName)
    }
}
