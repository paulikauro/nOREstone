package com.sloimay.norestone

import com.sk89q.worldedit.bukkit.BukkitAdapter
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
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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


    fun compileSim(player: Player, backendId: String, compileFlags: List<String>): Result<Unit, String> {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.compile) { return Result.err(it) }
        if (noreStone.doesPlayerSimExists(player)) {
            return Result.err("A simulation is still active, please clear it before trying to" +
                    " compile a new one.")
        }

        val selValidationRes = noreStone.simSelValidator.validateForCompilation(player)
        if (selValidationRes.isErr()) return selValidationRes

        if (!RS_BACKEND_INFO.any { it.backendId == backendId }) {
            return Result.err("Unknown backend of id '${backendId}'.")
        }

        val sesh = noreStone.getSession(player)
        val sel = sesh.sel
        val simWorldBounds = sel.bounds()!!
        val simWorld = sel.world!!
        // The origin of the simulation inside the standalone mcvolume is at 0,0
        val volBounds = simWorldBounds.move(-simWorldBounds.a)

        // TODO: set chunk bit size to 4
        val vol = McVolume.new(volBounds.a, volBounds.b, chunkBitSize = 5)
        val chunksDoneForTileEntities = hashSetOf<Chunk>()
        for (worldPos in simWorldBounds.iterYzx()) {
            val block = simWorld.getBlockAt(worldPos.x, worldPos.y, worldPos.z)
            if (block.type == Material.AIR) continue

            // Place block state
            val volPos = worldPos - simWorldBounds.a
            vol.setBlockStateStr(volPos, block.blockData.asString)

            // Place tile entity data
            val chunkHere = simWorld.getChunkAt(block)
            if (chunkHere !in chunksDoneForTileEntities) {
                chunksDoneForTileEntities.add(chunkHere)

                for (tileEntityBukkitBlockState in chunkHere.tileEntities) {
                    val teWorldPos = tileEntityBukkitBlockState.location.blockPos()
                    if (!simWorldBounds.posInside(teWorldPos)) continue

                    // Transfer NBT to the mcvolume
                    NBT.get(tileEntityBukkitBlockState) { nbt ->
                        val querzNbt = nbtApiToQuerzNbt(nbt as NBTCompound)
                        vol.setTileData(volPos, querzNbt)
                    }
                }
            }
        }

        // Make the backend, do it differently depending on which one it is
        val backendInfo = RS_BACKEND_INFO.first { it.backendId == backendId }
        val rsBackend: RedstoneSimBackend
        if (backendInfo.backendId == RS_BACKENDS.shrimple.backendId) {
            // Shrimple compilation
            rsBackend = ShrimpleBackend.new(vol, volBounds)
        } else {
            return Result.err("Unknown backend of id '${backendId}'.")
        }

        // Make a new NsSim
        val nsSim = NsSim(noreStone, sesh.sel, rsBackend, simWorldBounds.a, 20)

        // Add it to the manager
        sesh.nsSim = nsSim
        noreStone.simManager.requestSimAdd(nsSim)

        return Result.ok(Unit)
    }

}