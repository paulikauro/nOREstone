package com.sloimay.norestone

import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.regions.CuboidRegion
import com.sloimay.mcvolume.McVolume
import com.sloimay.nodestonecore.simulation.initinterfaces.AreaRepresentationInitialized
import com.sloimay.nodestonecore.simulation.initinterfaces.CompileFlagInitialized
import com.sloimay.norestone.Result.Err
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.selection.SimSelection
import com.sloimay.norestone.simulation.NsSim
import com.sloimay.smath.geometry.boundary.IntBoundary
import de.tr7zw.nbtapi.NBT
import de.tr7zw.nbtapi.NBTCompound
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.TimeSource

class NsPlayerInteractions(val noreStone: NOREStone) {
    fun select(player: Player): Result<String, String> {
        val wePlayer = BukkitAdapter.adapt(player)
        val localSession = noreStone.worldEdit.sessionManager.get(wePlayer)
        // TODO: possibly force selection to be from player's current world? (maybe not)
        // (can be done with getSelection(world))
        val weSel = try {
            localSession.selection
        } catch (_: IncompleteRegionException) {
            return Result.err("Make a WorldEdit selection first")
        } as? CuboidRegion ?: return Result.err("Your WorldEdit selection must be a cuboid selection")
        // world is always non-null when you get it from LocalSession#getSelection
        val sel = SimSelection(BukkitAdapter.adapt(weSel.world!!), weSel.pos1.toIVec3(), weSel.pos2.toIVec3())
        // TODO: save the selection here (and in clearSelection)
        noreStone.getSession(player).sel = sel
        return Result.ok("Succesfully set your simulation selection from your WorldEdit selection.")
    }

    fun clearSelection(player: Player) {
        noreStone.getSession(player).sel = null
    }

    /**
     * I tried to make it asynchronous but couldn't figure out how.
     * Chunk Snapshots are very bad for this instance; if anyone has any idea then please do tell lol
     */
    fun compileSim(player: Player, backendId: String, compileFlags: List<String>): Result<Duration, String> {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.compile) { return Result.err(it) }
        if (noreStone.doesPlayerSimExists(player)) {
            return Result.err(
                "Your simulation is still active, please clear it before trying to compile a new one."
            )
        }
        val sesh = noreStone.getSession(player)
        // TODO: this is a bad error message
        val sel = sesh.sel ?: return Result.err("You must have a selection to compile. Select with /sim select")
        val selValidationRes = noreStone.simSelValidator.validateForCompilation(player, sel)
        if (selValidationRes is Err) return selValidationRes

        val backendInfo = RS_BACKEND_INFO.firstOrNull { it.backendId == backendId }
            ?: return Result.err("Unknown backend of id '${backendId}'.")

