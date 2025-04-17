package com.sloimay.norestone

import com.sloimay.mcvolume.IntBoundary
import com.sloimay.nodestonecore.backends.RedstoneSimBackend
import com.sloimay.nodestonecore.backends.shrimple.ShrimpleBackend
import org.bukkit.World

class NsSim(
    val world: World,
    val worldBounds: IntBoundary,
    val nodestoneSim: RedstoneSimBackend,
) {




    fun end() {
    }

}