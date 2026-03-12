package com.bossbuttonstudios.machinewars.model.factory

import java.util.UUID

/**
 * A single drivetrain component — either placed on the factory grid or held
 * in the player's between-mission inventory.
 *
 * @param size      Integer 1–8 (standard = 4). Determines ratio contribution.
 *                  Belts and Motors ignore this field.
 * @param gridX     Column on the 4×6 factory grid (0-indexed). -1 = in inventory.
 * @param gridY     Row on the 4×6 factory grid (0-indexed).    -1 = in inventory.
 * @param wearPct   Degradation level [0.0, 1.0]. At 1.0 the component expires.
 */
data class Component(
    val id: UUID = UUID.randomUUID(),
    val type: ComponentType,
    val size: Int = 4,
    var gridX: Int = -1,
    var gridY: Int = -1,
    var wearPct: Float = 0f,
) {
    init {
        require(type == ComponentType.BELT || type == ComponentType.MOTOR || size in 1..8) {
            "Component size must be in 1..8 (got $size for $type)"
        }
        require(wearPct in 0f..1f) { "wearPct must be in [0, 1]" }
    }

    val isPlaced: Boolean get() = gridX >= 0 && gridY >= 0
    val isExpired: Boolean get() = wearPct >= 1f

    /**
     * Friction energy draw scales linearly with wear — a fully worn component
     * draws [BASE_FRICTION_MULTIPLIER * 2] times the baseline.
     * Exact coefficients are tuned in Session 2 (drivetrain simulation).
     */
    companion object {
        const val BASE_FRICTION_MULTIPLIER = 1.0f
        const val MAX_WEAR_FRICTION_MULTIPLIER = 2.0f
    }
}
