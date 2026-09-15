package com.example.orbit

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/** Instrumentovani test, izvrsava se na Android uredjaju */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context aplikacije koja se testira
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.orbit", appContext.packageName)
    }
}