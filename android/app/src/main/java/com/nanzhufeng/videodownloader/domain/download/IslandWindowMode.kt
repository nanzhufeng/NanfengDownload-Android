package com.nanzhufeng.videodownloader.domain.download

/** The connected service, not a saved permission switch, proves a top window is available. */
internal enum class IslandWindowMode {
    NONE, APPLICATION, ACCESSIBILITY;

    companion object {
        fun resolve(topConnected: Boolean, overlayAllowed: Boolean): IslandWindowMode =
            if (topConnected) ACCESSIBILITY else if (overlayAllowed) APPLICATION else NONE
    }
}

/** A late unbind from an old service must not discard the replacement service's token. */
internal class IslandTopHostStore<H> {
    private var owner: Any? = null
    var host: H? = null
        private set

    fun connect(owner: Any, host: H) { this.owner = owner; this.host = host }
    fun disconnect(owner: Any): Boolean {
        if (this.owner !== owner) return false
        this.owner = null; host = null
        return true
    }
}
