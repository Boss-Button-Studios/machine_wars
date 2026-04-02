package com.bossbuttonstudios.machinewars.combat

import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.model.map.LaneBoundary
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitState
import com.bossbuttonstudios.machinewars.model.unit.UnitType
import java.util.UUID

/**
 * Resolves target acquisition and unit state transitions each tick.
 *
 * Priority order (spec §2.1, §4.2):
 *
 *  1. Wreckage ahead in the same lane within range — Brute treats wreckage as
 *     a blocking obstacle and must clear it before advancing. Skirmisher and
 *     Artillery do not acquire wreckage as primary targets; their shots resolve
 *     wreckage interactions in [CombatSystem] instead.
 *
 *  2. Favoured enemy class (RPS triangle) in a valid lane within range.
 *
 *  3. Nearest enemy unit in a valid lane within range (any class).
 *
 *  4. Enemy base — player units only; acquired when the base position is
 *     within firing range. Artillery's favoured multiplier against buildings
 *     is applied in [CombatSystem].
 *
 * Cross-lane validity (spec §3.2):
 *  - Brute is always lane-locked. It can only target units in its own lane.
 *  - Skirmisher and Artillery may fire into an adjacent lane if and only if
 *    the shared boundary is a [LaneBoundary.Space]. A [LaneBoundary.Wall]
 *    blocks all cross-lane targeting for all unit types.
 *
 * State transitions:
 *  - A unit with a valid in-range target moves to ENGAGING.
 *  - A unit whose target has died, moved out of range, or is no longer reachable
 *    moves back to ADVANCING and clears [UnitInstance.targetId].
 */
class TargetingSystem {

    /**
     * Sentinel value used in [GameState] to represent the enemy base as a
     * targetable entity. CombatSystem checks for this ID to route damage to
     * [GameState.enemyBaseHp] instead of a [UnitInstance].
     */
    companion object {
        val ENEMY_BASE_ID: UUID  = UUID(0L, 1L)
        val PLAYER_BASE_ID: UUID = UUID(0L, 2L)

        /** Normalised position of the enemy base (far end of the field). */
        const val ENEMY_BASE_POSITION  = 1.0f

        /** Normalised position of the player's factory wall (near end). */
        const val PLAYER_BASE_POSITION = 0.0f
    }

    /**
     * Runs target acquisition for every living unit.
     * Must be called before [CombatSystem.tick] so that shot resolution
     * can use the freshly resolved [UnitInstance.targetId].
     */
    fun tick(state: GameState) {
        for (unit in state.livingUnits) {
            if (unit.state == UnitState.DEAD) continue
            resolveTarget(unit, state)
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun resolveTarget(unit: UnitInstance, state: GameState) {
        val normalizedRange = CombatConstants.normalizedRange(unit.stats.range)
        val enemies = state.livingUnits.filter { it.team != unit.team }

        // --- 1. Wreckage blocking (Brute only) ---
        if (unit.type == UnitType.BRUTE) {
            val blockingWreckage = state.wreckage
                .filter { w -> !w.isCleared && w.lane == unit.lane }
                .filter { w -> isAheadAndInRange(unit, w.position, normalizedRange) }
                .minByOrNull { w -> distanceAhead(unit, w.position) }

            if (blockingWreckage != null) {
                unit.targetId = blockingWreckage.id
                unit.state = UnitState.ENGAGING
                return
            }
        }

        val validLanes = validTargetLanes(unit, state)

        // --- 2. Favoured enemy class in range ---
        val favoured = enemies
            .filter { e -> e.lane in validLanes }
            .filter { e -> isAheadAndInRange(unit, e.position, normalizedRange) }
            .filter { e -> unit.type.isFavoredAgainst(e.type) }
            .minByOrNull { e -> distanceAhead(unit, e.position) }

        if (favoured != null) {
            unit.targetId = favoured.id
            unit.state = UnitState.ENGAGING
            return
        }

        // --- 3. Nearest enemy in valid lanes ---
        val nearest = enemies
            .filter { e -> e.lane in validLanes }
            .filter { e -> isAheadAndInRange(unit, e.position, normalizedRange) }
            .minByOrNull { e -> distanceAhead(unit, e.position) }

        if (nearest != null) {
            unit.targetId = nearest.id
            unit.state = UnitState.ENGAGING
            return
        }

        // --- 4. Enemy base (player units) / Player base (enemy units) ---
        val baseId       = if (unit.team == Team.PLAYER) ENEMY_BASE_ID  else PLAYER_BASE_ID
        val basePosition = if (unit.team == Team.PLAYER) ENEMY_BASE_POSITION else PLAYER_BASE_POSITION
        if (isAheadAndInRange(unit, basePosition, normalizedRange)) {
            unit.targetId = baseId
            unit.state    = UnitState.ENGAGING
            return
        }

        // No target found — ensure unit is advancing
        unit.targetId = null
        unit.state = UnitState.ADVANCING
    }

    /**
     * Returns the set of lane indices this unit may fire into, given the
     * map's lane boundaries.
     *
     * Brute: own lane only, always.
     * Skirmisher / Artillery: own lane always; adjacent lane if the shared
     * boundary is a Space.
     */
    internal fun validTargetLanes(unit: UnitInstance, state: GameState): Set<Int> {
        if (unit.type == UnitType.BRUTE) return setOf(unit.lane)

        val lanes = mutableSetOf(unit.lane)
        val map = state.mission.mapConfig

        // Left adjacent lane (lane - 1): shared boundary is leftBoundary when
        // unit is in lane 1, rightBoundary when unit is in lane 2... we need
        // the boundary between unit.lane and unit.lane - 1.
        if (unit.lane > 0) {
            val boundary = if (unit.lane == 1) map.leftBoundary else map.rightBoundary
            if (boundary == LaneBoundary.Space) lanes.add(unit.lane - 1)
        }

        // Right adjacent lane (lane + 1)
        if (unit.lane < 2) {
            val boundary = if (unit.lane == 0) map.leftBoundary else map.rightBoundary
            if (boundary == LaneBoundary.Space) lanes.add(unit.lane + 1)
        }

        return lanes
    }

    /**
     * Returns true if [targetPosition] is ahead of [unit] (toward the enemy
     * end) and within [normalizedRange].
     */
    internal fun isAheadAndInRange(
        unit: UnitInstance,
        targetPosition: Float,
        normalizedRange: Float,
    ): Boolean {
        val dist = distanceAhead(unit, targetPosition)
        return dist >= 0f && dist <= normalizedRange
    }

    /**
     * Signed distance from [unit] to [targetPosition] in the direction the
     * unit faces. Positive means the target is ahead; negative means behind.
     */
    internal fun distanceAhead(unit: UnitInstance, targetPosition: Float): Float =
        if (unit.team == Team.PLAYER) targetPosition - unit.position
        else unit.position - targetPosition
}
