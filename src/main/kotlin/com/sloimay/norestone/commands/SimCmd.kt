package com.sloimay.norestone.commands

import com.sloimay.norestone.*
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.simulation.NsSim
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.kotlindsl.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class SimCmd(val noreStone: NOREStone) {

    fun register() {



        commandTree("sim") {
            withPermission(noreStone.pp.permStr(NsPerms.Cmd.sim))


            literalArgument("backends") {
                withPermission(NsPerms.Cmd.Sim.backends)

                playerExecutor { p, args ->
                    p.nsInfo("Here is a list of the backends that are available to you:")
                    RS_BACKEND_INFO.forEach {
                        if (p.hasPermission(NsPerms.Cmd.Sim.Compile.BackendAccess.backendId(it.backendId))) {
                            p.nsInfo(" - ${it.displayName} (id: ${it.backendId}): ${it.descStr}")
                        }
                    }
                }

                for (bi in RS_BACKEND_INFO) literalArgument(bi.backendId) {
                    // TODO: should use a separate permission to be consistent with the
                    //       permission granularity already present in the plugin
                    withPermission(NsPerms.Cmd.Sim.Compile.BackendAccess.backendId(bi.backendId))

                    playerExecutor { p, args ->
                        p.nsInfo("Simulation backend \"${bi.displayName}\" (id: ${bi.backendId}):")
                        p.nsInfo("  Description: ${bi.descStr}")
                        p.nsInfo("  Compile flags:")
                        for (cf in bi.compileFlags) {
                            p.nsInfo("  - -${cf.id}: ${cf.desc}")
                        }
                    }
                }
            }


            literalArgument("desel") {
                withPermission(NsPerms.Cmd.Sim.select)

                playerExecutor { p, args ->
                    val res = noreStone.playerInteract.desel(p)
                    if (res.isErr()) {
                        p.nsErr(res.getErr())
                    } else {
                        p.nsInfo("Deselected sim selection.")
                    }
                }
            }


            for (alias in listOf("pos1", "1")) literalArgument(alias) {
                withPermission(NsPerms.Cmd.Sim.select)

                playerExecutor { p, args ->
                    val cornerPos = p.location.blockPos()
                    val res = noreStone.playerInteract.setSimSelCorner(p, cornerPos, 0)
                    if (res.isErr()) {
                        p.nsErr(res.getErr())
                    } else {
                        val cornerChanged = res.isOk()
                        if (cornerChanged) {
                            p.nsInfoSimSelSetPos(cornerPos, 0, noreStone.getSession(p).sel)
                        }
                    }
                }
            }


            for (alias in listOf("pos2", "2")) literalArgument(alias) {
                withPermission(NsPerms.Cmd.Sim.select)

                playerExecutor { p, args ->
                    val cornerPos = p.location.blockPos()
                    val res = noreStone.playerInteract.setSimSelCorner(p, cornerPos, 1)
                    if (res.isErr()) {
                        p.nsErr(res.getErr())
                    } else {
                        val cornerChanged = res.isOk()
                        if (cornerChanged) {
                            p.nsInfoSimSelSetPos(cornerPos, 1, noreStone.getSession(p).sel)
                        }
                    }
                }
            }



            literalArgument("freeze") {
                withPermission(NsPerms.Cmd.Sim.freeze)

                playerExecutor { p, args ->
                    val sim = noreStone.simManager.getPlayerSim(p.uniqueId)
                    if (sim == null) {
                        p.nsErr("No simulation currently on-going.")
                        return@playerExecutor
                    }

                    // TODO: later down the line when warping will maybe be added,
                    //       add a check for freezing if warping is already going
                    //       Also use results and the NsPlayerInteraction class

                    playerFeedbackRequirePerm(p, NsPerms.Simulation.freeze) {
                        p.nsErr(it)
                        return@playerExecutor
                    }
                    val newState = sim.requestFreeze()

                    if (newState is NsSim.SimState.Frozen) {
                        p.nsInfo("Froze simulation.")
                    } else if (newState is NsSim.SimState.Running) {
                        p.nsInfo("Simulation is now running.")
                    }
                }
            }

            literalArgument("step") {
                withPermission(NsPerms.Cmd.Sim.step)

                longArgument("ticks") {
                    playerExecutor { p, args ->
                        val ticksStepped = args["ticks"] as Long
                        val tickStepRes = noreStone.playerInteract.tickStep(p, ticksStepped)

                        if (tickStepRes.isErr()) {
                            p.nsErr(tickStepRes.getErr())
                        } else {
                            p.nsInfo("Stepping $ticksStepped tick${if (ticksStepped==1L) "" else "s"}..")
                        }
                    }
                }
            }


            literalArgument("tps") {
                withPermission(NsPerms.Cmd.Sim.tps)

                playerExecutor { p, args ->
                    val sim = noreStone.simManager.getPlayerSim(p.uniqueId)
                    if (sim == null) {
                        p.nsErr("No simulation currently on-going.")
                        return@playerExecutor
                    }

                    val tpsAnalysis = noreStone.simManager.getSimTpsAnalysis(sim)

                    p.nsInfo("Target TPS: ${formatTps(tpsAnalysis.oneSecond)}, last 1s, 10s, 1m: " +
                            "${formatTps(tpsAnalysis.oneSecond)}, " +
                            "${formatTps(tpsAnalysis.tenSeconds)}, " +
                            "${formatTps(tpsAnalysis.oneMinute)}.")
                }

                doubleArgument("new_tps") {
                    playerExecutor { p, args ->
                        val newTps = args["new_tps"] as Double
                        val changeSimTpsRes = noreStone.playerInteract.changeSimTps(p, newTps)

                        if (changeSimTpsRes.isErr()) {
                            p.nsErr(changeSimTpsRes.getErr())
                        } else {
                            p.nsInfo("Simulation target TPS set to ${"%.2f".format(Locale.ENGLISH, newTps)}.")
                        }
                    }
                }
            }


            literalArgument("compile") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.compile))

                for (bi in RS_BACKEND_INFO) {
                    literalArgument(bi.backendId) {
                        withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.Compile.BackendAccess.backendId(bi.backendId)))

                        // Compile with compile flags
                        greedyStringArgument("compile_flags") {
                            playerExecutor { p, args ->
                                val flagParseResult = tryParseCompileFlags(
                                    args["compile_flags"] as String, bi
                                )
                                if (flagParseResult.isErr()) {
                                    p.nsErr(flagParseResult.getErr())
                                    return@playerExecutor
                                }

                                val flags = flagParseResult.getOk()
                                val compileResult =
                                    noreStone.playerInteract.compileSim(p, bi.backendId, flags)
                                if (compileResult.isErr()) {
                                    p.nsErr(compileResult.getErr())
                                    return@playerExecutor
                                }

                                p.nsInfo("Backend successfully compiled in ${compileResult.getOk()}.")
                            }
                        }

                        // Compile without compile flags
                        playerExecutor { p, args ->
                            val compileResult = noreStone.playerInteract.compileSim(p, bi.backendId, listOf())
                            if (compileResult.isErr()) {
                                p.nsErr(compileResult.getErr())
                                return@playerExecutor
                            }

                            p.nsInfo("Backend successfully compiled in ${compileResult.getOk()}.")
                        }
                    }
                }
            }

            literalArgument("clear") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.compile))

                playerExecutor { p, args ->
                    if (!noreStone.doesPlayerSimExists(p)) {
                        p.nsErr("No simulation currently on-going.")
                        return@playerExecutor
                    }
                    noreStone.simManager.getPlayerSim(p.uniqueId)?.let {
                        noreStone.simManager.requestSimRemove(p.uniqueId, it)
                    }
                    p.nsInfo("Simulation cleared.")
                }
            }


            literalArgument("selwand") {
                withPermission(NsPerms.Cmd.Sim.selWand)

                fun logRes(p: Player, r: Result<String, String>) {
                    if (r.isErr()) {
                        p.nsErr(r.getErr())
                    } else if (r.isOk()) {
                        p.nsInfo(r.getOk())
                    }
                }

                val handExecutor: (Player, CommandArguments) -> Unit = lambda@ { p, args ->
                    val res = noreStone.playerInteract.bindSimSelWand(p, p.inventory.itemInMainHand)
                    logRes(p, res)
                }
                // /sim selwand hand
                literalArgument("hand") {
                    playerExecutor(handExecutor)
                }
                // /sim selwand
                playerExecutor(handExecutor)


                // /sim selwand default
                literalArgument("default") {
                    playerExecutor { p, args ->
                        val res = noreStone.playerInteract.bindSimSelWand(p, defaultSimSelWand())
                        logRes(p, res)
                    }
                }

                // /sim selwand <item stack>
                itemStackArgument("item") {
                    playerExecutor { p, args ->
                        val item = args["item"] as ItemStack
                        val res = noreStone.playerInteract.bindSimSelWand(p, item)
                        logRes(p, res)
                    }
                }
            }


        }

    }


}