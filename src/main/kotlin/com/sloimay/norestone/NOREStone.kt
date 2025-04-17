package com.sloimay.norestone

import com.plotsquared.core.PlotAPI
import com.plotsquared.core.plot.Plot
import com.sloimay.norestone.commands.SimCmd
import com.sloimay.norestone.permission.NsPermProvider
import com.sloimay.norestone.permission.NsPerms
import com.sloimay.norestone.selection.SimSelValidator
import com.sloimay.norestone.selection.SimSelection
import com.sloimay.smath.vectors.IVec3
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.collections.HashMap


class NOREStone : JavaPlugin() {

    val sessions = HashMap<UUID, NsPlayerSession>()

    val messenger = NsMessenger(this)

    val simSelValidator = SimSelValidator(this)


    // lol pp
    lateinit var pp: NsPermProvider
        private set

    lateinit var db: NorestoneDb
        private set

    lateinit var consts: NsConsts
        private set

    lateinit var plotApi: PlotAPI
        private set

    lateinit var adventure: BukkitAudiences
        private set

    // # Hooked plugins
    lateinit var luckPerms: LuckPerms
        private set



    override fun onEnable() {

        NORESTONE = this

        // # Config
        saveDefaultConfig()

        // # Permissions
        pp = NsPermProvider(this)

        // # Load time consts
        setupConsts()
        if (!isEnabled) return

        // # Hooking
        if (!hookIntoLuckPerms()) return
        plotApi = PlotAPI()

        // # Adventure
        adventureSetup()

        // # Register events
        server.pluginManager.registerEvents(NorestoneListener(this), this)

        // # Db file
        dataFolder.mkdirs()
        val dbFile = dataFolder.resolve("norestone.db")
        db = NorestoneDb(dbFile)

        // # Register commands
        SimCmd(this).register()

    }

    override fun onDisable() {
        // Plugin shutdown logic


        adventure.close()

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
        logger.info("Successfully hooked into LuckPerms.")

        return true
    }

    fun setupConsts() {
        var config = getConfig()

        class TypeData(
            val name: String,
            val isFunc: FileConfiguration.(String) -> Boolean,
            val getFunc: FileConfiguration.(String) -> Any,
        )

        val intTypeData = TypeData("Int", FileConfiguration::isInt, FileConfiguration::getInt)
        val longTypeData = TypeData("Long", FileConfiguration::isInt, FileConfiguration::getLong)


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


        // what the hell did i do here lmao
        val defaultMaxTps = (getOrDisable("default_max_sim_tps", intTypeData,)
            ?: run { isEnabled = false; return }) as Int

        val defaultSelMaxX = (getOrDisable("default_max_sim_sel_size_x", intTypeData,)
            ?: run { isEnabled = false; return }) as Int

        val defaultSelMaxY = (getOrDisable("default_max_sim_sel_size_y", intTypeData,)
            ?: run { isEnabled = false; return }) as Int

        val defaultSelMaxZ = (getOrDisable("default_max_sim_sel_size_z", intTypeData,)
            ?: run { isEnabled = false; return }) as Int

        val defaultSelMaxVol = (getOrDisable("default_max_sim_volume", longTypeData,)
            ?: run { isEnabled = false; return }) as Long

        consts = NsConsts(
            defaultMaxTps,
            defaultSelMaxX,
            defaultSelMaxY,
            defaultSelMaxZ,
            defaultSelMaxVol,
        )
    }

    private fun adventureSetup() {
        adventure = BukkitAudiences.create(this)
        logger.info("Plugin enabled with Adventure.")
    }



    fun addSession(player: Player) {
        sessions[player.uniqueId] = NsPlayerSession()
    }

    fun endSession(player: Player) {
        sessions.remove(player.uniqueId)?.end()
    }

    fun getSession(player: Player): NsPlayerSession {
        if (player.uniqueId !in sessions) addSession(player)
        return sessions[player.uniqueId]!!
    }


    /**
     * Setting selection corners should only happen in this method!!
     */
    fun setSimSelCorner(player: Player, newCorner: IVec3, cornerIdx: Int) {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.Selection.select) { return }

        val sesh = getSession(player)

        // Check if the new corner is in the same world as the previous one (if one was set)
        val selW = sesh.sel.world
        if (selW != null) {
            if (selW.uid != player.world.uid) {
                messenger.err(player, mmComp("Trying to select in 2 different worlds. Keep it in the same world," +
                        " or do \"/sim desel\" and start selecting again."))
                return
            }
        }

        // If we're setting the same corner, don't do anything
        val oldCorner = sesh.sel[cornerIdx]
        if (oldCorner == newCorner) {
            return
        }

        // Attempt a new selection
        val newSelAttempt = when (cornerIdx) {
            0 -> sesh.sel.withPos1(newCorner)
            1 -> sesh.sel.withPos2(newCorner)
            else -> error("Index out of bounds.")
        }
        // Spatial change validation
        val spatialChangeValidationRes = simSelValidator.validateForSimSpatialChange(player, newSelAttempt)
        if (spatialChangeValidationRes.isErr()) {
            messenger.err(player, mmComp(spatialChangeValidationRes.getErr()))
        }

        // New selection attempt success
        sesh.sel = newSelAttempt.withWorld(player.world)
    }

    fun desel(player: Player) {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.Selection.select) { return }

        val sesh = getSession(player)

        sesh.sel = SimSelection.empty()
    }


    fun compileSim(player: Player) {
        playerFeedbackRequirePerm(player, NsPerms.Simulation.compile) { return }
        if (doesPlayerSimExists(player)) {
            messenger.err(player, mmComp("A simulation is still active, please clear it before trying to" +
                    " compile a new one."))
            return
        }


        val validationRes = simSelValidator.validateForCompilation(player)
        if (validationRes.isErr()) {
            messenger.err(player, mmComp(validationRes.getErr()))
        }


        //val sesh = getSession(player)
    }



    private fun doesPlayerSimExists(player: Player): Boolean {
        val sesh = getSession(player)
        return sesh.nsSim != null
    }



    fun getPlayerMaxSimTps(player: Player): Int {
        return pp.getIntPerm(player, NsPerms.Simulation.maxTps, consts.DEFAULT_MAX_SIM_TPS)
    }

    fun getPlayerMaxSimSelSize(player: Player): IVec3 {
        return IVec3(
            pp.getIntPerm(player, NsPerms.Simulation.Selection.MaxDims.x, consts.DEFAULT_MAX_SIM_SIZE_X),
            pp.getIntPerm(player, NsPerms.Simulation.Selection.MaxDims.y, consts.DEFAULT_MAX_SIM_SIZE_Y),
            pp.getIntPerm(player, NsPerms.Simulation.Selection.MaxDims.z, consts.DEFAULT_MAX_SIM_SIZE_Z),
        )
    }

    fun getPlayerMaxSimSelVolume(player: Player): Long {
        return pp.getLongPerm(player, NsPerms.Simulation.Selection.maxVolume, consts.DEFAULT_MAX_SIM_VOLUME)
    }

    fun getPlotAt(world: World, pos: IVec3): Plot? {
        val psLocation = pos.toPsqLoc(world)
        return psLocation.plot
    }

    /*private fun getPlotsInBoundary(
        boundary: IntBoundary,
        world: World,
    ) {
        val area = plotApi.getPlotAreas(world.name)

        for (a in area) {
            a.
        }
    }*/



}
