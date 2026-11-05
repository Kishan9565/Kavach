package com.kishan.kavach.services.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import com.kishan.kavach.app.AccessHost
import com.kishan.kavach.app.AccessHostProvider
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.automation.accessibility.ScreenController
import com.kishan.kavach.domain.agent.ScreenSnapshot
import com.kishan.kavach.domain.agent.TaskStatus
import com.kishan.kavach.domain.agent.UiNode
import com.kishan.kavach.domain.tools.Guard
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Everything on-screen lives here: floating bubble, Stop pill, captions, highlight ring (TYPE_ACCESSIBILITY_OVERLAY, so
 * no overlay permission is needed) plus the screen primitives behind the tool registry (ScreenController).
 * Business logic does NOT live here: voice results are submitted to the TaskManager like any other task.
 */
class KavachAccess : AccessibilityService(), ScreenController {
    companion object { @Volatile var instance: KavachAccess? = null }

    private lateinit var wm: WindowManager
    private lateinit var scope: CoroutineScope
    private val h = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private var pill: View? = null
    private var captionView: TextView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val hideCaption = Runnable { captionView?.visibility = View.GONE }
    @Volatile private var lastNodes: List<UiNode> = emptyList()
    private val host: AccessHost? get() = (application as? AccessHostProvider)?.host

