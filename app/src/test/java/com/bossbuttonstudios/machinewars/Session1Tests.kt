package com.bossbuttonstudios.machinewars

import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.core.GameLoop
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.core.MissionEndedEvent
import com.bossbuttonstudios.machinewars.interfaces.NoOpRenderer
import com.bossbuttonstudios.machinewars.model.economy.Store
import com.bossbuttonstudios.machinewars.model.economy.Wallet
import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.ComponentType
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.factory.MachineType
import com.bossbuttonstudios.machinewars.model.map.LaneBoundary
import com.bossbuttonstudios.machinewars.model.map.MapConfig
import com.bossbuttonstudios.machinewars.model.map.Wreckage
import com.bossbuttonstudios.machinewars.model.mission.MissionConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.mission.WaveDefinition
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitRegistry
import com.bossbuttonstudios.machinewars.model.unit.UnitType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

// =============================================================================
// UnitRegistry
// =============================================================================

class UnitRegistryTest {

    @Test fun `all three unit types are registered`() {
        UnitType.entries.forEach { type ->
            val stats = UnitRegistry[type]
            assertEquals(type, stats.type)
        }
    }

    @Test fun `base DPS matches spec values`() {
        // Spec 1.1: Brute 15, Skirmisher 20, Artillery 35
        assertEquals(15f,  UnitRegistry[UnitType.BRUTE].baseDps,      0.01f)
        assertEquals(20f,  UnitRegistry[UnitType.SKIRMISHER].baseDps, 0.01f)
        assertEquals(35f,  UnitRegistry[UnitType.ARTILLERY].baseDps,  0.01f)
    }

    @Test fun `stats are immutable between calls`() {
        val first  = UnitRegistry[UnitType.BRUTE]
        val second = UnitRegistry[UnitType.BRUTE]
        assertSame(first, second)
    }
}

// =============================================================================
// UnitType — favored attacker triangle
// =============================================================================

class UnitTypeFavoredTest {

    @Test fun `brute is favored against skirmisher`() {
        assertTrue(UnitType.BRUTE.isFavoredAgainst(UnitType.SKIRMISHER))
    }

    @Test fun `skirmisher is favored against artillery`() {
        assertTrue(UnitType.SKIRMISHER.isFavoredAgainst(UnitType.ARTILLERY))
    }

    @Test fun `artillery is favored against brute`() {
        assertTrue(UnitType.ARTILLERY.isFavoredAgainst(UnitType.BRUTE))
    }

    @Test fun `no unit is favored against itself`() {
        UnitType.entries.forEach { type ->
            assertFalse(type.isFavoredAgainst(type))
        }
    }

    @Test fun `reverse matchups are not favored`() {
        assertFalse(UnitType.SKIRMISHER.isFavoredAgainst(UnitType.BRUTE))
        assertFalse(UnitType.ARTILLERY.isFavoredAgainst(UnitType.SKIRMISHER))
        assertFalse(UnitType.BRUTE.isFavoredAgainst(UnitType.ARTILLERY))
    }
}

// =============================================================================
// MapConfig
// =============================================================================

class MapConfigTest {

    @Test fun `map A has wall on both boundaries`() {
        assertEquals(LaneBoundary.Wall,  MapConfig.A.leftBoundary)
        assertEquals(LaneBoundary.Wall,  MapConfig.A.rightBoundary)
    }

    @Test fun `map D has space on both boundaries`() {
        assertEquals(LaneBoundary.Space, MapConfig.D.leftBoundary)
        assertEquals(LaneBoundary.Space, MapConfig.D.rightBoundary)
    }

    @Test fun `all four configs are distinct`() {
        assertEquals(4, MapConfig.all.map { it.id }.toSet().size)
    }
}

// =============================================================================
// FactoryGrid
// =============================================================================

class FactoryGridTest {

    private fun grid() = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())

    @Test fun `place and retrieve a component`() {
        val g = grid()
        val c = Component(type = ComponentType.GEAR, size = 4)
        assertTrue(g.place(c, 1, 1))
        assertSame(c, g.componentAt(1, 1))
        assertTrue(c.isPlaced)
    }

    @Test fun `cannot place on occupied cell`() {
        val g = grid()
        val c1 = Component(type = ComponentType.GEAR, size = 4)
        val c2 = Component(type = ComponentType.PULLEY, size = 2)
        g.place(c1, 2, 2)
        assertFalse(g.place(c2, 2, 2))
    }

    @Test fun `cannot place on motor cell`() {
        val g = grid()
        val c = Component(type = ComponentType.GEAR, size = 4)
        assertFalse(g.place(c, 0, 0)) // motor is at (0,0)
    }

    @Test fun `remove returns component and frees cell`() {
        val g = grid()
        val c = Component(type = ComponentType.PULLEY, size = 3)
        g.place(c, 3, 3)
        val removed = g.remove(3, 3)
        assertSame(c, removed)
        assertNull(g.componentAt(3, 3))
        assertFalse(c.isPlaced)
    }

    @Test fun `out-of-bounds placement fails`() {
        val g = grid()
        val c = Component(type = ComponentType.GEAR, size = 4)
        assertFalse(g.place(c, 10, 10))
        assertFalse(g.place(c, -1, 0))
    }
}

// =============================================================================
// Wallet
// =============================================================================

class WalletTest {

    @Test fun `earn and spend ore`() {
        val w = Wallet()
        w.earnOre(100)
        assertEquals(100, w.ore)
        assertTrue(w.spendOre(40))
        assertEquals(60, w.ore)
    }

    @Test fun `cannot overspend ore`() {
        val w = Wallet(initialOre = 10)
        assertFalse(w.spendOre(20))
        assertEquals(10, w.ore) // unchanged
    }

