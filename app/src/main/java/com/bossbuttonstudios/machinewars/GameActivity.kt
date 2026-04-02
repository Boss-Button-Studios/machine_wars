package com.bossbuttonstudios.machinewars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.core.GameLoop
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.core.MachineOutputRateChangedEvent
import com.bossbuttonstudios.machinewars.core.MissionEndedEvent
import com.bossbuttonstudios.machinewars.core.OreChangedEvent
import com.bossbuttonstudios.machinewars.combat.CombatOrchestrator
import com.bossbuttonstudios.machinewars.combat.WaveManager
import com.bossbuttonstudios.machinewars.core.WaveClearedEvent
import com.bossbuttonstudios.machinewars.drivetrain.DrivetrainSolver
import com.bossbuttonstudios.machinewars.drivetrain.WearSystem
import com.bossbuttonstudios.machinewars.interfaces.NoOpAdProvider
import com.bossbuttonstudios.machinewars.rendering.GameView
import com.bossbuttonstudios.machinewars.model.economy.Wallet
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.factory.BeltConnection
import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.ComponentType
import com.bossbuttonstudios.machinewars.model.factory.Machine
import com.bossbuttonstudios.machinewars.model.factory.MachineType
import com.bossbuttonstudios.machinewars.model.map.MapConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.mission.WaveDefinition
import com.bossbuttonstudios.machinewars.model.unit.UnitType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Android entry point — prototype stub.
 *
 * Wires together:
 *  - A sample mission config (Map A, BASE_ATTACK, two simple waves)
 *  - A factory grid with the motor at (0, 0) and no pre-placed machines
 *  - No-op renderer and ad provider
 *  - The event bus and game loop
 *  - DrivetrainSolver and WearSystem (Session 2)
 *
 * Real rendering, input, and ad integration drop in as the sessions
 * progress without touching the logic assembled here.
 */
class GameActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var gameLoop: GameLoop
    private lateinit var gameView: GameView
    private lateinit var eventBus: EventBus

    // Session 2 systems
    private val drivetrainSolver = DrivetrainSolver()
    private val wearSystem       = WearSystem()

    // Session 6 systems
    private val waveManager        = WaveManager()
    private val combatOrchestrator = CombatOrchestrator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventBus = EventBus()

        val mission = buildSampleMission()
        val factory = FactoryGrid(
            motorGridX = 0,
            motorGridY = 0,
            machines = listOf(
                Machine(type = MachineType.COMBAT_BRUTE,      gridX = 2, gridY = 0, assignedLane = 0),
                Machine(type = MachineType.COMBAT_SKIRMISHER, gridX = 4, gridY = 0, assignedLane = 2),
                Machine(type = MachineType.COMBAT_ARTILLERY,  gridX = 2, gridY = 2, assignedLane = 1),
                Machine(type = MachineType.MINER,             gridX = 4, gridY = 2),
            ),
        )
        val state   = GameState(mission = mission, factory = factory, wallet = Wallet(initialOre = 50))

        gameView = GameView(this)
        setContentView(gameView)

        placeSampleDrivetrain(factory)

        gameView.onLaneAssigned = { machineId, lane ->
            state.laneAssignments[machineId] = lane
            state.factory.machines.find { it.id == machineId }?.assignedLane = lane
        }

        gameView.onStorePurchase = purchase@{ index ->
            val component = state.store.purchase(index, state.wallet) ?: return@purchase
            state.playerInventory.add(component)
            eventBus.post(OreChangedEvent(state.wallet.ore))
        }

        gameView.onComponentPlace = place@{ inventoryIndex, col, row ->
            val component = state.playerInventory.getOrNull(inventoryIndex) ?: return@place
            if (state.factory.place(component, col, row)) {
                state.playerInventory.removeAt(inventoryIndex)
            }
        }

        gameView.onBeltAdded = { fromX, fromY, toX, toY ->
            state.factory.addBelt(BeltConnection(fromX = fromX, fromY = fromY, toX = toX, toY = toY))
        }

        gameView.onContinueWave = {
            state.currentWaveIndex++
            state.waveSpawnCursor = 0
            state.timeUntilNextSpawn = 0f
            state.betweenWaves = false
        }

        @Suppress("UNUSED_VARIABLE") val adProvider = NoOpAdProvider() // wired in a later session

        // Subscribe to mission-end events to handle win/loss UI (stub).
        eventBus.on<MissionEndedEvent> { _ ->
            // TODO: show result screen overlay
        }

        // WaveManager posts WaveClearedEvent; the between-waves UI is driven by
        // state.betweenWaves rather than by this event, but we subscribe here
        // for any future logging or animation hooks.
        eventBus.on<WaveClearedEvent> { _ -> }

        gameLoop = GameLoop(
            scope    = activityScope,
            state    = state,
            renderer = gameView,
            onTick   = { dt, s ->
                if (!s.isOver) {
                    tickDrivetrain(dt, s)
                    waveManager.tick(dt, s, eventBus)
                    combatOrchestrator.onTick(dt, s)
                    // CombatOrchestrator increments elapsedSeconds and posts MissionEndedEvent.
                    // OreChangedEvent is posted by WaveManager on each miner tick.
                }
            },
        )

        gameLoop.start()
    }

    // -----------------------------------------------------------------------
    // Session 2: drivetrain tick
    // -----------------------------------------------------------------------

    private fun tickDrivetrain(dt: Float, state: GameState) {
        // 1. Solve the network — pure computation, no side effects.
        val result = drivetrainSolver.solve(state.factory, dt)

        // 2. Store result in game state for tooltip reads and other systems.
        state.drivetrainResult = result

        // 3. Update machine output rates and emit change events.
        val previousRates = state.factory.machines.associate { it.id to it.outputRatePerSec }
        result.machineOutputRates.forEach { (id, newRate) ->
            val machine = state.factory.machines.find { it.id == id } ?: return@forEach
            if (Math.abs(newRate - (previousRates[id] ?: 0f)) > RATE_CHANGE_EPSILON) {
                machine.outputRatePerSec = newRate
                eventBus.post(MachineOutputRateChangedEvent(id, newRate))
            } else {
                machine.outputRatePerSec = newRate
            }
        }

        // 4. Update powerFlow for the renderer (spec §5.9 — bar fill rate).
        result.nodes.forEach { (pos, node) ->
            state.factory.powerFlow[pos] = node.rpm
        }

        // 5. Apply wear and handle any expired components.
        wearSystem.applyWear(state.factory, result, eventBus)
    }

    // -----------------------------------------------------------------------

    override fun onPause() {
        super.onPause()
        gameLoop.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameLoop.isInitialized) gameLoop.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameLoop.stop()
        activityScope.cancel()
        eventBus.clear()
    }

    // -----------------------------------------------------------------------

    /**
     * Populates the factory grid with a sample drivetrain that exercises all
     * component types and shows three machines running at their preferred RPMs.
     *
     * Network layout (6×4 grid, motor at 0,0):
     *
     *   [M]  [G8] [BRT]  ...  [GP2][SKR]
     *   [G4] [G2] [G4]   ...
     *   [GP2]... (belt)  ...
     *   ...               ... [ART] ...  [MNR unconnected]
     *
     * Chains:
     *   Motor → GEAR(8)(1,0) → BRT(2,0)          50 RPM  (BRT preferred 50)
     *   Motor → GEAR(4)(0,1) → GEAR(2)(1,1)
     *                        → GEAR(4)(2,1) → ART(2,2)   100 RPM (ART preferred 100)
     *                        → GP(2)(0,2)   → [belt] → GP(2)(3,0) → SKR(4,0)  200 RPM (SKR preferred 200)
     *   MNR(4,2) — deliberately unconnected, output = 0.
     */
    private fun placeSampleDrivetrain(factory: FactoryGrid) {
        // Chain 1: motor → BRT (step-down to 50 RPM)
        factory.place(Component(type = ComponentType.GEAR, size = 8), 1, 0)

        // Chain 2: motor → ART (100 RPM via up/down gear pair)
        factory.place(Component(type = ComponentType.GEAR,        size = 4), 0, 1)
        factory.place(Component(type = ComponentType.GEAR,        size = 2), 1, 1)
        factory.place(Component(type = ComponentType.GEAR,        size = 4), 2, 1)

        // Chain 3: branch from (0,1) → belt → SKR (200 RPM)
        factory.place(Component(type = ComponentType.GEAR_PULLEY, size = 2), 0, 2)
        factory.place(Component(type = ComponentType.GEAR_PULLEY, size = 2), 3, 0)
        factory.addBelt(BeltConnection(fromX = 0, fromY = 2, toX = 3, toY = 0))
    }

    private fun buildSampleMission(): MissionConfig = MissionConfig(
        missionNumber = 1,
        type          = MissionType.BASE_ATTACK,
        mapConfig     = MapConfig.A,
        waves         = listOf(
            WaveDefinition(
                composition   = mapOf(UnitType.BRUTE to 3),
                spawnInterval = 2f,
            ),
            WaveDefinition(
                composition   = mapOf(UnitType.BRUTE to 2, UnitType.SKIRMISHER to 2),
                spawnInterval = 1.5f,
            ),
        ),
    )

    companion object {
        /** Minimum rate change required to post a [MachineOutputRateChangedEvent]. */
        private const val RATE_CHANGE_EPSILON = 0.001f
    }
}
