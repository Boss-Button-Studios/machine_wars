package com.bossbuttonstudios.machinewars.model.factory

/**
 * Every type of producer machine that can occupy a factory grid cell.
 *
 * Combat producers are fixed to the map.
 * Boost machines are player-owned and carried between missions.
 */
enum class MachineType {
    // --- Fixed to map ---
    COMBAT_BRUTE,
    COMBAT_SKIRMISHER,
    COMBAT_ARTILLERY,
    MINER,

    // --- Player-owned (unlocked via Artifacts) ---
    BOOST_AMPLIFIER,      // +damage output at cost of higher energy draw
    BOOST_CAPACITOR,      // +total network energy available
    BOOST_ARMOR_PLATER,   // +effective HP for nearby units
    BOOST_TARGETING,      // tightens damage variance toward the mean
}
