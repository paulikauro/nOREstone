package com.sloimay.norestone

import com.sloimay.norestone.selection.SimSelection

class NsPlayerSession {
    var sel = SimSelection(null, null, null)
    var nsSim: NsSim? = null

    fun end() {
        nsSim?.end()
    }
}