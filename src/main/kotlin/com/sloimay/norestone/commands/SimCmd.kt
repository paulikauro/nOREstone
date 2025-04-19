package com.sloimay.norestone.commands

import com.sloimay.norestone.*
import com.sloimay.norestone.permission.NsPerms
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.kotlindsl.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SimCmd(val noreStone: NOREStone) {

    fun register() {



        commandTree("sim") {
            withPermission(noreStone.pp.permStr(NsPerms.Cmd.sim))

            literalArgument("backends") {
                withPermission(NsPerms.Cmd.Sim.backends)

                playerExecutor { p, args ->
                    p.nsInfo("Here is a list of available backends:")
                    RS_BACKEND_INFO.forEach {
                        p.nsInfo(" - ${it.displayName} (id: ${it.backendId}): ${it.helpStr}")
                    }
                }
            }

            literalArgument("desel") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.select))

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
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.select))

                playerExecutor { p, args ->
                    val cornerPos = p.location.blockPos()
                    val res = noreStone.playerInteract.setSimSelCorner(p, cornerPos, 0)
                    if (res.isErr()) {
                        p.nsErr(res.getErr())
                    } else {
                        p.nsInfoSimSelSetPos(cornerPos, 0, noreStone.getSession(p).sel)
                    }
                }
            }


            for (alias in listOf("pos2", "2")) literalArgument(alias) {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.select))

                playerExecutor { p, args ->
                    val cornerPos = p.location.blockPos()
                    val res = noreStone.playerInteract.setSimSelCorner(p, cornerPos, 1)
                    if (res.isErr()) {
                        p.nsErr(res.getErr())
                    } else {
                        p.nsInfoSimSelSetPos(cornerPos, 1, noreStone.getSession(p).sel)
                    }
                }
            }



            literalArgument("freeze") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.freeze))

                anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("FREEZE") }
            }

            literalArgument("step") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.step))

                anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("STEP") }

            }


            literalArgument("compile") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.compile))

                anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("COMPILE") }


                for (bi in RS_BACKEND_INFO) {
                    literalArgument(bi.backendId) {
                        withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.Compile.BackendAccess.backendId(bi.backendId)))

                        anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("COMPILE W BACKEND ${bi.displayName}") }

                    }
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


            literalArgument("clear") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.compile))

            }


        }

    }


}