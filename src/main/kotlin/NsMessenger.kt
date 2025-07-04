package com.sloimay.norestone

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

private fun mmComp(miniMsg: String): Component = MiniMessage.miniMessage().deserialize(miniMsg)

private val INFO_PREFIX = mmComp("<gray>[nOREstone]</gray> >> ")
private val ERR_PREFIX = mmComp("<red>[nOREstone] >> </red>")
private val WARN_PREFIX = mmComp("<color:#ff7700>[nOREstone] >> </color>")

fun Player.nsInfo(msg: String): Unit = sendMessage(INFO_PREFIX.append(mmComp(msg)))
fun Player.nsErr(msg: String): Unit = sendMessage(ERR_PREFIX.append(mmComp(msg)))
fun Player.nsWarn(msg: String): Unit = sendMessage(WARN_PREFIX.append(mmComp(msg)))

fun Player.nsInfoSimSelSetPos(pos: IVec3, idx: Int, simSel: SimSelection) {
    val bounds = simSel.bounds()
    var msg = "Set pos ${idx + 1} at (${pos.x}, ${pos.y}, ${pos.z})"
    if (bounds != null) {
        msg += " (${(bounds.volumeLong())})"
    }
    nsInfo(msg)
}
