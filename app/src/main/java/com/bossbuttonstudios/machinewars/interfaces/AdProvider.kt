package com.bossbuttonstudios.machinewars.interfaces

/**
 * Ad texture contract. The renderer calls [getBillboardTexture] when it needs
 * the current ad to display on the in-world billboard.
 *
 * In production this is backed by AdMob (maxAdContentRating = G).
 * During prototype and tests the no-op implementation returns null, which
 * signals the renderer to show in-universe placeholder art instead.
 *
 * The interface is intentionally minimal — the ad system supplies a texture
 * ID; the billboard renders it as part of the world. No click handling,
 * no UI overlay.
 */
interface AdProvider {
    /**
     * Returns an opaque texture handle for the billboard, or null if no ad
     * is currently loaded (renderer shows placeholder art).
     */
    fun getBillboardTexture(): Any?

    /** Called when the billboard becomes visible on screen (impression). */
    fun onImpression()
}

/** No-op implementation used during prototype and development. */
class NoOpAdProvider : AdProvider {
    override fun getBillboardTexture(): Any? = null
    override fun onImpression() = Unit
}