    @Test fun `artifact earn and spend`() {
        val w = Wallet()
        w.earnArtifact()
        w.earnArtifact()
        assertEquals(2, w.artifacts)
        assertTrue(w.spendArtifact())
        assertEquals(1, w.artifacts)
        assertTrue(w.spendArtifact())
        assertFalse(w.spendArtifact()) // none left
    }
}

// =============================================================================
// Store
// =============================================================================

class StoreTest {

    @Test fun `rotate produces three items`() {
        val s = Store()
        s.rotate(seed = 42L)
        assertEquals(3, s.rotation.size)
    }

    @Test fun `purchase removes item and deducts ore`() {
        val s = Store()
        val w = Wallet(initialOre = 1000)
        s.rotate(seed = 99L)
        val item = s.rotation[0]
        val bought = s.purchase(0, w)
        assertNotNull(bought)
        assertEquals(2, s.rotation.size)
        assertEquals(1000 - item.oreCost, w.ore)
    }

    @Test fun `purchase fails with insufficient ore`() {
        val s = Store()
        val w = Wallet(initialOre = 0)
        s.rotate(seed = 7L)
        assertNull(s.purchase(0, w))
        assertEquals(3, s.rotation.size) // unchanged
    }

    @Test fun `recycle returns partial ore`() {
        val s = Store()
        val w = Wallet(initialOre = 0)
        val c = Component(type = ComponentType.GEAR, size = 4, wearPct = 0.5f)
        s.recycle(c, w)
        // base cost = 40, worn to 50%, recycle ratio 0.5 → 10 ore
        assertEquals(10, w.ore)
    }
}

// =============================================================================
// GameState
// =============================================================================

class GameStateTest {

    private fun sampleState(): GameState {
        val mission = MissionConfig(
            missionNumber = 1,
            type          = MissionType.BASE_ATTACK,
            mapConfig     = MapConfig.A,
            waves         = listOf(WaveDefinition(mapOf(UnitType.BRUTE to 1))),
        )
        val factory = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        return GameState(mission = mission, factory = factory)
    }

    @Test fun `purgeDeadEntities removes dead units`() {
        val s = sampleState()
        val living = UnitInstance(
            type = UnitType.BRUTE, team = Team.PLAYER, lane = 0,
            stats = UnitRegistry[UnitType.BRUTE],
        )
        val dead = UnitInstance(
            type = UnitType.SKIRMISHER, team = Team.ENEMY, lane = 1,
            stats = UnitRegistry[UnitType.SKIRMISHER],
            currentHp = 0f,
        )
        s.units.addAll(listOf(living, dead))
        val (removedUnits, _) = s.purgeDeadEntities()
        assertEquals(1, removedUnits)
        assertEquals(1, s.units.size)
        assertSame(living, s.units[0])
    }

    @Test fun `purgeDeadEntities removes cleared wreckage`() {
        val s = sampleState()
        s.wreckage.add(Wreckage(sourceType = UnitType.BRUTE, lane = 0, position = 0.5f, currentHp = 0f))
        s.wreckage.add(Wreckage(sourceType = UnitType.BRUTE, lane = 1, position = 0.3f, currentHp = 10f))
        val (_, removedWreckage) = s.purgeDeadEntities()
        assertEquals(1, removedWreckage)
        assertEquals(1, s.wreckage.size)
    }

    @Test fun `elapsed time accumulates`() {
        val s = sampleState()
        s.elapsedSeconds += 1.5f
        assertEquals(1.5f, s.elapsedSeconds, 0.001f)
    }
}

// =============================================================================
// EventBus
// =============================================================================

class EventBusTest {

    @Test fun `listener receives posted event`() {
        val bus = EventBus()
        var received: MissionEndedEvent? = null
        bus.on<MissionEndedEvent> { received = it }
        bus.post(MissionEndedEvent(playerWon = true))
        assertNotNull(received)
        assertTrue(received!!.playerWon)
    }

    @Test fun `listener does not receive unrelated events`() {
        val bus = EventBus()
        var received = false
        bus.on<MissionEndedEvent> { received = true }
        bus.post("unrelated string event")
        assertFalse(received)
    }

    @Test fun `clear removes all listeners`() {
        val bus = EventBus()
        var count = 0
        bus.on<MissionEndedEvent> { count++ }
        bus.clear()
        bus.post(MissionEndedEvent(playerWon = false))
        assertEquals(0, count)
    }
}

// =============================================================================
// GameLoop — basic start/stop and tick counting
// =============================================================================

class GameLoopTest {

    @Test fun `tick callback is called and elapsed time advances`() {
        // GameLoop uses System.nanoTime() for frame timing, which is real wall-clock
        // time. The coroutine test scheduler controls virtual time only — it cannot
        // advance nanoTime. We use a real scope and a short real sleep instead.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val mission = MissionConfig(
            missionNumber = 1,
            type          = MissionType.BASE_ATTACK,
            mapConfig     = MapConfig.A,
            waves         = listOf(WaveDefinition(mapOf(UnitType.BRUTE to 1))),
        )
        val factory = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        val state   = GameState(mission = mission, factory = factory)

        var tickCount = 0
        val loop = GameLoop(
            scope    = scope,
            state    = state,
            renderer = NoOpRenderer(),
            onTick   = { dt, s ->
                tickCount++
                s.elapsedSeconds += dt
            },
        )

        loop.start()
        Thread.sleep(200L) // 200 ms real time — enough for ~12 ticks at 60 Hz
        loop.stop()
        scope.cancel()

        assertTrue("Expected at least 1 tick, got $tickCount", tickCount > 0)
        assertTrue("Elapsed time should have advanced", state.elapsedSeconds > 0f)
    }
}
