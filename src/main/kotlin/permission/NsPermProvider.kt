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
        return getNumberPermByMax(player, basePermNode, default, String::toIntOrNull)
    }

    fun getLongPermByMax(player: Player, basePermNode: String, default: Long): Long {
        return getNumberPermByMax(player, basePermNode, default, String::toLongOrNull)
    }

    private fun <T : Comparable<T>> getNumberPermByMax(
        player: Player,
        basePermNode: String,
        default: T,
        parser: String.() -> T?,
    ): T {
        val allNodes = getAllNodesOfPlayer(player)
        val prefix = "$basePermNode."
        val maxVal = allNodes
            .filter { it.value == true } // Only get true nodes
            .map { it.key }
            .filter { it.startsWith(prefix) && it.removePrefix(prefix).parser() != null }
            .maxOfOrNull { it.removePrefix(prefix).parser()!! }

        return maxVal ?: default
    }

    fun destroy() {
        // Deload every permission NOREStone registered
        permissions.forEach { (_, perm) ->
            noreStone.server.pluginManager.removePermission(perm)
        }
    }

    private fun getAllNodesOfPlayer(player: Player): List<Node> {
        val user = noreStone.luckPerms.userManager.getUser(player.uniqueId) ?: return emptyList()
        val allNodes = mutableListOf<Node>()

        allNodes.addAll(user.nodes)
        val mainGroup = noreStone.luckPerms.groupManager.getGroup(user.primaryGroup)
        mainGroup?.nodes?.forEach { allNodes.add(it) }
        val inheritedGroups = user.getInheritedGroups(user.queryOptions)
        inheritedGroups.forEach { allNodes.addAll(it.nodes) }

        return allNodes
    }
}
