package com.sloimay.norestone

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class NsMessenger(private val noreStone: NOREStone) {

    val INFO_PREFIX = mmComp("<gray>[nOREstone]</gray> >> ")

    val ERR_PREFIX = mmComp("<red>[nOREstone] >> </red>")

    fun info(p: Player, c: Component) {
        noreStone.adventure.player(p).sendMessage(INFO_PREFIX.append(c))
    }

    fun err(p: Player, c: Component) {
        noreStone.adventure.player(p).sendMessage(ERR_PREFIX.append(c))
    }

}