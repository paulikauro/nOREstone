package com.sloimay.norestone.selection

import com.plotsquared.core.util.query.PlotQuery
import com.sloimay.norestone.*
import com.sloimay.norestone.Result.Err
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.smath.geometry.boundary.IntBoundary
import com.sloimay.smath.vectors.IVec3
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import kotlin.time.TimeSource

/**
 * Singleton checking static selections
 */
class SimSelValidator(val noreStone: NOREStone) {
    fun validateForCompilation(player: Player, sel: SimSelection): Result<Unit, String> = validateSpatially(player, sel)

    fun validateSpatially(
        player: Player,
        sel: SimSelection,
        validateCorners: Boolean = true,
        validateAxes: Boolean = true,
        validateVolume: Boolean = true,
        validateOverlap: Boolean = true,
        validatePlotPositioning: Boolean = true,
    ): Result<Unit, String> {
        if (validateCorners) {
            val pos1ValidationRes = validateSelCorner(player, sel.world, sel.pos1, 0)
            if (pos1ValidationRes is Err) return pos1ValidationRes
            val pos2ValidationRes = validateSelCorner(player, sel.world, sel.pos2, 1)
            if (pos2ValidationRes is Err) return pos2ValidationRes
        }
        // Axis dimensions validation
        if (validateAxes) {
            val axesRes = validatePlayerSelAxes(player, sel)
            if (axesRes is Err) return axesRes
        }
        // Validate volume
        if (validateVolume) {
            val volumeRes = validatePlayerSelVolume(player, sel)
            if (volumeRes is Err) return volumeRes
        }
        // Check if overlap any other selection / simulations
        if (validateOverlap) {
            val overlapRes = validatePlayerSelOverlap(player, sel)
            if (overlapRes is Err) return overlapRes
        }
        // Check if in legal spots
        if (validatePlotPositioning) {
            val plotPositioningRes = validatePlayerSelPlotPositioning(player, sel)
            if (plotPositioningRes is Err) return plotPositioningRes
        }


        return Result.ok()
    }

    fun validatePlayerSelAxes(player: Player, sel: SimSelection): Result<Unit, String> {
        // Bypass
        if (player.hasPermission(NsPerms.Simulation.Selection.MaxDims.bypass)) {
            return Result.ok()
        }
        val dimsLim = noreStone.getPlayerMaxSimSelSize(player)
        return validateSelAxes(sel, dimsLim)
    }

    fun validatePlayerSelVolume(player: Player, sel: SimSelection): Result<Unit, String> {
        // Bypass
        if (player.hasPermission(NsPerms.Simulation.Selection.MaxVolume.bypass)) {
            return Result.ok()
        }
        val maxVol = noreStone.getPlayerMaxSimSelVolume(player)
        return validateSelVolume(sel, maxVol)
    }

    fun validateSelAxes(sel: SimSelection, dimsLim: IVec3): Result<Unit, String> {
        val bounds = sel.bounds
        val selDims = (bounds.b - bounds.a).abs()
        // If the diff between any of the coords is 0 or greater it means selDims <= dimsDiff everywhere
        val dimsDiff = dimsLim - selDims
        if (dimsDiff.eMin() >= 0) return Result.ok()
        val axesNames = listOf("X", "Y", "Z")
        var wrongAxesCount = 0
        val axesStr = (0 until 3).filter {
            val wrongAxis = dimsDiff[it] < 0
            if (wrongAxis) wrongAxesCount += 1
            wrongAxis
        }.joinToString {
            "${axesNames[it]}: (${selDims[it]}/${dimsLim[it]})"
        }
        return Result.err("Selection too big in ${if (wrongAxesCount > 1) "axes" else "axis"} $axesStr.")
    }

    fun validateSelVolume(sel: SimSelection, maxVol: Long): Result<Unit, String> {
        val bounds = sel.bounds
        val selDims = (bounds.b - bounds.a).abs()
        val selVolume = selDims.eProdLong()
        if (selVolume <= maxVol) return Result.ok()

        return Result.err("Selection is too large. Max volume allowed: $maxVol but got $selVolume.")
    }

    fun validateSelCorner(player: Player, cornerWorld: World, corner: IVec3, cornerIdx: Int): Result<Unit, String> {
        // If the player can select everywhere then any corner is valid
        if (player.hasPermission(NsPerms.Simulation.Selection.Select.bypass)) {
            return Result.ok()
        }
        // Check if outside the world
        val cornerWithinWorldHeight = corner.y in cornerWorld.minHeight..cornerWorld.maxHeight
        if (!cornerWithinWorldHeight) {
            return Result.err("Selection corner ${cornerIdx + 1} should be within world height.")
        }
        // Check if the plot the corner is in is a trusted or owned plot
        val plot = noreStone.getPlotAt(cornerWorld, corner)
            ?: return Result.err("Selection corner should be inside a plot.")
        val canSetCornerHere = player.uniqueId in plot.owners || player.uniqueId in plot.trusted
        if (!canSetCornerHere) {
            return Result.err(
                "Selection corner ${cornerIdx + 1} should be inside a plot you either own or are trusted on."
            )
        }

        return Result.ok()
    }

