package com.bossbuttonstudios.machinewars.model.map

/**
 * Defines the boundary configuration for a three-lane battlefield.
 *
 * With two shared boundaries there are exactly four possible configurations:
 *
 *  Map A — Wall  | Wall  — Fully isolated corridors.
 *  Map B — Wall  | Space — Left locked, right fluid.
 *  Map C — Space | Wall  — Mirror of B.
 *  Map D — Space | Space — Open field, full cross-lane projection.
 *
 * @param leftBoundary  Boundary between lane 0 (left) and lane 1 (centre).
 * @param rightBoundary Boundary between lane 1 (centre) and lane 2 (right).
 * @param id            Human-readable identifier ("A", "B", "C", or "D").
 */
data class MapConfig(
    val id: String,
    val leftBoundary: LaneBoundary,
    val rightBoundary: LaneBoundary,
) {
    companion object {
        val A = MapConfig("A", LaneBoundary.Wall,  LaneBoundary.Wall)
        val B = MapConfig("B", LaneBoundary.Wall,  LaneBoundary.Space)
        val C = MapConfig("C", LaneBoundary.Space, LaneBoundary.Wall)
        val D = MapConfig("D", LaneBoundary.Space, LaneBoundary.Space)

        val all: List<MapConfig> = listOf(A, B, C, D)
    }
}
