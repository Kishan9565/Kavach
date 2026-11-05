package com.kishan.kavach.domain.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GuardTest {

    @Test
    fun `strict words are blocked`() {
        assertTrue(Guard.checkAction("Send", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("pay", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("Place order", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("delete", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("bhejo", "com.any.app", "Home").blocked)
    }

    @Test
    fun `strict words with emojis and punctuation are blocked`() {
        assertTrue(Guard.checkAction("Send 🚀", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("Pay!", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("  place   order  ", "com.any.app", "Home").blocked)
    }

    @Test
    fun `partial matches of strict words are NOT blocked`() {
        assertFalse(Guard.checkAction("Sender profile", "com.any.app", "Home").blocked)
        assertFalse(Guard.checkAction("Paytm", "com.any.app", "Home").blocked) // Handled by context/package, not button label
        assertFalse(Guard.checkAction("Send message to mom", "com.any.app", "Home").blocked)
    }

    @Test
    fun `contextual words allowed on normal screens`() {
        assertFalse(Guard.checkAction("Continue", "com.settings.app", "Display").blocked)
        assertFalse(Guard.checkAction("Next", "com.reader.app", "Page 2").blocked)
        assertFalse(Guard.checkAction("Proceed", "com.form.app", "Survey").blocked)
    }

    @Test
    fun `contextual words blocked on checkout screens`() {
        assertTrue(Guard.checkAction("Continue", "com.shop.app", "Checkout").blocked)
        assertTrue(Guard.checkAction("Next", "com.food.app", "Payment Details").blocked)
        assertTrue(Guard.checkAction("Proceed", "com.store.app", "Your Cart").blocked)
    }

    @Test
    fun `contextual words blocked on money apps`() {
        assertTrue(Guard.checkAction("Continue", "com.google.android.apps.nbu.paisa.user", "Home").blocked)
        assertTrue(Guard.checkAction("Next", "com.phonepe.app", "Dashboard").blocked)
        assertTrue(Guard.checkAction("Proceed", "net.one97.paytm", "Wallet").blocked)
    }

    @Test
    fun `devanagari words blocked correctly`() {
        assertTrue(Guard.checkAction("भेज", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("ऑर्डर", "com.any.app", "Home").blocked)
        assertTrue(Guard.checkAction("पेमेंट", "com.any.app", "Home").blocked)
        
        // Contextual Hindi
        assertFalse(Guard.checkAction("आगे बढ़ें", "com.news.app", "Article").blocked)
        assertTrue(Guard.checkAction("आगे बढ़ें", "com.shop.app", "Checkout").blocked)
    }

    @Test
    fun `existing checkUserText blocks secrets and money requests`() {
        assertNotNull(Guard.checkUserText("mera otp hai 1234"))
        assertNotNull(Guard.checkUserText("password reset kar do"))
        assertNotNull(Guard.checkUserText("100 rupees pay karo"))
        assertNull(Guard.checkUserText("youtube kholo"))
    }
    
    @Test
    fun `isMoneyApp detects financial packages`() {
        assertTrue(Guard.isMoneyApp("com.google.android.apps.nbu.paisa.user")) // GPay
        assertTrue(Guard.isMoneyApp("com.phonepe.app"))
        assertTrue(Guard.isMoneyApp("net.one97.paytm"))
        assertTrue(Guard.isMoneyApp("com.sbi.upi"))
        
        assertFalse(Guard.isMoneyApp("com.whatsapp"))
        assertFalse(Guard.isMoneyApp("com.google.android.youtube"))
    }
}