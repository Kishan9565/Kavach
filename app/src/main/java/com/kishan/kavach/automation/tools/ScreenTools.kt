package com.kishan.kavach.automation.tools

import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.automation.accessibility.ScreenController
import com.kishan.kavach.domain.tools.*
import kotlinx.coroutines.delay

private const val ACC = "accessibility"
private val EXPECT = ParamSpec("expect", ParamType.STRING, "Text that must appear on screen afterwards (verification)", required = false, maxLength = 80)

private fun noScreen() = ToolResult.fail(ErrorCode.PERMISSION_REQUIRED, "Accessibility service is off. Enable it in KAVACH > Setup.", permission = ACC)

/** Runs the post-condition: if `expect` is set the screen must show it, otherwise the action is reported as failed. */
private suspend fun verify(c: ScreenController, expect: String?, okMsg: String): ToolResult {
    if (expect.isNullOrBlank()) return ToolResult.ok(okMsg)
    return if (c.screenContains(expect, 3500)) ToolResult.ok("$okMsg ('$expect' visible)")
    else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Action done but '$expect' is not on screen", retryable = true)
}

private suspend fun moneyScreen(c: ScreenController): Boolean = c.snapshot()?.let { Guard.isMoneyApp(it.packageName) } == true

object ScreenTools {
    fun all(): List<Tool> = listOf(tap, swipe, longPress, inputText, pressBack, pressHome, scroll, getScreenInfo, readScreen, takeScreenshot, wait)

