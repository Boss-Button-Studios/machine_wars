package com.bossbuttonstudios.machinewars.rendering

import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid

/**
 * Maps game-space coordinates to screen pixels.
 *
 * Constructed (or reconstructed) each time the view is measured. Pure Kotlin —
 * no Android imports — so all coordinate math is testable as plain JVM unit tests.
 *
 * Screen layout (portrait):
 *
 *   ┌────────────────────────┐  y = 0
 *   │                        │
 *   │    Battlefield (70%)   │
 *   │                        │
 *   ├────────────────────────┤  y = battlefieldBottom
 *   │    Factory grid (30%)  │
 *   └────────────────────────┘  y = screenHeight
 *
 * Battlefield:
 *   Three lanes of equal width. Unit position [0, 1] runs along the lane:
 *   0 = player end (bottom of battlefield), 1 = enemy end (top).
 *
 * Factory grid:
 *   4 cols × 6 rows, centred horizontally inside the bottom portion.
 *   Cells are square; size = min(availableWidth/4, availableHeight/6).
 */
class SceneLayout(
    val screenWidth: Float,
    val screenHeight: Float,
    battlefieldFraction: Float = BATTLEFIELD_FRACTION,
) {
    val battlefieldBottom: Float = screenHeight * battlefieldFraction
    val battlefieldHeight: Float = battlefieldBottom

    val factoryHeight: Float = screenHeight - battlefieldBottom

    /** Side length of one factory grid cell in pixels (cells are square). */
    val cellSize: Float = minOf(
        screenWidth / FactoryGrid.COLS,
        factoryHeight / FactoryGrid.ROWS,
    )

    /** X offset to horizontally centre the factory grid on screen. */
    val factoryOffsetX: Float = (screenWidth - cellSize * FactoryGrid.COLS) / 2f

    /** Y of the top-left corner of the factory grid. */
    val factoryOffsetY: Float = battlefieldBottom

    // ---- Lane boundary lines -----------------------------------------------

    /** X of the line between lane 0 (left) and lane 1 (centre). */
    val leftBoundaryX: Float get() = screenWidth / 3f

    /** X of the line between lane 1 (centre) and lane 2 (right). */
    val rightBoundaryX: Float get() = screenWidth * 2f / 3f

    // ---- Battlefield coordinate helpers ------------------------------------

    /** X at the horizontal centre of [lane] (0, 1, or 2). */
    fun laneCenterX(lane: Int): Float = screenWidth * (lane + 0.5f) / 3f

    /** X at the left edge of [lane]. */
    fun laneLeftX(lane: Int): Float = screenWidth * lane.toFloat() / 3f

    /** X at the right edge of [lane]. */
    fun laneRightX(lane: Int): Float = screenWidth * (lane + 1).toFloat() / 3f

    /**
     * Screen Y for a unit at the given normalised [position] in its lane.
     * position = 0 → bottom of battlefield (player end)
     * position = 1 → top of battlefield (enemy end)
     */
    fun unitY(position: Float): Float = battlefieldBottom - position * battlefieldHeight

    /**
     * Interpolated screen Y for sub-tick position smoothing.
     *
     * Extrapolates one tick forward from [position] at the unit's current
     * [speed], blended by [interpolation] (fractional progress from the last
     * fixed tick toward the next, per GameLoop contract).
     *
     * For ENGAGING units pass speed = 0; the result equals [unitY]([position]).
     *
     * @param position      Current (post-tick) normalised lane position.
     * @param speed         Position units per second from UnitStats.
     * @param teamSign      +1 for PLAYER (position increases), -1 for ENEMY.
     * @param interpolation Fractional tick progress [0, 1] from the GameLoop.
     */
    fun interpolatedUnitY(
        position: Float,
        speed: Float,
        teamSign: Float,
        interpolation: Float,
    ): Float {
        val smoothed = (position + speed * teamSign * TICK_DURATION * interpolation)
            .coerceIn(0f, 1f)
        return unitY(smoothed)
    }

    // ---- Factory grid coordinate helpers -----------------------------------

    /** X at the centre of factory grid [col] (0-indexed). */
    fun cellCenterX(col: Int): Float = factoryOffsetX + col * cellSize + cellSize * 0.5f

    /** Y at the centre of factory grid [row] (0-indexed). */
    fun cellCenterY(row: Int): Float = factoryOffsetY + row * cellSize + cellSize * 0.5f

    companion object {
        /** Fraction of screen height dedicated to the battlefield. */
        const val BATTLEFIELD_FRACTION = 0.70f

        /** Nominal fixed-tick duration matching GameLoop's 60 Hz step. */
        const val TICK_DURATION = 1f / 60f
    }
}
