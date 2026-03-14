package com.bossbuttonstudios.machinewars.drivetrain

import java.util.UUID

/**
 * Full output of one drivetrain solve pass.
 *
 * Written into [com.bossbuttonstudios.machinewars.core.GameState.drivetrainResult]
 * each tick by [DrivetrainSolver]. No other system writes here after the
 * solver returns.
 *
 * @param nodes             Per-cell computed state. Keyed by (col, row).
 *                          Only reachable cells are present.
 * @param totalEnergyDraw   Sum of friction draws across all reachable
 *                          components this tick.
 * @param powerAvailable    Motor power minus total friction, clamped ≥ 0.
 * @param powerFraction     [powerAvailable] / MOTOR_POWER ∈ [0, 1].
 *                          Shared multiplier applied to all machine output rates.
 * @param machineOutputRates Machine UUID → computed output rate this tick
 *                          (spawn tokens or ore per second).
 * @param expiredPositions  Grid positions of components whose wearPct will
 *                          reach or exceed 1.0 after this tick's delta is
 *                          applied. [WearSystem] removes them from the grid.
 */
data class DrivetrainResult(
    val nodes: Map<Pair<Int, Int>, DrivetrainNode>,
    val totalEnergyDraw: Float,
    val powerAvailable: Float,
    val powerFraction: Float,
    val machineOutputRates: Map<UUID, Float>,
    val expiredPositions: List<Pair<Int, Int>>,
) {
    companion object {
        /**
         * Neutral result used before the first solve or when the grid has no
         * placed components. Motor power is fully available; all rates are zero.
         */
        fun idle(motorPower: Float = 1_000f) = DrivetrainResult(
            nodes              = emptyMap(),
            totalEnergyDraw    = 0f,
            powerAvailable     = motorPower,
            powerFraction      = 1f,
            machineOutputRates = emptyMap(),
            expiredPositions   = emptyList(),
        )
    }
}
