package com.bossbuttonstudios.machinewars.model.economy

import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.ComponentType

/**
 * The between-wave store.
 *
 * Standard components (Gears, Pulleys, Gear Pulleys) rotate randomly.
 * Artifact-unlocked items are permanently available once earned.
 * Belts are always free and never appear in the store.
 *
 * Store rotation logic and pricing are expanded in Session 6.
 * This class is a skeleton that holds the current rotation and exposes
 * purchase / recycle helpers for wiring into the game loop.
 */
class Store {

    /**
     * A single item slot in the current rotation.
     */
    data class StoreItem(
        val component: Component,
        val oreCost: Int,
    )

    private val _rotation = mutableListOf<StoreItem>()

    /** The current randomly-selected component offerings. Read-only view. */
    val rotation: List<StoreItem> get() = _rotation.toList()

    /**
     * Replaces the current rotation with a new random selection.
     * Called by wave management at the end of each wave.
     *
     * Placeholder: returns three standard-size components of each purchasable
     * type. Session 6 will replace this with weighted random draw.
     */
    fun rotate(seed: Long = System.currentTimeMillis()) {
        _rotation.clear()
        val rng = java.util.Random(seed)
        val purchasable = listOf(ComponentType.GEAR, ComponentType.PULLEY, ComponentType.GEAR_PULLEY)
        repeat(3) {
            val type = purchasable[rng.nextInt(purchasable.size)]
            val size = rng.nextInt(8) + 1  // 1..8
            _rotation.add(StoreItem(Component(type = type, size = size), oreCostFor(size)))
        }
    }

    /**
     * Attempts to purchase the item at [index] using the player's [wallet].
     * @return The purchased [Component] on success; null if out of funds or
     *         index is invalid.
     */
    fun purchase(index: Int, wallet: Wallet): Component? {
        val item = _rotation.getOrNull(index) ?: return null
        if (!wallet.spendOre(item.oreCost)) return null
        _rotation.removeAt(index)
        return item.component
    }

    /**
     * Recycles a worn component for a partial ore refund.
     * Refund = floor(base cost * (1 - wearPct) * RECYCLE_RATIO).
     */
    fun recycle(component: Component, wallet: Wallet) {
        val baseValue = oreCostFor(component.size)
        val refund = (baseValue * (1f - component.wearPct) * RECYCLE_RATIO).toInt()
        wallet.earnOre(refund)
    }

    companion object {
        const val RECYCLE_RATIO = 0.5f

        /** Baseline cost scales with component size. Tuned in Session 6. */
        fun oreCostFor(size: Int): Int = size * 10
    }
}
