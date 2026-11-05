package com.kishan.kavach.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.room.Room
import com.kishan.kavach.agent.executor.SkillRunner
import com.kishan.kavach.agent.executor.TaskRunner
import com.kishan.kavach.agent.loop.AgentLoop
import com.kishan.kavach.agent.planner.MemoryProvider
import com.kishan.kavach.agent.planner.TaskRouter
import com.kishan.kavach.agent.state.AutomationEngine
import com.kishan.kavach.agent.state.ConfirmationBus
import com.kishan.kavach.agent.state.DeviceState
import com.kishan.kavach.agent.state.TaskManager
import com.kishan.kavach.ai.gemma.GemmaEngine
import com.kishan.kavach.ai.gemma.ModelManager
import com.kishan.kavach.automation.accessibility.AccessibilityScreenObserver
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.automation.registry.ToolBootstrap
import com.kishan.kavach.core.permissions.AndroidPermissionChecker
import com.kishan.kavach.data.local.KavachDatabase
import com.kishan.kavach.data.preferences.Prefs
import com.kishan.kavach.data.repository.*
import com.kishan.kavach.domain.agent.AgentTask
import com.kishan.kavach.domain.agent.LlmClient
import com.kishan.kavach.domain.skills.BuiltInSkills
import com.kishan.kavach.domain.tools.*
import com.kishan.kavach.services.notification.StoredNotification
import com.kishan.kavach.workers.AutomationScheduler
import com.kishan.kavach.workers.ReminderSchedulerImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Calendar

/** Manual dependency container: one place wires everything (single module, no DI framework to misconfigure). */
class AppContainer(val app: Application) : AccessHost {
    companion object { const val CHAT_PREFIX = "\u0001CHAT\u0001"; const val SKILL_PREFIX = "\u0001SKILL\u0001" }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val prefs = Prefs(app)
    val db: KavachDatabase = Room.databaseBuilder(app, KavachDatabase::class.java, "kavach.db").build()
    val notes = NoteRepositoryImpl(db.notes())
    val memories = MemoryRepositoryImpl(db.memories())
    val chat = ChatRepositoryImpl(db.messages())
    val taskRepo = TaskRepositoryImpl(db.tasks())
    val productivity = ProductivityRepositoryImpl(db.productivity())
    val automations = AutomationRepositoryImpl(db.automations())

    val models = ModelManager(app, prefs)
    val gemma = GemmaEngine(app, models, prefs)
    override val llm: LlmClient get() = gemma

    val confirmationBus = ConfirmationBus()
    private val toolLogMax = 200
    private val _toolLog = MutableStateFlow<List<ToolLogEntry>>(emptyList())
    val toolLog: StateFlow<List<ToolLogEntry>> = _toolLog

    val registry: ToolRegistry = ToolBootstrap.build(app, notes, productivity, prefs, ReminderSchedulerImpl(app))
    val permissions = ToolPermissionManager(AndroidPermissionChecker(app))
    val executor = ToolExecutor(registry, permissions, Guard, { confirmationBus }, { e -> _toolLog.update { (it + e).takeLast(toolLogMax) } })
    val skills = BuiltInSkills.all()

    private val memoryProvider = MemoryProvider { q, k ->
        (notes.search(q, k).map { it.title + ": " + it.text.take(200) } + memories.search(q, k).map { it.text }).take(k)
    }
    private val router = TaskRouter({ skills }, memoryProvider, { ScreenBridge.controller?.connected == true })
    private val loop = AgentLoop(gemma, executor, registry, AccessibilityScreenObserver())
    private val skillRunner = SkillRunner(executor, permissions, gemma)
    private val taskRunner = TaskRunner(router, { skills }, skillRunner, loop, executor, gemma)

    private val _finished = MutableSharedFlow<Pair<AgentTask, String>>(extraBufferCapacity = 16)
    override val finishedTasks: SharedFlow<Pair<AgentTask, String>> = _finished

