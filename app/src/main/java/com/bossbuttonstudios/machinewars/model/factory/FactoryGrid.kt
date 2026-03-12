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
 * simulation in Session 2 and stored in [powerFlow].
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

    // --- Placement helpers ---

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
     */
    fun remove(x: Int, y: Int): Component? {
        val c = _components.remove(x to y) ?: return null
        c.gridX = -1
        c.gridY = -1
        return c
    }

    /** Returns the component at (x, y), or null. */
    fun componentAt(x: Int, y: Int): Component? = _components[x to y]

    /** Returns the machine at (x, y), or null. */
    fun machineAt(x: Int, y: Int): Machine? = machines.firstOrNull { it.gridX == x && it.gridY == y }
}
