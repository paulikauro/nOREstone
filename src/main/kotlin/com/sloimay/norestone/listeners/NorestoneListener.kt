package com.sloimay.norestone.listeners

import com.plotsquared.core.PlotSquared
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.SideEffectSet
import com.sloimay.mcvolume.IntBoundary
import com.sloimay.norestone.*
import com.sloimay.norestone.permission.NsPerms
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
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

    }


    /**
     * TODO:
     *  Do some bounds checking on the sim selections anytime there's an update about plot trusts,
     *  plot clears, plot adds, plot claims etc..
     */

}