package com.bossbuttonstudios.machinewars.model.factory

/**
 * The 4×6 factory grid.
 *
 * Column index 0–3 (left to right), row index 0–5 (top to bottom).
 * Each cell may hold at most one [Component] or [Machine].
 *
 * The motor is always at a fixed position supplied by the map; it occupies
 * one cell and cannot be displaced.
 *
 * Power-flow state (RPM, torque at each node) is computed by the drivetrain
 * simulation (Session 2) and stored in [powerFlow].
 */
class FactoryGrid(
    val motorGridX: Int,
    val motorGridY: Int,
    /** Machines fixed to this map's floor. Immutable after construction. */
    val machines: List<Machine>,
) {
    companion object {
        const val COLS = 4
        const val ROWS = 6
    }

    // Sparse maps so empty cells cost nothing.
    private val _components = mutableMapOf<Pair<Int, Int>, Component>()
    val components: Map<Pair<Int, Int>, Component> get() = _components

    /** Resolved power output at each grid cell; updated by drivetrain sim. */
    val powerFlow = mutableMapOf<Pair<Int, Int>, Float>()

    /**
     * Explicit belt connections between pulley/gear-pulley cells.
     * Populated by input handling (Session 5) and readable by the drivetrain
     * solver each tick. Belts are free and do not consume grid cells.
     */
    private val _beltConnections = mutableListOf<BeltConnection>()
    val beltConnections: List<BeltConnection> get() = _beltConnections

    // -----------------------------------------------------------------------
    // Component placement
    // -----------------------------------------------------------------------

    fun isInBounds(x: Int, y: Int): Boolean = x in 0 until COLS && y in 0 until ROWS

    fun isCellOccupied(x: Int, y: Int): Boolean =
        _components.containsKey(x to y) ||
        machines.any { it.gridX == x && it.gridY == y } ||
        (x == motorGridX && y == motorGridY)

    /**
     * Places [component] at (x, y).
     * @return true on success; false if the cell is out of bounds or occupied.
     */
    fun place(component: Component, x: Int, y: Int): Boolean {
        if (!isInBounds(x, y) || isCellOccupied(x, y)) return false
        component.gridX = x
        component.gridY = y
        _components[x to y] = component
        return true
    }

    /**
     * Removes the component at (x, y) and returns it (null if none).
     * Any belt connections that referenced this cell are also removed.
     */
    fun remove(x: Int, y: Int): Component? {
        val c = _components.remove(x to y) ?: return null
        c.gridX = -1
        c.gridY = -1
        // Orphaned belt connections are invalid — remove them.
        _beltConnections.removeAll { it.connects(x, y) }
        return c
    }

    /** Returns the component at (x, y), or null. */
    fun componentAt(x: Int, y: Int): Component? = _components[x to y]

    /** Returns the machine at (x, y), or null. */
    fun machineAt(x: Int, y: Int): Machine? =
        machines.firstOrNull { it.gridX == x && it.gridY == y }

    // -----------------------------------------------------------------------
    // Belt management
    // -----------------------------------------------------------------------

    /**
     * Adds a belt connection between (fromX, fromY) and (toX, toY).
     *
     * @return false if the connection already exists or either endpoint is
     *         out of bounds; true on success.
     */
    fun addBelt(belt: BeltConnection): Boolean {
        if (!isInBounds(belt.fromX, belt.fromY)) return false
        if (!isInBounds(belt.toX, belt.toY))   return false
        if (belt.from == belt.to)                return false
        // Reject duplicate connections (either direction).
        val alreadyExists = _beltConnections.any {
            (it.from == belt.from && it.to == belt.to) ||
            (it.from == belt.to   && it.to == belt.from)
        }
        if (alreadyExists) return false
        _beltConnections.add(belt)
        return true
    }

    /**
     * Removes the belt connection between the two given endpoints.
     * Order of from/to does not matter.
     *
     * @return true if a connection was found and removed, false otherwise.
     */
    fun removeBelt(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean =
        _beltConnections.removeAll {
            (it.fromX == fromX && it.fromY == fromY && it.toX == toX && it.toY == toY) ||
            (it.fromX == toX   && it.fromY == toY   && it.toX == fromX && it.toY == fromY)
        }

    /**
     * Returns all belt connections that have (x, y) as either endpoint.
     */
    fun beltsAt(x: Int, y: Int): List<BeltConnection> =
        _beltConnections.filter { it.connects(x, y) }
}