    fun validatePlayerSelOverlap(player: Player, sel: SimSelection): Result<Unit, String> {
        // Check over every active simulation if our selection is overlapping any other sims
        // TODO: check sims, not sessions (see: sim police checkup)
        for (playerUuid in noreStone.sessions.keys) {
            if (playerUuid == player.uniqueId) continue
            val playerCheckedAgainst = Bukkit.getPlayer(playerUuid) ?: continue
            //val pcaSesh = noreStone.getSession(playerCheckedAgainst)
            val pcaSim = noreStone.simManager.getPlayerSim(playerCheckedAgainst.uniqueId) ?: continue
            if (pcaSim.sel.world.uid != sel.world.uid) continue

            if (sel.bounds.intersects(pcaSim.sel.bounds)) {
                return Result.err("Simulation selection intersects with a currently on-going simulation.")
            }
        }

        return Result.ok()
    }

    /**
     * Check if the selection is entirely in plots that the owner owns / is trusted on
     */
    fun validatePlayerSelPlotPositioning(player: Player, sel: SimSelection): Result<Unit, String> {
        // If player can select everywhere
        if (player.hasPermission(NsPerms.Simulation.Selection.Select.bypass)) {
            return Result.ok()
        }

        if (!isSimSelInLegalSpot_assume2dPlots(sel, player)) return Result.err(
            "Selection overlaps with areas you do not have access to. (= Plots you aren't owner of or trusted on.)"
        )

        return Result.ok()
    }

    fun isSimSelInLegalSpot(sel: SimSelection, player: Player): Boolean {
        // # Iterate over every plot boundary that the player's sim is allowed to reside in.
        // # For each of these boundaries, set to 1 in a 3d array of booleans the blocks they
        // # intersect with the selection boundary. If the array is all "true" at the end of
        // # the for loops, then that means all the blocks within the simulation volume are
        // # in a place that's allowed.
        // TODO: Please someone find a better algorithm XDDDD, it takes ~500ms on my
        //       machine to check a 500x300x300 cuboid. It's especially important
        //       because this function is gonna get used a lot in the PlotSquaredListener;
        //       as anytime someone gets trusted (or gets removed trusted), anytime a plot
        //       is merged or deleted etc.. we have to conduct a health check of the sim selection
        //       on every sim selection, and every selection of simulations currently on-going.
        val selBounds = sel.bounds
        val blockFlagArr = BitArray(selBounds.volumeLong())
        blockFlagArr.zeroOut()
        val plotsWhereSimAllowed = PlotQuery
            .newQuery()
            .inWorld(sel.world.name)
            .withMember(player.uniqueId)
            .asList()
            .filter { player.uniqueId in it.trusted || player.uniqueId in it.owners }

        for (plot in plotsWhereSimAllowed) {
            val cuboidRegions = plot.regions
            for (cuboidRegion in cuboidRegions) {
                val boundaryToTest = cuboidRegion.toIntBounds()

                if (!boundaryToTest.intersects(selBounds)) continue
                // Iterate only through the blocks in common
                val commonBound = boundaryToTest.getClampedInside(selBounds)
                for (pos in commonBound.iterYzx()) {
                    val arrIdx = selBounds.posToYzxIdx(pos)
                    blockFlagArr[arrIdx.toLong()] = true
                }
            }
        }
        //println("Checking sim sel legal spot took ${checkStart.elapsedNow()}")
        return blockFlagArr.isAllOne()
    }

    fun isSimSelInLegalSpot_assume2dPlots(sel: SimSelection, player: Player): Boolean {
        TimeSource.Monotonic.markNow()
        // # Same as isSimSelInLegalSpot() but assuming the plots take up entire XZ columns
        // # of the world, allowing us to only use a 2d array instead of a 3d one.
        // # Performance improvement by H times; where H is the height of selection (W*H*L)
        // TODO: introduce some early-stopping somehow but that can be tricky with mega plots
        //       as I get them multiple times in the query, leading to us checking the same
        //       boundaries multiple times.
        fun IntBoundary.flatten() = IntBoundary.new(a.withY(0), b.withY(1))
        val selBounds = sel.bounds
        val selBoundsFlat = selBounds.flatten()
        val blockFlagArr = BitArray(selBoundsFlat.volumeLong())
        blockFlagArr.zeroOut()
        val plotsWhereSimAllowed = PlotQuery
            .newQuery()
            .inWorld(sel.world.name)
            .withMember(player.uniqueId)
            .asList()
            .filter { player.uniqueId in it.trusted || player.uniqueId in it.owners }

        for (plot in plotsWhereSimAllowed) {
            val cuboidRegions = plot.regions
            // Early returning is possible for plots that aren't megaplots
            // but this optimisation wasn't done yet
            for (cuboidRegion in cuboidRegions) {
                val boundaryToTest = cuboidRegion.toIntBounds().flatten()

                if (!boundaryToTest.intersects(selBoundsFlat)) continue
                // Iterate only through the blocks in common
                val commonBound = boundaryToTest.getClampedInside(selBoundsFlat)
                for (pos in commonBound.iterYzx()) {
                    val arrIdx = selBoundsFlat.posToYzxIdx(pos)
                    blockFlagArr[arrIdx.toLong()] = true
                }
            }
        }
        //println("Checking sim sel legal spot took ${checkStart.elapsedNow()}")
        return blockFlagArr.isAllOne()
    }
}
