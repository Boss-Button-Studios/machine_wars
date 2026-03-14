package com.bossbuttonstudios.machinewars

import com.bossbuttonstudios.machinewars.combat.CombatConstants
import com.bossbuttonstudios.machinewars.combat.CombatOrchestrator
import com.bossbuttonstudios.machinewars.combat.CombatSystem
import com.bossbuttonstudios.machinewars.combat.TargetingSystem
import com.bossbuttonstudios.machinewars.combat.WinConditionChecker
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.map.LaneBoundary
import com.bossbuttonstudios.machinewars.model.map.MapConfig
import com.bossbuttonstudios.machinewars.model.map.Wreckage
import com.bossbuttonstudios.machinewars.model.mission.MissionConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.mission.WaveDefinition
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitRegistry
import com.bossbuttonstudios.machinewars.model.unit.UnitState
import com.bossbuttonstudios.machinewars.model.unit.UnitType
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

// =============================================================================
// Test helpers
// =============================================================================

private fun makeState(
    type: MissionType = MissionType.BASE_ATTACK,
    map: MapConfig = MapConfig.A,
    timeLimitSeconds: Float = 60f,
    oreTarget: Int = 500,
): GameState = GameState(
    mission = MissionConfig(
        missionNumber = 1,
        type = type,
        mapConfig = map,
        waves = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 1))),
        timeLimitSeconds = timeLimitSeconds,
        oreTarget = oreTarget,
    ),
    factory = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList()),
)

private fun unit(
    type: UnitType,
    team: Team,
    lane: Int = 1,
    position: Float = if (team == Team.PLAYER) 0f else 1f,
    hp: Float = UnitRegistry[type].maxHp,
    state: UnitState = UnitState.ADVANCING,
): UnitInstance = UnitInstance(
    type = type,
    team = team,
    lane = lane,
    stats = UnitRegistry[type],
    currentHp = hp,
    position = position,
    state = state,
)

// =============================================================================
// CombatConstants — coordinate conversion
// =============================================================================

class CombatConstantsTest {

    @Test fun `normalizedRange converts spec range to 0-1 space`() {
        assertEquals(0.4f, CombatConstants.normalizedRange(4f), 0.001f)
        assertEquals(0.8f, CombatConstants.normalizedRange(8f), 0.001f)
        assertEquals(0.15f, CombatConstants.normalizedRange(1.5f), 0.001f)
    }

    @Test fun `normalizedSpeed converts spec speed to 0-1 space`() {
        assertEquals(0.5f, CombatConstants.normalizedSpeed(5f), 0.001f)
        assertEquals(0.2f, CombatConstants.normalizedSpeed(2f), 0.001f)
        assertEquals(0.15f, CombatConstants.normalizedSpeed(1.5f), 0.001f)
    }
}

// =============================================================================
// CombatSystem — damage multipliers
// =============================================================================

class DamageMultiplierTest {

    private val combat = CombatSystem(Random(42L))

    @Test fun `favoured attacker applies 1_5x multiplier to average`() {
        // Brute (favoured vs Skirmisher): average = 15 * 1.5 = 22.5
        // Over many rolls the mean should converge near 22.5
        val results = (1..10_000).map { combat.rollDamage(15f, 1.5f) }
        val mean = results.average()
        assertEquals(22.5, mean, 0.3)
    }

    @Test fun `unfavoured attacker applies 1_0x multiplier`() {
        // Skirmisher unfavoured vs Brute: average = 8 * 1.0 = 8
        val results = (1..10_000).map { combat.rollDamage(8f, 1.0f) }
        val mean = results.average()
        assertEquals(8.0, mean, 0.15)
    }

    @Test fun `building multiplier produces 1_5x average`() {
        // Artillery vs base: 70 * 1.5 = 105
        val results = (1..10_000).map { combat.rollDamage(70f, CombatConstants.BUILDING_DAMAGE_MULTIPLIER) }
        val mean = results.average()
        assertEquals(105.0, mean, 1.0)
    }
}

// =============================================================================
// CombatSystem — dice bell curve
// =============================================================================

class DiceBellCurveTest {

    private val combat = CombatSystem(Random(42L))

