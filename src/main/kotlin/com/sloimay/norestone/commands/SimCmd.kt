package com.sloimay.norestone.commands

import com.sloimay.norestone.*
import com.sloimay.norestone.permission.NsPerms
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import org.bukkit.Bukkit

class SimCmd(val noreStone: NOREStone) {

    fun register() {



        commandTree("sim") {
            withPermission(noreStone.pp.permStr(NsPerms.Cmd.sim))


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
                        p.nsInfo("[${cornerPos.x}, ${cornerPos.y}, ${cornerPos.z}]")
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
                        p.nsInfo("[${cornerPos.x}, ${cornerPos.y}, ${cornerPos.z}]")
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

            literalArgument("clear") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.compile))

            }


        }

    }


}