package com.bossbuttonstudios.machinewars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.core.GameLoop
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.core.MissionEndedEvent
import com.bossbuttonstudios.machinewars.interfaces.NoOpAdProvider
import com.bossbuttonstudios.machinewars.interfaces.NoOpRenderer
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
 *
 * Real rendering, input, and ad integration drop in as the sessions
 * progress without touching the logic assembled here.
 */
class GameActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var gameLoop: GameLoop
    private lateinit var eventBus: EventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventBus = EventBus()

        val mission = buildSampleMission()
        val factory = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        val state   = GameState(mission = mission, factory = factory, wallet = Wallet(initialOre = 50))

        val renderer  = NoOpRenderer()
        val adProvider = NoOpAdProvider()

        // Subscribe to mission-end events to handle win/loss UI (stub).
        eventBus.on<MissionEndedEvent> { event ->
            // TODO Session 4+: show result screen
        }

        gameLoop = GameLoop(
            scope    = activityScope,
            state    = state,
            renderer = renderer,
            onTick   = { dt, s ->
                // Systems will be registered here in Sessions 2, 3, 6.
                s.elapsedSeconds += dt
                eventBus.post(com.bossbuttonstudios.machinewars.core.OreChangedEvent(s.wallet.ore))
            },
        )

        gameLoop.start()
    }

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
                composition    = mapOf(UnitType.BRUTE to 3),
                spawnInterval  = 2f,
            ),
            WaveDefinition(
                composition    = mapOf(UnitType.BRUTE to 2, UnitType.SKIRMISHER to 2),
                spawnInterval  = 1.5f,
            ),
        ),
    )
}
