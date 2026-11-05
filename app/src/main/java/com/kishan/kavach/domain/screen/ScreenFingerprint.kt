package com.kishan.kavach.domain.screen

import kotlin.math.abs

data class Fingerprint(
    val pkg: String,
    val nodeCount: Int,
    val labelHash: Int
)

object ScreenDiff {

    private val clockPattern = Regex("""^\d{1,2}:\d{2}(:\d{2})?(\s?[APap][Mm])?$""")

    fun changed(a: Fingerprint, b: Fingerprint): Boolean {
        if (a.pkg != b.pkg) return true
        if (a.labelHash != b.labelHash) return true
        if (abs(a.nodeCount - b.nodeCount) >= 3) return true
        return false
    }

    fun compute(pkg: String, nodes: List<UiNode>): Fingerprint {
        val cleanLabels = nodes.mapNotNull { it.label }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !clockPattern.matches(it) }
            .sorted()

        val labelHash = cleanLabels.hashCode()
        return Fingerprint(
            pkg = pkg,
            nodeCount = nodes.size,
            labelHash = labelHash
        )
    }
}
