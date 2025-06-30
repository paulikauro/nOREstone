package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

private object NsPlayerDataTable : Table() {
    val playerUuid = uuid("id").uniqueIndex()
    val selWandItem = text("sel_wand_item").default(defaultSimSelWand().nsSerialize())
    val playerSimSel = text("simulation_selection").default(SimSelection.empty().nsSerialize())
}

class NsDbPlayerData(
    val uuid: UUID,
    val selWantItem: ItemStack,
    val simSel: SimSelection,
)

class NorestoneDb(
    dbFile: File,
) {
    private val db = Database.connect("jdbc:sqlite:$dbFile", "org.sqlite.JDBC")

    init {
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(NsPlayerDataTable)
        }
    }

    /**
     * Costly function, use sparingly
     */
    fun setPlayerSimSel(p: Player, sel: SimSelection) {
        updatePlayerEntry(p, NsPlayerDataTable.playerSimSel, sel.nsSerialize())
    }

    /**
     * Costly function, use sparingly
     */
    fun setPlayerSelWand(p: Player, itemStack: ItemStack) {
        updatePlayerEntry(p, NsPlayerDataTable.selWandItem, itemStack.nsSerialize())
    }

    /**
     * Costly function, use sparingly
     */
    private fun <T> updatePlayerEntry(p: Player, col: Column<T>, value: T) {
        transaction(db) {
            val pRow = getPlayerRowOrCreate(p.uniqueId)
            val uuid = pRow[NsPlayerDataTable.playerUuid]

            NsPlayerDataTable.update({ NsPlayerDataTable.playerUuid eq uuid }) {
                it[col] = value
            }
        }
    }

    /**
     * Retrieves the data associated to this player from the DB.
     * As it queries the DB, use very sparingly (like not in loops lmao)
     */
    fun retrievePlayerData(uuid: UUID): NsDbPlayerData {
        return transaction(db) {
            val row = getPlayerRowOrCreate(uuid)
            val outUuid = row[NsPlayerDataTable.playerUuid] // keeping this in out of consistency
            val selWandItem = nsDeserializeItemStack(row[NsPlayerDataTable.selWandItem])
            val playerSimSel = SimSelection.nsDeserialize(row[NsPlayerDataTable.playerSimSel])

            NsDbPlayerData(
                outUuid,
                selWandItem,
                playerSimSel,
            )
        }
    }

    private fun getPlayerRowOrCreate(uuid: UUID): ResultRow {
        var resultRow: ResultRow? = null

        transaction(db) {
            val result = NsPlayerDataTable
                .select { NsPlayerDataTable.playerUuid eq uuid }
                .singleOrNull()

            if (result == null) {
                createPlayerEntry(uuid)
            }

            resultRow = NsPlayerDataTable
                .select { NsPlayerDataTable.playerUuid eq uuid }
                .single()
        }

        return resultRow!!
    }

    private fun createPlayerEntry(uuid: UUID) {
        transaction(db) {
            NsPlayerDataTable.insert {
                it[playerUuid] = uuid
            }
        }
    }
}
