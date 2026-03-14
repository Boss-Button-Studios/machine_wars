package com.bossbuttonstudios.machinewars.combat

/**
 * Shared constants for all combat systems.
 *
 * Spec positions are normalised to [0, 1] along the lane axis for simulation.
 * [FIELD_LENGTH] is the conversion factor: a spec distance of 1.0 unit = 0.1
 * in normalised space. The choice of 10 is consistent with the approach-window
 * example in spec §2.2 (6.5 units of free fire at Brute speed 2.0 ≈ 3 seconds).
 */
object CombatConstants {

    /** Battlefield length in spec units. Converts spec distances to [0,1] positions. */
    const val FIELD_LENGTH = 10f

    /** Damage multiplier when the attacker has the RPS class advantage (spec §1.2). */
    const val DAMAGE_MULTIPLIER_FAVORED = 1.5f

    /**
     * Damage multiplier applied against buildings (enemy base wall).
     * Artillery is the primary beneficiary; spec §8.1 notes this makes it
     * "near-essential" for BASE_ATTACK missions.
     */
    const val BUILDING_DAMAGE_MULTIPLIER = 1.5f

    /**
     * Coefficient k in the dodge probability formula (spec §1.4):
     *   dodge% = max(0, (defenderSpeed / attackerSpeed − 1) × k)
     *
     * Calibrated so Skirmisher vs Artillery yields ≈25% and peer matchups
     * yield 0%.
     */
    const val DODGE_K = 0.107f

    /** ±Spread around the adjusted damage average for the bell-curve roll (spec §1.5). */
    const val DAMAGE_VARIANCE = 0.15f

    /** Number of dice averaged for the bell-curve roll. Three dice strongly clusters
     *  results near the mean while permitting rare extremes (spec §1.5). */
    const val DAMAGE_DICE_COUNT = 3

    /**
     * Fraction of a Skirmisher shot that bleeds through intervening wreckage to
     * the unit behind it. The remainder (1 − WRECKAGE_BLEED_THROUGH) is absorbed
     * by the wreckage itself.
     *
     * Starting value: 0.30. Tune once the game is playable.
     */
    const val WRECKAGE_BLEED_THROUGH = 0.30f

    // -------------------------------------------------------------------------
    // Coordinate helpers
    // -------------------------------------------------------------------------

    /** Converts a spec range value (battlefield units) to normalised [0,1] space. */
    fun normalizedRange(specRange: Float): Float = specRange / FIELD_LENGTH

    /** Converts a spec speed value (battlefield units/second) to normalised units/second. */
    fun normalizedSpeed(specSpeed: Float): Float = specSpeed / FIELD_LENGTH
}