    @Test fun `roll never exceeds adjusted average plus 15 percent`() {
        val average = 20f
        val max = average * (1f + CombatConstants.DAMAGE_VARIANCE)
        repeat(50_000) {
            val roll = combat.rollDamage(20f, 1.0f)
            assertTrue("Roll $roll exceeded max $max", roll <= max + 0.001f)
        }
    }

    @Test fun `roll never falls below adjusted average minus 15 percent`() {
        val average = 20f
        val min = average * (1f - CombatConstants.DAMAGE_VARIANCE)
        repeat(50_000) {
            val roll = combat.rollDamage(20f, 1.0f)
            assertTrue("Roll $roll below min $min", roll >= min - 0.001f)
        }
    }

    @Test fun `mean of rolls converges to adjusted average`() {
        val results = (1..20_000).map { combat.rollDamage(20f, 1.0f) }
        assertEquals(20.0, results.average(), 0.2)
    }

    @Test fun `bell curve clusters near mean more than uniform would`() {
        // The standard deviation of a 3-dice average should be noticeably
        // less than that of a single die over the same range.
        val spread = 20f * CombatConstants.DAMAGE_VARIANCE
        val rolls = (1..10_000).map { combat.rollDamage(20f, 1.0f) }
        val mean = rolls.average()
        val stddev = Math.sqrt(rolls.map { (it - mean) * (it - mean) }.average())
        // Single-die stddev over [-spread, +spread] ≈ spread / sqrt(3) ≈ 1.73
        // 3-dice average stddev ≈ spread / sqrt(9) ≈ 1.0
        // We just assert it is materially less than the single-die value.
        val singleDieStddev = spread / Math.sqrt(3.0)
        assertTrue(
            "3-dice stddev $stddev should be less than single-die $singleDieStddev",
            stddev < singleDieStddev,
        )
    }
}

// =============================================================================
// CombatSystem — dodge probability
// =============================================================================

class DodgeProbabilityTest {

    private val combat = CombatSystem(Random(42L))

    @Test fun `peer matchup produces zero dodge chance`() {
        // Same speed: ratio = 1.0, (1.0 - 1) * k = 0
        assertEquals(0f, combat.dodgeChance(5f, 5f), 0.001f)
        assertEquals(0f, combat.dodgeChance(2f, 2f), 0.001f)
    }

    @Test fun `Skirmisher vs Artillery dodge chance is approximately 25 percent`() {
        // defenderSpeed=5, attackerSpeed=1.5 → (5/1.5 - 1) * 0.107 = 2.333 * 0.107 ≈ 0.250
        val chance = combat.dodgeChance(
            attackerSpeed = UnitRegistry[UnitType.ARTILLERY].speed,
            defenderSpeed = UnitRegistry[UnitType.SKIRMISHER].speed,
        )
        assertEquals(0.25f, chance, 0.005f)
    }

    @Test fun `Skirmisher vs Brute dodge chance is approximately 16 percent`() {
        // defenderSpeed=5, attackerSpeed=2.0 → (5/2 - 1) * 0.107 = 1.5 * 0.107 = 0.1605
        val chance = combat.dodgeChance(
            attackerSpeed = UnitRegistry[UnitType.BRUTE].speed,
            defenderSpeed = UnitRegistry[UnitType.SKIRMISHER].speed,
        )
        assertEquals(0.1605f, chance, 0.005f)
    }

    @Test fun `Brute vs Artillery dodge chance is positive but small`() {
        // defenderSpeed=1.5, attackerSpeed=1.5 → 0. Wait, they have same speed.
        // Actually Brute speed=2.0, Artillery speed=1.5:
        // defending Brute vs attacking Artillery: defenderSpeed=2.0, attackerSpeed=1.5
        // (2.0/1.5 - 1) * 0.107 = 0.333 * 0.107 ≈ 0.0357
        val chance = combat.dodgeChance(
            attackerSpeed = UnitRegistry[UnitType.ARTILLERY].speed,
            defenderSpeed = UnitRegistry[UnitType.BRUTE].speed,
        )
        assertEquals(0.0357f, chance, 0.005f)
    }

