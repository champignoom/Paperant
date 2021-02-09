package com.champignoom.paperant

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.RuntimeException

abstract class KillableThread {
    companion object {
        private const val THREAD_SLOW_STARTED = 1
        private const val THREAD_SLOW_FINISHED = 2
        private const val THREAD_CANCELLED = -1

        init {
            System.loadLibrary("killable-thread")
            bindFieldAccessors()
            initSignalHandler()
        }

        private external fun bindFieldAccessors()
        private external fun initSignalHandler()
    }

    private var threadHandle: Long = 0;
    private var threadState: Int = 0;

    private var mainHandler = Handler(Looper.getMainLooper())

    private fun setStateIfNotCancelled(newState: Int): Int {
        synchronized(this) {
            val oldState = threadState
            if (threadState != THREAD_CANCELLED) {
                threadState = newState
            }
            return oldState
        }
    }

    abstract fun slowAsync();
    abstract fun fastSynced();

    private fun runBody() {
        if (setStateIfNotCancelled(THREAD_SLOW_STARTED) != THREAD_CANCELLED) {
            Log.d("Paperant", "thread ${"%x".format(threadHandle)} starts slow")
            slowAsync()  // only receives signal here
            Log.d("Paperant", "thread ${"%x".format(threadHandle)} finishes slow")
            setStateIfNotCancelled(THREAD_SLOW_FINISHED)  // always not cancelled here
            mainHandler.post {
                Log.d("Paperant", "thread ${"%x".format(threadHandle)} starts fast")
                if (threadState == THREAD_CANCELLED)
                    return@post
                fastSynced()
                Log.d("Paperant", "thread ${"%x".format(threadHandle)} finishes fast")
            }
        }
    }

    external fun start()
    external private fun sendSignal()
    external fun join()

    fun stop() {
        if (!Looper.getMainLooper().isCurrentThread)
            throw RuntimeException("not called from UI thread")

        synchronized(this) {
            val oldState = threadState
            threadState = THREAD_CANCELLED
            if (oldState == THREAD_SLOW_STARTED) {
                sendSignal()
            }
            // else:
            // THREAD_INIT: it will return upon checking THREAD_CANCELLED
            // THREAD_SLOW_FINISHED: not necessary and not safe to kill
            // THREAD_CANCELLED: not supposed to see this
        }
    }
}