package com.kishan.kavach.domain.screen

import kotlin.math.hypot

sealed interface Located {
    data class Found(val node: UiNode) : Located
    data class Remembered(val cx: Int, val cy: Int) : Located
}

object Relocator {

    fun relocate(old: UiNode, live: List<UiNode>): Located {
        val samePkg = live.filter { it.pkg == old.pkg }
        if (samePkg.isEmpty()) {
            return Located.Remembered(old.cx, old.cy)
        }

        val oldLabel = old.label
        val oldViewId = old.viewId

        // Match Strategy 1: Same pkg + same viewId + same label (nearest to old centre)
        if (!oldViewId.isNull_or_blank() && !oldLabel.isNull_or_blank()) {
            val matches = samePkg.filter {
                it.viewId == oldViewId && it.label == oldLabel
            }
            if (matches.isNotEmpty()) {
                return Located.Found(nearest(old, matches))
            }
        }

        // Match Strategy 2: Same pkg + identical bounds
        val exactBoundsMatch = samePkg.firstOrNull {
            it.left == old.left && it.top == old.top &&
                    it.right == old.right && it.bottom == old.bottom
        }
        if (exactBoundsMatch != null) {
            return Located.Found(exactBoundsMatch)
        }

        // Match Strategy 3: Same pkg + same class + same label (nearest)
        if (!oldLabel.isNull_or_blank()) {
            val matches = samePkg.filter {
                it.cls == old.cls && it.label == oldLabel
            }
            if (matches.isNotEmpty()) {
                return Located.Found(nearest(old, matches))
            }
        }

        // Match Strategy 4: Nothing found - return remembered old coordinates
        return Located.Remembered(old.cx, old.cy)
    }

    private fun nearest(target: UiNode, candidates: List<UiNode>): UiNode {
        return candidates.minByOrNull { distance(target.cx, target.cy, it.cx, it.cy) }!!
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double {
        return hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
