package com.bossbuttonstudios.machinewars.combat

import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.model.map.Wreckage
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitState
import com.bossbuttonstudios.machinewars.model.unit.UnitType
import kotlin.random.Random

/**
 * Resolves shot firing and damage application for all ENGAGING units.
 *
 * Shot resolution order (spec §2.1):
 *  1. Decrement [UnitInstance.shotCooldown]; fire when it crosses zero.
 *  2. Identify target: unit, wreckage, or enemy base.
 *  3. Apply damage multiplier (class advantage or building).
 *  4. Roll damage: 3-dice bell curve, ±15% around adjusted average (spec §1.5).
 *  5. Dodge check: defender rolls against the relative-speed formula (spec §1.4).
 *     Wreckage and the base never dodge.
 *  6. Wreckage bleed-through: if a Skirmisher's target unit has wreckage in the
 *     same lane directly between attacker and target, 70% of the rolled damage
 *     hits the wreckage and 30% bleeds through to the unit (spec §3.2).
 *  7. Apply damage. On kill, run the wreckage spawn check (spec §3.1).
 *
 * [random] is injectable for deterministic testing.
 */
class CombatSystem(private val random: Random = Random.Default) {

    /**
     * Processes one tick of combat for all ENGAGING units.
     * Must be called after [TargetingSystem.tick].
     */
    fun tick(state: GameState, dt: Float) {
        // Snapshot the living list; kills during this tick are deferred to
        // purgeDeadEntities() in the orchestrator — we do not remove mid-tick.
        val shooters = state.livingUnits.filter { it.state == UnitState.ENGAGING }

        for (shooter in shooters) {
            shooter.shotCooldown -= dt
            if (shooter.shotCooldown > 0f) continue
            shooter.shotCooldown += (1f / shooter.stats.fireRate)

            val targetId = shooter.targetId ?: continue
            fireShot(shooter, targetId, state)
        }
    }

    // -------------------------------------------------------------------------
    // Shot resolution
    // -------------------------------------------------------------------------

    private fun fireShot(shooter: UnitInstance, targetId: java.util.UUID, state: GameState) {

        // --- Wreckage target (Brute only path from TargetingSystem) ---
        val wreckageTarget = state.wreckage.find { it.id == targetId && !it.isCleared }
        if (wreckageTarget != null) {
            val damage = rollDamage(shooter.stats.damage, multiplier = 1f)
            applyDamageToWreckage(wreckageTarget, damage, state)
            return
        }

        // --- Enemy base ---
        if (targetId == TargetingSystem.ENEMY_BASE_ID) {
            val multiplier = if (shooter.type == UnitType.ARTILLERY)
                CombatConstants.BUILDING_DAMAGE_MULTIPLIER else 1f
            val damage = rollDamage(shooter.stats.damage, multiplier)
            state.enemyBaseHp -= damage
            if (state.enemyBaseHp < 0f) state.enemyBaseHp = 0f
            return
        }

        // --- Player base (factory wall) ---
        if (targetId == TargetingSystem.PLAYER_BASE_ID) {
            val multiplier = if (shooter.type == UnitType.ARTILLERY)
                CombatConstants.BUILDING_DAMAGE_MULTIPLIER else 1f
            val damage = rollDamage(shooter.stats.damage, multiplier)
            state.playerBaseHp -= damage
            if (state.playerBaseHp < 0f) state.playerBaseHp = 0f
            return
        }

        // --- Unit target ---
        val target = state.livingUnits.find { it.id == targetId } ?: return

        // Class advantage multiplier
        val multiplier = if (shooter.type.isFavoredAgainst(target.type))
            CombatConstants.DAMAGE_MULTIPLIER_FAVORED else 1f

        val rolledDamage = rollDamage(shooter.stats.damage, multiplier)

        // Dodge check — only unit targets dodge
        if (dodged(shooter, target)) return

        // Wreckage bleed-through for Skirmisher shots (spec §3.2)
        if (shooter.type == UnitType.SKIRMISHER) {
            val interveningWreckage = findInterveningWreckage(shooter, target, state)
            if (interveningWreckage != null) {
                val bleedDamage = rolledDamage * CombatConstants.WRECKAGE_BLEED_THROUGH
                val wreckDamage = rolledDamage * (1f - CombatConstants.WRECKAGE_BLEED_THROUGH)
                applyDamageToWreckage(interveningWreckage, wreckDamage, state)
                applyDamageToUnit(target, bleedDamage, shooter.stats.damage * multiplier, state)
                return
            }
        }

        applyDamageToUnit(target, rolledDamage, shooter.stats.damage * multiplier, state)
    }

