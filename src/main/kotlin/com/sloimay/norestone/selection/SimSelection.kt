package com.sloimay.norestone.selection

import com.sloimay.norestone.newInclusive
import com.sloimay.norestone.toInt
import com.sloimay.smath.geometry.boundary.IntBoundary
import com.sloimay.smath.vectors.IVec3
import org.bukkit.World

/**
 * No responsibility of valid data, it just merely holds the data
 * Immutable 3-tuple (world, pos1, pos2)
 *
 * TODO: use a better system to avoid invalid states (such as null world with non-null positions)
 */
data class SimSelection(
    val world: World?,
    val pos1: IVec3?,
    val pos2: IVec3?,
) {

    operator fun get(idx: Int) = when (idx) { 0 -> pos1; 1 -> pos2; else -> error("Index out of bounds") }

    // Looked nicer in my head lmao
    fun isSpatiallyNull() = ((pos1 != null).toInt() + (pos2 != null).toInt()) == 0
    fun isSpatiallyPartial() = ((pos1 != null).toInt() + (pos2 != null).toInt()) == 1
    fun isSpatiallyComplete() = ((pos1 != null).toInt() + (pos2 != null).toInt()) == 2

    fun isComplete() = isSpatiallyComplete() && world != null

    fun bounds() = if (isComplete()) IntBoundary.newInclusive(pos1!!, pos2!!) else null


    fun withPos1(pos1: IVec3) = SimSelection(world, pos1, pos2)
    fun withPos2(pos2: IVec3) = SimSelection(world, pos1, pos2)
    fun withWorld(w: World) = SimSelection(w, pos1, pos2)


    companion object {
        fun empty(): SimSelection {
            return SimSelection(null, null, null)
        }

        fun newValidOrEmpty(world: World?, pos1: IVec3?, pos2: IVec3?): SimSelection {
            if (pos1 != null && pos2 != null) {
                if (world == null) return empty()
            }
            return SimSelection(world, pos1, pos2)
        }
    }

    override fun toString(): String {
        return "$pos1 $pos2 ${world?.uid}"
    }
}