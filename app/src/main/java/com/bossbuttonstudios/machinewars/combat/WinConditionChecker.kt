package com.bossbuttonstudios.machinewars.combat

import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.unit.Team

/**
 * Evaluates win and loss conditions each tick for all three mission types.
 *
 * Loss (all mission types):
 *   Player base (factory wall) HP reaches zero. Enemy units attack the wall
 *   when they reach position 0.0 — the same mechanic as player Artillery
 *   attacking the enemy base at position 1.0.
 *
 * BASE_ATTACK (spec §8.1):
 *   Win  — enemy base HP reaches zero.
 *
 * TIMED_SURVIVAL (spec §8.2):
 *   Win  — survival timer has expired AND no living enemy units remain.
 *          Pure turtling is not viable — the active wave must be cleared.
 *
 * RESOURCE_HUNT (spec §8.3):
 *   Win  — treasury ore meets or exceeds the ore target.
 *
 * Once [GameState.isOver] is set to true this checker becomes a no-op —
 * subsequent ticks do not re-evaluate or change the outcome.
 */
class WinConditionChecker {

    /**
     * Checks all applicable conditions and sets [GameState.isOver] and
     * [GameState.playerWon] if a terminal state has been reached.
     */
    fun tick(state: GameState) {
        if (state.isOver) return

        // Universal loss: factory wall breached.
        if (state.playerBaseHp <= 0f) {
            state.isOver   = true
            state.playerWon = false
            return
        }

        val enemiesAlive = state.livingUnits.any { it.team == Team.ENEMY }

        when (state.mission.type) {
            MissionType.BASE_ATTACK -> {
                if (state.enemyBaseHp <= 0f) {
                    state.isOver   = true
                    state.playerWon = true
                }
            }
            MissionType.TIMED_SURVIVAL -> {
                if (state.survivalTimerExpired && !enemiesAlive) {
                    state.isOver   = true
                    state.playerWon = true
                }
            }
            MissionType.RESOURCE_HUNT -> {
                if (state.resourceTargetMet) {
                    state.isOver   = true
                    state.playerWon = true
                }
            }
        }
    }
}