    /** taskId -> (request id, callback package) for external automation status callbacks. */
    val externalPending = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, String?>>()

    override val taskManager: TaskManager = TaskManager(
        taskRepo, chat, appScope,
        runner = { text, emit ->
            when {
                text.startsWith(CHAT_PREFIX) -> chatOnly(text.removePrefix(CHAT_PREFIX))
                text.startsWith(SKILL_PREFIX) -> runSkillDirect(text.removePrefix(SKILL_PREFIX), emit)
                else -> taskRunner.run(text, emit)
            }
        },
        onFinished = { t, msg -> onTaskFinished(t, msg) },
    )

    val automationEngine = AutomationEngine(automations, taskManager, { deviceState() })
    val automationScheduler = AutomationScheduler(app)

    init {
        confirmationBus.onWaiting = { waiting, _ -> taskManager.setWaitingConfirmation(waiting); if (waiting) surfaceUi() }
        appScope.launch {
            taskManager.recoverAfterRestart()
            importLegacyNotes()
            automations.all().filter { it.enabled }.forEach { automationScheduler.sync(it) }
        }
    }

    /** Format: skillName\nkey=value\nkey=value (values may not contain newlines). */
    private suspend fun runSkillDirect(spec: String, emit: (com.kishan.kavach.domain.agent.AgentEvent) -> Unit): com.kishan.kavach.domain.agent.AgentOutcome {
        val lines = spec.lines()
        val skill = skills.firstOrNull { it.name == lines.first().trim() } ?: return com.kishan.kavach.domain.agent.AgentOutcome.Failed("Skill not found")
        val params = lines.drop(1).filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        return when (val o = skillRunner.run(skill, params, emit)) {
            is com.kishan.kavach.agent.executor.SkillOutcome.Success -> com.kishan.kavach.domain.agent.AgentOutcome.Completed(o.message)
            is com.kishan.kavach.agent.executor.SkillOutcome.Failure -> com.kishan.kavach.domain.agent.AgentOutcome.Failed(o.reason)
        }
    }

    private suspend fun chatOnly(text: String): com.kishan.kavach.domain.agent.AgentOutcome {
        if (!gemma.isReady) return com.kishan.kavach.domain.agent.AgentOutcome.Failed("Gemma model nahi mila.")
        return try {
            com.kishan.kavach.domain.agent.AgentOutcome.Answer(gemma.generate("You are KAVACH, a concise on-device assistant. Answer in the user's language (Hinglish is fine). Keep it short.", text.take(500)))
        } catch (e: com.kishan.kavach.domain.agent.LlmUnavailableException) { com.kishan.kavach.domain.agent.AgentOutcome.Failed(e.message ?: "Gemma error") }
    }

    private fun onTaskFinished(task: AgentTask, msg: String) {
        _finished.tryEmit(task to msg)
        appScope.launch {
            automationEngine.onTaskFinished(task, msg)
            externalPending.remove(task.id)?.let { (reqId, cb) ->
                com.kishan.kavach.automation.external.ExternalStatusSender.send(app, cb, reqId, task.status.name, msg)
            }
        }
    }

    override fun onNotification(n: StoredNotification) {
        appScope.launch { automationEngine.onNotification(n.pkg, n.title + " " + n.text) }
    }

    override fun surfaceUi() {
        runCatching {
            app.startActivity(Intent(app, com.kishan.kavach.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun deviceState(): DeviceState {
        val b = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return DeviceState(
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            wifi = wifi, hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        )
    }

    /** One-time import of the Sprint 3 plain-text notes file into Room. */
    private suspend fun importLegacyNotes() {
        val f = File(app.filesDir, "notes.txt")
        if (!f.exists()) return
        runCatching {
            f.readLines().filter { it.isNotBlank() }.forEach { notes.add("Note", it) }
            f.renameTo(File(app.filesDir, "notes.txt.migrated"))
        }
    }
}