    override val connected: Boolean get() = instance === this
    override val screenWidth: Int get() = resources.displayMetrics.widthPixels
    override val screenHeight: Int get() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        instance = this
        ScreenBridge.controller = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        tts = TextToSpeech(this) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale("en", "IN")
        }
        host?.let { hst ->
            scope.launch { hst.taskManager.activeTaskId.collect { setRunning(it != null) } }
            scope.launch {
                hst.taskManager.tasks.collect { list ->
                    list.firstOrNull { it.status == TaskStatus.RUNNING }?.currentAction?.takeIf { it.isNotBlank() }?.let { caption(it, 2500) }
                }
            }
            scope.launch {
                hst.finishedTasks.collect { (task, msg) ->
                    if (task.source == "bubble") { caption(msg, 5000); speak(msg) }
                }
            }
        }
        applyPrefs()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        ScreenBridge.controller = null
        host?.taskManager?.cancelActive()
        runCatching { recognizer?.destroy() }
        tts?.shutdown()
        h.removeCallbacksAndMessages(null)
        listOf(bubble, pill, captionView).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        bubble = null; pill = null; captionView = null
        if (::scope.isInitialized) scope.cancel()
        return super.onUnbind(intent)
    }

    // ------------------------------------------------------------------ overlay UI
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun params(w: Int, hh: Int, flags: Int) = WindowManager.LayoutParams(
        w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private val NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    fun applyPrefs() { if (host?.prefs?.bubble == true) showBubble() else hideBubble() }

    private fun showBubble() {
        if (bubble != null) return
        val v = TextView(this).apply {
            text = "🛡"; textSize = 24f; gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF22D3EE.toInt(), 0xFF8B5CF6.toInt())
            ).apply { shape = GradientDrawable.OVAL }
            elevation = dp(6).toFloat()
        }
        val lp = params(dp(56), dp(56), NF or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL).apply { x = dp(8); y = dp(300) }
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - dx) > 12 || abs(e.rawY - dy) > 12) moved = true
                    lp.x = sx + (e.rawX - dx).toInt(); lp.y = sy + (e.rawY - dy).toInt()
                    wm.updateViewLayout(view, lp); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onBubbleTap(); true }
                else -> false
            }
        }
        wm.addView(v, lp); bubble = v
    }

    private fun hideBubble() { bubble?.let { runCatching { wm.removeView(it) } }; bubble = null }

    private fun onBubbleTap() { if (host?.taskManager?.activeTaskId?.value != null) host?.taskManager?.cancelActive() else listen() }

    private fun setRunning(on: Boolean) {
        if (on && pill == null) {
            val v = TextView(this).apply {
                text = "■  Stop"; setTextColor(Color.WHITE); textSize = 16f
                setPadding(dp(20), dp(10), dp(20), dp(10))
                background = GradientDrawable().apply { setColor(0xFFDC2626.toInt()); cornerRadius = dp(24).toFloat() }
                setOnClickListener { host?.taskManager?.cancelActive() }
            }
            val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, NF)
                .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
            wm.addView(v, lp); pill = v
        }
        pill?.visibility = if (on) View.VISIBLE else View.GONE
    }

    override fun caption(text: String) = caption(text, 3500)

    fun caption(text: String, ms: Long) {
        if (!::wm.isInitialized) return
        val c = captionView ?: TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; maxWidth = dp(320)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { setColor(0xE6101830.toInt()); cornerRadius = dp(18).toFloat() }
        }.also { v ->
            val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                NF or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                .apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(110) }
            wm.addView(v, lp); captionView = v
        }
        c.text = text.take(200); c.visibility = View.VISIBLE
        h.removeCallbacks(hideCaption); h.postDelayed(hideCaption, ms)
    }

    private fun showRing(r: Rect) {
        if (r.width() <= 0 || r.height() <= 0) return
        val v = View(this).apply {
            background = GradientDrawable().apply {
                setStroke(dp(4), 0xFF22D3EE.toInt()); cornerRadius = dp(12).toFloat(); setColor(0x2222D3EE)
            }
        }
        val lp = params(r.width(), r.height(),
            NF or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply { x = r.left; y = r.top }
        runCatching { wm.addView(v, lp) }.onFailure { return }
        v.animate().alpha(0.3f).setDuration(300).withEndAction {
            v.animate().alpha(1f).setDuration(300).withEndAction { runCatching { wm.removeView(v) } }.start()
        }.start()
    }

    override fun highlight(text: String) {
        h.post { find(text)?.let { n -> val r = Rect(); n.getBoundsInScreen(r); showRing(r) } }
    }

    private fun speak(t: String) {
        if (ttsReady && host?.prefs?.speak == true) tts?.speak(t.take(120), TextToSpeech.QUEUE_FLUSH, null, "kavach")
    }

    // ------------------------------------------------------------------ voice (bubble)
    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            caption("Mic permission do: KAVACH > Setup", 3500); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { caption("Speech recognition available nahi.", 3500); return }
        runCatching { recognizer?.destroy() }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { caption("🎤 Bolo…", 6000) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onError(e: Int) { caption("Mic error ($e). Bubble dobara tap karo.", 3500) }
                override fun onPartialResults(p: Bundle?) {
                    p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { caption("🎤 $it", 2000) }
                }
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (t.isNullOrBlank()) caption("Kuch sunai nahi diya.", 3500)
                    else { caption("▶ $t", 3500); host?.taskManager?.submit(t, "bubble") }
                }
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, host?.prefs?.voiceLang ?: "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        recognizer?.startListening(i)
    }

    // ------------------------------------------------------------------ screen primitives
    private fun find(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text)
            .sortedBy { n -> if ((n.text ?: n.contentDescription)?.toString().equals(text, true)) 0 else 1 }
            .firstOrNull { it.isVisibleToUser }
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var t = n
        while (t != null && !t.isClickable) t = t.parent
        return t
    }

    private fun nodeByBounds(n: AccessibilityNodeInfo?, b: UiNode, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 30) return null
        val r = Rect(); n.getBoundsInScreen(r)
        if (r.left == b.left && r.top == b.top && r.right == b.right && r.bottom == b.bottom && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) nodeByBounds(n.getChild(i), b, depth + 1)?.let { return it }
        return null
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))).build()
        val accepted = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!accepted && cont.isActive) cont.resume(false)
    }

    override suspend fun snapshot(): ScreenSnapshot? = withContext(Dispatchers.Default) {
        val root = rootInActiveWindow ?: return@withContext null
        val pkg = root.packageName?.toString() ?: "?"
        if (Guard.isMoneyApp(pkg)) { lastNodes = emptyList(); return@withContext ScreenSnapshot(pkg, emptyList()) }
        val nodes = ArrayList<UiNode>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || nodes.size >= 60 || depth > 30) return
            val label = (n.text ?: n.contentDescription ?: n.hintText)?.toString()?.trim().orEmpty()
            if (n.isVisibleToUser && (label.isNotEmpty() || n.isClickable || n.isEditable)) {
                val r = Rect(); n.getBoundsInScreen(r)
                nodes += UiNode(
                    nodes.size, n.className?.toString()?.substringAfterLast('.') ?: "View",
                    if (label.isEmpty()) "(no label)" else label.take(80),
                    n.isClickable, n.isEditable, n.isScrollable, r.left, r.top, r.right, r.bottom, n.isPassword,
                )
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        lastNodes = nodes
        val kb = runCatching { windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }.getOrDefault(false)
        ScreenSnapshot(pkg, nodes, kb, nodes.any { it.role.contains("Dialog", true) })
    }

    override suspend fun tapElement(id: Int): Boolean {
        val b = lastNodes.firstOrNull { it.id == id } ?: return false
        val node = nodeByBounds(rootInActiveWindow, b)
        clickableAncestor(node)?.let { if (it.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true }
        return tapAt(b.cx, b.cy)
    }

    override suspend fun tapText(text: String): Boolean {
        val n = find(text) ?: return false
        val r = Rect(); n.getBoundsInScreen(r)
        clickableAncestor(n)?.let { if (it.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true }
        return tapAt(r.centerX(), r.centerY())
    }

    override suspend fun tapAt(x: Int, y: Int): Boolean = dispatch(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 60)

    override suspend fun longPressAt(x: Int, y: Int): Boolean = dispatch(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 700)

    override suspend fun longPressElement(id: Int): Boolean {
        val b = lastNodes.firstOrNull { it.id == id } ?: return false
        return longPressAt(b.cx, b.cy)
    }

    override suspend fun longPressText(text: String): Boolean {
        val n = find(text) ?: return false
        val r = Rect(); n.getBoundsInScreen(r)
        return longPressAt(r.centerX(), r.centerY())
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        dispatch(Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }, durationMs)

    private fun firstEditable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 30) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) firstEditable(n.getChild(i), depth + 1)?.let { return it }
        return null
    }

    private fun inputField(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: firstEditable(root)
    }

    override suspend fun inputText(text: String, submit: Boolean): Boolean {
        val f = inputField() ?: return false
        if (f.isPassword) return false
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (!f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return false
        if (!submit) return true
        delay(250)
        if (Build.VERSION.SDK_INT >= 30 && f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
        return tapText("Search")
    }

    override suspend fun pressSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val labels = listOf("Send", "भेजें")
        for (l in labels) {
            val hit = root.findAccessibilityNodeInfosByText(l).firstOrNull { n ->
                n.isVisibleToUser && ((n.contentDescription ?: n.text)?.toString().equals(l, true))
            } ?: continue
            clickableAncestor(hit)?.let { if (it.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true }
        }
        return false
    }

    override suspend fun scroll(forward: Boolean): Boolean {
        fun findScrollable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
            if (n == null || depth > 30) return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) findScrollable(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        val s = findScrollable(rootInActiveWindow)
        if (s != null && s.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        // fallback: gesture swipe (no scrollable node exposed)
        val x = screenWidth / 2; val a = (screenHeight * 0.7).toInt(); val b = (screenHeight * 0.3).toInt()
        return if (forward) swipe(x, a, x, b, 350) else swipe(x, b, x, a, 350)
    }

    override fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    override fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    override suspend fun screenContains(text: String, waitMs: Long): Boolean {
        val end = System.currentTimeMillis() + waitMs
        do {
            if (rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true) return true
            delay(400)
        } while (System.currentTimeMillis() < end)
        return false
    }

    override suspend fun screenshot(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val hw = result.hardwareBuffer
                        val bmp = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()
                        if (bmp == null) { if (cont.isActive) cont.resume(null); return }
                        val dir = File(cacheDir, "screenshots").apply { mkdirs() }
                        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(4)?.forEach { it.delete() }
                        val f = File(dir, "shot_${System.currentTimeMillis()}.png")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        if (cont.isActive) cont.resume(f.absolutePath)
                    } catch (e: Exception) { if (cont.isActive) cont.resume(null) }
                }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        }
    }
}
