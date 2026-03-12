package com.bossbuttonstudios.machinewars.model.map

/**
 * The boundary between two adjacent lanes.
 *
 * Per spec section 3.2 / 4.1:
 *  - Wall  → Brute and Skirmisher are blocked; Artillery fires over.
 *  - Space → Brute is blocked; Skirmisher and Artillery fire across.
 */
sealed class LaneBoundary {
    /** Solid brick wall. Blocks all unit movement; Artillery fires over it. */
    object Wall : LaneBoundary()

    /** Open gap / space. Blocks Brute movement; ranged units fire across. */
    object Space : LaneBoundary()
}
