package com.champignoom.paperant

abstract class KillableThread {
    companion object {
        private const val THREAD_SLOW_STARTED = 1
        private const val THREAD_SLOW_FINISHED = 2
        private const val THREAD_CANCELLED = -1

        init {
            System.loadLibrary("killable-thread")
            bindFieldAccessors()
        }

        private external fun bindFieldAccessors()
    }

    private var threadHandle: Long = 0;
    private var threadState: Int = 0;

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
            slowAsync()  // only receives signal here
            setStateIfNotCancelled(THREAD_SLOW_FINISHED)  // always not cancelled here
            fastSynced()
        }
    }

    external fun run()
    external private fun sendSignal()
    external fun join()

    fun stop() {
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