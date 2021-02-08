package com.champignoom.paperant

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.concurrent.thread

class KillableThreadTest {
    @Test
    fun testEmpty() {
//        assertEquals(0, 1)
        println("PATH = ${System.getProperty("user.dir")}")
        println("LD_LIBRARY_PATH = ${System.getenv("LD_LIBRARY_PATH")}")

        val t = object: KillableThread() {
            override fun slowAsync() {
                for (i in 1..10) {
                    System.err.println("slow ${i}")
                    Thread.sleep(200)
//                    t += i
                }
            }

            override fun fastSynced() {
                for (i in 1..10) {
                    System.err.println("fast ${i+100}")
                    Thread.sleep(200)
//                    t += i
                }
            }
        }
        t.run()
//        Thread.sleep(1000)
        Thread.sleep(3000)
        t.stop()
        t.join()
    }

    @Test
    fun testKotlinThread() {
        val t = thread {
            println("start")
            Thread.sleep(1000)
            println("stop")
        }
        t.join()
//        t.start()
    }
}