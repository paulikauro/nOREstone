package com.sloimay.norestone.listeners

import com.plotsquared.core.PlotSquared
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.SideEffectSet
import com.sloimay.nodestonecore.simulation.abilities.RsInputSchedulingAbility
import com.sloimay.norestone.*
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.smath.geometry.boundary.IntBoundary
import com.sloimay.smath.vectors.IVec3
import de.tr7zw.nbtapi.NBT
import de.tr7zw.nbtapi.NBTCompound
import net.querz.nbt.io.SNBTUtil
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class NorestoneListener(val noreStone: NOREStone) : Listener {

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        noreStone.addSession(e.player)
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        noreStone.endSession(e.player)
    }

    @EventHandler
    fun onPlayerUseSimSelWand(e: PlayerInteractEvent) {

        val p = e.player
        if (p.gameMode != GameMode.CREATIVE) return
        if (e.item == null) return

        val sesh = noreStone.getSession(p)
        if (e.item!!.type != sesh.selWand.type) return



        val action = e.action
        val actionIsClickBlock = action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK
        if (!actionIsClickBlock) return

        // Selection click so we cancel
        e.isCancelled = true

        val blockClicked = e.clickedBlock ?: return
        val posToSet = IVec3.fromBlock(blockClicked)

        val cornerIdx = when (action) {
            Action.LEFT_CLICK_BLOCK -> 0
            Action.RIGHT_CLICK_BLOCK -> 1
            else -> error("Unreachable")
        }

        val simSelSettingRes = noreStone.playerInteract.setSimSelCorner(p, posToSet, cornerIdx)

        if (simSelSettingRes.isErr()) {
            p.nsErr(simSelSettingRes.getErr())
        } else {
            p.nsInfoSimSelSetPos(posToSet, cornerIdx, noreStone.getSession(p).sel)
        }

    }


    @EventHandler
    fun onRedstoneChangeInSim(e: BlockRedstoneEvent) {
        fun cancelEvent() {
            e.newCurrent = e.oldCurrent
        }

        val block = e.block
        val blockPos = block.location.blockPos()

        // Could use some kind of spatial tree, as it may get pretty laggy if there are a lot of sims
        // going at the same time
        noreStone.simManager.applyOnSimsReadOnly { sim ->
            if (sim.sel.world!!.uid != e.block.world.uid) return@applyOnSimsReadOnly
            // Update happened in the same world as this sim
            val simWorldBounds = sim.sel.bounds()!!
            if (!simWorldBounds.posInside(blockPos)) return@applyOnSimsReadOnly
            // Update happened in this sim bounds

            // Handle user inputs
            val nodeStoneSim = sim.nodeStoneSim

            if (nodeStoneSim is RsInputSchedulingAbility) {

                if (block.type in INPUT_MATERIALS) {

                    // Inputs in shrimple are relative to the simulation location in the volume
                    // (which is anchored at 0,0)
                    val blockPosInNodestoneSim = blockPos - sim.simWorldOrigin
                    val positionedInput = sim.positionedInputs[blockPosInNodestoneSim]

                    if (positionedInput != null) {
                        val doCancelEvent: Boolean = when (block.type) {
                            Material.LEVER -> {
                                nodeStoneSim.scheduleRsInput(positionedInput, 0, e.newCurrent)
                                true
                            }

                            in STONE_BUTTON_MATS -> {
                                nodeStoneSim.scheduleRsInput(positionedInput, 0, 15)
                                nodeStoneSim.scheduleRsInput(positionedInput, 10, 0)
                                true
                            }
                            in WOODEN_BUTTON_MATS -> {
                                nodeStoneSim.scheduleRsInput(positionedInput, 0, 15)
                                nodeStoneSim.scheduleRsInput(positionedInput, 15, 0)
                                true
                            }

                            in PRESSURE_PLATE_MATS -> {
                                nodeStoneSim.scheduleRsInput(positionedInput, 0, e.newCurrent)
                                false
                            }

                            else -> { true }
                        }

                        if (doCancelEvent) {
                            cancelEvent()
                        }
                    }

                    return@applyOnSimsReadOnly
                }
            }
            cancelEvent()
        }
    }











    @EventHandler
    fun debug(e: PlayerInteractEvent) {


        if (DEBUG.PLOT) {
            val blockPos = e.player.location.blockPos()
            val world = e.player.world
            val plot = noreStone.getPlotAt(world, blockPos)
            if (plot == null) Bukkit.broadcastMessage("null plot")
            if (plot != null) {

                // Visualize plot cuboid regions
                Bukkit.broadcastMessage("REGIONS")
                for ((regionIdx, r) in plot.regions.withIndex()) {
                    Bukkit.broadcastMessage("reg")
                    Bukkit.broadcastMessage(r.pos1.toString())
                    Bukkit.broadcastMessage(r.pos2.toString())

                    val weWorld = BukkitAdapter.adapt(world)
                    val p1 = r.minimumPoint.toIVec3().withY(4)
                    val p2 = r.maximumPoint.toIVec3().withY(4) + 1
                    val bounds = IntBoundary.new(p1, p2)

                    val blocks = listOf(
                        "white", "light_gray", "gray", "black",
                        "brown", "red", "orange", "yellow", "lime", "green",
                        "cyan", "light_blue", "blue", "purple", "magenta", "pink"
                    ).map { BukkitAdapter.adapt(Bukkit.createBlockData("minecraft:${it}_wool")) }

                    println(bounds)

                    for (p in bounds.iterYzx()) {
                        weWorld.setBlock(p.toBlockVector3(), blocks[regionIdx % blocks.size], SideEffectSet.none())
                    }
                }
            }
        }

        if (DEBUG.NUMBER_PERMS) {
            val player = e.player
            val maxTps = noreStone.getPlayerMaxSimTps(player)
            Bukkit.broadcastMessage("maxTps: $maxTps")

            println(player.hasPermission(NsPerms.Simulation.Selection.Select.bypass))
        }

        if (DEBUG.PLOT_ITERATION) {
            Bukkit.broadcastMessage("==== PLOT ITERATION DEBUG")
            val p = e.player

            noreStone.plotApi.wrapPlayer(p.uniqueId)!!.plots

            PlotSquared.get().plotAreaManager.allPlotAreas
                .filter { it.worldName == p.world.name }
                .forEach { plotArea ->
                    plotArea.plots.forEach { plot ->
                        Bukkit.broadcastMessage("${plot.area!!.plotManager}")
                        Bukkit.broadcastMessage("PLOT")
                    }
                }

        }

        if (DEBUG.NBTAPI_TO_QUERZ) {
            if (e.action != Action.RIGHT_CLICK_BLOCK) return

            val block = e.clickedBlock!!
            NBT.get(block.state) { nbt ->
                val querz = nbtApiToQuerzNbt(nbt as NBTCompound)
                Bukkit.broadcastMessage(SNBTUtil.toSNBT(querz))
                println(SNBTUtil.toSNBT(querz))
            }
        }

    }


}