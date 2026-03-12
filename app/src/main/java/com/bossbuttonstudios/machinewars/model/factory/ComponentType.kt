package com.bossbuttonstudios.machinewars.model.factory

/**
 * Every kind of drivetrain component the player can own or place.
 * The MOTOR is fixed to the map and is never in the player's inventory.
 */
enum class ComponentType {
    MOTOR,        // Fixed to map; one output pulley + one output gear built in.
    BELT,         // Free to place; transmits power between pulleys with length-based loss.
    GEAR,         // Mesh transfer; integer size 1-8; enables branching.
    PULLEY,       // Belt endpoint; integer size 1-8.
    GEAR_PULLEY,  // Hybrid: belt input / gear output (or vice versa); mid-network branching.
}