        val compileStartTime = TimeSource.Monotonic.markNow()
        val simInit = backendInfo.initialiserProvider()
        val simWorldBounds = sel.bounds
        // ## ==== Initialize depending on how this simulation wants to be initialized.
        // Init with an area representation
        if (simInit is AreaRepresentationInitialized) {
            val simWorld = sel.world
            // The origin of the simulation inside the standalone mcvolume is at 0,0
            val volBounds = simWorldBounds.shift(-simWorldBounds.a)
            // # Instantiate vol
            val vol = McVolume.new(volBounds.a, volBounds.b, chunkBitSize = 4)
            // # Get blocks
            // TODO: optimisation idea:
            //         BFS over non-air blocks (tho idk how to make it substantially faster than just brute force)
            run {
                for (worldPos in simWorldBounds.iterYzx()) {
                    val block = simWorld.getBlockAt(worldPos.x, worldPos.y, worldPos.z)
                    if (block.type == Material.AIR) continue
                    // Place block state
                    val volPos = worldPos - simWorldBounds.a
                    vol.setBlockStateStr(volPos, block.blockData.asString)
                }
            }
            // # Get tile entities
            /**
             * TODO: look into using worldedit instead of NBTAPI to get the NBT.
             *       (use weWorld.getBlock().toBaseBlock().nbtData
             *       Also, converting WE NBT into Querz NBT sounds easier than converting
             *       from NBT API nbt.
             */
            run {
                val chunkGridWBounds = IntBoundary.new(
                    simWorldBounds.a.withY(0) shr 4,
                    ((simWorldBounds.b shr 4) + 1).withY(1)
                )
                for (chunkPos in chunkGridWBounds.iterYzx()) {
                    //println("chunk polled: $chunkPos")
                    val chunkHere = simWorld.getChunkAt(chunkPos.x, chunkPos.z, true)
                    for (teBukkitBs in chunkHere.tileEntities) {
                        val teWorldPos = teBukkitBs.location.blockPos()
                        if (!simWorldBounds.posInside(teWorldPos)) continue
                        // Transfer NBT to the mcvolume
                        NBT.get(teBukkitBs) { nbt ->
                            val teVolPos = teWorldPos - simWorldBounds.a
                            val querzNbt = nbtApiToQuerzNbt(nbt as NBTCompound)
                            vol.setTileData(teVolPos, querzNbt)
                        }
                    }
                }
            }

            simInit.withAreaRepresentation(vol, volBounds)
        }
        // Init with compile flags
        if (simInit is CompileFlagInitialized) {
            simInit.withCompileFlags(compileFlags)
        }
        // ## ====
        // Make the backend, do it differently depending on which one it is
        // The end goal for nodestone is to have a unified interface that removes the burden on the plugin
        // (or mod, or separate app) developer of implementing different compilation / interaction
        // logic for each simulation
        // Make a new NsSim
        val simBackend = try {
            simInit.finishInit()
        } catch (e: Exception) {
            return Result.err(e.toString()) // Better than nothing logged to the player
        }
        val nsSim = NsSim(noreStone, sel, simBackend, simWorldBounds.a, noreStone.simManager, 20.0)
        // Request addition of this sim
        noreStone.simManager.requestSimAdd(player.uniqueId, nsSim)
        // Just means the synced code was successful
        return Result.ok(compileStartTime.elapsedNow())
    }

    fun changeSimTps(p: Player, newTps: Double): Result<Unit, String> {
        playerFeedbackRequirePerm(p, NsPerms.Simulation.changeTps) { return Result.err(it) }
        val sim = noreStone.simManager.getPlayerSim(p.uniqueId)
        if (sim == null) {
            return Result.err("No simulation currently on-going.")
        }

        if (newTps < 0.0) {
            return Result.err("TPS cannot be negative.")
        }
        if (abs(newTps) == 0.0) {
            return Result.err("TPS cannot be 0.")
        }
        val maxSimTps = noreStone.getPlayerMaxSimTps(p).toDouble()
        // Bypass of max tps
        if (!p.hasPermission(NsPerms.Simulation.MaxTps.bypass)) {
            if (newTps > maxSimTps) {
                return Result.err("Your permissions do not permit you to go over $maxSimTps TPS.")
            }
        }

        sim.requestTpsChange(newTps)
        return Result.ok()
    }

    fun tickStep(p: Player, ticksStepped: Long): Result<Unit, String> {
        playerFeedbackRequirePerm(p, NsPerms.Simulation.step) { return Result.err(it) }
        val sim = noreStone.simManager.getPlayerSim(p.uniqueId)
        if (sim == null) {
            return Result.err("No simulation currently on-going.")
        }

        if (!sim.isFrozen()) {
            return Result.err("Cannot step while the simulation isn't frozen.")
        }

        if (ticksStepped < 0L) {
            return Result.err("Cannot step for a negative amount of ticks.")
        }
        if (ticksStepped == 0L) {
            return Result.err("Cannot step for 0 ticks.")
        }

        sim.requestStep(ticksStepped)

        return Result.ok()
    }
}
