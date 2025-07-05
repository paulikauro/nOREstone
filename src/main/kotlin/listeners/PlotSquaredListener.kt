package com.sloimay.norestone.listeners

import com.google.common.eventbus.Subscribe
import com.plotsquared.core.events.*
import com.sloimay.norestone.NOREStone

class PlotSquaredListener(val noreStone: NOREStone) {
    @Subscribe
    fun doSimPoliceChecks(e: PlotEvent) {
        if (!noreStone.consts.EXTRA_SAFE_SIM_BOUNDS_CHECKING) return
        // Some others need to be added like plot auto and auto merge events but idk
        // how to handle them properly (not that I do for these ones either lol)
        // For these 4 I'm assuming the event bus of PlotSquared is synced with Bukkit's,
        // so no race condition will happen from here with the rest of the codebase.
        // TODO: check thread safety ^
        val isRelevantEvent = (
                e is PlayerClaimPlotEvent || // Maybe you never need this one but I'm not sure
                        e is PlayerPlotTrustedEvent ||
                        e is PlotDeleteEvent || // This is fired when you do /plot delete, but you need to
                        // /plot confirm afterwards, which this function isn't detecting.
                        e is PlotMergeEvent
                )

        if (!isRelevantEvent) return
        noreStone.simulationsPoliceCheckup()
    }
}
