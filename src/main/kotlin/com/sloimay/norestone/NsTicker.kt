package com.sloimay.norestone

class NsTicker(val noreStone: NOREStone) : Runnable {
    private var tickCount = 0L
    override fun run() {
        noreStone.syncedWorker.flushWork()



        noreStone.simManager.tryRender()
        // Possible bug if isUpdating is true when we call tryRender() but becomes false when we call tryUpdateCycle()
        // It's very very rare, and will delay rendering of a simulation by at most 1 tick probabilistically
        noreStone.simManager.tryUpdateCycle(
            40, /* TODO: should be adjusted depending on the tick rate of the world for each sim */
        )



        tickCount += 1
    }
}
