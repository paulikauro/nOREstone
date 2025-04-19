package com.sloimay.norestone.simulation

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.SideEffectSet
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.nodestonecore.backends.RedstoneSimBackend
import com.sloimay.norestone.NOREStone
import com.sloimay.norestone.selection.SimSelection
import com.sloimay.norestone.toBlockVector3
import com.sloimay.smath.floor
import com.sloimay.smath.vectors.IVec3


private typealias WeBlockState = com.sk89q.worldedit.world.block.BlockState


/**
 * After the initialisation, all var changes must be requested through the manager
 */
class NsSim(
    private val noreStone: NOREStone,

    val sel: SimSelection,
    val nodestoneSim: RedstoneSimBackend,
    val simWorldOrigin: IVec3,

    tps: Int,
) {
    var tps: Int = tps
        private set

    private var state: SimState

    private val weBlockStateCache = hashMapOf<BlockState, WeBlockState>()



    init {
        require(sel.isComplete()) { "Nodestone simulation selection has to be complete." }

        state = SimState.Running(this)
    }


    fun requestTpsChange(newTickRate: Int) {
        noreStone.simManager.requestSimVarMutation(this) { it.tps = newTickRate }
    }






    fun endingSequence() {
        //
    }


    // == Render methods

    // Main render method
    fun shouldRender(): Boolean {
        return state.shouldRender()
    }

    fun render() {
        val weWorld = BukkitAdapter.adapt(sel.world!!)
        nodestoneSim.updateRepr { localPos, newBlockState ->
            val worldPos = local2World(localPos)
            val weBlockState = weBlockStateCache.computeIfAbsent(newBlockState) {
                BukkitAdapter.adapt( noreStone.server.createBlockData(it.stateStr) )
            }
            weWorld.setBlock(worldPos.toBlockVector3(), weBlockState, SideEffectSet.none())
        }
    }

    private fun local2World(local: IVec3) = local + simWorldOrigin

    // ==


    // == Update cycle methods

    fun getTicksToRunFor(upcomingDeltaSeconds: Double): Long {
        return state.getTicksToRunFor(upcomingDeltaSeconds)
    }

    fun notifyRanFor(ticks: Long) {
        state.notifyRanFor(ticks)
    }

    // ==




    private abstract class SimState(val sim: NsSim) {

        class Frozen(sim: NsSim) : SimState(sim) {
            var isStepping = false

            var simTime = 0.0 // one unit is one tick

            var idealTickCountThisUpdate = 0L
            var timeToAddIfIdealTickCountIsReached = 0.0

            var lastUpdateCycleTickCount = 0L

            // Called at the start of an update cycle
            override fun getTicksToRunFor(upcomingDeltaSeconds: Double): Long {
                if (!isStepping) return 0

                val deltaTicks = serverSecondsToSimTicks(upcomingDeltaSeconds)
                val deltaTimestamp = simTime + deltaTicks

                val ticksToRun = (deltaTimestamp.floor()).toLong() - (simTime.floor()).toLong()
                idealTickCountThisUpdate = ticksToRun
                timeToAddIfIdealTickCountIsReached = deltaTicks
                return ticksToRun
            }

            // Called at the end of an update cycle
            override fun notifyRanFor(ticks: Long) {
                if (!isStepping) return

                if (ticks == idealTickCountThisUpdate) {
                    simTime += timeToAddIfIdealTickCountIsReached
                } else {
                    simTime += ticks.toDouble()
                }

                lastUpdateCycleTickCount = ticks
            }

            private var hasAlreadyCalledShouldRender = false
            override fun shouldRender(): Boolean {
                if (!hasAlreadyCalledShouldRender) {
                    // First call will always lead to a render
                    hasAlreadyCalledShouldRender = true
                    return true
                }

                return lastUpdateCycleTickCount > 0
            }
        }

        class Running(sim: NsSim) : SimState(sim) {
            var simTime = 0.0 // one unit is one tick

            var idealTickCountThisUpdate = 0L
            var timeToAddIfIdealTickCountIsReached = 0.0

            var lastUpdateCycleTickCount = 0L

            // Called at the start of an update cycle
            override fun getTicksToRunFor(upcomingDeltaSeconds: Double): Long {
                val deltaTicks = serverSecondsToSimTicks(upcomingDeltaSeconds)
                val deltaTimestamp = simTime + deltaTicks

                val ticksToRun = (deltaTimestamp.floor()).toLong() - (simTime.floor()).toLong()
                idealTickCountThisUpdate = ticksToRun
                timeToAddIfIdealTickCountIsReached = deltaTicks
                return ticksToRun
            }

            // Called at the end of an update cycle
            override fun notifyRanFor(ticks: Long) {
                if (ticks == idealTickCountThisUpdate) {
                    simTime += timeToAddIfIdealTickCountIsReached
                } else {
                    simTime += ticks.toDouble()
                }

                lastUpdateCycleTickCount = ticks
            }

            private var hasAlreadyCalledShouldRender = false
            override fun shouldRender(): Boolean {
                if (!hasAlreadyCalledShouldRender) {
                    // First call will always lead to a render
                    hasAlreadyCalledShouldRender = true
                    return true
                }

                return lastUpdateCycleTickCount > 0
            }
        }

        abstract fun shouldRender(): Boolean

        /**
         * At the start of a server tick (where we run an update cycle), we ask the
         * simulations how many ticks they'd like to run for
         */
        abstract fun getTicksToRunFor(upcomingDeltaSeconds: Double): Long

        /**
         * At the end of an update cycle, we tell the simulation
         * how many ticks it ran for
         */
        abstract fun notifyRanFor(ticks: Long)

        protected fun serverSecondsToSimTicks(serverSeconds: Double): Double {
            return sim.tps.toDouble() * serverSeconds
        }
    }

}