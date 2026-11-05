package com.kishan.kavach.automation.tools

import android.util.Log
import com.kishan.kavach.automation.accessibility.A11yInput
import com.kishan.kavach.automation.accessibility.A11ySubmit
import com.kishan.kavach.automation.accessibility.ScreenBridge
import com.kishan.kavach.automation.accessibility.ScreenController
import com.kishan.kavach.automation.accessibility.ScreenReadResult
import com.kishan.kavach.automation.accessibility.ScreenReader
import com.kishan.kavach.automation.accessibility.SubmitResult
import com.kishan.kavach.domain.screen.EidRegistry
import com.kishan.kavach.domain.screen.Fingerprint
import com.kishan.kavach.domain.screen.Located
import com.kishan.kavach.domain.screen.OutlineBuilder
import com.kishan.kavach.domain.screen.Relocator
import com.kishan.kavach.domain.screen.RepeatGuard
import com.kishan.kavach.domain.screen.ScreenDiff
import com.kishan.kavach.domain.screen.TypePolicy
import com.kishan.kavach.domain.screen.TypeResult
import com.kishan.kavach.domain.screen.UiNode
import com.kishan.kavach.domain.screen.awaitSettled
import com.kishan.kavach.domain.tools.*
import com.kishan.kavach.services.accessibility.KavachAccess
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

private suspend fun currentFingerprint(c: ScreenController): Fingerprint {
    val accessService = KavachAccess.instance
    val readResult = ScreenReader.read(accessService)
    val nodes = when (readResult) {
        is ScreenReadResult.Success -> readResult.nodes
        else -> emptyList()
    }
    val pkg = c.snapshot()?.packageName.orEmpty()
    return ScreenDiff.compute(pkg, nodes)
}

private val SENSITIVE_FIELD_REGEX = Regex("""(?i)\b(otp|password|cvv|upi pin|pin|passcode)\b|ओटीपी|पासवर्ड|पिन""")

object ScreenTools {
    val eidRegistry = EidRegistry()
    val repeatGuard = RepeatGuard()

    fun all(): List<Tool> = listOf(tap, swipe, longPress, inputText, typeText, submitInput, pressBack, pressHome, scroll, getScreenInfo, readScreen, takeScreenshot, wait)