    // -------------------------------------------------------------------------
    // Damage application
    // -------------------------------------------------------------------------

    private fun applyDamageToUnit(
        target: UnitInstance,
        damage: Float,
        adjustedAverage: Float,
        state: GameState,
    ) {
        val prevHp = target.currentHp
        target.currentHp -= damage
        if (target.currentHp <= 0f) {
            target.currentHp = 0f
            target.state = UnitState.DEAD
            spawnWreckage(target, prevHp, damage, adjustedAverage, state)
        }
    }

    private fun applyDamageToWreckage(wreckage: Wreckage, damage: Float, state: GameState) {
        wreckage.currentHp -= damage
        if (wreckage.currentHp < 0f) wreckage.currentHp = 0f
        // isCleared is computed from currentHp <= 0; purge happens in orchestrator
    }

    // -------------------------------------------------------------------------
    // Wreckage spawn (spec §3.1)
    // -------------------------------------------------------------------------

    private fun spawnWreckage(
        unit: UnitInstance,
        hpBeforeShot: Float,
        damageDealt: Float,
        adjustedAverage: Float,
        state: GameState,
    ) {
        // Excess damage = amount beyond what was needed to reduce HP to exactly 0.
        // We use hpBeforeShot as the "needed" amount, so excess = damage - hpBeforeShot.
        val excessDamage = (damageDealt - hpBeforeShot).coerceAtLeast(0f)
        val wreckageHp = (unit.stats.maxHp * 0.5f) - excessDamage
        if (wreckageHp > 0f) {
            state.wreckage.add(
                Wreckage(
                    sourceType = unit.type,
                    lane = unit.lane,
                    position = unit.position,
                    currentHp = wreckageHp,
                )
            )
        }
        // wreckageHp <= 0 → clean kill, no wreckage spawned
    }

    // -------------------------------------------------------------------------
    // Roll helpers — internal for direct test access
    // -------------------------------------------------------------------------

    /**
     * Rolls damage using a 3-dice bell curve, ±15% around [adjustedAverage].
     * The [multiplier] is applied to [baseDamage] before rolling, not to
     * the roll itself (spec §1.5).
     */
    internal fun rollDamage(baseDamage: Float, multiplier: Float): Float {
        val average = baseDamage * multiplier
        val spread = average * CombatConstants.DAMAGE_VARIANCE

        // Roll DAMAGE_DICE_COUNT dice, each uniform on [-spread, +spread],
        // then average to produce a bell-curve distribution.
        var total = 0f
        repeat(CombatConstants.DAMAGE_DICE_COUNT) {
            total += random.nextFloat() * 2f * spread - spread
        }
        val offset = total / CombatConstants.DAMAGE_DICE_COUNT

        return (average + offset).coerceIn(average - spread, average + spread)
    }

    /**
     * Returns true if this shot was dodged.
     *
     * Formula (spec §1.4):
     *   dodge% = max(0, (defenderSpeed / attackerSpeed − 1) × k)
     */
    internal fun dodged(shooter: UnitInstance, target: UnitInstance): Boolean {
        val chance = dodgeChance(shooter.stats.speed, target.stats.speed)
        return random.nextFloat() < chance
    }

    /**
     * Pure dodge probability calculation. Extracted for direct testing.
     */
    internal fun dodgeChance(attackerSpeed: Float, defenderSpeed: Float): Float =
        (((defenderSpeed / attackerSpeed) - 1f) * CombatConstants.DODGE_K).coerceAtLeast(0f)

    // -------------------------------------------------------------------------
    // Wreckage interposition check
    // -------------------------------------------------------------------------

    /**
     * Finds wreckage that lies between [shooter] and [target] in the same lane.
     * Returns the closest intervening wreckage to the shooter, or null if none.
     *
     * Only used for Skirmisher bleed-through logic. Brute is blocked before
     * reaching a unit; Artillery ignores wreckage entirely.
     */
    private fun findInterveningWreckage(
        shooter: UnitInstance,
        target: UnitInstance,
        state: GameState,
    ): Wreckage? {
        if (shooter.lane != target.lane) return null // cross-lane shots don't interact

        val shooterPos = shooter.position
        val targetPos = target.position

        val (nearPos, farPos) = if (shooterPos < targetPos)
            shooterPos to targetPos else targetPos to shooterPos

        return state.wreckage
            .filter { w -> !w.isCleared && w.lane == shooter.lane }
            .filter { w -> w.position > nearPos && w.position < farPos }
            .minByOrNull { w ->
                // Closest to shooter
                if (shooter.team == Team.PLAYER) w.position - shooterPos
                else shooterPos - w.position
            }
    }
}
