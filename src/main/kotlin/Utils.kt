package com.sloimay.norestone

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sloimay.smath.geometry.boundary.IntBoundary
import com.sloimay.smath.vectors.DVec3
import com.sloimay.smath.vectors.IVec3
import com.sloimay.smath.vectors.ivec3
import de.tr7zw.nbtapi.NBTCompound
import de.tr7zw.nbtapi.NBTType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.querz.nbt.tag.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

sealed interface Result<out O, out E> {
    data class Ok<T>(val value: T) : Result<T, Nothing>
    data class Err<T>(val err: T) : Result <Nothing, T>
    companion object {
        fun ok(): Result<Unit, Nothing> = ok(Unit)
        fun <T> ok(v: T): Result<T, Nothing> = Ok(v)
        fun <T> err(v: T): Result<Nothing, T> = Err(v)
    }
}

class BitArray(val size: Long) {
    val words = IntArray(ceil(size.toDouble() / 32.0).toInt()) { 0 }
    fun zeroOut() = words.fill(0)
    fun isAllOne(): Boolean {
        for (idx in 0 until words.size - 1) {
            if (words[idx] != -1) {
                return false
            }
        }
        // Special case for the last word
        for (idx in (words.lastIndex shl 5) until size) {
            if (this[idx] == false) {
                return false
            }
        }

        return true
    }

    operator fun set(idx: Long, value: Boolean) {
        require(idx in 0 until size) { "Bit array out of bounds." }
        val wordIdx = (idx ushr 5).toInt()
        val idxInWord = (idx and 0x1F).toInt()
        val valueInt = value.toInt()
        val bit = (valueInt shl idxInWord)
        words[wordIdx] = (words[wordIdx] and bit.inv()) or bit
    }

    operator fun get(idx: Long): Boolean {
        require(idx in 0 until size) { "Bit array out of bounds." }
        val wordIdx = (idx ushr 5).toInt()
        val idxInWord = (idx and 0x1F).toInt()
        return ((words[wordIdx] ushr idxInWord) and 1) == 1
    }
}

/**
 * A thread that stays alive, sleeping, until you call it to do work for you,
 * then will go back sleeping when it's done.
 */
class BurstWorkThread {
    private val lock = Object()
    private var paused = true
    private var shouldBeKilled = false
    private var work: (() -> Unit)? = null
    private val thread = Thread {
        while (!shouldBeKilled) {
            // waiting if we paused the thread
            synchronized(lock) {
                while ((paused || work == null) && !shouldBeKilled) {
                    lock.wait()
                }
            }
            // do the work if we have some and we don't need to kill the thread
            if (!shouldBeKilled && work != null) {
                try {
                    val w = work
                    if (w != null) w()
                } catch (e: Exception) {
                    println("Error in thread: ${e.message}")
                }
                // reset and go back to sleep
                synchronized(lock) {
                    work = null
                    paused = true
                }
            }
        }
    }

    init {
        thread.start()
    }

    fun work(block: () -> Unit) {
        synchronized(lock) {
            work = block
            // resume thread
            paused = false
            lock.notify()
        }
    }

    fun kill() {
        shouldBeKilled = true
        synchronized(lock) {
            lock.notify() // so it checks shouldBeKilled
        }
        thread.join()
    }
}

fun DVec3.toLoc(w: World) = Location(w, x, y, z)
fun BlockVector3.toIVec3() = IVec3(x, y, z)
fun Location.blockPos() = IVec3(blockX, blockY, blockZ)
fun Location.toPlotSquaredLoc(): com.plotsquared.core.location.Location {
    return com.plotsquared.core.location.Location.at(world!!.name, blockPos().toBlockVector3(), yaw, pitch)
}

fun IVec3.toLoc(w: World) = this.toDVec3().toLoc(w)
fun IVec3.toBlockVector3(): BlockVector3 = BlockVector3.at(x, y, z)
fun IVec3.toPsqLoc(w: World) = toLoc(w).toPlotSquaredLoc()
fun IVec3.eProdLong() = x.toLong() * y.toLong() * z.toLong()
fun IVec3.Companion.fromBlock(b: Block) = ivec3(b.x, b.y, b.z)
fun IVec3.blockPosStr() = "($x, $y, $z)"
fun Long.clamp(lo: Long, hi: Long) = max(min(this, hi), lo)
fun Int.clamp(lo: Int, hi: Int) = max(min(this, hi), lo)
fun Boolean.toInt() = if (this) 1 else 0

/**
 * a and b inclusive. (IntBoundary.new has b exclusive)
 */
fun IntBoundary.Companion.newInclusive(a: IVec3, b: IVec3): IntBoundary {
    val aReal = a.min(b)
    val bReal = a.max(b)
    return IntBoundary.new(aReal, bReal + 1)
}

fun Double.round(prec: Int) = round(this * prec.toDouble()) / prec.toDouble()
fun IntBoundary.volumeLong() = (b - a).eProdLong()
fun CuboidRegion.toIntBounds() = IntBoundary.new(minimumPoint.toIVec3(), maximumPoint.toIVec3() + 1)

fun formatTps(tps: Double): String {
    val s = if (tps <= 100.0) {
        "%.2f".format(Locale.ENGLISH, tps)
    } else {
        "%.1f".format(Locale.ENGLISH, tps)
    }
    return s.trimEnd('0').removeSuffix(".")
}