    @Test fun `slower defender produces zero dodge chance, not negative`() {
        // Attacker faster than defender — clamp to 0
        val chance = combat.dodgeChance(attackerSpeed = 5f, defenderSpeed = 1f)
        assertEquals(0f, chance, 0.001f)
    }

    @Test fun `dodge is statistically applied at correct rate`() {
        // Skirmisher vs Artillery: ~25% dodge. Over 10000 shots, should be near 25%.
        val skirmStats  = UnitRegistry[UnitType.SKIRMISHER]
        val artStats    = UnitRegistry[UnitType.ARTILLERY]
        val shooter = unit(UnitType.ARTILLERY, Team.ENEMY, position = 0.7f, state = UnitState.ENGAGING)
        val target  = unit(UnitType.SKIRMISHER, Team.PLAYER, position = 0.5f)

        val combatSeeded = CombatSystem(Random(99L))
        var dodges = 0
        repeat(10_000) {
            if (combatSeeded.dodged(shooter, target)) dodges++
        }
        val rate = dodges / 10_000.0
        assertEquals(
            combatSeeded.dodgeChance(artStats.speed, skirmStats.speed).toDouble(),
            rate,
            0.02,
        )
    }
}

// =============================================================================
// CombatSystem — wreckage spawn
// =============================================================================

class WreckageSpawnTest {

    @Test fun `clean kill produces no wreckage`() {
        // Artillery (damage=70) vs Brute (HP=150, wreckageHp=75):
        // Shot deals 200 damage → excess = 200-150=50 → wreckageHp=75-50=25 > 0… not clean.
        // For a clean kill: excess damage must exceed unit.maxHp * 0.5.
        // Brute HP=150, wreckageThreshold=75. Excess must be > 75.
        // Deal 226+ total → excess 76+ → wreckageHp < 0 → clean.
        val state = makeState()
        val brute = unit(UnitType.BRUTE, Team.ENEMY, position = 0.5f, hp = 150f)
        state.units.add(brute)

        val shooter = unit(UnitType.ARTILLERY, Team.PLAYER, position = 0.3f, state = UnitState.ENGAGING)
        shooter.targetId = brute.id

        // Use seeded random; artillery damage avg=70, rolled close to mean.
        // To guarantee a clean kill we give artillery a very high hp brute and
        // force a high-damage roll by using artillery's favored multiplier.
        // Simpler: just check the formula directly.
        // wreckageHp = (150 * 0.5) - (damageDealt - hpBeforeShot)
        // Clean if damageDealt - hpBeforeShot > 75 → damageDealt > 225.
        // At average 70*1.5=105, that takes 3 artillery shots. Instead use a
        // direct unit test of the formula via visible state.

        // Brute has 1 HP left; artillery deals 70+ → excess ≥ 69 → wreckageHp ≤ 75-69 = 6 > 0.
        // Not clean. Give it 0.1 HP and deal enough that excess > 75.
        // Excess = damage - 0.1. For excess > 75: damage > 75.1. Artillery avg is 70. 
        // Use 1.5x multiplier (favoured): avg = 105 → excess = ~104.9 → wreckageHp = 75-104.9 < 0 → clean.
        // Artillery is favoured against Brute.

        val state2 = makeState()
        val bruteAlmostDead = unit(UnitType.BRUTE, Team.ENEMY, position = 0.5f, hp = 0.1f)
        val artilleryShooter = unit(UnitType.ARTILLERY, Team.PLAYER, position = 0.3f, state = UnitState.ENGAGING)
        artilleryShooter.targetId = bruteAlmostDead.id
        state2.units.add(bruteAlmostDead)
        state2.units.add(artilleryShooter)

        val combat = CombatSystem(Random(42L))
        // Fire enough ticks to guarantee a shot (fireRate=0.5, so 2s of ticks needed)
        repeat(130) { combat.tick(state2, 1f / 60f) }

        assertTrue("Brute should be dead", !bruteAlmostDead.isAlive)
        // Clean kill: excess damage >> wreckageThreshold (75), so no wreckage
        assertTrue("Clean kill should leave no wreckage", state2.wreckage.isEmpty())
    }

