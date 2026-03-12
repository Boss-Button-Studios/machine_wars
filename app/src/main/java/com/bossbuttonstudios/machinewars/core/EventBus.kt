package com.bossbuttonstudios.machinewars.core

import kotlin.reflect.KClass

/**
 * Lightweight synchronous event bus.
 *
 * All game systems post and subscribe to typed events here rather than
 * calling each other directly. This keeps systems decoupled and makes it
 * straightforward to add logging, replay recording, or test observers later.
 *
 * Events are dispatched synchronously on the calling thread (always the
 * game-loop thread in production). No threading concerns here.
 */
class EventBus {

    private val listeners = mutableMapOf<KClass<*>, MutableList<(Any) -> Unit>>()

    /** Subscribe [listener] to events of type [T]. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> subscribe(type: KClass<T>, listener: (T) -> Unit) {
        listeners.getOrPut(type) { mutableListOf() }
            .add(listener as (Any) -> Unit)
    }

    /** Inline convenience wrapper. */
    inline fun <reified T : Any> on(noinline listener: (T) -> Unit) =
        subscribe(T::class, listener)

    /** Dispatch [event] to all registered listeners for its type. */
    fun post(event: Any) {
        listeners[event::class]?.forEach { it(event) }
    }

    /** Remove all listeners (useful between missions / in tests). */
    fun clear() = listeners.clear()
}

// ---------------------------------------------------------------------------
// Game events — add more in later sessions as needed
// ---------------------------------------------------------------------------

/** A unit was destroyed. */
data class UnitDestroyedEvent(
    val unitId: java.util.UUID,
    val excessDamage: Float,
    val lane: Int,
    val position: Float,
)

/** A wave has been fully spawned. */
data class WaveSpawnedEvent(val waveIndex: Int)

/** The player's ore total changed. */
data class OreChangedEvent(val newTotal: Int)

/** The mission ended. */
data class MissionEndedEvent(val playerWon: Boolean)