    val tap = SimpleTool("tap", "Tap a UI element by element_id (from SCREEN), by visible text, or by x,y", ToolGroup.SCREEN,
        listOf(
            ParamSpec("element_id", ParamType.INTEGER, "id number from SCREEN", required = false, min = 0.0, max = 500.0),
            ParamSpec("text", ParamType.STRING, "visible label", required = false, maxLength = 80),
            ParamSpec("x", ParamType.INTEGER, "x pixel", required = false, min = 0.0, max = 10000.0),
            ParamSpec("y", ParamType.INTEGER, "y pixel", required = false, min = 0.0, max = 10000.0),
            EXPECT),
        permissions = listOf(ACC), timeoutMs = 15_000) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val snapshot = c.snapshot() ?: return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "No readable window")
        if (Guard.isMoneyApp(snapshot.packageName)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app screen par main tap nahi karta.")
        val pkg = snapshot.packageName

        val accessService = KavachAccess.instance
        val beforeFp = currentFingerprint(c)

        if (a.has("element_id")) {
            val eid = a.int("element_id")!!
            val oldNode = eidRegistry.lookup(eid)
                ?: return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "No outline yet or invalid element_id (e$eid). Call read_screen first.", retryable = true)

            val liveNodes = when (val res = ScreenReader.read(accessService)) {
                is ScreenReadResult.Success -> res.nodes
                else -> emptyList()
            }

            val located = Relocator.relocate(oldNode, liveNodes)
            val resolvedNode: UiNode
            var movedWarning = ""

            when (located) {
                is Located.Found -> {
                    resolvedNode = located.node
                }
                is Located.Remembered -> {
                    resolvedNode = oldNode
                    movedWarning = " (Element moved or vanished; tapped remembered position)"
                }
            }

            // T-U1.4: Run SensitiveActionGuard on the RESOLVED live node label before every tap
            val targetLabel = resolvedNode.label ?: ""
            val verdict = Guard.checkAction(targetLabel, pkg, null)
            if (verdict.blocked) {
                return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, verdict.reason ?: "Action blocked by safety guard")
            }

            val ok = c.tapAt(resolvedNode.cx, resolvedNode.cy)
            if (!ok) return@SimpleTool ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "Could not tap element e$eid", retryable = true)
            
            val afterFp = awaitSettled(
                read = { kotlinx.coroutines.runBlocking { currentFingerprint(c) } },
                now = { System.currentTimeMillis() },
                pause = { delay(it) }
            )

            val changed = ScreenDiff.changed(beforeFp, afterFp)
            if (!changed) {
                return@SimpleTool ToolResult.Failed(
                    reason = "Tapped e$eid ($targetLabel)$movedWarning but screen did NOT change",
                    next = "Try another element, scroll, or ask the user"
                )
            }

            return@SimpleTool ToolResult.Ok(
                saw = "Screen changed, now in ${afterFp.pkg}",
                mapData = mapOf("action" to "Tapped e$eid ($targetLabel)")
            )
        }

        if (a.has("text")) {
            val textLabel = a.string("text")
            val verdict = Guard.checkAction(textLabel, pkg, null)
            if (verdict.blocked) {
                return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, verdict.reason ?: "Action blocked by safety guard")
            }
            c.highlight(textLabel)
            delay(350)
            val ok = c.tapText(textLabel)
            if (!ok) return@SimpleTool ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "Could not tap '$textLabel'", retryable = true)
            
            val afterFp = awaitSettled(
                read = { kotlinx.coroutines.runBlocking { currentFingerprint(c) } },
                now = { System.currentTimeMillis() },
                pause = { delay(it) }
            )

            val changed = ScreenDiff.changed(beforeFp, afterFp)
            if (!changed) {
                return@SimpleTool ToolResult.Failed(
                    reason = "Tapped '$textLabel' but screen did NOT change",
                    next = "Try another element, scroll, or ask the user"
                )
            }

            return@SimpleTool ToolResult.Ok(
                saw = "Screen changed, now in ${afterFp.pkg}",
                mapData = mapOf("action" to "Tapped '$textLabel'")
            )
        }

        if (a.has("x") && a.has("y")) {
            val x = a.int("x")!!
            val y = a.int("y")!!
            if (x > c.screenWidth || y > c.screenHeight) {
                return@SimpleTool ToolResult.fail(ErrorCode.INVALID_ARGS, "Coordinates outside screen (${c.screenWidth}x${c.screenHeight})")
            }

            // T-U2.3: Check RepeatGuard for coordinate taps
            try {
                repeatGuard.check(x, y)
            } catch (e: IllegalStateException) {
                return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, e.message ?: "Repeat tap refused")
            }

            val ok = c.tapAt(x, y)
            if (!ok) return@SimpleTool ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Tap at ($x,$y) failed")

            val afterFp = awaitSettled(
                read = { kotlinx.coroutines.runBlocking { currentFingerprint(c) } },
                now = { System.currentTimeMillis() },
                pause = { delay(it) }
            )

            val changed = ScreenDiff.changed(beforeFp, afterFp)
            if (!changed) {
                return@SimpleTool ToolResult.Failed(
                    reason = "Tapped ($x,$y) but screen did NOT change",
                    next = "Try another element, scroll, or ask the user"
                )
            }

            return@SimpleTool ToolResult.Ok(
                saw = "Screen changed, now in ${afterFp.pkg}",
                mapData = mapOf("action" to "Tapped ($x,$y)")
            )
        }

        ToolResult.fail(ErrorCode.INVALID_ARGS, "Give element_id, text, or x and y")
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

    val typeText = SimpleTool("type_text", "Type text like a keyboard into the focused input field", ToolGroup.SCREEN,
        listOf(ParamSpec("text", ParamType.STRING, "text to type", maxLength = 400), EXPECT),
        permissions = listOf(ACC), timeoutMs = 15_000) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (moneyScreen(c)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app screen par typing blocked.")
        
        val targetText = a.string("text")
        val accessService = KavachAccess.instance

        // H10 Safety Check: Check for password, OTP, CVV, or PIN
        val liveSnapshot = c.snapshot()
        val sensitiveNode = liveSnapshot?.nodes?.firstOrNull { node ->
            node.password || SENSITIVE_FIELD_REGEX.containsMatchIn(node.label)
        }
        if (sensitiveNode != null) {
            return@SimpleTool ToolResult.Denied(
                what = "Sensitive field detected",
                hint = "Cannot type into password, OTP, CVV, or PIN fields"
            )
        }

        // T-U3.4 Pipeline: A11yInput -> TypePolicy -> fallback
        val channelReadback = accessService?.let { A11yInput.replace(it, targetText) }
        when (val decision = TypePolicy.decide(channelReadback, targetText)) {
            is TypeResult.Typed -> {
                delay(300)
                return@SimpleTool verify(c, a.str("expect"), "Field now contains: '${decision.text}'")
            }
            is TypeResult.Mismatch -> {
                return@SimpleTool ToolResult.Failed(
                    reason = "Typed text mismatch: expected '$targetText', but field contains '${decision.actual}'",
                    next = "Check field or ask user"
                )
            }
            is TypeResult.Unavailable -> {
                // Fallback to ACTION_SET_TEXT
                if (!c.inputText(targetText, submit = false)) {
                    return@SimpleTool ToolResult.fail(ErrorCode.ELEMENT_NOT_FOUND, "No editable field found", retryable = true)
                }
                delay(300)
                return@SimpleTool verify(c, a.str("expect"), "Typed via fallback. Field now contains: '$targetText'")
            }
        }
    }

    val inputText = SimpleTool("input_text", "Type text into focused field (compatibility alias for type_text)", ToolGroup.SCREEN,
        listOf(ParamSpec("text", ParamType.STRING, "text to type", maxLength = 400), ParamSpec("submit", ParamType.BOOLEAN, "press enter after", required = false), EXPECT),
        permissions = listOf(ACC), timeoutMs = 15_000) { a ->
        typeText.execute(a)
    }

    val submitInput = SimpleTool("submit_input", "Submit the focused editor using its preferred IME action", ToolGroup.SCREEN,
        permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val accessService = KavachAccess.instance

        val submitResult = accessService?.let { A11ySubmit.performSubmit(it) } ?: SubmitResult.Unavailable
        when (submitResult) {
            is SubmitResult.Submitted -> {
                delay(400)
                return@SimpleTool ToolResult.Ok(saw = "Submitted editor action: ${submitResult.actionName}")
            }
            is SubmitResult.Skipped, is SubmitResult.Unavailable -> {
                // Fallback to ACTION_IME_ENTER
                if (c.inputText("", submit = true)) {
                    delay(400)
                    return@SimpleTool ToolResult.Ok(saw = "Submitted via fallback IME enter")
                }
                return@SimpleTool ToolResult.Failed(
                    reason = "Could not submit editor automatically",
                    next = "Tap the on-screen Send or Search button directly"
                )
            }
        }
    }

    val pressBack = SimpleTool("press_back", "Android Back", ToolGroup.SCREEN, listOf(EXPECT), permissions = listOf(ACC)) { _ ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val beforeFp = currentFingerprint(c)
        if (!c.back()) return@SimpleTool ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Back failed", retryable = true)
        
        val afterFp = awaitSettled(
            read = { kotlinx.coroutines.runBlocking { currentFingerprint(c) } },
            now = { System.currentTimeMillis() },
            pause = { delay(it) }
        )
        val changed = ScreenDiff.changed(beforeFp, afterFp)
        if (!changed) {
            return@SimpleTool ToolResult.Failed(reason = "Pressed back but screen did NOT change", next = "Ask the user to navigate or try home")
        }
        ToolResult.Ok(saw = "Went back, now in ${afterFp.pkg}")
    }

    val pressHome = SimpleTool("press_home", "Android Home", ToolGroup.SCREEN, permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        if (c.home()) ToolResult.ok("Home") else ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Home failed", retryable = true)
    }

    val scroll = SimpleTool("scroll", "Scroll the current screen", ToolGroup.SCREEN,
        listOf(ParamSpec("direction", ParamType.STRING, "down shows later content", enumValues = listOf("down", "up"))), permissions = listOf(ACC)) { a ->
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val beforeFp = currentFingerprint(c)
        val ok = c.scroll(a.string("direction") != "up")
        if (!ok) return@SimpleTool ToolResult.fail(ErrorCode.EXECUTION_FAILED, "Nothing to scroll", retryable = false)
        
        val afterFp = awaitSettled(
            read = { kotlinx.coroutines.runBlocking { currentFingerprint(c) } },
            now = { System.currentTimeMillis() },
            pause = { delay(it) }
        )
        val changed = ScreenDiff.changed(beforeFp, afterFp)
        if (!changed) {
            return@SimpleTool ToolResult.Failed(reason = "Scrolled ${a.string("direction")} but screen did NOT change", next = "Reached end of list or cannot scroll further")
        }
        ToolResult.Ok(saw = "Scrolled ${a.string("direction")}, screen changed")
    }

    val getScreenInfo = SimpleTool("get_screen_info", "Read the accessibility UI tree (element ids, roles, labels, actions)", ToolGroup.SCREEN, permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val s = c.snapshot() ?: return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "No readable window (secure screen or loading)", retryable = true)
        ToolResult.ok(s.toPromptText(60, 3000), mapOf("package" to s.packageName, "elements" to s.nodes.size, "keyboard" to s.keyboardVisible))
    }

    val readScreen = SimpleTool("read_screen", "Read screen outline with contiguous element IDs for tapping", ToolGroup.SCREEN, permissions = listOf(ACC)) {
        val c = ScreenBridge.controller ?: return@SimpleTool noScreen()
        val snapshot = c.snapshot() ?: return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "No readable window", retryable = true)
        if (Guard.isMoneyApp(snapshot.packageName)) return@SimpleTool ToolResult.fail(ErrorCode.BLOCKED, "Paisa/bank app hai, main ise nahi padhunga.")

        val accessService = KavachAccess.instance
        when (val readResult = ScreenReader.read(accessService)) {
            is ScreenReadResult.Withheld -> {
                val winText = if (readResult.availableWindows.isNotEmpty()) {
                    "Available windows: ${readResult.availableWindows.joinToString(", ")}"
                } else "No interactive windows available"
                return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "Window content withheld by system. $winText", retryable = true)
            }
            is ScreenReadResult.Success -> {
                if (readResult.isBlind) {
                    return@SimpleTool ToolResult.Failed(
                        reason = "This app hides its screen from accessibility",
                        next = "Ask the user to do this step or open it another way"
                    )
                }

                val outline = OutlineBuilder.build(readResult.nodes, c.screenWidth, c.screenHeight)
                eidRegistry.replace(outline.rows)

                // H15: Log character count of every read_screen
                Log.d("ScreenTools", "read_screen prompt character count: ${outline.text.length}")

                if (outline.text.isBlank()) {
                    return@SimpleTool ToolResult.fail(ErrorCode.SCREEN_UNAVAILABLE, "Screen par padhne layak content nahi mila", retryable = true)
                } else {
                    return@SimpleTool ToolResult.ok(outline.text, mapOf("package" to snapshot.packageName, "rows" to outline.rows.size))
                }
            }
        }
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
