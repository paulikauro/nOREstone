package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection
import com.sloimay.smath.vectors.IVec3
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

private const val NULL_STR = "null"
private const val FIELD_SEP = "|"
fun IVec3.nsSerialise() = "$x $y $z"
fun IVec3.Companion.nsDeserialize(s: String) = fromArray(s.split(" ").map { it.toInt() }.toIntArray())
fun SimSelection.nsSerialize() =
    "${pos1?.nsSerialise()} $FIELD_SEP ${pos2?.nsSerialise()} $FIELD_SEP ${world?.uid?.toString()}"

fun SimSelection.Companion.nsDeserialize(s: String): SimSelection {
    val (pos1Str, pos2Str, worldUuidStr) = s.split(FIELD_SEP).map { it.trim() }
    val pos1 = if (pos1Str != NULL_STR) IVec3.nsDeserialize(pos1Str) else null
    val pos2 = if (pos2Str != NULL_STR) IVec3.nsDeserialize(pos2Str) else null
    val world = if (worldUuidStr != NULL_STR) Bukkit.getWorld(UUID.fromString(worldUuidStr)) else null

    return SimSelection.newValidOrEmpty(world, pos1, pos2)
}

fun ItemStack.nsSerialize(): String {
    val s = ByteArrayOutputStream().use { baos ->
        BukkitObjectOutputStream(baos).use { dataOutput ->
            dataOutput.writeObject(this)
        }
        Base64Coder.encodeLines(baos.toByteArray())
    }
    return s
}

fun nsDeserializeItemStack(s: String): ItemStack {
    val itemStack = ByteArrayInputStream(Base64Coder.decodeLines(s)).use { bais ->
        BukkitObjectInputStream(bais).use { dataInput ->
            dataInput.readObject() as ItemStack
        }
    }
    return itemStack
}
