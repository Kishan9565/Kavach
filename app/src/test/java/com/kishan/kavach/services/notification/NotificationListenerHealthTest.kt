package com.kishan.kavach.services.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationListenerHealthTest {

    @Test
    fun `connected state tracks listener connection`() {
        NotificationListenerHealth.connected = false
        assertFalse(NotificationListenerHealth.connected)

        NotificationListenerHealth.connected = true
        assertTrue(NotificationListenerHealth.connected)
    }
}
