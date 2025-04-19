package com.sloimay.norestone

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sloimay.mcvolume.IntBoundary
import com.sloimay.smath.vectors.DVec3
import com.sloimay.smath.vectors.IVec3
import com.sloimay.smath.vectors.ivec3
import de.tr7zw.nbtapi.NBTCompound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import kotlin.math.ceil


private class Ok<T>(val v: T) : Result<T, Nothing>() {
    override fun isErr() = false
    override fun isOk() = true

    override fun getOk() = v

    override fun getErr(): Nothing {
        error("Tried getting the error value from an ok result.")
    }
}

private class Err<T>(val v: T) : Result<Nothing, T>() {

    override fun isErr() = true
    override fun isOk() = false

    override fun getOk(): Nothing {
        error("Tried getting the ok value from an error result.")
    }

    override fun getErr() = v
}

abstract class Result<out O, out E> {
    abstract fun isErr(): Boolean
    abstract fun isOk(): Boolean

    abstract fun getOk(): O
    abstract fun getErr(): E

    companion object {
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


fun Boolean.toInt() = if (this) 1 else 0

/**
 * a and b inclusive. (IntBoundary.new has b exclusive)
 */
fun IntBoundary.Companion.newInclusive(a: IVec3, b: IVec3): IntBoundary {
    val aReal = a.min(b)
    val bReal = a.max(b)
    return IntBoundary.new(aReal, bReal + 1)
}

fun IntBoundary.volumeLong() = (b - a).eProdLong()


fun CuboidRegion.toIntBounds() = IntBoundary.new(minimumPoint.toIVec3(), maximumPoint.toIVec3() + 1)

fun mmComp(miniMsg: String): Component {
    return MiniMessage.miniMessage().deserialize(miniMsg)
}



fun nbtApiToQuerzNbt(nbtApiNbt: NBTCompound) {

}





inline fun playerFeedbackRequirePerm(player: Player, permNode: String, ifNoPerm: (String) -> Unit = {}) {
    if (!player.hasPermission(permNode)) {
        ifNoPerm("Lacking permission: '$permNode'.")
    }
}
