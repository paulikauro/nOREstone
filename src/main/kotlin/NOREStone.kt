package com.sloimay.norestone

import com.plotsquared.core.PlotAPI
import com.plotsquared.core.plot.Plot
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.WorldEditPlugin
import com.sloimay.norestone.commands.SimCmd
import com.sloimay.norestone.listeners.NorestoneListener
import com.sloimay.norestone.listeners.PlotSquaredListener
import com.sloimay.norestone.permission.NsPermProvider
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.selection.SimSelValidator
import com.sloimay.norestone.simulation.NsSimManager
import com.sloimay.smath.vectors.IVec3
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class NOREStone : JavaPlugin() {
    val sessions = HashMap<UUID, NsPlayerSession>()
    val simSelValidator = SimSelValidator(this)
    val playerInteract = NsPlayerInteractions(this)
    val simManager = NsSimManager(20)
    val syncedWorker = SyncedWorker()

    // lol pp
    lateinit var pp: NsPermProvider
        private set
    lateinit var db: NorestoneDb
        private set
    lateinit var consts: NsConsts
        private set
    lateinit var plotApi: PlotAPI
        private set

    // # Hooked plugins
    lateinit var luckPerms: LuckPerms
        private set
    lateinit var worldEdit: WorldEdit
        private set

    override fun onEnable() {
        // # Config
        saveDefaultConfig()
        // # Permissions
        pp = NsPermProvider(this)
        // # Load time consts
        setupConsts()
        if (!isEnabled) return
        // # Hooking
        if (!hookIntoLuckPerms()) return
        if (!hookIntoWorldEdit()) return
        plotApi = PlotAPI()
        // # Db file
        dataFolder.mkdirs()
        val dbFile = dataFolder.resolve("norestone.db")
        db = NorestoneDb(dbFile)
        // # Register events
        server.pluginManager.registerEvents(NorestoneListener(this), this)
        plotApi.registerListener(PlotSquaredListener(this))
        // # Register commands
        SimCmd(this).register()
        // # Tickers
        Bukkit.getScheduler().runTaskTimer(this, NsTicker(this), 1, 1)
    }

    override fun onDisable() {
        // End all sessions
        sessions.map { it.key }.forEach { endSession(it) }
        // This line is awesome
        pp.destroy()
    }

    private fun hookIntoLuckPerms(): Boolean {
        val provider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
        if (provider == null) {
            logger.severe("LuckPerms not found, disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return false
        }
        luckPerms = provider.provider
        return true
    }

    private fun hookIntoWorldEdit(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin("WorldEdit") as? WorldEditPlugin
        if (plugin == null) {
            // TODO: dedup, also careful with disabling as it runs onDisable. figure out a better way
            logger.severe("WorldEdit not found, disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return false
        }
        worldEdit = plugin.worldEdit
        return true
    }

    fun setupConsts() {
        val config = getConfig()

        class TypeData(
            val name: String,
            val isFunc: FileConfiguration.(String) -> Boolean,
            val getFunc: FileConfiguration.(String) -> Any,
        )

        val intTypeData = TypeData("Int", FileConfiguration::isInt, FileConfiguration::getInt)
        val longTypeData = TypeData("Long", FileConfiguration::isInt, FileConfiguration::getLong)
        val boolTypeData = TypeData("Boolean", FileConfiguration::isBoolean, FileConfiguration::getBoolean)

        // what the hell did i do here lmao
        fun getOrDisable(path: String, typeData: TypeData): Any? {
            var value: Any? = null
            if (path in config.getKeys(false)) {
                val isFunc = typeData.isFunc
                if (config.isFunc(path)) {
                    val getFunc = typeData.getFunc
                    value = config.getFunc(path)
                } else {
                    logger.severe("$path isn't of the expected type: ${typeData.name}.")
                }
            } else {
                logger.severe("config.yml missing '$path'.")
            }
            return value
        }

        val defaultMaxTps = (getOrDisable("default_max_sim_tps", intTypeData)
            ?: run { isEnabled = false; return }) as Int
        val defaultSelMaxX = (getOrDisable("default_max_sim_sel_size_x", intTypeData)
            ?: run { isEnabled = false; return }) as Int
        val defaultSelMaxY = (getOrDisable("default_max_sim_sel_size_y", intTypeData)
            ?: run { isEnabled = false; return }) as Int
        val defaultSelMaxZ = (getOrDisable("default_max_sim_sel_size_z", intTypeData)
            ?: run { isEnabled = false; return }) as Int
        val defaultSelMaxVol = (getOrDisable("default_max_sim_volume", longTypeData)
            ?: run { isEnabled = false; return }) as Long
        val extraSafeSimStateChecking = (getOrDisable("extra_safe_sim_state_checking", boolTypeData)
            ?: run { isEnabled = false; return }) as Boolean

        consts = NsConsts(
            defaultMaxTps,
            defaultSelMaxX,
            defaultSelMaxY,
            defaultSelMaxZ,
            defaultSelMaxVol,
            extraSafeSimStateChecking,
        )
    }

    fun addSession(player: Player) {
        addSession(player.uniqueId)
    }

    fun addSession(playerUuid: UUID) {
        val player = Bukkit.getPlayer(playerUuid)
        require(player != null) { "Trying to setup a session session for a null player." }
        sessions[playerUuid] = NsPlayerSession(player, this)
    }

    fun endSession(player: Player) {
        endSession(player.uniqueId)
    }

    fun endSession(playerUuid: UUID) {
        sessions.remove(playerUuid)?.end()
    }

    fun getSession(player: Player): NsPlayerSession {
        return getSession(player.uniqueId)
    }

    fun getSession(playerUuid: UUID): NsPlayerSession {
        if (playerUuid !in sessions) addSession(playerUuid)
        return sessions[playerUuid]!!
    }

    fun doesPlayerSimExists(player: Player): Boolean {
        return simManager.playerUuidSimExists(player.uniqueId)
    }

    fun getPlayerMaxSimTps(player: Player): Int {
        return pp.getIntPermByMax(player, NsPerms.Simulation.maxTps, consts.DEFAULT_MAX_SIM_TPS)
    }

    fun getPlayerMaxSimSelSize(player: Player): IVec3 {
        return IVec3(
            pp.getIntPermByMax(player, NsPerms.Simulation.Selection.MaxDims.x, consts.DEFAULT_MAX_SIM_SIZE_X),
            pp.getIntPermByMax(player, NsPerms.Simulation.Selection.MaxDims.y, consts.DEFAULT_MAX_SIM_SIZE_Y),
            pp.getIntPermByMax(player, NsPerms.Simulation.Selection.MaxDims.z, consts.DEFAULT_MAX_SIM_SIZE_Z),
        )
    }

    fun getPlayerMaxSimSelVolume(player: Player): Long {
        return pp.getLongPermByMax(player, NsPerms.Simulation.Selection.maxVolume, consts.DEFAULT_MAX_SIM_VOLUME)
    }

    fun getPlotAt(world: World, pos: IVec3): Plot? {
        val psLocation = pos.toPsqLoc(world)
        return psLocation.plot
    }

    /**
     * Checks if sims are living in legality
     */
    fun simulationsPoliceCheckup() {
        // TODO: really you just want to check the running sims instead
        // see: validatePlayerSelOverlap
        for (seshUuid in sessions.keys) {
            val player = Bukkit.getPlayer(seshUuid)!!
            if (player.hasPermission(NsPerms.Simulation.Selection.Select.bypass)) continue
            val playerSim = simManager.getPlayerSim(player.uniqueId) ?: continue
            if (simSelValidator.isSimSelInLegalSpot_assume2dPlots(playerSim.sel, player)) continue
            simManager.requestSimRemove(player.uniqueId, playerSim)
            // not ideal for the message to be here, but it is only called from the plot listener for now
            player.nsWarn(
                "Following a modification of your trusted status, or plots changing" +
                        " geometry, which lead to your currently on-going simulation being in an invalid" +
                        " state, your simulation was cleared."
            )
        }
    }
}
