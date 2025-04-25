package com.sloimay.norestone.permission

import com.sloimay.norestone.NOREStone
import net.luckperms.api.node.Node
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties


class NsPermProvider(val noreStone: NOREStone) {
    private val permissions = HashMap<String, Permission>()


    private fun getPermsOfObj(obj: KClass<*>, result: MutableList<String>) {
        val nestedObjects = obj.nestedClasses
            .filter { it.objectInstance != null }

        val properties = obj.memberProperties.filter {
            val contains = it.visibility?.name?.contains("PRIVATE", ignoreCase = true)
            if (contains != null) {
                return@filter !contains
            } else {
                return@filter false
            }
        }

        properties.forEach {
            if (it.returnType.classifier == String::class) {
                val value = it.getter.call(obj.objectInstance)
                if (value is String) result.add(value)
            }
        }

        nestedObjects.forEach {
            getPermsOfObj(it, result)
        }
    }

    init {
        // Add every permission using reflection
        // Not the best best approach but I still think it's kinda awesome lol
        val perms = mutableListOf<String>()
        getPermsOfObj(NsPerms::class, perms)
        perms.forEach {
            //println("Registering perm: ${it}")
            perm(it)
        }
    }


    private fun registerPerm(perm: Permission) {
        noreStone.server.pluginManager.addPermission(perm)
    }

    fun perm(permNode: String): Permission {
        val perm = permissions.computeIfAbsent(permNode) {
            val perm = Permission(it).apply {
                default = PermissionDefault.OP
            }
            registerPerm(perm)
            perm
        }
        return perm
    }

    fun permStr(permNode: String): String {
        return perm(permNode).name
    }

    fun getIntPermByMax(player: Player, basePermNode: String, default: Int): Int {
        val allNodes = getAllOfPlayersNode(player)
        val prefix = "$basePermNode."

        val intVal = allNodes
            .map { it.key }
            .filter { it.startsWith(prefix) && it.removePrefix(prefix).toIntOrNull() != null }
            .maxOfOrNull { it.removePrefix(prefix).toIntOrNull()!! }

        return intVal ?: default
    }

    fun getLongPermByMax(player: Player, basePermNode: String, default: Long): Long {
        val allNodes = getAllOfPlayersNode(player)
        val prefix = "$basePermNode."

        val longVal = allNodes
            .map { it.key }
            .filter { it.startsWith(prefix) && it.removePrefix(prefix).toLongOrNull() != null }
            .maxOfOrNull { it.removePrefix(prefix).toLongOrNull()!! }

        return longVal ?: default
    }

    fun destroy() {
        // Deload every permission NOREStone registered
        permissions.forEach { (_, perm) ->
            noreStone.server.pluginManager.removePermission(perm)
        }
    }


    private fun getAllOfPlayersNode(player: Player): List<Node> {
        val user = noreStone.luckPerms.userManager.getUser(player.uniqueId) ?: return emptyList()
        val allNodes = mutableListOf<Node>()

        val mainGroup = noreStone.luckPerms.groupManager.getGroup(user.primaryGroup)
        mainGroup?.nodes?.forEach { allNodes.add(it) }
        val inheritedGroups = user.getInheritedGroups(user.queryOptions)
        inheritedGroups.forEach { allNodes.addAll(it.nodes) }

        return allNodes
    }

}



