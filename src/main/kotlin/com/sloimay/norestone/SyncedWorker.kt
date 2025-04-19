package com.sloimay.norestone

/**
 * Every server tick, this worker flushes its work queue
 */
class SyncedWorker {
    private val lock = Object()

    private val workQueue = mutableListOf<() -> Unit>()

    fun addWork(work: () -> Unit) {
        synchronized(lock) {
            workQueue.add(work)
        }
    }

    fun flushWork() {
        synchronized(lock) {
            workQueue.forEach {
                it()
            }
            workQueue.clear()
        }
    }
}