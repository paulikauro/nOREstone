package com.sloimay.norestone.commands

import com.sloimay.norestone.NOREStone
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.RS_BACKEND_INFO
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import org.bukkit.Bukkit

class SimCmd(val noreStone: NOREStone) {

    fun register() {



        commandTree("sim") {
            withPermission(noreStone.pp.permStr(NsPerms.Cmd.sim))


            literalArgument("desel") {
                withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.select))

                anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("DESEL") }
            }

            for (alias in listOf("pos1", "1")) {
                literalArgument(alias) {
                    withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.select))

                    anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("POS1") }
                }
            }

            for (alias in listOf("pos2", "2")) {
                literalArgument(alias) {
                    withPermission(noreStone.pp.permStr(NsPerms.Cmd.Sim.select))

                    anyExecutor { commandSender, commandArguments -> Bukkit.broadcastMessage("POS2") }
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