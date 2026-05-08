package com.shower.voicectrl.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedAppTest {

    @Test
    fun `known package is supported`() {
        assertTrue(SupportedApp.isSupported("com.ss.android.ugc.aweme"))
    }

    @Test
    fun `unknown package is not supported`() {
        assertFalse(SupportedApp.isSupported("com.example.notes"))
    }

    @Test
    fun `enabled package must be supported and present in enabled set`() {
        val enabled = setOf("com.ss.android.ugc.aweme")

        assertTrue(SupportedApp.isEnabled("com.ss.android.ugc.aweme", enabled))
        assertFalse(SupportedApp.isEnabled("com.smile.gifmaker", enabled))
        assertFalse(SupportedApp.isEnabled("com.example.notes", enabled))
    }
}
