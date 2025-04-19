package com.sloimay.norestone.simulation

import com.sloimay.norestone.BurstWorkThread
import com.sloimay.norestone.NOREStone
import org.bukkit.entity.Player
import java.util.*
import kotlin.time.TimeSource


/**
 * TODO: Make the sim removal and adding of player sessions done through the sim manager too (or maybe remove the
 *       sim pointer from the NOREStone object altogether? idk i likey it being there too
 */
class NsSimManager(val noreStone: NOREStone) {

    private val simulations = hashSetOf<NsSim>()
    private val uuidToSimulations = hashMapOf<UUID, NsSim>()

    private val queueLock = Object()
    private val addingQueue = mutableListOf<Pair<UUID, NsSim>>()
    private val removingQueue = mutableListOf<Pair<UUID, NsSim>>()

    private val simMutQueueLock = Object()
    private val simMutQueue = mutableListOf<Pair<NsSim, (NsSim) -> Unit>>()

    // == Update cycle variables
    private val timingThread = BurstWorkThread()
    private val simThreads = hashMapOf<NsSim, BurstWorkThread>()

    private var isUpdating = false
    private var simsCanTick = false

    // At the end of an update cycle
    private var simsFinishedTickingLock = Object()
    private var simsFinishedTicking = hashMapOf<NsSim, Boolean>()
    // ==


    fun requestSimAdd(playerUuid: UUID, sim: NsSim) {
        synchronized(queueLock) { addingQueue.add(playerUuid to sim) }
    }
    fun requestSimRemove(playerUuid: UUID, sim: NsSim) {
        synchronized(queueLock) { removingQueue.add(playerUuid to sim) }
    }

    fun requestSimVarMutation(sim: NsSim, block: (NsSim) -> Unit) {
        synchronized(simMutQueueLock) { simMutQueue.add(sim to block) }
    }


    fun playerUuidSimExists(playerUuid: UUID) = playerUuid in uuidToSimulations.keys
    fun getPlayerSim(playerUuid: UUID) = uuidToSimulations[playerUuid]



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
            for ((uuid, s) in addingQueue) {
                if (s !in simulations) addSim(uuid, s)
            }
            for ((uuid, s) in removingQueue) {
                if (s in simulations) removeSim(uuid, s)
            }
            addingQueue.clear()
            removingQueue.clear()
        }
    }

    private fun addSim(uuid: UUID, s: NsSim) {
        simulations.add(s)
        uuidToSimulations[uuid] = s
        simThreads[s] = BurstWorkThread()
    }
    private fun removeSim(uuid: UUID, s: NsSim) {
        s.endingSequence()
        simulations.remove(s)
        uuidToSimulations.remove(uuid)
        simThreads.remove(s)
    }

}