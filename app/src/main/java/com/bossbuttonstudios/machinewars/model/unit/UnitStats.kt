package com.bossbuttonstudios.machinewars.model.unit

/**
 * Immutable stat profile for a unit type.
 *
 * @param type          Which unit this profile belongs to.
 * @param maxHp         Base hit points (before upgrades / boost machines).
 * @param range         Attack range in battlefield units.
 * @param damage        Base damage per shot (before multipliers / variance).
 * @param fireRate      Shots per second.
 * @param speed         Movement speed in battlefield units per second.
 */
data class UnitStats(
    val type: UnitType,
    val maxHp: Float,
    val range: Float,
    val damage: Float,
    val fireRate: Float,   // shots / second
    val speed: Float,
) {
    /** Theoretical DPS with no multipliers applied. */
    val baseDps: Float get() = damage * fireRate
}
