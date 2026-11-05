package com.kishan.kavach.domain.screen

data class OutlineRow(
    val eid: Int,
    val idStr: String,
    val node: UiNode,
    val formattedLine: String
)

data class Outline(
    val rows: List<OutlineRow>,
    val text: String,
    val screenW: Int,
    val screenH: Int
)

object OutlineBuilder {

    fun build(
        nodes: List<UiNode>,
        screenW: Int,
        screenH: Int,
        maxRows: Int = 60
    ): Outline {
        val safeW = screenW.coerceAtLeast(1)
        val safeH = screenH.coerceAtLeast(1)

        // 1. Filter nodes
        val validNodes = nodes.filter { node ->
            // Drop empty or off-screen bounds
            if (node.width <= 0 || node.height <= 0) return@filter false
            if (node.right <= 0 || node.bottom <= 0 || node.left >= safeW || node.top >= safeH) return@filter false

            // Keep if has label or is interactive
            node.label != null || node.clickable || node.editable || node.scrollable
        }

        // 2. Trim to maxRows keeping clickable & editable first, then largest visible ones
        val selectedNodes = if (validNodes.size <= maxRows) {
            validNodes
        } else {
            val (interactive, passive) = validNodes.partition { it.clickable || it.editable }
            val chosen = mutableListOf<UiNode>()

            if (interactive.size >= maxRows) {
                // Keep interactive nodes, prioritisng largest
                chosen.addAll(interactive.sortedByDescending { it.area }.take(maxRows))
            } else {
                chosen.addAll(interactive)
                val remainingSlots = maxRows - interactive.size
                chosen.addAll(passive.sortedByDescending { it.area }.take(remainingSlots))
            }

            // Restore original appearance order
            validNodes.filter { it in chosen }
        }

        // 3. Format lines & number contiguously e0..eN
        val rows = mutableListOf<OutlineRow>()
        val sb = StringBuilder()

        selectedNodes.forEachIndexed { index, node ->
            val eid = index
            val idStr = "e$eid"
            val star = if (node.clickable) "*" else ""
            val indent = "  ".repeat(node.depth.coerceIn(0, 4))
            val simpleClass = node.cls.substringAfterLast('.')
            
            val rawLabel = node.label ?: ""
            val cleanLabel = rawLabel.replace(Regex("""[\r\n\t]+"""), " ")
                .trim()
                .take(40)
            
            val editTag = if (node.editable) " [edit]" else ""

            val cxNorm = ((node.cx.toDouble() / safeW) * 1000).toInt().coerceIn(0, 1000)
            val cyNorm = ((node.cy.toDouble() / safeH) * 1000).toInt().coerceIn(0, 1000)

            val labelPart = if (cleanLabel.isNotEmpty()) " $cleanLabel" else ""
            val line = "$idStr$star $indent$simpleClass$labelPart$editTag ($cxNorm,$cyNorm)"

            val row = OutlineRow(eid, idStr, node, line)
            rows.add(row)
            sb.append(line).append("\n")
        }

        return Outline(
            rows = rows,
            text = sb.toString().trimEnd(),
            screenW = safeW,
            screenH = safeH
        )
    }
}
