package com.bossbuttonstudios.machinewars.drivetrain

import com.bossbuttonstudios.machinewars.core.ComponentExpiredEvent
import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid

/**
 * Applies wear deltas computed by [DrivetrainSolver] to the live component
 * state and removes expired components from the grid.
 *
 * This is the one place in Session 2 where side effects happen. The solver
 * itself is pure; this system translates its output into mutations.
 *
 * Called once per tick, after [DrivetrainSolver.solve] returns.
 *
 * Wear stages (spec §5.6):
 *   Stage 1 [0.00, 0.33): nominal — some scuffs, full efficiency
 *   Stage 2 [0.33, 0.66): worn  — modest penalty, accelerated wear
 *   Stage 3 [0.66, 1.00): critical — significant penalty, rapidly expiring
 *
 * At wearPct ≥ 1.0 the component disappears from the grid immediately
 * (spec §5.6: "A component that expires at stage 3 disappears from the grid
 * immediately and must be replaced from inventory or purchased from the store").
 */
class WearSystem {

    /**
     * Apply wear deltas from [result] to placed components in [grid].
     * Expired components are removed from the grid and a
     * [ComponentExpiredEvent] is posted for each.
     *
     * @return The list of components removed this tick, in removal order.
     */
    fun applyWear(
        grid: FactoryGrid,
        result: DrivetrainResult,
        eventBus: EventBus,
    ): List<Component> {
        val removed = mutableListOf<Component>()

        for (pos in result.expiredPositions) {
            val (x, y) = pos
            val component = grid.componentAt(x, y) ?: continue

            // Clamp to 1.0 before removal so any downstream reader sees a
            // clean terminal value rather than a value slightly over 1.
            component.wearPct = 1f

            grid.remove(x, y)
            removed.add(component)

            eventBus.post(
                ComponentExpiredEvent(
                    gridX = x,
                    gridY = y,
                    type  = component.type,
                )
            )
        }

        // Apply non-expiring deltas to all other reachable components.
        for ((pos, node) in result.nodes) {
            if (node.wearDelta <= 0f) continue
            if (pos in result.expiredPositions) continue   // already handled above

            val (x, y) = pos
            val component = grid.componentAt(x, y) ?: continue
            component.wearPct = (component.wearPct + node.wearDelta).coerceIn(0f, 1f)
        }

        return removed
    }
}