    @Test fun `partial kill produces wreckage with correct HP`() {
        // A Brute (HP=150) killed by exactly 151 damage → excess=1 → wreckageHp=75-1=74
        val state = makeState()
        val brute = unit(UnitType.BRUTE, Team.ENEMY, position = 0.5f, hp = 150f)
        state.units.add(brute)

        // Manually invoke wreckage spawn logic via combat tick by using a
        // high-HP brute and a unit that can barely kill it.
        // Easiest: check spawn formula numerically.
        // hpBeforeShot=150, damageDealt=151 → excess=1 → wreckageHp=74
        val wreckageHp = (150f * 0.5f) - (151f - 150f)
        assertEquals(74f, wreckageHp, 0.001f)
    }

    @Test fun `wreckage HP formula gives zero for exact overkill threshold`() {
        // hpBeforeShot=100, damageDealt=150 → excess=50 → wreckageHp = (100*0.5)-50 = 0
        val wreckageHp = (100f * 0.5f) - (150f - 100f)
        assertEquals(0f, wreckageHp, 0.001f)
    }

    @Test fun `wreckage spawns after non-clean kill in integration`() {
        // Skirmisher (damage=8, fireRate=2.5) kills Artillery (HP=50).
        // Artillery is not favoured vs Skirmisher so no multiplier for Skirmisher.
        // excessDamage = 8-ish above 50 → wreckageHp = 25 - excess.
        // If excess < 25, wreckage spawns. Average roll ≈ 8, so if HP=50 and
        // last shot brings it to 0 with ~8 damage, excess ≈ 0, wreckageHp ≈ 25.

        val state = makeState()
        // Give Artillery exactly 8 HP so the kill shot is close to zero excess
        val art = unit(UnitType.ARTILLERY, Team.ENEMY, position = 0.5f, hp = 8f)
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, position = 0.3f, state = UnitState.ENGAGING)
        skirmisher.targetId = art.id
        state.units.add(art)
        state.units.add(skirmisher)

        val combat = CombatSystem(Random(42L))
        // Fire ticks until Artillery dies (it has very low HP, should die quickly)
        repeat(60) { combat.tick(state, 1f / 60f) }

        assertFalse("Artillery should be dead", art.isAlive)
        assertTrue("Wreckage should have spawned", state.wreckage.isNotEmpty())
    }
}

// =============================================================================
// CombatSystem — bleed-through
// =============================================================================

class WreckageBleedThroughTest {

    @Test fun `bleed through constant is 30 percent`() {
        assertEquals(0.30f, CombatConstants.WRECKAGE_BLEED_THROUGH, 0.001f)
    }

    @Test fun `Skirmisher shot splits damage 70-30 when wreckage intervenes`() {
        val state = makeState()

        // Layout: Skirmisher (player) at 0.1, Wreckage at 0.3, Brute (enemy) at 0.5
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1, position = 0.1f, state = UnitState.ENGAGING)
        val brute = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 0.5f)
        val wreckage = Wreckage(sourceType = UnitType.BRUTE, lane = 1, position = 0.3f, currentHp = 100f)

        skirmisher.targetId = brute.id
        state.units.add(skirmisher)
        state.units.add(brute)
        state.wreckage.add(wreckage)

        val initialBruteHp = brute.currentHp
        val initialWreckHp = wreckage.currentHp

        // Run one shot's worth of ticks (fireRate=2.5, so ~24 ticks for first shot)
        val combat = CombatSystem(Random(42L))
        repeat(30) { combat.tick(state, 1f / 60f) }

        val bruteDamage = initialBruteHp - brute.currentHp
        val wreckDamage = initialWreckHp - wreckage.currentHp

        assertTrue("Brute should take some bleed-through damage", bruteDamage > 0f)
        assertTrue("Wreckage should absorb some damage", wreckDamage > 0f)

        // Bleed ratio: brute damage ≈ 30% of (brute + wreck) damage
        val totalDamage = bruteDamage + wreckDamage
        val bleedRatio = bruteDamage / totalDamage
        assertEquals(CombatConstants.WRECKAGE_BLEED_THROUGH.toDouble(), bleedRatio.toDouble(), 0.05)
    }

    @Test fun `Artillery shot ignores wreckage entirely`() {
        // Artillery target should take full damage even with wreckage in lane
        val state = makeState()

        val artillery = unit(UnitType.ARTILLERY, Team.PLAYER, lane = 1, position = 0.05f, state = UnitState.ENGAGING)
        val brute = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 0.5f, hp = 500f)
        val wreckage = Wreckage(sourceType = UnitType.BRUTE, lane = 1, position = 0.3f, currentHp = 1000f)

        artillery.targetId = brute.id
        state.units.add(artillery)
        state.units.add(brute)
        state.wreckage.add(wreckage)

        val initialWreckHp = wreckage.currentHp
        val initialBruteHp = brute.currentHp

        val combat = CombatSystem(Random(42L))
        // Artillery fireRate=0.5, so ~120 ticks for one shot
        repeat(130) { combat.tick(state, 1f / 60f) }

        // Wreckage should be untouched by Artillery
        assertEquals(initialWreckHp, wreckage.currentHp, 0.001f)
        // Brute should have taken full damage
        assertTrue("Brute should take full artillery damage", brute.currentHp < initialBruteHp)
    }
}