fun tryParseCompileFlags(flagsStr: String, backendInfo: RsBackendInfo): Result<List<String>, String> {
    val availableFlags = backendInfo.compileFlags.map { it.id }
    val rawFlags = flagsStr.split(" ")
    val flags = mutableListOf<String>()
    for (rawFlag in rawFlags) {
        if (!rawFlag.startsWith("-")) {
            return Result.err("Compile flags must start with a dash but got '${rawFlag}'.")
        }
        val flag = rawFlag.removePrefix("-")
        if (flag !in availableFlags) {
            return Result.err(
                "Unknown compile flag '$flag' for backend '${backendInfo.backendId}'." +
                        " Available compile flags: ${availableFlags.joinToString { "-$it" }}. "
            )
        }

        flags.add(flag)
    }

    return Result.ok(flags)
}

inline fun playerFeedbackRequirePerm(player: Player, permNode: String, ifNoPerm: (String) -> Unit = {}) {
    if (!player.hasPermission(permNode)) {
        ifNoPerm("Lacking permission: '$permNode'.")
    }
}

fun nbtApiToQuerzNbt(nbtApiNbt: NBTCompound): CompoundTag {
    fun nbtApiCompoundToQuerzNbtCompound(nbtApiCompound: NBTCompound): CompoundTag {
        val querzCompound = CompoundTag()

        for (key in nbtApiCompound.keys) {
            val t = nbtApiCompound.getType(key)!!

            when (t) {
                NBTType.NBTTagEnd -> {}
                NBTType.NBTTagByte -> querzCompound.putByte(key, nbtApiCompound.getByte(key))
                NBTType.NBTTagShort -> querzCompound.putShort(key, nbtApiCompound.getShort(key))
                NBTType.NBTTagInt -> querzCompound.putInt(key, nbtApiCompound.getInteger(key))
                NBTType.NBTTagLong -> querzCompound.putLong(key, nbtApiCompound.getLong(key))
                NBTType.NBTTagFloat -> querzCompound.putFloat(key, nbtApiCompound.getFloat(key))
                NBTType.NBTTagDouble -> querzCompound.putDouble(key, nbtApiCompound.getDouble(key))
                NBTType.NBTTagByteArray -> querzCompound.putByteArray(key, nbtApiCompound.getByteArray(key))
                NBTType.NBTTagString -> querzCompound.putString(key, nbtApiCompound.getString(key))
                NBTType.NBTTagIntArray -> querzCompound.putIntArray(key, nbtApiCompound.getIntArray(key))
                NBTType.NBTTagLongArray -> querzCompound.putLongArray(key, nbtApiCompound.getLongArray(key))
                NBTType.NBTTagCompound -> {
                    querzCompound.put(key, nbtApiCompoundToQuerzNbtCompound(nbtApiCompound.getCompound(key)!!))
                }

                NBTType.NBTTagList -> {
                    val listType = nbtApiCompound.getListType(key)!!
                    if (listType == NBTType.NBTTagEnd) break
                    var listTag: ListTag<*>? = null
                    when (listType) {
                        NBTType.NBTTagEnd -> error("Unreachable")
                        NBTType.NBTTagByte -> {}
                        NBTType.NBTTagShort -> {}
                        NBTType.NBTTagInt -> {
                            listTag = ListTag(IntTag::class.java)
                                .also { list -> nbtApiCompound.getIntegerList(key).forEach { t -> list.addInt(t) } }
                        }

                        NBTType.NBTTagLong -> {
                            listTag = ListTag(LongTag::class.java)
                                .also { list -> nbtApiCompound.getLongList(key).forEach { t -> list.addLong(t) } }
                        }

                        NBTType.NBTTagFloat -> {
                            listTag = ListTag(FloatTag::class.java)
                                .also { list -> nbtApiCompound.getFloatList(key).forEach { t -> list.addFloat(t) } }
                        }

                        NBTType.NBTTagDouble -> {
                            listTag = ListTag(DoubleTag::class.java)
                                .also { list -> nbtApiCompound.getDoubleList(key).forEach { t -> list.addDouble(t) } }
                        }

                        NBTType.NBTTagByteArray -> {}
                        NBTType.NBTTagString -> {
                            listTag = ListTag(StringTag::class.java)
                                .also { list -> nbtApiCompound.getStringList(key).forEach { t -> list.addString(t) } }
                        }

                        NBTType.NBTTagList -> {}
                        NBTType.NBTTagCompound -> {
                            listTag = ListTag(CompoundTag::class.java)
                                .also { list ->
                                    try {
                                        nbtApiCompound.getCompoundList(key).forEach { t ->
                                            list.add(nbtApiCompoundToQuerzNbtCompound(t as NBTCompound))
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                        }

                        NBTType.NBTTagIntArray -> {
                            listTag = ListTag(IntArrayTag::class.java)
                                .also { list ->
                                    nbtApiCompound.getIntArrayList(key).forEach { t -> list.addIntArray(t) }
                                }
                        }

                        NBTType.NBTTagLongArray -> {}
                    }

                    querzCompound.put(key, listTag)
                }
            }
        }

        return querzCompound
    }

    return nbtApiCompoundToQuerzNbtCompound(nbtApiNbt)
}
