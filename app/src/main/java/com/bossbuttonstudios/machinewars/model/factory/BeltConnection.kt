package com.bossbuttonstudios.machinewars.model.factory

/**
 * An explicit belt connection between two pulley (or gear-pulley) cells.
 *
 * Belts are free to place and do not occupy a grid cell themselves. They are
 * stored as a list of connections on [FactoryGrid] and consulted by the
 * drivetrain solver when traversing the network.
 *
 * Directionality is irrelevant at rest — the solver determines which end is
 * upstream based on the BFS traversal order. Both ends are treated as valid
 * entry points.
 *
 * @param fromX     Column of one pulley endpoint.
 * @param fromY     Row of one pulley endpoint.
 * @param toX       Column of the other pulley endpoint.
 * @param toY       Row of the other pulley endpoint.
 * @param length    Belt run length in grid units. Efficiency loss scales
 *                  with this value. Defaults to the Chebyshev distance
 *                  between the two endpoints; override for routed belts
 *                  that travel around obstacles.
 */
data class BeltConnection(
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int,
    val length: Float = chebyshevDistance(fromX, fromY, toX, toY),
) {
    val from: Pair<Int, Int> get() = fromX to fromY
    val to: Pair<Int, Int> get() = toX to toY

    /** True if (x, y) is either endpoint of this connection. */
    fun connects(x: Int, y: Int): Boolean =
        (fromX == x && fromY == y) || (toX == x && toY == y)

    /**
     * Returns the endpoint opposite to (x, y), or null if (x, y) is not
     * an endpoint of this connection.
     */
    fun otherEnd(x: Int, y: Int): Pair<Int, Int>? = when {
        fromX == x && fromY == y -> to
        toX == x && toY == y -> from
        else -> null
    }

    companion object {
        fun chebyshevDistance(x1: Int, y1: Int, x2: Int, y2: Int): Float =
            maxOf(Math.abs(x2 - x1), Math.abs(y2 - y1)).toFloat()
    }
}
