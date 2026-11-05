package com.kishan.kavach.domain.screen

data class UiNode(
    val pkg: String,
    val viewId: String?,
    val cls: String,
    val text: String?,
    val desc: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val depth: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Int get() = width * height
    val cx: Int get() = (left + right) / 2
    val cy: Int get() = (top + bottom) / 2

    val label: String?
        get() {
            val t = text?.trim()?.ifEmpty { null }
            if (t != null) return t
            val d = desc?.trim()?.ifEmpty { null }
            if (d != null) return d
            val v = viewId?.trim()?.ifEmpty { null }
            if (v != null) return v.substringAfterLast('/')
            return null
        }
}
