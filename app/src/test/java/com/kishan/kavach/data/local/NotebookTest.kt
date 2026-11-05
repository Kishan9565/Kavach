package com.kishan.kavach.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotebookTest {

    @Test
    fun `memory entity default values and properties`() {
        val memory = MemoryEntity(
            id = 10,
            kind = "promise",
            text = "Remind to call Maa",
            createdAt = 1000L,
            dueAt = 5000L,
            source = "user"
        )

        assertEquals(10L, memory.id)
        assertEquals("promise", memory.kind)
        assertEquals("Remind to call Maa", memory.text)
        assertEquals(1000L, memory.createdAt)
        assertEquals(5000L, memory.dueAt)
        assertEquals("user", memory.source)
    }

    @Test
    fun `memory entity defaults to fact and null dueAt`() {
        val memory = MemoryEntity(
            text = "User prefers Hinglish",
            createdAt = 1000L
        )

        assertEquals("fact", memory.kind)
        assertNull(memory.dueAt)
        assertEquals("user", memory.source)
    }
}
