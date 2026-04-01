package com.bossbuttonstudios.machinewars.combat

import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.core.OreChangedEvent
import com.bossbuttonstudios.machinewars.core.WaveClearedEvent
import com.bossbuttonstudios.machinewars.core.WaveSpawnedEvent
import com.bossbuttonstudios.machinewars.model.factory.MachineType
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitRegistry
import com.bossbuttonstudios.machinewars.model.unit.UnitType

/**
 * Manages enemy wave spawning, player unit production, and ore generation.
 *
 * Stateless system — all state lives in [GameState]. Called once per tick
 * from [com.bossbuttonstudios.machinewars.GameActivity] after the drivetrain
 * solve and before combat.
 *
 * Responsibilities:
 *  1. Spawn enemy units from the current [com.bossbuttonstudios.machinewars.model.mission.WaveDefinition]
 *     at the configured interval.
 *  2. Detect wave-clear (all enemies dead after full spawn) and enter
 *     [GameState.betweenWaves], rotating the store.
 *  3. Spawn player units from connected combat machines via token accumulation.
 *  4. Generate ore from connected miner machines via fractional accumulation.
 *
 * Does nothing when [GameState.betweenWaves] is true or [GameState.isOver] is true.
 */
class WaveManager {

    fun tick(dt: Float, state: GameState, eventBus: EventBus) {
        if (state.isOver || state.betweenWaves) return

        tickEnemySpawning(dt, state, eventBus)
        tickPlayerSpawning(dt, state)
        tickMinerOre(dt, state, eventBus)
        checkWaveClear(state, eventBus)
    }

    // ---- Enemy spawning -------------------------------------------------------

    private fun tickEnemySpawning(dt: Float, state: GameState, eventBus: EventBus) {
        val waves = state.mission.waves
        if (state.currentWaveIndex >= waves.size) return

        val wave = waves[state.currentWaveIndex]
        val totalUnits = wave.composition.values.sum()
        if (state.waveSpawnCursor >= totalUnits) return  // all units for this wave already queued

        state.timeUntilNextSpawn -= dt
        if (state.timeUntilNextSpawn > 0f) return

        val unitType = unitAtCursor(wave.composition, state.waveSpawnCursor) ?: return
        val lane = state.waveSpawnCursor % LANE_COUNT
        state.units.add(
            UnitInstance(
                type  = unitType,
                team  = Team.ENEMY,
                lane  = lane,
                stats = UnitRegistry[unitType],
            )
        )
        state.waveSpawnCursor++
        state.timeUntilNextSpawn = wave.spawnInterval

        if (state.waveSpawnCursor >= totalUnits) {
            eventBus.post(WaveSpawnedEvent(state.currentWaveIndex))
        }
    }

    /**
     * Returns the [UnitType] at sequential position [index] within [composition].
     * Iterates types in declaration order; e.g. {BRUTE: 2, SKIRMISHER: 1}
     * yields BRUTE(0), BRUTE(1), SKIRMISHER(2).
     */
    internal fun unitAtCursor(composition: Map<UnitType, Int>, index: Int): UnitType? {
        var remaining = index
        for ((type, count) in composition) {
            if (remaining < count) return type
            remaining -= count
        }
        return null
    }

    // ---- Player unit production from machines ---------------------------------

    private fun tickPlayerSpawning(dt: Float, state: GameState) {
        for (machine in state.factory.machines) {
            val unitType = unitTypeForMachine(machine.type) ?: continue
            if (machine.assignedLane < 0) continue           // unassigned — no output
            if (machine.outputRatePerSec <= 0f) continue

            val prev  = state.spawnTokens.getOrDefault(machine.id, 0f)
            val token = prev + machine.outputRatePerSec * dt
            if (token >= 1f) {
                state.units.add(
                    UnitInstance(
                        type  = unitType,
                        team  = Team.PLAYER,
                        lane  = machine.assignedLane,
                        stats = UnitRegistry[unitType],
                    )
                )
                state.spawnTokens[machine.id] = token - 1f
            } else {
                state.spawnTokens[machine.id] = token
            }
        }
    }

    // ---- Ore generation from miners -------------------------------------------

    private fun tickMinerOre(dt: Float, state: GameState, eventBus: EventBus) {
        var oreGenerated = false
        for (machine in state.factory.machines) {
            if (machine.type != MachineType.MINER) continue
            if (machine.outputRatePerSec <= 0f) continue
            state.oreAccumulator += machine.outputRatePerSec * dt
        }
        val whole = state.oreAccumulator.toInt()
        if (whole > 0) {
            state.wallet.earnOre(whole)
            state.oreAccumulator -= whole
            oreGenerated = true
        }
        if (oreGenerated) eventBus.post(OreChangedEvent(state.wallet.ore))
    }

    // ---- Wave-clear detection -------------------------------------------------

    private fun checkWaveClear(state: GameState, eventBus: EventBus) {
        val waves = state.mission.waves
        if (state.currentWaveIndex >= waves.size) return

        val wave = waves[state.currentWaveIndex]
        val totalUnits = wave.composition.values.sum()
        if (state.waveSpawnCursor < totalUnits) return              // wave not fully spawned yet
        if (state.livingUnits.any { it.team == Team.ENEMY }) return // enemies still alive

        // Wave cleared — enter between-waves if more waves remain.
        val nextWave = state.currentWaveIndex + 1
        if (nextWave < waves.size) {
            state.betweenWaves = true
            state.store.rotate(state.elapsedSeconds.toLong())
            eventBus.post(WaveClearedEvent(state.currentWaveIndex))
        }
        // If no more waves, WinConditionChecker (in CombatOrchestrator) handles the terminal condition.
    }

    companion object {
        private const val LANE_COUNT = 3

        /** Maps a combat [MachineType] to the [UnitType] it produces. */
        fun unitTypeForMachine(type: MachineType): UnitType? = when (type) {
            MachineType.COMBAT_BRUTE       -> UnitType.BRUTE
            MachineType.COMBAT_SKIRMISHER  -> UnitType.SKIRMISHER
            MachineType.COMBAT_ARTILLERY   -> UnitType.ARTILLERY
            else                           -> null
        }
    }
}
