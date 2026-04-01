package com.bossbuttonstudios.machinewars.core

import com.bossbuttonstudios.machinewars.drivetrain.DrivetrainResult
import java.util.concurrent.ConcurrentHashMap
import com.bossbuttonstudios.machinewars.model.economy.Store
import com.bossbuttonstudios.machinewars.model.economy.Wallet
import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.map.Wreckage
import com.bossbuttonstudios.machinewars.model.mission.MissionConfig
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitState

/**
 * The complete mutable state of a running mission.
 *
 * Every system reads from and writes to this object. It is intentionally a
 * plain data container — no logic lives here. Logic belongs in the systems
 * (combat, drivetrain, wave management) that operate on it.
 *
 * Thread-safety: all mutations happen on the single game-loop coroutine.
 * The renderer takes a snapshot for interpolation; it never writes here.
 */
class GameState(
    val mission: MissionConfig,
    val factory: FactoryGrid,
    val wallet: Wallet = Wallet(),
    val store: Store = Store(),
    val playerInventory: MutableList<Component> = mutableListOf(),
) {
    // --- Battlefield units ---
    val units: MutableList<UnitInstance> = mutableListOf()

    val livingUnits: List<UnitInstance>
        get() = units.filter { it.isAlive }

    // --- Terrain ---
    val wreckage: MutableList<Wreckage> = mutableListOf()

    // --- Wave tracking ---
    var currentWaveIndex: Int = 0
    var waveSpawnCursor: Int = 0       // how many units in the current wave have been spawned
    var timeUntilNextSpawn: Float = 0f // seconds until next unit in current wave deploys

    // --- Mission clock ---
    var elapsedSeconds: Float = 0f

    // --- Enemy base HP (BASE_ATTACK missions only) ---
    var enemyBaseHp: Float = BASE_HP

    // --- Win/loss ---
    var isOver: Boolean = false
    var playerWon: Boolean = false

    // --- Between-wave state (Session 6) ---
    /**
     * True when the current wave is fully cleared and the player is in the
     * between-wave shopping window. [WaveManager] sets this; the player clears
     * it by tapping NEXT WAVE in [com.bossbuttonstudios.machinewars.rendering.GameView].
     *
     * Written on the game-loop thread by WaveManager; read on the main thread
     * by GameView touch handling. @Volatile — one-tick stale is acceptable.
     */
    @Volatile var betweenWaves: Boolean = false

    /**
     * Per-machine fractional spawn token. Accumulates `outputRatePerSec * dt`
     * each tick; a unit is produced when the token reaches 1.0 (spec §5.7).
     * Keys are combat machine IDs.
     */
    val spawnTokens: MutableMap<java.util.UUID, Float> = mutableMapOf()

    /**
     * Sub-integer ore accumulator for miner output. Incremented by
     * `outputRatePerSec * dt` each tick; integer portion earned and cleared.
     */
    var oreAccumulator: Float = 0f

    // --- Pending lane assignments from input (Session 5 writes here) ---
    /**
     * Maps machine ID to the lane the player has assigned it to.
     * Combat systems read this each spawn tick.
     *
     * ConcurrentHashMap: written on the main (UI) thread via touch input,
     * read on the game-loop coroutine thread. No lock needed for prototype
     * single-player use; worst case is a one-tick stale read.
     */
    val laneAssignments: MutableMap<java.util.UUID, Int> = ConcurrentHashMap()

    // --- Drivetrain (Session 2 writes here each tick) ---
    /**
     * Most recent drivetrain solve result. Updated by DrivetrainSolver each
     * tick before combat or wave systems run.
     *
     * The press-and-hold tooltip reads from this directly (spec §5.9).
     * Initialised to an idle (no components) result.
     */
    var drivetrainResult: DrivetrainResult = DrivetrainResult.idle()

    // --- Convenience queries ---

    /** Remaining survival time; only meaningful for TIMED_SURVIVAL missions. */
    val survivalTimeRemaining: Float
        get() = (mission.timeLimitSeconds - elapsedSeconds).coerceAtLeast(0f)

    /** True when survival timer has expired (does not mean the mission is won). */
    val survivalTimerExpired: Boolean
        get() = mission.type == MissionType.TIMED_SURVIVAL &&
                elapsedSeconds >= mission.timeLimitSeconds

    /** True when the resource target has been met (RESOURCE_HUNT). */
    val resourceTargetMet: Boolean
        get() = mission.type == MissionType.RESOURCE_HUNT &&
                wallet.ore >= mission.oreTarget

    /** Removes all dead units and cleared wreckage, returning counts for logging. */
    fun purgeDeadEntities(): Pair<Int, Int> {
        val deadUnits = units.count { !it.isAlive }
        val clearedWreckage = wreckage.count { it.isCleared }
        units.removeAll { !it.isAlive }
        wreckage.removeAll { it.isCleared }
        return deadUnits to clearedWreckage
    }

    companion object {
        const val BASE_HP = 500f
    }
}
