package com.kishan.kavach.domain.screen

class EidRegistry {
    @Volatile
    private var currentRows: Map<Int, UiNode> = emptyMap()

    fun replace(rows: List<OutlineRow>) {
        currentRows = rows.associate { it.eid to it.node }
    }

    fun lookup(eid: Int): UiNode? {
        return currentRows[eid]
    }

    fun isEmpty(): Boolean = currentRows.isEmpty()
}
