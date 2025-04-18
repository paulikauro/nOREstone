package com.sloimay.norestone.permission


private val prefix = "norestone"
object NsPerms { // NorestonePerms

    private val cmd = "$prefix.cmd"
    object Cmd {

        // == # /sim
        val sim = "$cmd.sim"
        object Sim {
            val freeze = "$sim.freeze"
            val select = "$sim.select"
            val step = "$sim.step"
            val compile = "$sim.compile"
            object Compile {
                private val backendAccess = "$compile.backendaccess"
                object BackendAccess {
                    fun backendId(backendId: String) = "$backendAccess.$backendId"
                }
            }
        }
        // ==

    }

    private val simulation = "$prefix.simulation"
    object Simulation {
        val maxTps = "$simulation.maxtps"
        object MaxTps {
            val bypass = "$maxTps.bypass"
        }
        val compile = "$simulation.compile"
        private val selection = "$simulation.selection"
        object Selection {
            val select = "$selection.select"
            object Select {
                val bypass = "$select.bypass"
            }
            private val maxDims = "$selection.maxdims"
            object MaxDims {
                val x = "$maxDims.x"
                val y = "$maxDims.y"
                val z = "$maxDims.z"
                val bypass = "$maxDims.bypass"
            }
            val maxVolume = "$selection.volume"
            object MaxVolume {
                val bypass = "$maxVolume.bypass"
            }
        }
    }
}