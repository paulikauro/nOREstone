package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

class NsPlayerSession(val player: Player, val noreStone: NOREStone) {
    val db = noreStone.db
    var sel: SimSelection? = db.getPlayerSimSel(player.uniqueId)

    fun end() {
        // End simulation
        noreStone.simManager.getPlayerSim(player.uniqueId)?.let {
            noreStone.simManager.requestSimRemove(player.uniqueId, it)
        }
        // Write player data that changed to the db
        db.setPlayerSimSel(player.uniqueId, sel)
    }
}