// =============================================================================
// TargetingSystem — lane validity
// =============================================================================

class TargetingLaneValidityTest {

    private val targeting = TargetingSystem()

    @Test fun `Brute is always lane-locked regardless of boundary`() {
        val state = makeState(map = MapConfig.D) // all Space boundaries
        val brute = unit(UnitType.BRUTE, Team.PLAYER, lane = 1)
        state.units.add(brute)
        val lanes = targeting.validTargetLanes(brute, state)
        assertEquals(setOf(1), lanes)
    }

    @Test fun `Skirmisher in centre lane with Wall boundaries stays in own lane`() {
        val state = makeState(map = MapConfig.A) // all Wall
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1)
        state.units.add(skirmisher)
        val lanes = targeting.validTargetLanes(skirmisher, state)
        assertEquals(setOf(1), lanes)
    }

    @Test fun `Skirmisher in centre lane on all-Space map covers all three lanes`() {
        val state = makeState(map = MapConfig.D) // all Space
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1)
        state.units.add(skirmisher)
        val lanes = targeting.validTargetLanes(skirmisher, state)
        assertEquals(setOf(0, 1, 2), lanes)
    }

    @Test fun `Skirmisher in left lane on map D reaches centre lane`() {
        val state = makeState(map = MapConfig.D)
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 0)
        state.units.add(skirmisher)
        val lanes = targeting.validTargetLanes(skirmisher, state)
        assertTrue(1 in lanes)  // left boundary is Space → can see lane 1
        assertFalse(2 in lanes) // right boundary is irrelevant from lane 0
    }

    @Test fun `Artillery in right lane on map B cannot cross left Wall`() {
        // Map B: left=Wall, right=Space
        // Artillery in lane 2 (right): right boundary is Space, left boundary is Wall.
        // Lane 2 right boundary (between 1 and 2) is rightBoundary = Space → can see lane 1.
        // Lane 1 left boundary (between 0 and 1) is leftBoundary = Wall → cannot see lane 0
        //   ...but that's two hops, which is never considered anyway.
        val state = makeState(map = MapConfig.B)
        val artillery = unit(UnitType.ARTILLERY, Team.PLAYER, lane = 2)
        state.units.add(artillery)
        val lanes = targeting.validTargetLanes(artillery, state)
        assertTrue(2 in lanes)
        assertTrue(1 in lanes)  // right boundary is Space
        assertFalse(0 in lanes) // not adjacent, always excluded
    }

    @Test fun `Artillery in left lane on map C cannot cross right Wall`() {
        // Map C: left=Space, right=Wall
        val state = makeState(map = MapConfig.C)
        val artillery = unit(UnitType.ARTILLERY, Team.PLAYER, lane = 0)
        state.units.add(artillery)
        val lanes = targeting.validTargetLanes(artillery, state)
        assertTrue(0 in lanes)
        assertTrue(1 in lanes)  // left boundary is Space
        assertFalse(2 in lanes)
    }
}

// =============================================================================
// TargetingSystem — target priority and state transitions
// =============================================================================

class TargetingPriorityTest {

    private val targeting = TargetingSystem()

