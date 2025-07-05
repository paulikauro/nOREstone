package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection
import com.sloimay.smath.vectors.IVec3
import org.bukkit.Bukkit
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.io.File
import java.util.*

private object PlayerSelections : Table() {
    val playerUuid = uuid("id").uniqueIndex()
    val playerSimSel = text("simulation_selection")
}

class NorestoneDb(
    dbFile: File,
) {
    private val db = Database.connect("jdbc:sqlite:$dbFile", "org.sqlite.JDBC")

    init {
        transaction(db) {
            SchemaUtils.create(PlayerSelections)
        }
    }

    fun setPlayerSimSel(uuid: UUID, sel: SimSelection?) {
        transaction(db) {
            if (sel == null) {
                PlayerSelections.deleteWhere { PlayerSelections.playerUuid eq uuid }
            } else {
                PlayerSelections.upsert {
                    it[PlayerSelections.playerUuid] = uuid
                    it[PlayerSelections.playerSimSel] = sel.nsSerialize()
                }
            }
        }
    }

    fun getPlayerSimSel(uuid: UUID): SimSelection? = transaction(db) {
        PlayerSelections
            .select(PlayerSelections.playerSimSel)
            .where { PlayerSelections.playerUuid eq uuid }
            .singleOrNull()?.let { SimSelection.nsDeserialize(it[PlayerSelections.playerSimSel]) }
    }
}

private const val FIELD_SEP = "|"
private fun IVec3.nsSerialise() = "$x $y $z"
private fun IVec3.Companion.nsDeserialize(s: String) = fromArray(s.split(" ").map { it.toInt() }.toIntArray())

private fun SimSelection.nsSerialize() =
    "${pos1.nsSerialise()} $FIELD_SEP ${pos2.nsSerialise()} $FIELD_SEP ${world.uid}"

private fun SimSelection.Companion.nsDeserialize(s: String): SimSelection? {
    val (pos1Str, pos2Str, worldUuidStr) = s.split(FIELD_SEP).map { it.trim() }
    return SimSelection(
        // TODO: return error instead of null here?
        // not a big deal, but might be a bit nicer for the user
        world = Bukkit.getWorld(UUID.fromString(worldUuidStr)) ?: return null,
        pos1 = IVec3.nsDeserialize(pos1Str),
        pos2 = IVec3.nsDeserialize(pos2Str),
    )
}
