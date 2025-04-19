package com.sloimay.norestone

import org.bukkit.Material
import org.bukkit.inventory.ItemStack



class RsBackendInfo(val backendId: String, val displayName: String, val helpStr: String, val compileFlags: List<String>)

object RS_BACKENDS {
    val shrimple = RsBackendInfo(
        "shrimple",
        "Shrimple",
        "Simple single threaded MCHPRS-like backend.",
        listOf("io-only")
    )
}

val RS_BACKEND_INFO = listOf(
    RS_BACKENDS.shrimple,
)

var NORESTONE: NOREStone? = null

fun defaultSimSelWand() = ItemStack(Material.NETHERITE_SHOVEL)

object DEBUG {
    val PLOT = false
    val NUMBER_PERMS = false
    val PLOT_ITERATION = false
    val NBTAPI_TO_QUERZ = false
}