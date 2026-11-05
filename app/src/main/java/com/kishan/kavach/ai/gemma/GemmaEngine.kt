package com.kishan.kavach.ai.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.kishan.kavach.ai.model.ModelState
import com.kishan.kavach.data.preferences.Prefs
import com.kishan.kavach.domain.agent.LlmClient
import com.kishan.kavach.domain.agent.LlmUnavailableException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The ONLY LLM in KAVACH: a local Gemma model through LiteRT-LM (call shapes copied from the PokeClaw build that
 * uses litertlm-android 0.10.0). No network, no fallback model: if Gemma cannot load, callers get a clear error.
 * Lazy load, one request at a time, unload after 5 idle minutes, GPU -> CPU fallback when GPU is enabled.
 */
class GemmaEngine(private val app: Context, private val models: ModelManager, private val prefs: Prefs) : LlmClient {
    companion object { private const val IDLE_MS = 5 * 60_000L; private const val MAX_TOKENS = 4096 }

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var idleJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val state: StateFlow<ModelState> = _state

    /** "Ready" here means a usable model file exists; the engine itself loads lazily on first use. */
    override val isReady: Boolean get() = models.selectedModel() != null

    override suspend fun generate(system: String, user: String): String {
        val file = models.selectedModel() ?: throw LlmUnavailableException("Gemma model nahi mila. Models screen se .litertlm file import karo.")
        return mutex.withLock {
            idleJob?.cancel()
            try {
                withContext(Dispatchers.Default) {
                    val e = ensureLoaded(file.absolutePath, file.name)
                    val conv = e.createConversation(ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.3),
                    ))
                    try { conv.sendMessage(user, emptyMap()).contents?.toString()?.trim().orEmpty() }
                    finally { runCatching { conv.close() } }
                }
            } catch (e: LlmUnavailableException) { throw e
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) {
                _state.value = ModelState.Error(e.message ?: e.javaClass.simpleName)
                throw LlmUnavailableException("Gemma error: ${e.message ?: e.javaClass.simpleName}".take(200))
            } finally { scheduleIdle() }
        }
    }

    private fun ensureLoaded(path: String, name: String): Engine {
        engine?.let { if (loadedPath == path) return it }
        closeEngine()
        val ramGb = models.totalRamBytes() / 1_073_741_824.0
        if (ramGb < 3.5) throw LlmUnavailableException("Is phone mein sirf %.1f GB RAM hai: Gemma load nahi ho sakta.".format(ramGb))
        _state.value = ModelState.Loading
        val t0 = System.currentTimeMillis()
        val attempts: List<Pair<String, () -> Backend>> =
            if (prefs.useGpu) listOf("GPU" to { Backend.GPU() }, "CPU" to { Backend.CPU() }) else listOf("CPU" to { Backend.CPU() })
        var last: Throwable? = null
        for ((label, make) in attempts) {
            try {
                val e = Engine(EngineConfig(modelPath = path, backend = make(), maxNumTokens = MAX_TOKENS, cacheDir = app.cacheDir.path))
                e.initialize()
                engine = e; loadedPath = path
                _state.value = ModelState.Ready(name, label, System.currentTimeMillis() - t0)
                return e
            } catch (t: Throwable) { last = t }
        }
        val msg = "Model load failed: ${last?.message ?: "unknown"}".take(200)
        _state.value = ModelState.Error(msg)
        throw LlmUnavailableException(msg)
    }

    private fun closeEngine() { runCatching { engine?.close() }; engine = null; loadedPath = null }

    private fun scheduleIdle() {
        idleJob = scope.launch { delay(IDLE_MS); mutex.withLock { closeEngine(); _state.value = ModelState.NotLoaded } }
    }

    /** Explicit unload (Models screen). */
    suspend fun unload() = mutex.withLock { idleJob?.cancel(); closeEngine(); _state.value = ModelState.NotLoaded }

    /** Warm-up: load now so the first real request is fast. Errors surface in [state]. */
    suspend fun warmUp() { runCatching { generate("Reply with OK.", "ping") } }
}
