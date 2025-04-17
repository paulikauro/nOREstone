package com.sloimay.norestone

import com.plotsquared.core.events.PlotClearEvent
import com.plotsquared.core.events.PlotDeleteEvent
import com.plotsquared.core.events.PlotEvent
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.SideEffectSet
import com.sloimay.mcvolume.IntBoundary
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
        if (!PLOT_DEBUG) return

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


    /**
     * TODO:
     *  Do some bounds checking on the sim selections anytime there's an update about plot trusts,
     *  plot clears, plot adds, plot claims etc..
     */

}