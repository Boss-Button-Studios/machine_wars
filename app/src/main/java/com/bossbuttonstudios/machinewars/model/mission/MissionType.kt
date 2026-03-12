package com.bossbuttonstudios.machinewars.model.mission

/**
 * The three mission types defined in spec section 8.
 */
enum class MissionType {
    /**
     * Destroy the enemy base wall at the far end of the field.
     * Artillery's favored multiplier against buildings makes it near-essential.
     * Waves continue until base falls or player is eliminated.
     */
    BASE_ATTACK,

    /**
     * Survive until the timer expires, then clear the active wave.
     * Pure turtling is not viable — the in-progress wave must be finished.
     */
    TIMED_SURVIVAL,

    /**
     * Accumulate a target ore amount in the treasury.
     * Spending on units/components depletes the treasury — balance offense
     * against economic accumulation.
     */
    RESOURCE_HUNT,
}
