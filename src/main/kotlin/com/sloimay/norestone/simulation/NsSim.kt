package com.sloimay.norestone.simulation

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.SideEffectSet
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.nodestonecore.simulation.SimBackend
import com.sloimay.nodestonecore.simulation.abilities.BlockPositionedRsInputs
import com.sloimay.nodestonecore.simulation.abilities.BlockStateChangeRequestAbility
import com.sloimay.nodestonecore.simulation.abilities.SyncedTickAbility
import com.sloimay.nodestonecore.simulation.backends.shrimple.ShrimpleRsInput
import com.sloimay.nodestonecore.simulation.inputs.SimRsInput
import com.sloimay.nodestonecore.simulation.siminterfaces.TileEntityRequestAbility
import com.sloimay.norestone.*
import com.sloimay.norestone.selection.SimSelection
import com.sloimay.smath.abs
import com.sloimay.smath.floor
import com.sloimay.smath.vectors.IVec3
import net.querz.nbt.tag.ListTag
import org.bukkit.block.data.BlockData
import kotlin.math.min


private typealias WeBlockState = com.sk89q.worldedit.world.block.BlockState


/**
 * After the initialisation, all var changes must be requested through the manager
 */
class NsSim(
    private val noreStone: NOREStone,

    val sel: SimSelection,
    val nodeStoneSim: SimBackend,
    val simWorldOrigin: IVec3,

    val simManager: NsSimManager,

    tps: Double,
) {
    var tps: Double = tps
        private set

    private var state: SimState
    private val weBlockStateCache = hashMapOf<BlockState, Pair<BlockData, WeBlockState>>()

    // Sim local coordinates
    val positionedInputs = hashMapOf<IVec3, SimRsInput>()
    val positionsOfContainers = hashSetOf<IVec3>()


    // == Managed by the ns sim manager
    // The samples over the last second
    val tpsTrackingLock = Object()
    val sampleBuf = LongArray(simManager.updateHz)
    val tpsTracker = RollingSampleAnalyser(simManager.tpsMonitoringMaxSampleCount)
    var updateCycleCountLifetime = 0L
    // ==


    init {
        require(sel.isComplete()) { "Nodestone simulation selection has to be complete." }

        state = SimState.Running(this)

        if (nodeStoneSim is BlockPositionedRsInputs) {
            // Get positioned redstone inputs
            (nodeStoneSim.getBlockPositionedRsInputs())
                .forEach { positionedInputs[it.key] = it.value }
        }

        if (nodeStoneSim is TileEntityRequestAbility) {
            val tileEntities = nodeStoneSim.requestTileEntities()
            for ((tePos, te) in tileEntities) {
                val itemsNbt = te.get("Items") ?: continue
                if (itemsNbt is ListTag<*>) {
                    positionsOfContainers.add(tePos)
                }
            }
        }
    }


    fun requestTpsChange(newTickRate: Double) {
        if (newTickRate < 0.0) error("Tick rate cannot be negative")
        if (newTickRate.abs() == 0.0) error("Tick rate cannot be 0.0")
        noreStone.simManager.requestSimVarMutation(this) { it.tps = newTickRate }
    }

    /**
     * Will overwrite the current stepping ticks if there are any
     */
    fun requestStep(tickCount: Long) {
        if (tickCount == 0L) error("Cannot step for 0 ticks.")
        if (tickCount < 0L) error("Cannot step for a negative amount of ticks.")
        val s = state
        if (!isFrozen()) error("Can only step while the simulation frozen.")

        noreStone.simManager.requestSimVarMutation(this) { (s as SimState.Frozen).step(tickCount) }
    }

    fun requestFreeze(): SimState {
        val newState = when (state) {
            is SimState.Frozen -> SimState.Running(this)
            is SimState.Running -> SimState.Frozen(this)
            else -> error("Unreachable")
        }

        noreStone.simManager.requestSimVarMutation(this) { it.state = newState }

        return newState
    }


    fun isFrozen() = state is SimState.Frozen





    fun endingSequence() {


        //if (nodeStoneSim is ShrimpleBackend) {
            // This backend, like most, leaves the blocks rendered in a limbo state, as
            // it doesn't carry over the scheduled ticks
            // This is an attempt at updating those blocks after the simulation was deleted
            // so the redstone is updated. It doesn't work and I have no idea how to make
            // it work lol
            /*noreStone.server.scheduler.runTaskLater(noreStone,
                Runnable {
                    val sideEffects = SideEffectSet.none()
                        .with(SideEffect.UPDATE, SideEffect.State.ON)
                        .with(SideEffect.NEIGHBORS, SideEffect.State.ON)
                        .with(SideEffect.VALIDATION, SideEffect.State.ON)
                    val simBounds = sel.bounds()!!
                    val world = sel.world!!
                    val weWorld = BukkitAdapter.adapt(world)
                    for (pos in simBounds.iterYzx()) {
                        val block = world.getBlockAt(pos.x, pos.y, pos.z)
                        if (block.type == Material.AIR) continue
                        if (block.type !in SHRIMPLE_SIM_END_MATS_TO_UPDATE) continue
                        val posBv3 = pos.toBlockVector3()
                        weWorld.setBlock(posBv3, weWorld.getBlock(posBv3), sideEffects)
                        //block.state.update(true, true)
                    }
                },
                20
            )*/
        //}
    }


    // == Render methods

    // Main render method
    fun shouldRender(): Boolean {
        return state.shouldRender()
    }

    fun render() {

        if (nodeStoneSim is BlockStateChangeRequestAbility) {
            val weWorld = BukkitAdapter.adapt(sel.world!!)
            nodeStoneSim.requestBlockStateChanges { localPos, newBlockState ->
                val worldPos = local2World(localPos)
                val (bukkitBlockData, weBlockState) = weBlockStateCache.computeIfAbsent(newBlockState) {
                    val bukkitBlockData = noreStone.server.createBlockData(it.stateStr)
                    Pair(bukkitBlockData, BukkitAdapter.adapt( bukkitBlockData ))
                }
                //println("rendering block at $worldPos")
                if (localPos !in positionsOfContainers) {
                    // Free to place blocks with WE if it's not a container, as WE erases
                    // NBT. However I'm pretty sure it might create some bugs with some more
                    // obscure tile entity so let's stay on the lookout
                    weWorld.setBlock(worldPos.toBlockVector3(), weBlockState, SideEffectSet.none())
                } else {
                    // It's a container so place with bukkit's API
                    //println("PLACED USING BUKKIT API")
                    sel.world
                        .getBlockAt(worldPos.x, worldPos.y, worldPos.z)
                        .setBlockData(bukkitBlockData, false)
                }
            }
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




    abstract class SimState(val sim: NsSim) {
        protected val stateTimePrecision = 1024


        /**
         * Like the running state but it stalls if we don't have any ticks to step through left
         */
        class Frozen(sim: NsSim) : SimState(sim) {
            var stepTicksLeft = 0L

            var stateTime = 0.0 // one unit is one tick

            var idealTickCountThisUpdate = 0L
            var timeToAddIfIdealTickCountIsReached = 0.0

            var lastUpdateCycleTickCount = 0L

            // Called at the start of an update cycle
            override fun getTicksToRunFor(upcomingDeltaSeconds: Double): Long {
                if (stepTicksLeft <= 0) return 0

                val deltaTicks = serverSecondsToSimTicks(upcomingDeltaSeconds)
                val deltaTimestamp = stateTime + deltaTicks

                // Don't run for longer than the amount of ticks we have left
                // to step through
                val ticksToRun = min(
                    (deltaTimestamp.floor()).toLong() - (stateTime.floor()).toLong(),
                    stepTicksLeft,
                )
                idealTickCountThisUpdate = ticksToRun
                timeToAddIfIdealTickCountIsReached = deltaTicks
                return ticksToRun
            }

            // Called at the end of an update cycle
            override fun notifyRanFor(ticks: Long) {
                lastUpdateCycleTickCount = ticks
                if (stepTicksLeft <= 0) return

                if (ticks == idealTickCountThisUpdate) {
                    stateTime += timeToAddIfIdealTickCountIsReached
                } else {
                    stateTime += ticks.toDouble()
                }
                stateTime = stateTime.round(stateTimePrecision) // Hopefully mitigates floating point errors

                stepTicksLeft -= ticks
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

            fun step(tickCount: Long) {
                stepTicksLeft = tickCount
            }
        }




        class Running(sim: NsSim) : SimState(sim) {
            var stateTime = 0.0 // one unit is one tick

            var idealTickCountThisUpdate = 0L
            var timeToAddIfIdealTickCountIsReached = 0.0

            var lastUpdateCycleTickCount = 0L

            // Called at the start of an update cycle
            override fun getTicksToRunFor(upcomingDeltaSeconds: Double): Long {
                //println("=====")
                //println("sim time: $stateTime")
                val deltaTicks = serverSecondsToSimTicks(upcomingDeltaSeconds)
                val deltaTimestamp = stateTime + deltaTicks

                val ticksToRun = (deltaTimestamp.floor()).toLong() - (stateTime.floor()).toLong()
                idealTickCountThisUpdate = ticksToRun
                timeToAddIfIdealTickCountIsReached = deltaTicks

                //println("ticks to run: $ticksToRun")
                //println("ideal: $idealTickCountThisUpdate")
                //println("time to add if ideal: $timeToAddIfIdealTickCountIsReached")

                //println("sim time: $stateTime")
                //println("delta timestamp: $deltaTimestamp")

                return ticksToRun
            }

            // Called at the end of an update cycle
            override fun notifyRanFor(ticks: Long) {
                //println("notified ran for:")
                //if (ticks == 0L) {
                //    Bukkit.broadcastMessage("RAN FOR 0 TICKS")
                //}
                //println("ideal tick count: $idealTickCountThisUpdate")
                //println("actual amount of ticks we ran for: $ticks")
                if (ticks == idealTickCountThisUpdate) {
                    stateTime += timeToAddIfIdealTickCountIsReached
                } else {
                    stateTime += ticks.toDouble()
                }
                stateTime = stateTime.round(stateTimePrecision) // Hopefully mitigates floating point errors
                //println("new sim time: $stateTime")

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