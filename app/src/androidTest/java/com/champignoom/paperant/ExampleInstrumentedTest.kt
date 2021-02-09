package com.champignoom.paperant

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.champignoom.paperant", appContext.packageName)

        val t = object: KillableThread() {
            override fun slowAsync() {
                for (i in 1..10) {
                    Log.d("TestPaperant", "loop ${i}")
                    Thread.sleep(400)
                }
            }
            override fun fastSynced() {

            }
        }

        t.start()
        Thread.sleep(1900)
        t.stop()
        t.join()
    }
}