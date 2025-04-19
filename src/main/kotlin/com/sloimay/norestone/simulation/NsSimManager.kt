package com.sloimay.norestone.simulation

import com.sk89q.worldedit.regions.CuboidRegion
import com.sloimay.norestone.BurstWorkThread
import com.sloimay.norestone.NOREStone
import kotlin.time.TimeSource

class NsSimManager(val noreStone: NOREStone) {

    private val simulations = hashSetOf<NsSim>()

    private val queueLock = Object()
    private val addingQueue = mutableListOf<NsSim>()
    private val removingQueue = mutableListOf<NsSim>()

    private val simMutQueueLock = Object()
    private val simMutQueue = mutableListOf<Pair<NsSim, (NsSim) -> Unit>>()

    // ==
    private val timingThread = BurstWorkThread()
    private val simThreads = hashMapOf<NsSim, BurstWorkThread>()

    private var isUpdating = false
    private var simsCanTick = false

    // At the end of an update cycle
    private var simsFinishedTickingLock = Object()
    private var simsFinishedTicking = hashMapOf<NsSim, Boolean>()
    // ==


    fun requestSimAdd(sim: NsSim) {
        synchronized(queueLock) { addingQueue.add(sim) }
    }
    fun requestSimRemove(sim: NsSim) {
        synchronized(queueLock) { removingQueue.add(sim) }
    }

    fun requestSimVarMutation(sim: NsSim, block: (NsSim) -> Unit) {
        synchronized(simMutQueueLock) { simMutQueue.add(sim to block) }
    }



    fun tryRender(): Boolean {
        if (isUpdating) return false

        for (sim in simulations) {
            if (!sim.shouldRender()) continue
            sim.render()
        }

        return true
    }



    /**
     * Called from the main thread (the ticking runnable) after the rendering
     * was done
     */
    fun tryUpdateCycle(allottedMillis: Long, serverTimeDelta: Double): Boolean {
        // Don't update if we're still updating (unlikely but if the sims are
        // beefy it can happen that it spills over one server tick)
        if (isUpdating) return false

        // add and remove sims as per requests
        flushAddRemoveQueue()

        // mutate the simulation variables
        synchronized(simMutQueueLock) {
            simMutQueue.forEach { (sim, block) ->
                if (sim in simulations) {
                    block(sim)
                }
            }
            simMutQueue.clear()
        }


        // We are now updating
        isUpdating = true

        // Set every sim's finished state to false
        for (sim in simulations) {
            simsFinishedTicking[sim] = false
        }

        // start the timing thread which will count down how many milliseconds we can
        // tick for
        val updateTimeSpanMillis = allottedMillis
        timingThread.work {
            // Make the sims tick for a certain amount of milliseconds
            simsCanTick = true
            val timeStart = TimeSource.Monotonic.markNow()
            do {
                val millisSinceUpdateStart = timeStart.elapsedNow().inWholeMilliseconds
            } while (millisSinceUpdateStart < updateTimeSpanMillis)
            simsCanTick = false
        }

        // start the sim ticking threads
        val updateCycleTimeOnServer = serverTimeDelta
        for (sim in simulations) {
            val ticksToRunFor = sim.getTicksToRunFor(updateCycleTimeOnServer)
            if (ticksToRunFor == 0L) {
                // Early mark as done ticking
                synchronized(simsFinishedTickingLock) {
                    simsFinishedTicking[sim] = true
                }
                continue
            }

            val simThread = simThreads[sim]!!
            var simTicksUpdated = 0
            simThread.work {
                sim.nodestoneSim.tickWhile {
                    (simTicksUpdated++ < ticksToRunFor) && simsCanTick
                }
                // Mark this sim as done ticking
                synchronized(simsFinishedTickingLock) {
                    simsFinishedTicking[sim] = true

                    // If all sims are done, this thread has the responsibility of setting isUpdating
                    // to false
                    if (simsFinishedTicking.all { (_, finishedTicking) -> finishedTicking }) {
                       isUpdating = false
                    }
                }
            }
        }

        // Successfully launched every update thread
        return true
    }




    private fun flushAddRemoveQueue() {
        synchronized(queueLock) {
            for (s in addingQueue) {
                addSim(s)
            }
            for (s in removingQueue) {
                removeSim(s)
            }
            addingQueue.clear()
            removingQueue.clear()
        }
    }

    private fun addSim(s: NsSim) {
        simulations.add(s)
        simThreads[s] = BurstWorkThread()
    }
    private fun removeSim(s: NsSim) {
        simulations.remove(s)
        simThreads.remove(s)
    }

}