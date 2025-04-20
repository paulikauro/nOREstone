package com.sloimay.norestone.simulation

import com.sloimay.norestone.BurstWorkThread
import com.sloimay.norestone.NOREStone
import java.util.*
import kotlin.time.TimeSource




data class TpsAnalysis(val oneSecond: Double, val tenSeconds: Double, val oneMinute: Double)



class NsSimManager(
    private val noreStone: NOREStone,
    val updateHz: Int,
) {
    val tpsMonitoringMaxSampleCount = 60

    private val simHashLock = Object()
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

    /**
     * Do not mutate sims through this method, do it synchronously using requestSimVarMutation
     */
    fun applyOnSimsReadOnly(block: (NsSim) -> Unit) {
        synchronized(simHashLock) {
            for (sim in simulations) {
                block(sim)
            }
        }
    }


    fun playerUuidSimExists(playerUuid: UUID) = playerUuid in uuidToSimulations.keys
    fun getPlayerSim(playerUuid: UUID) = uuidToSimulations[playerUuid]

    fun getSimTpsAnalysis(sim: NsSim): TpsAnalysis {
        return synchronized(sim.tpsTrackingLock) {
            TpsAnalysis(
                sim.tpsTracker.getWindowAvg(1),
                sim.tpsTracker.getWindowAvg(10),
                sim.tpsTracker.getWindowAvg(60),
            )
        }
    }



    fun tryRender(): Boolean {
        if (isUpdating) return false
        if (simulations.size == 0) return false

        for (sim in simulations) {
            if (!sim.shouldRender()) continue
            //println("============== rendering sim")
            sim.render()
        }

        return true
    }



    /**
     * Called from the main thread (the ticking runnable) after the rendering
     * was done
     */
    fun tryUpdateCycle(allottedMillis: Long): Boolean {
        // Don't update if we're still updating (unlikely but if the sims are
        // beefy it can happen that it spills over one server tick)
        if (isUpdating) return false

        // add and remove sims as per requests
        flushAddRemoveQueue()
        if (simulations.size == 0) return false

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
        simsCanTick = true
        timingThread.work {
            // Make the sims tick for a certain amount of milliseconds
            val timeStart = TimeSource.Monotonic.markNow()
            do {
                val millisSinceUpdateStart = timeStart.elapsedNow().inWholeMilliseconds
            } while (millisSinceUpdateStart < updateTimeSpanMillis)
            simsCanTick = false
        }

        // start the sim ticking threads
        val serverTimeDelta = 1.0 / updateHz.toDouble()
        val updateCycleTimeOnServer = serverTimeDelta
        for (sim in simulations) {

            fun simTickEndSequence(simTicksUpdated: Long) {
                // May call this function from the main thread or a worker thread,
                // however it's thread safe so we g

                // Mark this sim as done ticking
                synchronized(simsFinishedTickingLock) {
                    simsFinishedTicking[sim] = true

                    // If all sims are done, this thread has the responsibility of setting isUpdating
                    // to false
                    if ( simsFinishedTicking.all {(_, finishedTicking) -> finishedTicking} ) {
                        isUpdating = false
                    }
                }
                // Notify this sim for how long it ran for
                sim.notifyRanFor(simTicksUpdated)

                // Do tps monitoring
                synchronized(sim.tpsTrackingLock) {
                    sim.updateCycleCountLifetime += 1
                    val idxInBuf = (sim.updateCycleCountLifetime % updateHz.toLong()).toInt()
                    sim.sampleBuf[idxInBuf] = simTicksUpdated
                    if (idxInBuf == 0) {
                        val ticksLastSecond = sim.sampleBuf.sumOf { it.toDouble() }
                        sim.tpsTracker.addSample(ticksLastSecond)
                    }
                }
            }

            val ticksToRunFor = sim.getTicksToRunFor(updateCycleTimeOnServer)
            if (ticksToRunFor == 0L) {
                simTickEndSequence(0)
                continue
            }

            val simThread = simThreads[sim]!!
            var simTicksUpdated = 0L
            simThread.work {
                sim.nodestoneSim.tickWhile {
                    //println("TRY TICK: SIMS CAN TICK: $simsCanTick")
                    if (simTicksUpdated < ticksToRunFor && simsCanTick) {
                        //println("TICKING ONCE!!")
                        simTicksUpdated += 1
                        return@tickWhile true
                    } else {
                        return@tickWhile false
                    }
                }
                // We done ticking
                simTickEndSequence(simTicksUpdated)
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
        //println("added sim for player ${Bukkit.getPlayer(uuid)!!}")
        synchronized(simHashLock) {
            simulations.add(s)
            uuidToSimulations[uuid] = s
            simThreads[s] = BurstWorkThread()
        }
    }
    private fun removeSim(uuid: UUID, s: NsSim) {
        //println("removed sim for player ${Bukkit.getPlayer(uuid)!!}")

        synchronized(simHashLock) {
            s.endingSequence()
            simulations.remove(s)
            uuidToSimulations.remove(uuid)
            simThreads.remove(s)
        }
    }

}