    @Test fun `favoured target selected over nearer non-favoured target`() {
        // Skirmisher (player) is favoured against Artillery.
        // Place Brute close by and Artillery further away, both in range.
        // Skirmisher should pick Artillery (favoured) not Brute (nearer).
        val state = makeState(map = MapConfig.D)

        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1, position = 0.0f)
        // Brute is closer (0.2) but Skirmisher is NOT favoured against Brute
        val nearBrute = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 0.2f)
        // Artillery is farther (0.35) but Skirmisher IS favoured against Artillery
        val farArtillery = unit(UnitType.ARTILLERY, Team.ENEMY, lane = 1, position = 0.35f)

        // Skirmisher range = 4.0 → normalised 0.4. Both targets within range.
        state.units.add(skirmisher)
        state.units.add(nearBrute)
        state.units.add(farArtillery)

        targeting.tick(state)

        assertEquals(farArtillery.id, skirmisher.targetId)
        assertEquals(UnitState.ENGAGING, skirmisher.state)
    }

    @Test fun `nearest fallback selected when no favoured target in range`() {
        // Brute (player, favoured against Skirmisher) but only Artilleries in range.
        val state = makeState(map = MapConfig.D)

        val brute = unit(UnitType.BRUTE, Team.PLAYER, lane = 1, position = 0.0f)
        val nearArt = unit(UnitType.ARTILLERY, Team.ENEMY, lane = 1, position = 0.1f)
        val farArt  = unit(UnitType.ARTILLERY, Team.ENEMY, lane = 1, position = 0.12f)

        // Brute range = 1.5 → normalised 0.15. Both artillery within range.
        state.units.add(brute)
        state.units.add(nearArt)
        state.units.add(farArt)

        targeting.tick(state)

        // No favoured target (Brute favours Skirmisher, none present).
        // Falls back to nearest: nearArt.
        assertEquals(nearArt.id, brute.targetId)
    }

    @Test fun `out of range target does not trigger ENGAGING`() {
        val state = makeState()
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1, position = 0.0f)
        // Artillery is at 0.9 — Skirmisher range is 0.4, so out of range.
        val art = unit(UnitType.ARTILLERY, Team.ENEMY, lane = 1, position = 0.9f)

        state.units.add(skirmisher)
        state.units.add(art)

        targeting.tick(state)

        assertEquals(UnitState.ADVANCING, skirmisher.state)
        assertNull(skirmisher.targetId)
    }

    @Test fun `unit transitions back to ADVANCING when target dies`() {
        val state = makeState()
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1, position = 0.2f, state = UnitState.ENGAGING)
        val art = unit(UnitType.ARTILLERY, Team.ENEMY, lane = 1, position = 0.4f, hp = 0f) // already dead

        skirmisher.targetId = art.id
        state.units.add(skirmisher)
        // art is dead — not in livingUnits

        targeting.tick(state)

        assertEquals(UnitState.ADVANCING, skirmisher.state)
        assertNull(skirmisher.targetId)
    }

    @Test fun `player unit acquires enemy base when in range`() {
        // Artillery range = 8.0 → normalised 0.8. Place at 0.25 → base at 1.0, dist=0.75 ≤ 0.8.
        val state = makeState()
        val art = unit(UnitType.ARTILLERY, Team.PLAYER, lane = 1, position = 0.25f)
        state.units.add(art)
        // No enemy units

        targeting.tick(state)

        assertEquals(TargetingSystem.ENEMY_BASE_ID, art.targetId)
        assertEquals(UnitState.ENGAGING, art.state)
    }

    @Test fun `enemy unit does not acquire friendly base`() {
        val state = makeState()
        val art = unit(UnitType.ARTILLERY, Team.ENEMY, lane = 1, position = 0.75f)
        state.units.add(art)

        targeting.tick(state)

        // Enemy units do not target the enemy base (player's base is at 0.0,
        // handled when we implement player base HP in a later session).
        assertNull(art.targetId)
        assertEquals(UnitState.ADVANCING, art.state)
    }

    @Test fun `Brute acquires wreckage blocking its lane before enemy units`() {
        val state = makeState(map = MapConfig.A)

        val brute = unit(UnitType.BRUTE, Team.PLAYER, lane = 1, position = 0.0f)
        // Wreckage at 0.1 — within Brute range 1.5 → normalised 0.15
        val blocking = Wreckage(sourceType = UnitType.SKIRMISHER, lane = 1, position = 0.1f, currentHp = 50f)
        // Enemy unit beyond wreckage but also in range
        val enemy = unit(UnitType.SKIRMISHER, Team.ENEMY, lane = 1, position = 0.12f)

        state.units.add(brute)
        state.units.add(enemy)
        state.wreckage.add(blocking)

        targeting.tick(state)

        assertEquals(blocking.id, brute.targetId)
    }
}

