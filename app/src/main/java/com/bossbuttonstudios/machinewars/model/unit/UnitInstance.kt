package com.bossbuttonstudios.machinewars.model.unit

import java.util.UUID

/** Which side of the battlefield this unit belongs to. */
enum class Team { PLAYER, ENEMY }

/** High-level behavioural state for the unit AI (Session 3 will flesh this out). */
enum class UnitState {
    ADVANCING,   // moving toward the enemy end
    ENGAGING,    // target in range — firing
    DEAD,        // HP <= 0, pending removal / wreckage check
}

/**
 * Mutable, live representation of a single unit on the battlefield.
 *
 * Position is measured along the lane axis (0 = player end, 1 = enemy end,
 * normalised to [0, 1]). The renderer maps this to screen coordinates.
 *
 * [stats] is the resolved stat profile for this unit after upgrades; the
 * registry base values are its starting point but the instance owns its own
 * copy so mid-battle boosts don't corrupt shared state.
 */
data class UnitInstance(
    val id: UUID = UUID.randomUUID(),
    val type: UnitType,
    val team: Team,
    val lane: Int,                        // 0 = left, 1 = centre, 2 = right
    val stats: UnitStats,                 // resolved (post-upgrade) stat profile

    var currentHp: Float = stats.maxHp,
    var position: Float = if (team == Team.PLAYER) 0f else 1f,
    var state: UnitState = UnitState.ADVANCING,

    /** Seconds until next shot is allowed. Decremented each tick. */
    var shotCooldown: Float = 0f,

    /** ID of the current target, null when advancing or no valid target. */
    var targetId: UUID? = null,
) {
    val isAlive: Boolean get() = currentHp > 0f

    /**
     * Returns the effective HP fraction [0, 1] for bar rendering.
     */
    val hpFraction: Float get() = (currentHp / stats.maxHp).coerceIn(0f, 1f)
}
