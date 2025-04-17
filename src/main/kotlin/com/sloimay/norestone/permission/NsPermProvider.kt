package com.sloimay.norestone.permission

import com.sloimay.norestone.NOREStone
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault


class NsPermProvider(val noreStone: NOREStone) {
    private val permissions = HashMap<String, Permission>()


    private fun registerPerm(perm: Permission) {
        noreStone.server.pluginManager.addPermission(perm)
    }

    fun perm(permNode: String): Permission {
        val perm = permissions.computeIfAbsent(permNode) {
            val perm = Permission(it).apply {
                default = PermissionDefault.FALSE
            }
            registerPerm(perm)
            perm
        }
        return perm
    }

    fun permStr(permNode: String): String {
        return perm(permNode).name
    }

    fun getIntPerm(player: Player, basePermNode: String, default: Int): Int {
        val user = noreStone.luckPerms.userManager.getUser(player.uniqueId) ?: return default
        val nodes = user.nodes
        val prefix = "$basePermNode."

        val intVal = nodes
            .map { it.key }
            .firstOrNull { it.startsWith(prefix) && it.removePrefix(prefix).toIntOrNull() == null }
            ?.removePrefix(prefix)
            ?.toIntOrNull()

        return intVal ?: default
    }

    fun getLongPerm(player: Player, basePermNode: String, default: Long): Long {
        val user = noreStone.luckPerms.userManager.getUser(player.uniqueId) ?: return default
        val nodes = user.nodes
        val prefix = "$basePermNode."

        val longVal = nodes
            .map { it.key }
            .firstOrNull { it.startsWith(prefix) && it.removePrefix(prefix).toLongOrNull() == null }
            ?.removePrefix(prefix)
            ?.toLongOrNull()

        return longVal ?: default
    }

    fun destroy() {
        // Deload every permission NOREStone registered
        permissions.forEach { (_, perm) ->
            noreStone.server.pluginManager.removePermission(perm)
        }
    }
}

