package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

class NsPlayerSession(val player: Player, val noreStone: NOREStone) {
    val db = noreStone.db
    var sel: SimSelection
    var selWand: ItemStack

    init {
        val playerData = db.retrievePlayerData(player.uniqueId)

        noreStone.logger.log(Level.INFO, "Retrieved selection: ${playerData.simSel.nsSerialize()}")
        noreStone.logger.log(Level.INFO, "Retrieved selwand: ${playerData.selWantItem.type.name}")

        sel = playerData.simSel
        selWand = playerData.selWantItem
    }

    fun end() {
        // End simulation
        noreStone.simManager.getPlayerSim(player.uniqueId)?.let {
            noreStone.simManager.requestSimRemove(player.uniqueId, it)
        }
        // Write player data that changed to the db
        noreStone.logger.log(Level.INFO, "Saving selelection: ${sel.nsSerialize()}")
        db.setPlayerSimSel(player, sel)
        noreStone.logger.log(Level.INFO, "Saving selwand: ${selWand.type.name}")
        db.setPlayerSelWand(player, selWand)
    }
}
