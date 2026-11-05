package com.kishan.kavach.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypePolicyTest {

    @Test
    fun `channel null returns Unavailable`() {
        val result = TypePolicy.decide(null, "Hello World")
        assertEquals(TypeResult.Unavailable, result)
    }

    @Test
    fun `matching readback returns Typed`() {
        val result = TypePolicy.decide("Hello World", "Hello World")
        assertTrue(result is TypeResult.Typed)
        assertEquals("Hello World", (result as TypeResult.Typed).text)
    }

    @Test
    fun `different readback returns Mismatch`() {
        val result = TypePolicy.decide("Hello", "Hello World")
        assertTrue(result is TypeResult.Mismatch)
        val mismatch = result as TypeResult.Mismatch
        assertEquals("Hello World", mismatch.expected)
        assertEquals("Hello", mismatch.actual)
    }
}
