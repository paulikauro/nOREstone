package com.sloimay.norestone

import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.selection.SimSelection
import com.sloimay.smath.vectors.IVec3
import org.bukkit.entity.Player

class PlayerInteractions(val noreStone: NOREStone) {

    /**
     * Setting selection corners should only happen in this method!!
     */
    fun setSimSelCorner(player: Player, newCorner: IVec3, cornerIdx: Int): Result<Unit, String> {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.Selection.select) { return Result.err(it) }

        val sesh = noreStone.getSession(player)

        // Check if the new corner is in the same world as the previous one (if one was set)
        val selW = sesh.sel.world
        if (selW != null) {
            if (selW.uid != player.world.uid) {
                return Result.err(
                    "Trying to select in 2 different worlds. Keep it in the same world," +
                            " or do \"/sim desel\" and start selecting again."
                )
            }
        }

        // If we're setting the same corner, don't do anything
        val oldCorner = sesh.sel[cornerIdx]
        if (oldCorner == newCorner) {
            return Result.ok(Unit)
        }

        // # Attempt a new selection
        val newSelAttempt = when (cornerIdx) {
            0 -> sesh.sel.withPos1(newCorner)
            1 -> sesh.sel.withPos2(newCorner)
            else -> error("Index out of bounds.")
        }.withWorld(player.world)
        // Spatial change validation
        val spatialChangeValidationRes =
            noreStone.simSelValidator.validateForSimSpatialChange(player, newSelAttempt)
        if (spatialChangeValidationRes.isErr()) return spatialChangeValidationRes

        // New selection attempt success
        sesh.sel = newSelAttempt

        return Result.ok(Unit)
    }

    fun desel(player: Player): Result<Unit, String> {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.Selection.select) { return Result.err(it) }

        val sesh = noreStone.getSession(player)

        sesh.sel = SimSelection.empty()

        return Result.ok(Unit)
    }


    fun compileSim(player: Player): Result<Unit, String> {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.compile) { return Result.err(it) }
        if (noreStone.doesPlayerSimExists(player)) {
            return Result.err("A simulation is still active, please clear it before trying to" +
                    " compile a new one.")
        }

        val validationRes = noreStone.simSelValidator.validateForCompilation(player)
        if (validationRes.isErr()) return validationRes

        return Result.ok(Unit)
    }

}