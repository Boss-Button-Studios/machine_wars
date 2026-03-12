package com.bossbuttonstudios.machinewars.model.unit

/**
 * Single source of truth for base unit statistics.
 * Values are taken directly from Design Spec section 1.1.
 *
 * Upgrade paths and boost-machine effects are applied at runtime on top of
 * these base values; this registry is never mutated.
 */
object UnitRegistry {

    private val table: Map<UnitType, UnitStats> = mapOf(
        UnitType.BRUTE to UnitStats(
            type      = UnitType.BRUTE,
            maxHp     = 150f,
            range     = 1.5f,
            damage    = 15f,
            fireRate  = 1.0f,
            speed     = 2.0f,
        ),
        UnitType.SKIRMISHER to UnitStats(
            type      = UnitType.SKIRMISHER,
            maxHp     = 80f,
            range     = 4.0f,
            damage    = 8f,
            fireRate  = 2.5f,
            speed     = 5.0f,
        ),
        UnitType.ARTILLERY to UnitStats(
            type      = UnitType.ARTILLERY,
            maxHp     = 50f,
            range     = 8.0f,
            damage    = 70f,
            fireRate  = 0.5f,
            speed     = 1.5f,
        ),
    )

    /** Returns the base [UnitStats] for [type]. Always non-null. */
    operator fun get(type: UnitType): UnitStats =
        table[type] ?: error("No stats registered for $type")

    /** Convenience accessor for all registered types. */
    val all: Collection<UnitStats> get() = table.values
}