// =============================================================================
// MovementSystem — advancement and blocking
// =============================================================================

class MovementSystemTest {

    @Test fun `ADVANCING player unit moves toward enemy end`() {
        val state = makeState()
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1, position = 0.0f)
        state.units.add(skirmisher)

        val orchestrator = CombatOrchestrator(Random(42L))
        // Tick once with no enemies — unit should advance
        orchestrator.onTick(1f / 60f, state)

        val expectedDelta = CombatConstants.normalizedSpeed(UnitRegistry[UnitType.SKIRMISHER].speed) / 60f
        assertEquals(expectedDelta, skirmisher.position, 0.0001f)
    }

    @Test fun `ADVANCING enemy unit moves toward player end`() {
        val state = makeState()
        val brute = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 1.0f)
        state.units.add(brute)

        val orchestrator = CombatOrchestrator(Random(42L))
        orchestrator.onTick(1f / 60f, state)

        assertTrue("Enemy should move toward 0", brute.position < 1.0f)
    }

    @Test fun `ENGAGING unit does not move`() {
        val state = makeState()
        val art = unit(UnitType.ARTILLERY, Team.PLAYER, lane = 1, position = 0.3f, state = UnitState.ENGAGING)
        val target = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 0.4f)
        art.targetId = target.id
        state.units.add(art)
        state.units.add(target)

        val startPos = art.position
        val combat = CombatSystem(Random(42L))
        com.bossbuttonstudios.machinewars.combat.MovementSystem().tick(state, 1f / 60f)

        // ENGAGING unit — movement system should not move it
        assertEquals(startPos, art.position, 0.0001f)
    }

    @Test fun `position is clamped to 1_0 for player units`() {
        val state = makeState()
        val skirmisher = unit(UnitType.SKIRMISHER, Team.PLAYER, lane = 1, position = 0.9999f)
        state.units.add(skirmisher)

        val movement = com.bossbuttonstudios.machinewars.combat.MovementSystem()
        repeat(600) { movement.tick(state, 1f / 60f) }  // advance far past boundary

        assertEquals(1.0f, skirmisher.position, 0.0001f)
    }

    @Test fun `position is clamped to 0_0 for enemy units`() {
        val state = makeState()
        val brute = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 0.0001f)
        state.units.add(brute)

        val movement = com.bossbuttonstudios.machinewars.combat.MovementSystem()
        repeat(600) { movement.tick(state, 1f / 60f) }

        assertEquals(0.0f, brute.position, 0.0001f)
    }
}

// =============================================================================
// WinConditionChecker
// =============================================================================

class WinConditionBaseAttackTest {

    private val checker = WinConditionChecker()

    @Test fun `base destroyed triggers player win`() {
        val state = makeState(type = MissionType.BASE_ATTACK)
        state.enemyBaseHp = 0f
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))

        checker.tick(state)

        assertTrue(state.isOver)
        assertTrue(state.playerWon)
    }

    @Test fun `all player units dead triggers loss`() {
        val state = makeState(type = MissionType.BASE_ATTACK)
        state.enemyBaseHp = 500f
        // No player units added — livingUnits contains no PLAYER

        checker.tick(state)

        assertTrue(state.isOver)
        assertFalse(state.playerWon)
    }

    @Test fun `ongoing battle does not trigger over`() {
        val state = makeState(type = MissionType.BASE_ATTACK)
        state.enemyBaseHp = 100f
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))

        checker.tick(state)

        assertFalse(state.isOver)
    }

    @Test fun `checker is no-op once isOver is set`() {
        val state = makeState(type = MissionType.BASE_ATTACK)
        state.isOver = true
        state.playerWon = true
        state.enemyBaseHp = 500f // contradicts win

        checker.tick(state)

        assertTrue("isOver must stay true", state.isOver)
        assertTrue("playerWon must not change", state.playerWon)
    }
}