    val tap = SimpleTool("tap", "Tap a UI element by element_id (from SCREEN), by visible text, or by x,y", ToolGroup.SCREEN,
        listOf(
            ParamSpec("element_id", ParamType.INTEGER, "id number from SCREEN", required = false, min = 0.0, max = 500.0),
            ParamSpec("text", ParamType.STRING, "visible label", required = false, maxLength = 80),
            ParamSpec("x", ParamType.INTEGER, "x pixel", required = false, min = 0.0, max = 10000.0),
            ParamSpec("y", ParamType.INTEGER, "y pixel", required = false, min = 0.0, max = 10000.0),
            EXPECT),
        permissions = listOf(ACC), timeoutMs = 15_000) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (moneyScreen(c)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app screen par main tap nahi karta.")
        val ok = when {
            a.has("element_id") -> c.tapElement(a.int("element_id")!!)
            a.has("text") -> { c.highlight(a.string("text")); delay(350); c.tapText(a.string("text")) }
            a.has("x") && a.has("y") -> {
                if (a.int("x")!! > c.screenWidth || a.int("y")!! > c.screenHeight) return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Coordinates outside the screen (${c.screenWidth}x${c.screenHeight})")
                c.tapAt(a.int("x")!!, a.int("y")!!)
            }
            else -> return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Give element_id, text, or x and y")
        }
        if (!ok) ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "Could not tap that target (not visible?)", retryable = true)
        else { delay(400); verify(c, a.str("expect"), "Tapped") }
    }

    val swipe = SimpleTool("swipe", "Swipe from (x1,y1) to (x2,y2)", ToolGroup.SCREEN,
        listOf(ParamSpec("x1", ParamType.INTEGER, "start x", min = 0.0, max = 10000.0), ParamSpec("y1", ParamType.INTEGER, "start y", min = 0.0, max = 10000.0),
            ParamSpec("x2", ParamType.INTEGER, "end x", min = 0.0, max = 10000.0), ParamSpec("y2", ParamType.INTEGER, "end y", min = 0.0, max = 10000.0),
            ParamSpec("duration_ms", ParamType.INTEGER, "default 350", required = false, min = 50.0, max = 3000.0)),
        permissions = listOf(ACC)) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (moneyScreen(c)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app screen par gestures blocked.")
        val ok = c.swipe(a.int("x1")!!, a.int("y1")!!, a.int("x2")!!, a.int("y2")!!, (a.long("duration_ms") ?: 350L))
        if (ok) ToolResult.ok("Swiped") else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Swipe was not performed", retryable = true)
    }

    val longPress = SimpleTool("long_press", "Long press an element by element_id or visible text", ToolGroup.SCREEN,
        listOf(ParamSpec("element_id", ParamType.INTEGER, "id from SCREEN", required = false, min = 0.0, max = 500.0),
            ParamSpec("text", ParamType.STRING, "visible label", required = false, maxLength = 80)),
        permissions = listOf(ACC)) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (moneyScreen(c)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app screen par gestures blocked.")
        val ok = when {
            a.has("element_id") -> c.longPressElement(a.int("element_id")!!)
            a.has("text") -> c.longPressText(a.string("text"))
            else -> return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Give element_id or text")
        }
        if (ok) ToolResult.ok("Long pressed") else ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "Target not found", retryable = true)
    }

    val inputText = SimpleTool("input_text", "Type text into the focused (or first) input field; submit=true presses Enter/Search", ToolGroup.SCREEN,
        listOf(ParamSpec("text", ParamType.STRING, "text to type", maxLength = 400), ParamSpec("submit", ParamType.BOOLEAN, "press enter after", required = false), EXPECT),
        permissions = listOf(ACC), timeoutMs = 15_000) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (moneyScreen(c)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app screen par typing blocked.")
        if (!c.inputText(a.string("text"), a.bool("submit"))) ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "No editable field found (or it is a password field)", retryable = true)
        else { delay(300); verify(c, a.str("expect"), "Typed") }
    }

    val pressBack = SimpleTool("press_back", "Android Back", ToolGroup.SCREEN, listOf(EXPECT), permissions = listOf(ACC)) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (!c.back()) ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Back failed", retryable = true) else { delay(300); verify(c, a.str("expect"), "Went back") }
    }

    val pressHome = SimpleTool("press_home", "Android Home", ToolGroup.SCREEN, permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (c.home()) ToolResult.ok("Home") else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Home failed", retryable = true)
    }

    val scroll = SimpleTool("scroll", "Scroll the current screen", ToolGroup.SCREEN,
        listOf(ParamSpec("direction", ParamType.STRING, "down shows later content", enumValues = listOf("down", "up"))), permissions = listOf(ACC)) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (c.scroll(a.string("direction") != "up")) ToolResult.ok("Scrolled ${a.string("direction")}") else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Nothing to scroll", retryable = false)
    }

    val getScreenInfo = SimpleTool("get_screen_info", "Read the accessibility UI tree (element ids, roles, labels, actions)", ToolGroup.SCREEN, permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val s = c.snapshot() ?: return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "No readable window (secure screen or loading)", retryable = true)
        ToolResult.ok(s.toPromptText(60, 3000), mapOf("package" to s.packageName, "elements" to s.nodes.size, "keyboard" to s.keyboardVisible))
    }

    val readScreen = SimpleTool("read_screen", "Read all visible text on screen as plain text", ToolGroup.SCREEN, permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val s = c.snapshot() ?: return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "No readable window", retryable = true)
        if (Guard.isMoneyApp(s.packageName)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app hai, main ise nahi padhunga.")
        val text = s.nodes.filter { !it.password && it.label != "(no label)" }.joinToString("\n") { it.label }.take(2500)
        if (text.isBlank()) ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "Screen par padhne layak text nahi mila", retryable = true) else ToolResult.ok(text, mapOf("package" to s.packageName))
    }

    val takeScreenshot = SimpleTool("take_screenshot", "Save a screenshot (Android 11+ only); returns the file path", ToolGroup.SCREEN, permissions = listOf(ACC), timeoutMs = 10_000) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (moneyScreen(c)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app ka screenshot blocked.")
        val p = c.screenshot() ?: return@SimpleTool ToolResult.fail(ErrorCode.NOT_AVAILABLE, "Screenshot not available (needs Android 11+ and the screenshot capability)")
        ToolResult.ok("Screenshot saved", mapOf("path" to p))
    }

    val wait = SimpleTool("wait", "Wait for the UI to settle", ToolGroup.SCREEN,
        listOf(ParamSpec("ms", ParamType.INTEGER, "100-3000", min = 100.0, max = 3000.0))) { a -> delay(a.long("ms")!!); ToolResult.ok("Waited") }
}
