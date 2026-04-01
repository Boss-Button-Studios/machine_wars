package com.bossbuttonstudios.machinewars

import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.rendering.SceneLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session 4 — Rendering layer.
 *
 * GameView itself is not testable in JVM unit tests (it extends SurfaceView).
 * SceneLayout is pure Kotlin with no Android imports and covers all coordinate
 * math, so every geometric guarantee lives here.
 */
class Session4Tests {

    // ---- Layout dimensions --------------------------------------------------

    @Test
    fun `battlefield bottom is battlefield fraction of screen height`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(800f * SceneLayout.BATTLEFIELD_FRACTION, l.battlefieldBottom, 0.01f)
    }

    @Test
    fun `factory top equals battlefield bottom`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.battlefieldBottom, l.factoryOffsetY, 0.01f)
    }

    @Test
    fun `factory height is screen height minus battlefield bottom`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(800f - l.battlefieldBottom, l.factoryHeight, 0.01f)
    }

    @Test
    fun `cell size fits horizontally within four columns`() {
        val l = SceneLayout(360f, 800f)
        assertTrue(l.cellSize <= 360f / FactoryGrid.COLS + 0.01f)
    }

    @Test
    fun `cell size fits vertically within six rows`() {
        val l = SceneLayout(360f, 800f)
        assertTrue(l.cellSize <= l.factoryHeight / FactoryGrid.ROWS + 0.01f)
    }

    @Test
    fun `factory grid is centred horizontally`() {
        val l = SceneLayout(360f, 800f)
        val gridWidth = l.cellSize * FactoryGrid.COLS
        val expectedOffset = (360f - gridWidth) / 2f
        assertEquals(expectedOffset, l.factoryOffsetX, 0.01f)
    }

    // ---- Lane geometry -------------------------------------------------------

    @Test
    fun `lane centre x values divide screen into thirds`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(60f,  l.laneCenterX(0), 0.01f)
        assertEquals(180f, l.laneCenterX(1), 0.01f)
        assertEquals(300f, l.laneCenterX(2), 0.01f)
    }

    @Test
    fun `lane left and right edges are consistent with centre`() {
        val l = SceneLayout(360f, 800f)
        for (lane in 0..2) {
            val mid = (l.laneLeftX(lane) + l.laneRightX(lane)) / 2f
            assertEquals(l.laneCenterX(lane), mid, 0.01f)
        }
    }

    @Test
    fun `boundary x positions fall at lane edges`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.laneRightX(0), l.leftBoundaryX,  0.01f)
        assertEquals(l.laneRightX(1), l.rightBoundaryX, 0.01f)
    }

    // ---- Unit Y mapping ------------------------------------------------------

    @Test
    fun `position 0 maps to battlefield bottom (player end)`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.battlefieldBottom, l.unitY(0f), 0.01f)
    }

    @Test
    fun `position 1 maps to top of battlefield (enemy end)`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(0f, l.unitY(1f), 0.01f)
    }

    @Test
    fun `position 0_5 maps to midpoint of battlefield`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.battlefieldBottom / 2f, l.unitY(0.5f), 0.01f)
    }

    @Test
    fun `unitY decreases as position increases`() {
        val l = SceneLayout(360f, 800f)
        assertTrue(l.unitY(0.3f) > l.unitY(0.6f))
    }

    // ---- Interpolated unit Y -------------------------------------------------

    @Test
    fun `interpolatedUnitY at zero interpolation equals unitY at current position`() {
        val l = SceneLayout(360f, 800f)
        val pos = 0.4f
        assertEquals(l.unitY(pos), l.interpolatedUnitY(pos, 2f, 1f, 0f), 0.01f)
    }

    @Test
    fun `interpolatedUnitY with zero speed is unaffected by interpolation`() {
        val l = SceneLayout(360f, 800f)
        val pos = 0.5f
        val atZero = l.interpolatedUnitY(pos, 0f, 1f, 0f)
        val atOne  = l.interpolatedUnitY(pos, 0f, 1f, 1f)
        assertEquals(atZero, atOne, 0.01f)
    }

    @Test
    fun `interpolatedUnitY extrapolates player unit forward (toward enemy end)`() {
        val l = SceneLayout(360f, 800f)
        val pos = 0.4f
        val speed = 2f
        val teamSign = 1f  // PLAYER moves toward 1.0
        val yAtZero = l.interpolatedUnitY(pos, speed, teamSign, 0f)
        val yAtOne  = l.interpolatedUnitY(pos, speed, teamSign, 1f)
        // Higher position → lower Y on screen
        assertTrue(yAtOne < yAtZero)
    }

    @Test
    fun `interpolatedUnitY extrapolates enemy unit forward (toward player end)`() {
        val l = SceneLayout(360f, 800f)
        val pos = 0.6f
        val speed = 2f
        val teamSign = -1f  // ENEMY moves toward 0.0
        val yAtZero = l.interpolatedUnitY(pos, speed, teamSign, 0f)
        val yAtOne  = l.interpolatedUnitY(pos, speed, teamSign, 1f)
        // Lower position → higher Y on screen
        assertTrue(yAtOne > yAtZero)
    }

    @Test
    fun `interpolatedUnitY at full interpolation matches manual extrapolation`() {
        val l = SceneLayout(360f, 800f)
        val pos = 0.5f
        val speed = 3f
        val teamSign = 1f
        val expected = l.unitY((pos + speed * teamSign * SceneLayout.TICK_DURATION).coerceIn(0f, 1f))
        val actual = l.interpolatedUnitY(pos, speed, teamSign, 1f)
        assertEquals(expected, actual, 0.01f)
    }

    @Test
    fun `interpolatedUnitY clamps extrapolated position to valid range`() {
        val l = SceneLayout(360f, 800f)
        // Unit nearly at enemy end moving fast — should not overshoot
        val y = l.interpolatedUnitY(0.999f, 5f, 1f, 1f)
        assertTrue(y >= 0f)
        assertTrue(y <= l.battlefieldBottom)
    }

    // ---- Factory cell centres -----------------------------------------------

    @Test
    fun `cellCenterX for column 0 is factoryOffsetX plus half cell`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.factoryOffsetX + l.cellSize * 0.5f, l.cellCenterX(0), 0.01f)
    }

    @Test
    fun `cellCenterY for row 0 is factoryOffsetY plus half cell`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.factoryOffsetY + l.cellSize * 0.5f, l.cellCenterY(0), 0.01f)
    }

    @Test
    fun `adjacent cell centres are exactly one cell apart`() {
        val l = SceneLayout(360f, 800f)
        assertEquals(l.cellSize, l.cellCenterX(1) - l.cellCenterX(0), 0.01f)
        assertEquals(l.cellSize, l.cellCenterY(1) - l.cellCenterY(0), 0.01f)
    }

    @Test
    fun `cell centres stay within factory area`() {
        val l = SceneLayout(360f, 800f)
        for (col in 0 until FactoryGrid.COLS) {
            for (row in 0 until FactoryGrid.ROWS) {
                val cx = l.cellCenterX(col)
                val cy = l.cellCenterY(row)
                assertTrue("col $col cx $cx out of [0, ${l.screenWidth}]", cx in 0f..l.screenWidth)
                assertTrue("row $row cy $cy out of [${l.battlefieldBottom}, ${l.screenHeight}]",
                    cy in l.battlefieldBottom..l.screenHeight)
            }
        }
    }

    // ---- Rotation constant --------------------------------------------------

    @Test
    fun `tick duration matches 60 Hz fixed step`() {
        assertEquals(1f / 60f, SceneLayout.TICK_DURATION, 0.0001f)
    }

    // ---- Screen size variants -----------------------------------------------

    @Test
    fun `layout scales correctly for narrow tall screen`() {
        val l = SceneLayout(320f, 960f)
        assertEquals(320f, l.laneRightX(2), 0.01f)
        assertTrue(l.battlefieldBottom < 960f)
        assertTrue(l.cellSize > 0f)
    }

    @Test
    fun `layout scales correctly for wide short screen`() {
        val l = SceneLayout(480f, 640f)
        assertEquals(480f, l.laneRightX(2), 0.01f)
        assertTrue(l.battlefieldBottom < 640f)
        assertTrue(l.cellSize > 0f)
    }
}
