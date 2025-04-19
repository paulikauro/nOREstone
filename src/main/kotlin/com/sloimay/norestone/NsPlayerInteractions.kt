package com.sloimay.norestone

import com.sloimay.mcvolume.IntBoundary
import com.sloimay.mcvolume.McVolume
import com.sloimay.nodestonecore.backends.RedstoneSimBackend
import com.sloimay.nodestonecore.backends.shrimple.ShrimpleBackend
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.selection.SimSelection
import com.sloimay.norestone.simulation.NsSim
import com.sloimay.smath.vectors.IVec3
import de.tr7zw.nbtapi.NBT
import de.tr7zw.nbtapi.NBTCompound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.time.TimeSource

class NsPlayerInteractions(val noreStone: NOREStone) {

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

    fun bindSimSelWand(p: Player, item: ItemStack): Result<String, String> {
        playerFeedbackRequirePerm(p, NsPerms.Simulation.Selection.changeSelWand) { return Result.err(it) }

        if (item.type.isAir) {
            return Result.err("Cannot bind simulation selection wand to empty.")
        }

        noreStone.getSession(p).selWand = item

        return Result.ok("Successfully bind simulation selection wand to " +
                "'${MiniMessage.miniMessage().serialize(Component.translatable(item.type.translationKey))}'.")
    }

    /**
     * If result is err, that means the synced part of the compilation wasn't successful.
     * Async results are logged later
     */
    fun compileSim(player: Player, backendId: String, compileFlags: List<String>, asyncLogToPlayer: Boolean): Result<Unit, String> {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.compile) { return Result.err(it) }
        if (noreStone.doesPlayerSimExists(player)) {
            return Result.err("A simulation is still active, please clear it before trying to" +
                    " compile a new one.")
        }

        val compileStartTime = TimeSource.Monotonic.markNow()

        val selValidationRes = noreStone.simSelValidator.validateForCompilation(player)
        if (selValidationRes.isErr()) return Result.err(selValidationRes.getErr())

        if (!RS_BACKEND_INFO.any { it.backendId == backendId }) {
            return Result.err("Unknown backend of id '${backendId}'.")
        }

        val sesh = noreStone.getSession(player)
        val sel = sesh.sel
        val simWorldBounds = sel.bounds()!!
        val simWorld = sel.world!!
        // The origin of the simulation inside the standalone mcvolume is at 0,0
        val volBounds = simWorldBounds.move(-simWorldBounds.a)

        // # Get chunk snapshots for async block access
        // 2d array of chunk snapshots
        val chunkGridWBounds = IntBoundary.new(
            simWorldBounds.a.withY(0) shr 4,
            ((simWorldBounds.b + 1) shr 4).withY(1)
        )
        println("sim world bounds $simWorldBounds")
        println("chunk grid world bounds $chunkGridWBounds")
        val chunkSnaps = mutableListOf<ChunkSnapshot>()
        for (chunkPos in chunkGridWBounds.iterYzx()) {
            val wp = chunkPos shl 4
            val c = simWorld
                .getChunkAt(wp.x, wp.z, true)
                .getChunkSnapshot(true, false, false)
            println("got chunk snap at $wp")
            chunkSnaps.add(c)
        }

        // # Get NBT on the main thread (can't access nbt from chunk snapshots
        // TODO: when im sure the simulations are working, set chunk bit size to 4
        val t = TimeSource.Monotonic.markNow()
        val vol = McVolume.new(volBounds.a, volBounds.b, chunkBitSize = 5)
        for (chunkPos in chunkGridWBounds.iterYzx()) {
            val wp = chunkPos shl 4
            val chunkHere = simWorld.getChunkAt(wp.x, wp.z, true)
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
        println("getting nbt time: ${t.elapsedNow()}")


        noreStone.server.scheduler.runTaskAsynchronously(noreStone, Runnable {

            for (worldPos in simWorldBounds.iterYzx()) {
                val chunkPos = worldPos shr 4
                val chunkSnapshot = chunkSnaps[chunkGridWBounds.posToYzxIdx(chunkPos)]
                val blockType = chunkSnapshot.getBlockType(worldPos.x, worldPos.y, worldPos.z)
                if (blockType == Material.AIR) continue

                val blockData = chunkSnapshot.getBlockData(worldPos.x, worldPos.y, worldPos.z)

                // Place block state
                val volPos = worldPos - simWorldBounds.a
                vol.setBlockStateStr(volPos, blockData.asString)
            }

            // Make the backend, do it differently depending on which one it is
            // The end goal for nodestone is to have a unified interface that removes the burden on the plugin
            // (or mod, or separate app) developer of implementing different compilation / interaction
            // logic for each simulation
            val backendInfo = RS_BACKEND_INFO.first { it.backendId == backendId }
            val rsBackend: RedstoneSimBackend

            if (backendInfo.backendId == RS_BACKENDS.shrimple.backendId) {
                // Shrimple compilation
                rsBackend = ShrimpleBackend.new(vol, volBounds, compileFlags)
            } else {
                if (asyncLogToPlayer) {
                    noreStone.syncedWorker.addWork {
                        player.nsErr("Unknown backend of id '${backendId}'.")
                    }
                }
                return@Runnable
            }

            // Make a new NsSim
            val nsSim = NsSim(noreStone, sesh.sel, rsBackend, simWorldBounds.a, 20)

            // Add it to the manager
            noreStone.simManager.requestSimAdd(player.uniqueId, nsSim)

            if (asyncLogToPlayer) {
                val compileTime = compileStartTime.elapsedNow()
                player.nsInfo("Backend successfully compiled in $compileTime.")
            }
        })

        // Just means the synced code was successful
        return Result.ok()
    }

}