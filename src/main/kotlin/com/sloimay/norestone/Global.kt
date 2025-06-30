package com.sloimay.norestone

import com.sloimay.nodestonecore.simulation.SimInitializer
import com.sloimay.nodestonecore.simulation.initialisers.ShrimpleBackendInitializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class RsBackendInfo(
    val backendId: String,
    val displayName: String,
    val descStr: String,
    val compileFlags: List<CompileFlag>,
    val initialiserProvider: () -> SimInitializer,
)

class CompileFlag(
    val id: String,
    val desc: String,
)

object RS_BACKENDS {
    val shrimple = RsBackendInfo(
        "shrimple",
        "Shrimple",
        "Simple single threaded MCHPRS-like backend.",
        listOf(
            CompileFlag("no-wire-rendering", "Stop redstone wire visual updates (automatically disabled in io-only)."),
            CompileFlag("io-only", "Only visually update IO blocks."),
        )
    ) { ShrimpleBackendInitializer() }
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

val PRESSURE_PLATE_MATS = arrayOf(
    Material.STONE_PRESSURE_PLATE,
    Material.OAK_PRESSURE_PLATE,
    Material.BIRCH_PRESSURE_PLATE,
    Material.SPRUCE_PRESSURE_PLATE,
    Material.JUNGLE_PRESSURE_PLATE,
    Material.ACACIA_PRESSURE_PLATE,
    Material.DARK_OAK_PRESSURE_PLATE,
    Material.WARPED_PRESSURE_PLATE,
    Material.CRIMSON_PRESSURE_PLATE,
    Material.BAMBOO_PRESSURE_PLATE,
    Material.CHERRY_PRESSURE_PLATE,
    Material.MANGROVE_PRESSURE_PLATE,
    Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
    Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
    Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
)
val WOODEN_BUTTON_MATS = arrayOf(
    Material.OAK_BUTTON,
    Material.BIRCH_BUTTON,
    Material.SPRUCE_BUTTON,
    Material.JUNGLE_BUTTON,
    Material.ACACIA_BUTTON,
    Material.DARK_OAK_BUTTON,
    Material.WARPED_BUTTON,
    Material.CRIMSON_BUTTON,
    Material.BAMBOO_BUTTON,
    Material.CHERRY_BUTTON,
    Material.MANGROVE_BUTTON,
)
val STONE_BUTTON_MATS = arrayOf(
    Material.STONE_BUTTON,
    Material.POLISHED_BLACKSTONE_BUTTON,
)
val BUTTON_MATS = arrayOf(
    *WOODEN_BUTTON_MATS,
    *STONE_BUTTON_MATS,
)

/**
 * Materials of user input blocks
 */
val INPUT_MATERIALS = arrayOf(
    Material.LEVER,
    *BUTTON_MATS,
    *PRESSURE_PLATE_MATS,
)
val SHRIMPLE_SIM_END_MATS_TO_UPDATE = arrayOf(
    Material.REPEATER,
    Material.COMPARATOR,
    Material.REDSTONE_LAMP,
    Material.REDSTONE_TORCH,
    Material.REDSTONE_WALL_TORCH,
)
