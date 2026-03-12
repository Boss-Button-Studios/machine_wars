package com.bossbuttonstudios.machinewars.model.factory

import java.util.UUID

/**
 * A producer or boost machine occupying a fixed point on the factory grid.
 *
 * Combat machines and miners are placed by the map generator and cannot be
 * moved. Boost machines are player-owned; they occupy grid points the same
 * way but travel with the player between missions.
 *
 * @param gridX             Column on the 4×6 grid (0-indexed).
 * @param gridY             Row on the 4×6 grid (0-indexed).
 * @param outputRatePerSec  Current resolved output rate (units/ore per second),
 *                          updated each tick by the drivetrain simulation.
 * @param assignedLane      For combat machines: which lane spawned units go to.
 *                          -1 means unassigned (player must assign before use).
 */
data class Machine(
    val id: UUID = UUID.randomUUID(),
    val type: MachineType,
    val gridX: Int,
    val gridY: Int,
    var outputRatePerSec: Float = 0f,
    var assignedLane: Int = -1,
) {
    val isCombatMachine: Boolean get() = type in combatTypes
    val isPlayerOwned: Boolean get() = type in boostTypes

    companion object {
        val combatTypes = setOf(
            MachineType.COMBAT_BRUTE,
            MachineType.COMBAT_SKIRMISHER,
            MachineType.COMBAT_ARTILLERY,
        )
        val boostTypes = setOf(
            MachineType.BOOST_AMPLIFIER,
            MachineType.BOOST_CAPACITOR,
            MachineType.BOOST_ARMOR_PLATER,
            MachineType.BOOST_TARGETING,
        )
    }
}
