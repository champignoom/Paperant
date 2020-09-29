package com.champignoom.paperant

import android.app.Activity
import android.util.Log
import android.widget.Toast
import java.util.concurrent.LinkedBlockingQueue

class Worker(private val activity: Activity) : Runnable {
    open class Task : Runnable {
        open fun work() {} /* The 'work' method will be executed on the background thread. */
        override fun run() {} /* The 'run' method will be executed on the UI thread. */
    }

    private val queue = LinkedBlockingQueue<Task>()
    private var alive = false

    fun start() {
        alive = true
        Thread(this).start()
    }

    fun stop() {
        alive = false
    }

    fun add(task: Task) {
        try {
            queue.put(task)
        } catch (x: InterruptedException) {
            Log.e("MuPDF Worker", x.message)
        }
    }

    override fun run() {
        while (alive) {
            try {
                val task = queue.take()
                task.work()
                activity.runOnUiThread(task)
            } catch (x: Throwable) {
                Log.e("MuPDF Worker", x.message)
                activity.runOnUiThread {
                    Toast.makeText(activity, x.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}