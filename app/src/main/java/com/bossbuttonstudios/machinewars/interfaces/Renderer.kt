package com.bossbuttonstudios.machinewars.interfaces

import com.bossbuttonstudios.machinewars.core.GameState

/**
 * Rendering contract. The game loop calls [render] once per frame after
 * all simulation updates are complete.
 *
 * The prototype no-op does nothing; the real implementation (Session 4)
 * draws units, wreckage, the factory grid, HP bars, and the billboard.
 *
 * Keeping this as an interface ensures that zero game logic depends on any
 * particular rendering technology.
 */
interface Renderer {
    /**
     * Draw the current [state] to the screen.
     * @param interpolation Fractional progress [0, 1] between the last fixed
     *                      tick and the next, used for sub-tick position smoothing.
     */
    fun render(state: GameState, interpolation: Float)
}

/** No-op implementation used during prototype and tests. */
class NoOpRenderer : Renderer {
    override fun render(state: GameState, interpolation: Float) = Unit
}
