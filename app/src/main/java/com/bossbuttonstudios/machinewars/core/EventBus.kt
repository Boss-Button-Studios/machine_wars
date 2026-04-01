package com.bossbuttonstudios.machinewars.core

import com.bossbuttonstudios.machinewars.model.factory.ComponentType
import kotlin.reflect.KClass
import java.util.UUID

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
// Game events
// ---------------------------------------------------------------------------

/** A unit was destroyed. */
data class UnitDestroyedEvent(
    val unitId: java.util.UUID,
    val excessDamage: Float,
    val lane: Int,
    val position: Float,
)

/** A wave has been fully spawned (last unit queued). */
data class WaveSpawnedEvent(val waveIndex: Int)

/** A wave has been fully cleared (all spawned enemies destroyed). */
data class WaveClearedEvent(val waveIndex: Int)

/** The player's ore total changed. */
data class OreChangedEvent(val newTotal: Int)

/** The mission ended. */
data class MissionEndedEvent(val playerWon: Boolean)

// --- Session 2 events ---

/**
 * A drivetrain component expired (wearPct reached 1.0) and was removed
 * from the grid. The network gap it leaves reduces output until the slot
 * is filled (spec §5.6).
 */
data class ComponentExpiredEvent(
    val gridX: Int,
    val gridY: Int,
    val type: ComponentType,
)

/**
 * A machine's computed output rate changed this tick.
 *
 * Posted after each solve pass for machines whose rate has changed by more
 * than a small epsilon. The renderer uses this to update bar-fill animations
 * on machine faces (spec §5.9).
 */
data class MachineOutputRateChangedEvent(
    val machineId: UUID,
    val newRate: Float,
)
