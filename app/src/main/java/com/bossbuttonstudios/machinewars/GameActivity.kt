package com.bossbuttonstudios.machinewars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.core.GameLoop
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.core.MachineOutputRateChangedEvent
import com.bossbuttonstudios.machinewars.core.MissionEndedEvent
import com.bossbuttonstudios.machinewars.core.OreChangedEvent
import com.bossbuttonstudios.machinewars.drivetrain.DrivetrainSolver
import com.bossbuttonstudios.machinewars.drivetrain.WearSystem
import com.bossbuttonstudios.machinewars.interfaces.NoOpAdProvider
import com.bossbuttonstudios.machinewars.rendering.GameView
import com.bossbuttonstudios.machinewars.model.economy.Wallet
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventBus = EventBus()

        val mission = buildSampleMission()
        val factory = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        val state   = GameState(mission = mission, factory = factory, wallet = Wallet(initialOre = 50))

        gameView = GameView(this)
        setContentView(gameView)

        @Suppress("UNUSED_VARIABLE") val adProvider = NoOpAdProvider() // wired in a later session

        // Subscribe to mission-end events to handle win/loss UI (stub).
        eventBus.on<MissionEndedEvent> { _ ->
            // TODO: show result screen overlay
        }

        gameLoop = GameLoop(
            scope    = activityScope,
            state    = state,
            renderer = gameView,
            onTick   = { dt, s ->
                tickDrivetrain(dt, s)
                s.elapsedSeconds += dt
                eventBus.post(OreChangedEvent(s.wallet.ore))
                // Session 3: combat system tick goes here
                // Session 6: wave management tick goes here
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