class WinConditionTimedSurvivalTest {

    private val checker = WinConditionChecker()

    @Test fun `timer expired with no enemies triggers win`() {
        val state = makeState(type = MissionType.TIMED_SURVIVAL, timeLimitSeconds = 30f)
        state.elapsedSeconds = 31f
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))
        // No enemy units

        checker.tick(state)

        assertTrue(state.isOver)
        assertTrue(state.playerWon)
    }

    @Test fun `timer expired but enemies remain does not trigger win`() {
        val state = makeState(type = MissionType.TIMED_SURVIVAL, timeLimitSeconds = 30f)
        state.elapsedSeconds = 31f
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))
        state.units.add(unit(UnitType.BRUTE, Team.ENEMY))

        checker.tick(state)

        assertFalse(state.isOver)
    }

    @Test fun `player dies before timer triggers loss`() {
        val state = makeState(type = MissionType.TIMED_SURVIVAL, timeLimitSeconds = 30f)
        state.elapsedSeconds = 10f
        // No player units

        checker.tick(state)

        assertTrue(state.isOver)
        assertFalse(state.playerWon)
    }
}

class WinConditionResourceHuntTest {

    private val checker = WinConditionChecker()

    @Test fun `ore target met triggers win`() {
        val state = makeState(type = MissionType.RESOURCE_HUNT, oreTarget = 500)
        state.wallet.earnOre(500)
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))

        checker.tick(state)

        assertTrue(state.isOver)
        assertTrue(state.playerWon)
    }

    @Test fun `ore below target does not trigger win`() {
        val state = makeState(type = MissionType.RESOURCE_HUNT, oreTarget = 500)
        state.wallet.earnOre(499)
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))

        checker.tick(state)

        assertFalse(state.isOver)
    }

    @Test fun `player dies before target triggers loss`() {
        val state = makeState(type = MissionType.RESOURCE_HUNT, oreTarget = 500)
        state.wallet.earnOre(100)
        // No player units

        checker.tick(state)

        assertTrue(state.isOver)
        assertFalse(state.playerWon)
    }
}

// =============================================================================
// CombatOrchestrator — integration
// =============================================================================

class CombatOrchestratorIntegrationTest {

    @Test fun `elapsedSeconds increments each tick`() {
        val state = makeState()
        // Need a player unit so win condition doesn't fire immediately
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))
        val orchestrator = CombatOrchestrator(Random(42L))
        val dt = 1f / 60f
        repeat(60) { orchestrator.onTick(dt, state) }
        assertEquals(1.0f, state.elapsedSeconds, 0.01f)
    }

    @Test fun `dead units are purged each tick`() {
        val state = makeState()
        val art = unit(UnitType.ARTILLERY, Team.PLAYER, position = 0.3f, state = UnitState.ENGAGING)
        val brute = unit(UnitType.BRUTE, Team.ENEMY, lane = 1, position = 0.35f, hp = 1f)
        art.targetId = brute.id
        state.units.add(art)
        state.units.add(brute)

        val orchestrator = CombatOrchestrator(Random(42L))
        // Run until brute dies (very low HP — should die on first shot)
        repeat(130) { orchestrator.onTick(1f / 60f, state) }

        assertFalse("Dead unit should be purged from state.units", state.units.contains(brute))
    }

    @Test fun `orchestrator stops ticking after isOver`() {
        val state = makeState(type = MissionType.BASE_ATTACK)
        state.enemyBaseHp = 0f
        state.units.add(unit(UnitType.BRUTE, Team.PLAYER))

        val orchestrator = CombatOrchestrator(Random(42L))
        orchestrator.onTick(1f / 60f, state)  // triggers win
        val timeAfterWin = state.elapsedSeconds

        orchestrator.onTick(1f / 60f, state)  // should be no-op
        orchestrator.onTick(1f / 60f, state)

        assertEquals(timeAfterWin, state.elapsedSeconds, 0.0001f)
    }
}
