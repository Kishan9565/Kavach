package com.kishan.kavach.agent.state

import com.kishan.kavach.domain.tools.ConfirmationRequest
import com.kishan.kavach.domain.tools.Confirmer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

class PendingConfirmation(val request: ConfirmationRequest, internal val answer: CompletableDeferred<Boolean>)

/** Bridges the suspended tool pipeline to the UI. Times out to "denied": nothing consequential runs unattended. */
class ConfirmationBus(
    private val timeoutMs: Long = 60_000,
    /** Called when a confirmation starts/ends (TaskManager uses it for WAITING_CONFIRMATION; app uses it to surface the UI). */
    var onWaiting: (waiting: Boolean, request: ConfirmationRequest?) -> Unit = { _, _ -> },
) : Confirmer {
    private val _pending = MutableStateFlow<PendingConfirmation?>(null)
    val pending: StateFlow<PendingConfirmation?> = _pending

    override suspend fun confirm(request: ConfirmationRequest): Boolean {
        val p = PendingConfirmation(request, CompletableDeferred())
        _pending.value = p
        onWaiting(true, request)
        return try {
            withTimeout(timeoutMs) { p.answer.await() }
        } catch (e: TimeoutCancellationException) {
            false
        } finally {
            _pending.value = null
            onWaiting(false, null)
        }
    }

    fun resolve(approved: Boolean) { _pending.value?.answer?.complete(approved) }
}
