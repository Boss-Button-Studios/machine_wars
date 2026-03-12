package com.bossbuttonstudios.machinewars.core

import com.bossbuttonstudios.machinewars.interfaces.Renderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fixed-timestep game loop.
 *
 * The loop runs at [TICK_RATE_HZ] logical ticks per second regardless of
 * render frame rate. Each tick advances simulation by exactly [TICK_DELTA]
 * seconds, keeping physics and combat deterministic.
 *
 * Between ticks the renderer is called with a linear interpolation value
 * [0, 1] so it can smooth unit positions between logical steps without the
 * simulation drifting.
 *
 * Pattern: https://gafferongames.com/post/fix_your_timestep/
 *
 * All simulation callbacks ([onTick]) run on the coroutine's thread.
 * [renderer] is called from the same thread; if the real renderer needs the
 * main thread, it should post a runnable internally.
 */
class GameLoop(
    private val scope: CoroutineScope,
    private val state: GameState,
    private val renderer: Renderer,
    private val onTick: (dt: Float, state: GameState) -> Unit,
) {
    companion object {
        const val TICK_RATE_HZ = 60
        const val TICK_DELTA = 1f / TICK_RATE_HZ          // seconds per tick (~16.67 ms)
        const val TICK_DELTA_MS = 1000L / TICK_RATE_HZ    // milliseconds per tick

        /** Cap frame time to prevent the "spiral of death" on slow frames. */
        const val MAX_FRAME_TIME = 0.25f // seconds
    }

    private var loopJob: Job? = null

    @Volatile var isPaused: Boolean = false
        private set

    val isRunning: Boolean get() = loopJob?.isActive == true

    /**
     * Starts the loop. Safe to call only once per instance; to restart,
     * create a new [GameLoop].
     */
    fun start() {
        check(loopJob == null) { "GameLoop is already started" }
        loopJob = scope.launch {
            var previousTime = System.nanoTime()
            var accumulator = 0f

            while (isActive && !state.isOver) {
                if (isPaused) {
                    delay(TICK_DELTA_MS)
                    previousTime = System.nanoTime() // reset clock after unpause
                    continue
                }

                val currentTime = System.nanoTime()
                val frameTime = ((currentTime - previousTime) / 1_000_000_000f)
                    .coerceAtMost(MAX_FRAME_TIME)
                previousTime = currentTime

                accumulator += frameTime

                // Consume accumulated time in fixed steps.
                while (accumulator >= TICK_DELTA) {
                    onTick(TICK_DELTA, state)
                    accumulator -= TICK_DELTA
                }

                // Render with sub-tick interpolation.
                val interpolation = accumulator / TICK_DELTA
                renderer.render(state, interpolation)

                // Yield to avoid busy-spinning; real rendering cost will
                // naturally absorb most of this budget.
                val sleepMs = TICK_DELTA_MS - ((System.nanoTime() - currentTime) / 1_000_000L)
                if (sleepMs > 0) delay(sleepMs)
            }
        }
    }

    fun pause() { isPaused = true }

    fun resume() { isPaused = false }

    /** Stops the loop. The coroutine job is cancelled. */
    fun stop() { loopJob?.cancel() }

}