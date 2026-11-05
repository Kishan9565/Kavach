package com.kishan.kavach.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kishan.kavach.domain.screen.UiNode

sealed interface ScreenReadResult {
    data class Success(val nodes: List<UiNode>, val isBlind: Boolean) : ScreenReadResult
    data class Withheld(val availableWindows: List<String>) : ScreenReadResult
}

object ScreenReader {

    fun read(
        service: AccessibilityService?,
        timeBudgetMs: Long = 400L,
        maxNodes: Int = 800
    ): ScreenReadResult {
        if (service == null) return ScreenReadResult.Success(emptyList(), isBlind = true)

        var root = runCatching { service.rootInActiveWindow }.getOrNull()

        // Check if root is withheld (no children and empty bounds)
        if (root == null || isWithheld(root)) {
            val bestWindowRoot = findBestWindowRoot(service)
            if (bestWindowRoot != null) {
                root = bestWindowRoot
            } else {
                val windowTitles = runCatching { service.windows }
                    .getOrDefault(emptyList())
                    .mapNotNull { w -> w.title?.toString()?.ifBlank { null } ?: "Window(${w.type})" }
                return ScreenReadResult.Withheld(windowTitles)
            }
        }

        val pkg = root.packageName?.toString().orEmpty()
        val result = mutableListOf<UiNode>()
        val startTime = SystemClock.uptimeMillis()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || result.size >= maxNodes) return
            if (SystemClock.uptimeMillis() - startTime > timeBudgetMs) return

            runCatching {
                val text = node.text?.toString()
                val desc = node.contentDescription?.toString()
                val viewId = node.viewIdResourceName
                val cls = node.className?.toString().orEmpty()

                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                val uiNode = UiNode(
                    pkg = pkg,
                    viewId = viewId,
                    cls = cls,
                    text = text,
                    desc = desc,
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    depth = depth
                )

                result.add(uiNode)

                for (i in 0 until node.childCount) {
                    if (result.size >= maxNodes || SystemClock.uptimeMillis() - startTime > timeBudgetMs) break
                    traverse(node.getChild(i), depth + 1)
                }
            }
        }

        traverse(root, 0)

        // Check if screen is blind (5 nodes or fewer, or zero nodes with any label)
        val hasLabel = result.any { !it.label.isNull_or_blank() }
        val isBlind = result.size <= 5 || !hasLabel

        return ScreenReadResult.Success(result, isBlind)
    }

    private fun isWithheld(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return node.childCount == 0 && (bounds.isEmpty || (bounds.left == 0 && bounds.top == 0 && bounds.right == 0 && bounds.bottom == 0))
    }

    private fun findBestWindowRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows }.getOrNull() ?: return null
        var maxLabelCount = 0
        var bestRoot: AccessibilityNodeInfo? = null

        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            val labelCount = countLabels(root)
            if (labelCount > maxLabelCount) {
                maxLabelCount = labelCount
                bestRoot = root
            }
        }

        return bestRoot
    }

    private fun countLabels(node: AccessibilityNodeInfo?, depth: Int = 0): Int {
        if (node == null || depth > 10) return 0
        var count = if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) 1 else 0
        for (i in 0 until node.childCount) {
            count += countLabels(node.getChild(i), depth + 1)
        }
        return count
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
