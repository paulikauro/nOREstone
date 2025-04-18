package com.sloimay.norestone

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class NsMessenger(private val noreStone: NOREStone) {

    val INFO_PREFIX = mmComp("<gray>[nOREstone]</gray> >> ")
    val ERR_PREFIX = mmComp("<red>[nOREstone] >> </red>")
    val WARN_PREFIX = mmComp("<color:#ff7700>[nOREstone] >> </color>")

    fun info(p: Player, c: Component) {
        noreStone.adventure.player(p).sendMessage(INFO_PREFIX.append(c))
    }

    fun err(p: Player, c: Component) {
        noreStone.adventure.player(p).sendMessage(ERR_PREFIX.append(c))
    }

    fun warn(p: Player, c: Component) {
        noreStone.adventure.player(p).sendMessage(WARN_PREFIX.append(c))
    }
}

fun Player.nsInfo(msg: String) {
    NORESTONE!!.messenger.info(this, mmComp(msg))
}

fun Player.nsErr(msg: String) {
    NORESTONE!!.messenger.err(this, mmComp(msg))
}

fun Player.nsWarn(msg: String) {
    NORESTONE!!.messenger.warn(this, mmComp(msg))
}