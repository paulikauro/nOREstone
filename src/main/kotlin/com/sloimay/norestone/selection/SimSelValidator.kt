package com.sloimay.norestone.selection

import com.sloimay.norestone.*
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.smath.vectors.IVec3
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * Singleton checking static selections
 */
class SimSelValidator(val noreStone: NOREStone) {


    fun validateForCompilation(player: Player): Result<Unit, String> {
        val sesh = noreStone.getSession(player)
        if (sesh.sel.isSpatiallyComplete()) {
            val spatialValidationRes = validateSpatially(player, sesh.sel)
            return spatialValidationRes
        } else {
            return Result.err("Selection isn't complete.")
        }
    }

    fun validateForSimSpatialChange(player: Player, newSel: SimSelection): Result<Unit, String> {
        if (newSel.isSpatiallyComplete()) {
            val spatialValidationRes = validateSpatially(player, newSel)
            return spatialValidationRes
        } else {
            if (!newSel.isSpatiallyNull()) {
                require(newSel.world != null) { "Non spatially null selection needs a world." }
                if (newSel.pos1 != null) {
                    val pos1ValidationRes = validateSelCorner(player, newSel.world, newSel.pos1)
                    if (pos1ValidationRes.isErr()) return pos1ValidationRes
                }
                if (newSel.pos2 != null) {
                    val pos1ValidationRes = validateSelCorner(player, newSel.world, newSel.pos2)
                    if (pos1ValidationRes.isErr()) return pos1ValidationRes
                }
            }
            return Result.ok(Unit)
        }
    }


    /**
     * Assumes the selection is spatially complete
     */
    fun validateSpatially(
        player: Player,
        sel: SimSelection,
        validateAxes: Boolean = true,
        validateVolume: Boolean = true,
    ): Result<Unit, String> {
        require(sel.isSpatiallyComplete()) { "Selection needs to be spatially complete" }

        // Axis dimensions validation
        if (validateAxes) {
            val axesRes = validatePlayerSelAxes(player, sel)
            if (axesRes.isErr()) return axesRes
        }

        // Validate volume
        if (validateVolume) {
            val volumeRes = validatePlayerSelVolume(player, sel)
            if (volumeRes.isErr()) return volumeRes
        }

        // Check if overlap any other selection / simulations

        // Check for plot trusting


        return Result.ok(Unit)
    }



    fun validatePlayerSelAxes(player: Player, sel: SimSelection): Result<Unit, String> {
        // Bypass
        if (player.hasPermission(NsPerms.Simulation.Selection.MaxDims.bypass)) {
            return Result.ok(Unit)
        }

        val dimsLim = noreStone.getPlayerMaxSimSelSize(player)
        val res = validateSelAxes(sel, dimsLim)
        return res
    }

    fun validatePlayerSelVolume(player: Player, sel: SimSelection): Result<Unit, String> {
        // Bypass
        if (player.hasPermission(NsPerms.Simulation.Selection.MaxVolume.bypass)) {
            return Result.ok(Unit)
        }

        val maxVol = noreStone.getPlayerMaxSimSelVolume(player)
        val res = validateSelVolume(sel, maxVol)
        return res
    }


    fun validateSelAxes(sel: SimSelection, dimsLim: IVec3): Result<Unit, String> {
        val bounds = sel.bounds() ?: return Result.ok(Unit)
        val selDims = (bounds.b - bounds.a).abs()
        // If the diff between any of the coords is 0 or greater it means selDims <= dimsDiff everywhere
        val dimsDiff = dimsLim - selDims
        if (dimsDiff.eMin() >= 0) return Result.ok(Unit)

        val axesNames = listOf("X", "Y", "Z")
        var wrongAxesCount = 0
        val axesStr = (0 until 3).filter { wrongAxesCount += 1; return@filter dimsDiff[it] < 0 }.joinToString {
            "${axesNames[it]}: (${selDims[it]}/${dimsLim[it]})"
        }
        return Result.err("Selection too big in ${if (wrongAxesCount > 1) "axes" else "axis"} $axesStr.")
    }

    fun validateSelVolume(sel: SimSelection, maxVol: Long): Result<Unit, String> {
        val bounds = sel.bounds() ?: return Result.ok(Unit)
        val selDims = (bounds.b - bounds.a).abs()
        val selVolume = selDims.eProdLong()
        if (selVolume <= maxVol) return Result.ok(Unit)

        return Result.err("Selection is too large. Max volume allowed: " +
                "$maxVol but got $selVolume.")
    }

    fun validateSelCorner(player: Player, cornerWorld: World, corner: IVec3): Result<Unit, String> {
        // If the player can select everywhere then any corner is valid
        if (player.hasPermission(NsPerms.Simulation.Selection.Select.bypass)) {
            return Result.ok(Unit)
        }

        // Check if the plot the corner is in, is in a trusted or owned plot
        val plot = noreStone.getPlotAt(cornerWorld, corner)
            ?: return Result.err("Plot corner should be inside a plot.")
        val canSetCornerHere = (player.uniqueId in plot.owners) || (player.uniqueId in plot.trusted)
        if (!canSetCornerHere) {
            return Result.err("Plot corner should be inside a plot you either own or are trusted on.")
        }

        return Result.ok(Unit)
    }

}