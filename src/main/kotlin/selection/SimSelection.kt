package com.sloimay.norestone.selection

import com.sloimay.norestone.newInclusive
import com.sloimay.smath.geometry.boundary.IntBoundary
import com.sloimay.smath.vectors.IVec3
import org.bukkit.World

data class SimSelection(
    val world: World,
    val pos1: IVec3,
    val pos2: IVec3,
) {
    val bounds: IntBoundary
        get() = IntBoundary.newInclusive(pos1, pos2)

    companion object {
        // TODO: can we get rid of this?
        fun empty(): SimSelection? = null
    }

    override fun toString() = "$pos1 $pos2 ${world.uid}"
}
