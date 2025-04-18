package com.sloimay.norestone


class RsBackendInfo(val backendId: String, val displayName: String)
val RS_BACKEND_INFO = listOf(
    RsBackendInfo("shrimple", "Shrimple")
)

var NORESTONE: NOREStone? = null


object DEBUG {
    val PLOT = false
    val NUMBER_PERMS = false
    val PLOT_ITERATION = false
}