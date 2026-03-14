package com.bossbuttonstudios.machinewars.drivetrain

/**
 * Computed power-flow state at one grid position for the current tick.
 *
 * Produced by [DrivetrainSolver] and stored in [DrivetrainResult.nodes].
 * Read by the renderer (via [com.bossbuttonstudios.machinewars.model.factory.FactoryGrid.powerFlow])
 * and by the press-and-hold tooltip (spec §5.9).
 *
 * @param gridPos       Grid cell (col, row) this node describes.
 * @param rpm           Rotational speed at this node after full gear-ratio
 *                      traversal from the motor.
 * @param energyDraw    Friction power consumed from the motor budget this
 *                      tick. Zero for the motor itself and for machines.
 * @param wearDelta     Wear increment to apply to the component this tick.
 *                      Zero for the motor and machines (which don't degrade).
 * @param pathEfficiency Cumulative belt efficiency along the path from motor
 *                      to this node [0, 1]. Pure gear chains = 1.0; each
 *                      belt segment reduces this by its length penalty.
 * @param reachable     True when the motor has a connected path to this node.
 *                      Unreachable nodes have zero RPM and zero output.
 */
data class DrivetrainNode(
    val gridPos: Pair<Int, Int>,
    val rpm: Float,
    val energyDraw: Float,
    val wearDelta: Float,
    val pathEfficiency: Float,
    val reachable: Boolean,
)
