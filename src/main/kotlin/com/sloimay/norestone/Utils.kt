package com.sloimay.norestone

import com.sk89q.worldedit.math.BlockVector3
import com.sloimay.mcvolume.IntBoundary
import com.sloimay.smath.vectors.DVec3
import com.sloimay.smath.vectors.IVec3
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player


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


/*class Result<O, E> private constructor(val o: O?, val e: E?) {

    companion object {

        fun <T> err(): Result<T, Any> {

        }

    }

}*/




fun DVec3.toLoc(w: World) = Location(w, x, y, z)
fun IVec3.toLoc(w: World) = this.toDVec3().toLoc(w)

fun IVec3.toBlockVector3(): BlockVector3 = BlockVector3.at(x, y, z)

fun BlockVector3.toIVec3() = IVec3(x, y, z)

fun Location.blockPos() = IVec3(blockX, blockY, blockZ)
fun Location.toPlotSquaredLoc(): com.plotsquared.core.location.Location {
    return com.plotsquared.core.location.Location.at(world!!.name, blockPos().toBlockVector3(), yaw, pitch)
}

fun IVec3.toPsqLoc(w: World) = toLoc(w).toPlotSquaredLoc()

fun IVec3.eProdLong() = x.toLong() * y.toLong() * z.toLong()

fun Boolean.toInt() = if (this) 1 else 0

/**
 * a and b inclusive. (IntBoundary.new has b exclusive)
 */
fun IntBoundary.Companion.newInclusive(a: IVec3, b: IVec3): IntBoundary {
    val aReal = a.min(b)
    val bReal = a.max(b)
    return IntBoundary.new(aReal, bReal + 1)
}

fun mmComp(miniMsg: String): Component {
    return MiniMessage.miniMessage().deserialize(miniMsg)
}




inline fun playerFeedbackRequirePerm(player: Player, permNode: String, ifNoPerm: () -> Unit = {}) {
    if (!player.hasPermission(permNode)) {
        NORESTONE?.messenger?.err(player, mmComp("Lacking permission: '$permNode'."))
        ifNoPerm()
    }
}