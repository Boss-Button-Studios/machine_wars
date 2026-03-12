package com.bossbuttonstudios.machinewars.model.map

import com.bossbuttonstudios.machinewars.model.unit.UnitType
import java.util.UUID

/**
 * Debris left behind when a unit is destroyed without being overkilled.
 *
 * Wreckage HP formula (spec 3.1):
 *   wreckageHp = (unitBaseHp * 0.5) - excessDamage
 *
 * If wreckageHp <= 0 the kill was clean and no wreckage spawns.
 *
 * Terrain permeability (spec 3.2):
 *  - Blocks Brute entirely until cleared.
 *  - Partially absorbs Skirmisher shots.
 *  - Ignored completely by Artillery.
 *
 * @param sourceType  The unit type that produced this wreckage (affects visual).
 * @param lane        Which lane this wreckage occupies.
 * @param position    Normalised position along the lane [0, 1].
 * @param currentHp   Remaining wreckage HP; clears when it reaches 0.
 */
data class Wreckage(
    val id: UUID = UUID.randomUUID(),
    val sourceType: UnitType,
    val lane: Int,
    val position: Float,
    var currentHp: Float,
) {
    val isCleared: Boolean get() = currentHp <= 0f
}
