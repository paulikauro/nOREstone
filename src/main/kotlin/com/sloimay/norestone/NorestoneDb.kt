package com.sloimay.norestone

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File



object PlayerData : Table() {
    val uuid = uuid("id").uniqueIndex()
}



class NorestoneDb(
    dbFile: File
) {
    private val db = Database.connect("jdbc:sqlite:$dbFile", "org.sqlite.JDBC")

    init {
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(PlayerData)
        }
    }


}