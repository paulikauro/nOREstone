package com.sloimay.norestone.listeners

import com.google.common.eventbus.Subscribe
import com.plotsquared.core.events.*
import com.sloimay.norestone.NOREStone
import com.sloimay.norestone.SimAnomalies
import com.sloimay.norestone.nsWarn
import com.sloimay.norestone.selection.SimSelection
import org.bukkit.Bukkit

class PlotSquaredListener(val noreStone: NOREStone) {

    @Subscribe
    fun onPlotEvent(e: PlotEvent) {
        if (!noreStone.consts.EXTRA_SAFE_SIM_BOUNDS_CHECKING) return


        // Some others need to be added like plot auto and auto merge events but idk
        // how to handle them properly (not that I do for these ones either lol)
        // For these 4 I'm assuming the event bus of PlotSquared is synced with Bukkit's,
        // so no race condition will happen from here with the rest of the codebase.
        val isRelevantEvent = (
            e is PlayerClaimPlotEvent || // Maybe you never need this one but I'm not sure
            e is PlayerPlotTrustedEvent ||
            e is PlotDeleteEvent || // This is fired when you do /plot delete, but you need to
                                    // /plot confirm afterwards.
            e is PlotMergeEvent
        )

        if (isRelevantEvent) {
            val anomalies = noreStone.simulationsPoliceCheckup()
            for ((seshUuid, seshAnomalies) in anomalies) {
                val player = Bukkit.getPlayer(seshUuid)

                val sesh = noreStone.getSession(seshUuid)

                for (anomaly in seshAnomalies) {
                    when (anomaly) {
                        SimAnomalies.PLAYER_SEL_BOUNDS -> {
                            sesh.sel = SimSelection.empty()
                            player?.nsWarn("Following a modification of your trusted status, or plots changing" +
                                    " geometry, which lead to your selection being in an invalid state, your selection" +
                                    " was reset.")
                        }
                        SimAnomalies.SIM_SEL_BOUNDS -> {
                            sesh.endSim()
                            player?.nsWarn("Following a modification of your trusted status, or plots changing" +
                                    " geometry, which lead to your currently on-going simulation being in an invalid" +
                                    " state, your simulation was cleared.")
                        }
                    }
                }
            }
        }

    }

}