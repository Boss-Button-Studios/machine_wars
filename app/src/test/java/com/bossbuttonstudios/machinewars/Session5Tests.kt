package com.bossbuttonstudios.machinewars

import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.rendering.SceneLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session 5 — Input handling (lane assignment).
 *
 * Touch handling lives in GameView (not JVM-testable). All coordinate mapping
 * that drives touch decisions lives in SceneLayout and is tested here.
 */
class Session5Tests {

    private val layout = SceneLayout(360f, 800f)

    // ---- isBattlefieldTouch / isFactoryTouch --------------------------------

    @Test
    fun `y above battlefield bottom is a battlefield touch`() {
        assertTrue(layout.isBattlefieldTouch(layout.battlefieldBottom - 1f))
    }

    @Test
    fun `y at battlefield bottom is a factory touch`() {
        assertTrue(layout.isFactoryTouch(layout.battlefieldBottom))
    }

    @Test
    fun `y below battlefield bottom is a factory touch`() {
        assertTrue(layout.isFactoryTouch(layout.battlefieldBottom + 1f))
    }

    @Test
    fun `battlefield and factory touches are mutually exclusive`() {
        for (y in listOf(0f, 100f, layout.battlefieldBottom - 1f)) {
            assertTrue(layout.isBattlefieldTouch(y))
            assertFalse(layout.isFactoryTouch(y))
        }
        for (y in listOf(layout.battlefieldBottom, layout.battlefieldBottom + 1f, layout.screenHeight - 1f)) {
            assertFalse(layout.isBattlefieldTouch(y))
            assertTrue(layout.isFactoryTouch(y))
        }
    }

    // ---- laneAt -------------------------------------------------------------

    @Test
    fun `touch at left lane centre returns lane 0`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(0, layout.laneAt(layout.laneCenterX(0), y))
    }

    @Test
    fun `touch at centre lane centre returns lane 1`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(1, layout.laneAt(layout.laneCenterX(1), y))
    }

    @Test
    fun `touch at right lane centre returns lane 2`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(2, layout.laneAt(layout.laneCenterX(2), y))
    }

    @Test
    fun `touch at x=0 returns lane 0`() {
        assertEquals(0, layout.laneAt(0f, layout.battlefieldBottom / 2f))
    }

    @Test
    fun `touch at right edge of screen returns lane 2`() {
        assertEquals(2, layout.laneAt(layout.screenWidth, layout.battlefieldBottom / 2f))
    }

    @Test
    fun `touch just left of left boundary is lane 0`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(0, layout.laneAt(layout.leftBoundaryX - 1f, y))
    }

    @Test
    fun `touch at left boundary belongs to lane 1`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(1, layout.laneAt(layout.leftBoundaryX, y))
    }

    @Test
    fun `touch just left of right boundary is lane 1`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(1, layout.laneAt(layout.rightBoundaryX - 1f, y))
    }

    @Test
    fun `touch at right boundary belongs to lane 2`() {
        val y = layout.battlefieldBottom / 2f
        assertEquals(2, layout.laneAt(layout.rightBoundaryX, y))
    }

    @Test
    fun `laneAt returns null when y is in factory area`() {
        assertNull(layout.laneAt(layout.laneCenterX(1), layout.battlefieldBottom + 10f))
    }

    @Test
    fun `laneAt is consistent across the full battlefield height`() {
        val yValues = listOf(1f, layout.battlefieldBottom / 3f, layout.battlefieldBottom - 1f)
        for (y in yValues) {
            assertEquals(0, layout.laneAt(layout.laneCenterX(0), y))
            assertEquals(1, layout.laneAt(layout.laneCenterX(1), y))
            assertEquals(2, layout.laneAt(layout.laneCenterX(2), y))
        }
    }

    // ---- gridCellAt ---------------------------------------------------------

    @Test
    fun `touch at each cell centre returns correct col and row`() {
        for (col in 0 until FactoryGrid.COLS) {
            for (row in 0 until FactoryGrid.ROWS) {
                val x = layout.cellCenterX(col)
                val y = layout.cellCenterY(row)
                val cell = layout.gridCellAt(x, y)
                assertNotNull("Expected cell at col=$col row=$row", cell)
                assertEquals("col mismatch at col=$col row=$row", col, cell!!.first)
                assertEquals("row mismatch at col=$col row=$row", row, cell.second)
            }
        }
    }

    @Test
    fun `gridCellAt returns null when y is in battlefield`() {
        assertNull(layout.gridCellAt(layout.cellCenterX(0), layout.battlefieldBottom - 1f))
    }

    @Test
    fun `gridCellAt returns null when x is left of factory grid`() {
        if (layout.factoryOffsetX > 0f) {
            assertNull(layout.gridCellAt(layout.factoryOffsetX - 1f, layout.cellCenterY(0)))
        }
    }

    @Test
    fun `gridCellAt returns null when x is right of factory grid`() {
        val rightEdge = layout.factoryOffsetX + layout.cellSize * FactoryGrid.COLS
        assertNull(layout.gridCellAt(rightEdge + 1f, layout.cellCenterY(0)))
    }

    @Test
    fun `gridCellAt returns null when y is below factory grid`() {
        val bottomEdge = layout.factoryOffsetY + layout.cellSize * FactoryGrid.ROWS
        assertNull(layout.gridCellAt(layout.cellCenterX(0), bottomEdge + 1f))
    }

    @Test
    fun `gridCellAt top-left corner cell is col 0 row 0`() {
        val x = layout.factoryOffsetX + 1f
        val y = layout.factoryOffsetY + 1f
        val cell = layout.gridCellAt(x, y)
        assertNotNull(cell)
        assertEquals(0, cell!!.first)
        assertEquals(0, cell.second)
    }

    @Test
    fun `gridCellAt bottom-right corner cell is max col and max row`() {
        val x = layout.factoryOffsetX + layout.cellSize * FactoryGrid.COLS - 1f
        val y = layout.factoryOffsetY + layout.cellSize * FactoryGrid.ROWS - 1f
        val cell = layout.gridCellAt(x, y)
        assertNotNull(cell)
        assertEquals(FactoryGrid.COLS - 1, cell!!.first)
        assertEquals(FactoryGrid.ROWS - 1, cell.second)
    }
}
