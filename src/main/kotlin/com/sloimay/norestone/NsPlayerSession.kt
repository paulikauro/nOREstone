package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection
import com.sloimay.norestone.simulation.NsSim
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class NsPlayerSession(val player: Player, val noreStone: NOREStone) {

    val db = noreStone.db

    var sel: SimSelection
    var nsSim: NsSim?
    var selWand: ItemStack

    init {
        val playerData = db.retrievePlayerData(player.uniqueId)

        println("Retrieved sel: ${playerData.simSel.nsSerialize()}")
        println("Retrieved selwand: ${playerData.selWantItem.type.name}")

        sel = playerData.simSel
        selWand = playerData.selWantItem
        nsSim = null
    }


    /**
     * Simulation destroy routine
     */
    fun endSim() {
        nsSim?.endingSequence()
        nsSim = null
    }


    fun end() {
        endSim()

        // Write player data that changed to the db
        println("Saving sel: ${sel.nsSerialize()}")
        db.setPlayerSimSel(player, sel)
        println("Saving selwand: ${selWand.type.name}")
        db.setPlayerSelWand(player, selWand)
    }

}