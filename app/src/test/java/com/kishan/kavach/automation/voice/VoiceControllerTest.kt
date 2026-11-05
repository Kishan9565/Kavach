package com.kishan.kavach.automation.voice

import com.kishan.kavach.domain.tools.Guard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceControllerTest {

    @Test
    fun `voice command containing secrets is blocked by Guard`() {
        val blocked1 = Guard.checkUserText("mera otp hai 9876")
        assertNotNull(blocked1)

        val blocked2 = Guard.checkUserText("send 500 rupees to John")
        assertNotNull(blocked2)
    }

    @Test
    fun `safe voice command passes Guard`() {
        val blocked = Guard.checkUserText("open YouTube and search lofi music")
        assertNull(blocked)
    }
}
