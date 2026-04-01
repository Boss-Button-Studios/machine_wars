package com.bossbuttonstudios.machinewars

import com.bossbuttonstudios.machinewars.combat.WaveManager
import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.core.OreChangedEvent
import com.bossbuttonstudios.machinewars.core.WaveClearedEvent
import com.bossbuttonstudios.machinewars.core.WaveSpawnedEvent
import com.bossbuttonstudios.machinewars.model.economy.Wallet
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.factory.Machine
import com.bossbuttonstudios.machinewars.model.factory.MachineType
import com.bossbuttonstudios.machinewars.model.map.MapConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.mission.WaveDefinition
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session 6 — wave management, store, and factory grid touch.
 *
 * Factory grid touch lives in GameView (not JVM-testable). All WaveManager
 * logic and store operations are tested here.
 */
class Session6Tests {

    private val waveManager = WaveManager()

    // ---- Helpers -------------------------------------------------------------

    private fun buildState(
        waves: List<WaveDefinition>,
        machines: List<Machine> = emptyList(),
        initialOre: Int = 0,
    ): GameState {
        val mission = MissionConfig(
            missionNumber = 1,
            type          = MissionType.BASE_ATTACK,
            mapConfig     = MapConfig.A,
            waves         = waves,
        )
        val factory = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = machines)
        return GameState(
            mission = mission,
            factory = factory,
            wallet  = Wallet(initialOre = initialOre),
        )
    }

    // ---- unitAtCursor --------------------------------------------------------

    @Test
    fun `unitAtCursor returns first type at index 0`() {
        val comp = mapOf(UnitType.BRUTE to 2, UnitType.SKIRMISHER to 1)
        assertEquals(UnitType.BRUTE, waveManager.unitAtCursor(comp, 0))
    }

    @Test
    fun `unitAtCursor transitions to second type after first type count`() {
        val comp = mapOf(UnitType.BRUTE to 2, UnitType.SKIRMISHER to 1)
        assertEquals(UnitType.BRUTE,      waveManager.unitAtCursor(comp, 1))
        assertEquals(UnitType.SKIRMISHER, waveManager.unitAtCursor(comp, 2))
    }

    @Test
    fun `unitAtCursor returns null beyond total count`() {
        val comp = mapOf(UnitType.BRUTE to 1)
        assertNull(waveManager.unitAtCursor(comp, 1))
    }

    // ---- Enemy spawning ------------------------------------------------------

    @Test
    fun `first enemy spawns on first tick when timeUntilNextSpawn is 0`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 2f)
        ))
        val bus = EventBus()
        waveManager.tick(0.016f, state, bus)
        assertEquals(1, state.units.size)
        assertEquals(Team.ENEMY, state.units[0].team)
        assertEquals(UnitType.BRUTE, state.units[0].type)
    }

    @Test
    fun `second enemy waits for spawn interval`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 2), spawnInterval = 1f)
        ))
        val bus = EventBus()
        waveManager.tick(0.016f, state, bus)     // spawns first
        assertEquals(1, state.units.size)
        waveManager.tick(0.5f, state, bus)       // interval=1s, only 0.5s elapsed
        assertEquals(1, state.units.size)
        waveManager.tick(0.6f, state, bus)       // cumulative > 1s → second spawns
        assertEquals(2, state.units.size)
    }

    @Test
    fun `WaveSpawnedEvent posted when last unit in wave is queued`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 1f)
        ))
        val bus = EventBus()
        var spawnedWave = -1
        bus.on<WaveSpawnedEvent> { spawnedWave = it.waveIndex }
        waveManager.tick(0.016f, state, bus)
        assertEquals(0, spawnedWave)
    }

    @Test
    fun `no more enemies spawn after wave cursor reaches total`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f)
        ))
        val bus = EventBus()
        waveManager.tick(0.016f, state, bus)
        waveManager.tick(0.016f, state, bus)
        assertEquals(1, state.units.size)
    }

    @Test
    fun `enemies are assigned to lanes in round-robin order`() {
        val state = buildState(waves = listOf(
            WaveDefinition(
                composition   = mapOf(UnitType.BRUTE to 3),
                spawnInterval = 0f,
            )
        ))
        val bus = EventBus()
        repeat(3) { waveManager.tick(0.016f, state, bus) }
        assertEquals(0, state.units[0].lane)
        assertEquals(1, state.units[1].lane)
        assertEquals(2, state.units[2].lane)
    }

    // ---- Wave-clear detection ------------------------------------------------

    @Test
    fun `betweenWaves becomes true when wave fully spawned and no enemies alive`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
        ))
        val bus = EventBus()
        waveManager.tick(0.016f, state, bus)
        assertFalse(state.betweenWaves)   // enemy still alive

        state.units.clear()               // simulate enemy dying
        waveManager.tick(0.016f, state, bus)
        assertTrue(state.betweenWaves)
    }

    @Test
    fun `WaveClearedEvent posted when wave clears with more waves remaining`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
        ))
        val bus = EventBus()
        var clearedIndex = -1
        bus.on<WaveClearedEvent> { clearedIndex = it.waveIndex }
        waveManager.tick(0.016f, state, bus)
        state.units.clear()
        waveManager.tick(0.016f, state, bus)
        assertEquals(0, clearedIndex)
    }

    @Test
    fun `store is rotated when wave clears`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
        ))
        val bus = EventBus()
        assertTrue(state.store.rotation.isEmpty())
        waveManager.tick(0.016f, state, bus)
        state.units.clear()
        waveManager.tick(0.016f, state, bus)
        assertEquals(3, state.store.rotation.size)
    }

    @Test
    fun `betweenWaves does not trigger on final wave clear`() {
        // Only one wave defined — last wave clearing does not set betweenWaves
        // (WinConditionChecker handles the game-over, not WaveManager).
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 1), spawnInterval = 0f),
        ))
        val bus = EventBus()
        waveManager.tick(0.016f, state, bus)
        state.units.clear()
        waveManager.tick(0.016f, state, bus)
        assertFalse(state.betweenWaves)
    }

    @Test
    fun `WaveManager does nothing when betweenWaves is true`() {
        val state = buildState(waves = listOf(
            WaveDefinition(composition = mapOf(UnitType.BRUTE to 2), spawnInterval = 0f),
        ))
        val bus = EventBus()
        state.betweenWaves = true
        waveManager.tick(1f, state, bus)
        assertTrue(state.units.isEmpty())
    }

    // ---- Player spawning from machines ---------------------------------------

    @Test
    fun `combat machine produces player unit when token reaches 1`() {
        val machine = Machine(
            type            = MachineType.COMBAT_BRUTE,
            gridX           = 2,
            gridY           = 0,
            outputRatePerSec = 1f,
            assignedLane    = 0,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(machine),
        )
        val bus = EventBus()

        waveManager.tick(0.5f, state, bus)
        assertEquals(0, state.units.count { it.team == Team.PLAYER })

        waveManager.tick(0.6f, state, bus)   // total > 1s → token fires
        assertEquals(1, state.units.count { it.team == Team.PLAYER })
    }

    @Test
    fun `combat machine does not spawn when unassigned`() {
        val machine = Machine(
            type            = MachineType.COMBAT_SKIRMISHER,
            gridX           = 2,
            gridY           = 0,
            outputRatePerSec = 10f,
            assignedLane    = -1,  // unassigned
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(machine),
        )
        val bus = EventBus()
        waveManager.tick(2f, state, bus)
        assertEquals(0, state.units.count { it.team == Team.PLAYER })
    }

    @Test
    fun `combat machine does not spawn when outputRate is zero`() {
        val machine = Machine(
            type            = MachineType.COMBAT_ARTILLERY,
            gridX           = 2,
            gridY           = 0,
            outputRatePerSec = 0f,
            assignedLane    = 1,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(machine),
        )
        val bus = EventBus()
        waveManager.tick(5f, state, bus)
        assertEquals(0, state.units.count { it.team == Team.PLAYER })
    }

    @Test
    fun `spawned player unit has correct type for machine`() {
        val machine = Machine(
            type            = MachineType.COMBAT_ARTILLERY,
            gridX           = 2,
            gridY           = 0,
            outputRatePerSec = 2f,
            assignedLane    = 2,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(machine),
        )
        val bus = EventBus()
        waveManager.tick(1f, state, bus)
        val playerUnit = state.units.firstOrNull { it.team == Team.PLAYER }
        assertNotNull(playerUnit)
        assertEquals(UnitType.ARTILLERY, playerUnit!!.type)
        assertEquals(2, playerUnit.lane)
    }

    @Test
    fun `token remainder carries forward after spawn`() {
        val machine = Machine(
            type            = MachineType.COMBAT_BRUTE,
            gridX           = 2,
            gridY           = 0,
            outputRatePerSec = 1f,
            assignedLane    = 0,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(machine),
        )
        val bus = EventBus()
        waveManager.tick(1.3f, state, bus)    // token = 1.3 → spawn, remainder = 0.3
        assertEquals(1, state.units.count { it.team == Team.PLAYER })
        waveManager.tick(0.8f, state, bus)    // token = 0.3 + 0.8 = 1.1 → second spawn
        assertEquals(2, state.units.count { it.team == Team.PLAYER })
    }

    // ---- Ore generation from miners ------------------------------------------

    @Test
    fun `miner generates ore when outputRate is nonzero`() {
        val miner = Machine(
            type            = MachineType.MINER,
            gridX           = 4,
            gridY           = 2,
            outputRatePerSec = 5f,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(miner),
        )
        val bus = EventBus()
        waveManager.tick(1f, state, bus)
        assertEquals(5, state.wallet.ore)
    }

    @Test
    fun `OreChangedEvent posted when miner earns ore`() {
        val miner = Machine(
            type            = MachineType.MINER,
            gridX           = 4,
            gridY           = 2,
            outputRatePerSec = 1f,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(miner),
        )
        val bus = EventBus()
        var lastOre = -1
        bus.on<OreChangedEvent> { lastOre = it.newTotal }
        waveManager.tick(1f, state, bus)
        assertEquals(1, lastOre)
    }

    @Test
    fun `sub-integer ore accumulates fractionally`() {
        val miner = Machine(
            type            = MachineType.MINER,
            gridX           = 4,
            gridY           = 2,
            outputRatePerSec = 1f,
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(miner),
        )
        val bus = EventBus()
        waveManager.tick(0.4f, state, bus)    // accumulator = 0.4 → no ore yet
        assertEquals(0, state.wallet.ore)
        waveManager.tick(0.7f, state, bus)    // accumulator = 1.1 → earn 1, carry 0.1
        assertEquals(1, state.wallet.ore)
        assertTrue(state.oreAccumulator < 0.2f)
    }

    @Test
    fun `disconnected miner generates no ore`() {
        val miner = Machine(
            type            = MachineType.MINER,
            gridX           = 4,
            gridY           = 2,
            outputRatePerSec = 0f,  // disconnected — solver sets this to 0
        )
        val state = buildState(
            waves    = listOf(WaveDefinition(composition = mapOf(UnitType.BRUTE to 0))),
            machines = listOf(miner),
        )
        val bus = EventBus()
        waveManager.tick(10f, state, bus)
        assertEquals(0, state.wallet.ore)
    }

    // ---- Store ---------------------------------------------------------------

    @Test
    fun `store rotate produces 3 items`() {
        val state = buildState(waves = emptyList())
        state.store.rotate(42L)
        assertEquals(3, state.store.rotation.size)
    }

    @Test
    fun `store purchase deducts ore and returns component`() {
        val state = buildState(waves = emptyList(), initialOre = 100)
        state.store.rotate(1L)
        val item = state.store.rotation[0]
        val component = state.store.purchase(0, state.wallet)
        assertNotNull(component)
        assertEquals(100 - item.oreCost, state.wallet.ore)
        assertEquals(2, state.store.rotation.size)
    }

    @Test
    fun `store purchase fails when insufficient ore`() {
        val state = buildState(waves = emptyList(), initialOre = 0)
        state.store.rotate(1L)
        val result = state.store.purchase(0, state.wallet)
        assertNull(result)
        assertEquals(3, state.store.rotation.size)
    }

    @Test
    fun `store purchase at invalid index returns null`() {
        val state = buildState(waves = emptyList(), initialOre = 999)
        state.store.rotate(1L)
        assertNull(state.store.purchase(10, state.wallet))
    }

    // ---- unitTypeForMachine (companion) --------------------------------------

    @Test
    fun `unitTypeForMachine maps all three combat types`() {
        assertEquals(UnitType.BRUTE,      WaveManager.unitTypeForMachine(MachineType.COMBAT_BRUTE))
        assertEquals(UnitType.SKIRMISHER, WaveManager.unitTypeForMachine(MachineType.COMBAT_SKIRMISHER))
        assertEquals(UnitType.ARTILLERY,  WaveManager.unitTypeForMachine(MachineType.COMBAT_ARTILLERY))
    }

    @Test
    fun `unitTypeForMachine returns null for non-combat types`() {
        assertNull(WaveManager.unitTypeForMachine(MachineType.MINER))
        assertNull(WaveManager.unitTypeForMachine(MachineType.BOOST_AMPLIFIER))
    }
}
