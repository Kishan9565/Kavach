package com.kishan.kavach.core.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

interface UiState
interface UiIntent
interface UiEffect

/**
 * MVI base: UI sends [UiIntent]s, the ViewModel reduces them into one immutable [UiState] (StateFlow) and
 * emits one-time [UiEffect]s through a Channel. Composables hold no business logic.
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initial: S) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()
    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val current: S get() = _state.value
    protected fun setState(reducer: S.() -> S) { _state.update { it.reducer() } }
    protected fun emit(effect: E) { _effects.trySend(effect) }

    abstract fun onIntent(intent: I)
}
