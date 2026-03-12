package com.bossbuttonstudios.machinewars.model.unit

/**
 * The three unit classes. The rock-paper-scissors triangle is:
 *   Brute > Skirmisher > Artillery > Brute (and Buildings)
 */
enum class UnitType {
    BRUTE,
    SKIRMISHER,
    ARTILLERY;

    /**
     * Returns true if this unit type has the favored-attacker advantage
     * against [target], triggering the 1.5x damage multiplier.
     */
    fun isFavoredAgainst(target: UnitType): Boolean = when (this) {
        BRUTE       -> target == SKIRMISHER
        SKIRMISHER  -> target == ARTILLERY
        ARTILLERY   -> target == BRUTE
    }
}
