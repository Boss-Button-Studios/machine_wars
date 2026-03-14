package com.bossbuttonstudios.machinewars.combat

import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitState

/**
 * Advances ADVANCING units along the lane axis each tick.
 *
 * ENGAGING and DEAD units are not moved — the targeting system is responsible
 * for transitioning a unit back to ADVANCING when it loses its target.
 *
 * Player units move from position 0 toward 1 (enemy end).
 * Enemy units move from position 1 toward 0 (player end).
 *
 * Position is normalised to [0, 1]; spec speeds are converted via
 * [CombatConstants.normalizedSpeed].
 *
 * Position is clamped to [0, 1] to prevent units from overshooting the field
 * boundaries. In practice the targeting system will acquire the base or enemy
 * units before this clamp is ever reached, but the defensive clamp costs
 * nothing and prevents any edge-case accumulation.
 */
class MovementSystem {

    /**
     * Advances all ADVANCING units by [dt] seconds.
     * Called once per game tick before [CombatSystem] resolves shots.
     */
    fun tick(state: GameState, dt: Float) {
        for (unit in state.livingUnits) {
            if (unit.state != UnitState.ADVANCING) continue

            val normalizedSpeed = CombatConstants.normalizedSpeed(unit.stats.speed)
            val delta = normalizedSpeed * dt

            unit.position = if (unit.team == Team.PLAYER) {
                (unit.position + delta).coerceAtMost(1f)
            } else {
                (unit.position - delta).coerceAtLeast(0f)
            }
        }
    }
}
