package com.sloimay.norestone

import com.sloimay.mcvolume.IntBoundary
import com.sloimay.nodestonecore.backends.RedstoneSimBackend
import com.sloimay.nodestonecore.backends.shrimple.ShrimpleBackend
import com.sloimay.norestone.selection.SimSelection
import org.bukkit.World

class NsSim(
    val sel: SimSelection,
    val nodestoneSim: RedstoneSimBackend,
) {

    init {
        require(sel.isComplete()) { "Nodestone simulation selection has to be complete." }
    }


    fun end() {
    }

}