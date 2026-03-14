package com.bossbuttonstudios.machinewars.combat

import com.bossbuttonstudios.machinewars.core.GameState
import kotlin.random.Random

/**
 * Wires the combat systems into a single [onTick] callback suitable for
 * passing directly to [com.bossbuttonstudios.machinewars.core.GameLoop].
 *
 * Tick execution order:
 *  1. [TargetingSystem]    — resolves targets, transitions ADVANCING ↔ ENGAGING.
 *  2. [CombatSystem]       — decrements cooldowns, fires shots, applies damage,
 *                            spawns wreckage, marks DEAD.
 *  3. [MovementSystem]     — advances ADVANCING units.
 *  4. [WinConditionChecker]— evaluates terminal conditions; sets isOver.
 *  5. [GameState.purgeDeadEntities] — removes dead units and cleared wreckage.
 *  6. Increments [GameState.elapsedSeconds].
 *
 * Targeting before combat ensures that shot resolution uses freshly acquired
 * targets, including units that became ADVANCING after losing a target last
 * tick. Movement after combat means a unit that just killed its target can
 * immediately start advancing in the same tick rather than waiting a full tick.
 *
 * [random] is injectable so integration tests can run deterministically.
 */
class CombatOrchestrator(random: Random = Random.Default) {

    private val targeting = TargetingSystem()
    private val combat    = CombatSystem(random)
    private val movement  = MovementSystem()
    private val winCheck  = WinConditionChecker()

    /**
     * One full simulation tick. Signature matches the [GameLoop] onTick lambda.
     */
    fun onTick(dt: Float, state: GameState) {
        if (state.isOver) return

        targeting.tick(state)
        combat.tick(state, dt)
        movement.tick(state, dt)
        winCheck.tick(state)
        state.purgeDeadEntities()

        state.elapsedSeconds += dt
    }
}
