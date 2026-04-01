package com.bossbuttonstudios.machinewars

import com.bossbuttonstudios.machinewars.core.ComponentExpiredEvent
import com.bossbuttonstudios.machinewars.core.EventBus
import com.bossbuttonstudios.machinewars.drivetrain.DrivetrainResult
import com.bossbuttonstudios.machinewars.drivetrain.DrivetrainSolver
import com.bossbuttonstudios.machinewars.drivetrain.WearSystem
import com.bossbuttonstudios.machinewars.model.factory.BeltConnection
import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.ComponentType
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.factory.Machine
import com.bossbuttonstudios.machinewars.model.factory.MachineProfile
import com.bossbuttonstudios.machinewars.model.factory.MachineRegistry
import com.bossbuttonstudios.machinewars.model.factory.MachineType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// =============================================================================
// MachineRegistry
// =============================================================================

class MachineRegistryTest {

    @Test fun `all machine types are registered`() {
        MachineType.entries.forEach { type ->
            val profile = MachineRegistry[type]
            assertEquals(type, profile.type)
        }
    }

    @Test fun `brute prefers lower RPM than skirmisher`() {
        val brute       = MachineRegistry[MachineType.COMBAT_BRUTE]
        val skirmisher  = MachineRegistry[MachineType.COMBAT_SKIRMISHER]
        assertTrue(brute.preferredRpm < skirmisher.preferredRpm)
    }

    @Test fun `artillery has tighter tolerance than miner`() {
        val artillery = MachineRegistry[MachineType.COMBAT_ARTILLERY]
        val miner     = MachineRegistry[MachineType.MINER]
        assertTrue(artillery.rpmTolerance < miner.rpmTolerance)
    }

    @Test fun `skirmisher has higher base output rate than artillery`() {
        val skirmisher = MachineRegistry[MachineType.COMBAT_SKIRMISHER]
        val artillery  = MachineRegistry[MachineType.COMBAT_ARTILLERY]
        assertTrue(skirmisher.baseOutputRate > artillery.baseOutputRate)
    }

    @Test fun `all profiles have positive preferred RPM and tolerance`() {
        MachineType.entries.forEach { type ->
            val p = MachineRegistry[type]
            assertTrue("${p.type} preferredRpm must be > 0", p.preferredRpm > 0f)
            assertTrue("${p.type} rpmTolerance must be > 0", p.rpmTolerance > 0f)
            assertTrue("${p.type} baseOutputRate must be > 0", p.baseOutputRate > 0f)
        }
    }
}

// =============================================================================
// DrivetrainSolver — rpmQuality curve
// =============================================================================

class RpmQualityTest {

    private val solver = DrivetrainSolver()

    @Test fun `quality is 1_0 at preferred RPM`() {
        val q = solver.rpmQuality(100f, preferredRpm = 100f, tolerance = 30f)
        assertEquals(1.0f, q, 0.001f)
    }

    @Test fun `quality is below 1_0 away from preferred`() {
        val q = solver.rpmQuality(150f, preferredRpm = 100f, tolerance = 30f)
        assertTrue("quality should be less than 1.0 off-peak", q < 1.0f)
        assertTrue("quality should be positive", q > 0f)
    }

    @Test fun `quality is symmetric around preferred RPM`() {
        val qAbove = solver.rpmQuality(130f, preferredRpm = 100f, tolerance = 30f)
        val qBelow = solver.rpmQuality(70f,  preferredRpm = 100f, tolerance = 30f)
        assertEquals(qAbove, qBelow, 0.001f)
    }

    @Test fun `tighter tolerance drops faster`() {
        val qLoose  = solver.rpmQuality(150f, preferredRpm = 100f, tolerance = 60f)
        val qTight  = solver.rpmQuality(150f, preferredRpm = 100f, tolerance = 20f)
        assertTrue("tight tolerance should give lower quality off-peak", qTight < qLoose)
    }

    @Test fun `quality is zero for zero tolerance`() {
        val q = solver.rpmQuality(100f, preferredRpm = 100f, tolerance = 0f)
        assertEquals(0f, q, 0.001f)
    }
}

// =============================================================================
// DrivetrainSolver — empty grid
// =============================================================================

class DrivetrainSolverEmptyGridTest {

    private val solver = DrivetrainSolver()

    private fun emptyGrid() = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())

    @Test fun `empty grid returns full power available`() {
        val result = solver.solve(emptyGrid(), dt = 1f / 60f)
        assertEquals(DrivetrainSolver.MOTOR_POWER, result.powerAvailable, 0.01f)
        assertEquals(1f, result.powerFraction, 0.001f)
    }

    @Test fun `empty grid has no machine output rates`() {
        val result = solver.solve(emptyGrid(), dt = 1f / 60f)
        assertTrue(result.machineOutputRates.isEmpty())
    }

    @Test fun `empty grid has no expired positions`() {
        val result = solver.solve(emptyGrid(), dt = 1f / 60f)
        assertTrue(result.expiredPositions.isEmpty())
    }

    @Test fun `motor node is always present in result`() {
        val result = solver.solve(emptyGrid(), dt = 1f / 60f)
        val motorNode = result.nodes[0 to 0]
        assertNotNull(motorNode)
        assertEquals(DrivetrainSolver.MOTOR_RPM, motorNode!!.rpm, 0.01f)
    }
}

// =============================================================================
// DrivetrainSolver — gear ratio propagation
// =============================================================================

class DrivetrainSolverGearRatioTest {

    private val solver = DrivetrainSolver()
    private val dt     = 1f / 60f

    /**
     * Motor(4) at (0,0) adjacent to Gear(size=S) at (1,0).
     * Expected gear RPM = MOTOR_RPM * (4 / S).
     */
    private fun gridWithOneGear(size: Int): FactoryGrid {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = size), 1, 0)
        return g
    }

    @Test fun `size-4 gear adjacent to motor runs at motor RPM`() {
        val result = solver.solve(gridWithOneGear(4), dt)
        val gearNode = result.nodes[1 to 0]
        assertNotNull(gearNode)
        assertEquals(DrivetrainSolver.MOTOR_RPM, gearNode!!.rpm, 0.1f)
    }

    @Test fun `size-2 gear adjacent to motor runs at 2x motor RPM`() {
        val result = solver.solve(gridWithOneGear(2), dt)
        val gearNode = result.nodes[1 to 0]
        assertNotNull(gearNode)
        assertEquals(DrivetrainSolver.MOTOR_RPM * 2f, gearNode!!.rpm, 0.1f)
    }

    @Test fun `size-8 gear adjacent to motor runs at half motor RPM`() {
        val result = solver.solve(gridWithOneGear(8), dt)
        val gearNode = result.nodes[1 to 0]
        assertNotNull(gearNode)
        assertEquals(DrivetrainSolver.MOTOR_RPM / 2f, gearNode!!.rpm, 0.1f)
    }

    @Test fun `gear chain compounds ratios correctly`() {
        // Motor(4) at (0,0) → Gear(2) at (1,0) → Gear(8) at (2,0)
        // Gear(2) rpm = 100 * 4/2 = 200
        // Gear(8) rpm = 200 * 2/8 = 50
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 2), 1, 0)
        g.place(Component(type = ComponentType.GEAR, size = 8), 2, 0)
        val result = solver.solve(g, dt)
        assertEquals(200f, result.nodes[1 to 0]!!.rpm, 0.1f)
        assertEquals(50f,  result.nodes[2 to 0]!!.rpm, 0.1f)
    }

    @Test fun `disconnected gear has no node in result`() {
        // Gear at (5, 3) — far corner, not adjacent to motor or any connected gear.
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4), 5, 3)
        val result = solver.solve(g, dt)
        assertNull(result.nodes[5 to 3])
    }
}

// =============================================================================
// DrivetrainSolver — machine output rates
// =============================================================================

class DrivetrainSolverMachineOutputTest {

    private val solver = DrivetrainSolver()
    private val dt     = 1f / 60f

    /**
     * Grid with motor at (0,0) and a machine at (machineX, machineY).
     * An optional intermediate gear at (gearX, gearY) between motor and machine.
     */
    private fun gridWithMachine(
        machineType: MachineType,
        machineX: Int, machineY: Int,
        gearSize: Int? = null, gearX: Int = 0, gearY: Int = 0,
    ): FactoryGrid {
        val machine = Machine(type = machineType, gridX = machineX, gridY = machineY)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        if (gearSize != null) {
            g.place(Component(type = ComponentType.GEAR, size = gearSize), gearX, gearY)
        }
        return g
    }

    @Test fun `machine directly adjacent to motor receives motor RPM`() {
        // Motor(0,0) adjacent to machine(1,0). Machine RPM = motor RPM = 100.
        val machine = Machine(type = MachineType.COMBAT_ARTILLERY, gridX = 1, gridY = 0)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        val result = solver.solve(g, dt)
        val machineNode = result.nodes[1 to 0]
        assertNotNull(machineNode)
        assertEquals(DrivetrainSolver.MOTOR_RPM, machineNode!!.rpm, 0.1f)
    }

    @Test fun `artillery at motor RPM produces near-peak output rate`() {
        // Artillery preferred = 100 RPM, machine directly adjacent to motor.
        val machine = Machine(type = MachineType.COMBAT_ARTILLERY, gridX = 1, gridY = 0)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        val result = solver.solve(g, dt)
        val profile = MachineRegistry[MachineType.COMBAT_ARTILLERY]
        // Fresh grid, full power — output should be ≥ 95% of base rate.
        val rate = result.machineOutputRates[machine.id] ?: 0f
        assertTrue(
            "Artillery at preferred RPM should output ≥ 95% of base rate, got $rate",
            rate >= profile.baseOutputRate * 0.95f,
        )
    }

    @Test fun `skirmisher at 2x motor RPM produces near-peak output rate`() {
        // Size-2 gear between motor and machine → machine sees 200 RPM.
        // Skirmisher preferred = 200 RPM.
        val machine = Machine(type = MachineType.COMBAT_SKIRMISHER, gridX = 2, gridY = 0)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        g.place(Component(type = ComponentType.GEAR, size = 2), 1, 0)
        val result = solver.solve(g, dt)
        val profile = MachineRegistry[MachineType.COMBAT_SKIRMISHER]
        val rate = result.machineOutputRates[machine.id] ?: 0f
        assertTrue(
            "Skirmisher at preferred RPM should output ≥ 95% of base rate, got $rate",
            rate >= profile.baseOutputRate * 0.95f,
        )
    }

    @Test fun `brute at half motor RPM produces near-peak output rate`() {
        // Size-8 gear → machine sees 50 RPM. Brute preferred = 50 RPM.
        val machine = Machine(type = MachineType.COMBAT_BRUTE, gridX = 2, gridY = 0)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        g.place(Component(type = ComponentType.GEAR, size = 8), 1, 0)
        val result = solver.solve(g, dt)
        val profile = MachineRegistry[MachineType.COMBAT_BRUTE]
        val rate = result.machineOutputRates[machine.id] ?: 0f
        assertTrue(
            "Brute at preferred RPM should output ≥ 95% of base rate, got $rate",
            rate >= profile.baseOutputRate * 0.95f,
        )
    }

    @Test fun `machine far off preferred RPM produces reduced output`() {
        // Size-2 gear drives machine → 200 RPM. Brute prefers 50 RPM.
        // The penalty should be severe — brute is 3 sigma away.
        val machine = Machine(type = MachineType.COMBAT_BRUTE, gridX = 2, gridY = 0)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        g.place(Component(type = ComponentType.GEAR, size = 2), 1, 0)
        val result = solver.solve(g, dt)
        val profile = MachineRegistry[MachineType.COMBAT_BRUTE]
        val rate = result.machineOutputRates[machine.id] ?: 0f
        assertTrue(
            "Brute at 200 RPM (preferred=50) should output ≤ 10% of base rate, got $rate",
            rate <= profile.baseOutputRate * 0.10f,
        )
    }

    @Test fun `isolated machine with no path from motor produces zero output`() {
        // Machine at (5, 3) with no connected components.
        val machine = Machine(type = MachineType.MINER, gridX = 5, gridY = 3)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        val result = solver.solve(g, dt)
        val rate = result.machineOutputRates[machine.id]
        // Either not present or zero — disconnected machines get nothing.
        assertTrue(
            "Isolated machine should have no output, got $rate",
            rate == null || rate == 0f,
        )
    }

    @Test fun `two machines on separate branches both get output rates`() {
        // Motor(0,0) → Gear(2) at (1,0) → Skirmisher at (2,0)
        //           → Gear(8) at (0,1) → Brute at (0,2)
        val skirmisher = Machine(type = MachineType.COMBAT_SKIRMISHER, gridX = 2, gridY = 0)
        val brute      = Machine(type = MachineType.COMBAT_BRUTE,      gridX = 0, gridY = 2)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(skirmisher, brute))
        g.place(Component(type = ComponentType.GEAR, size = 2), 1, 0)
        g.place(Component(type = ComponentType.GEAR, size = 8), 0, 1)
        val result = solver.solve(g, dt)
        assertNotNull(result.machineOutputRates[skirmisher.id])
        assertNotNull(result.machineOutputRates[brute.id])
        assertTrue(result.machineOutputRates[skirmisher.id]!! > 0f)
        assertTrue(result.machineOutputRates[brute.id]!! > 0f)
    }
}

// =============================================================================
// DrivetrainSolver — belt connections
// =============================================================================

class DrivetrainSolverBeltTest {

    private val solver = DrivetrainSolver()
    private val dt     = 1f / 60f

    @Test fun `belt connects two same-size pulleys at same RPM`() {
        // Motor-pulley(4) at (0,0) → Belt → Pulley(4) at (3,0)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.PULLEY, size = 4), 3, 0)
        g.addBelt(BeltConnection(fromX = 0, fromY = 0, toX = 3, toY = 0))
        val result = solver.solve(g, dt)
        val pulleyNode = result.nodes[3 to 0]
        assertNotNull("Pulley at (3,0) should be reachable via belt", pulleyNode)
        assertEquals(DrivetrainSolver.MOTOR_RPM, pulleyNode!!.rpm, 0.1f)
    }

    @Test fun `belt with smaller driven pulley increases RPM`() {
        // Motor-pulley(4) → Belt → Pulley(2) at (3,0)
        // Driven pulley RPM = 100 * (4/2) = 200
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.PULLEY, size = 2), 3, 0)
        g.addBelt(BeltConnection(fromX = 0, fromY = 0, toX = 3, toY = 0))
        val result = solver.solve(g, dt)
        assertEquals(200f, result.nodes[3 to 0]!!.rpm, 0.5f)
    }

    @Test fun `belt reduces path efficiency with length`() {
        // Motor → long belt (length 3) → Pulley(4) at (3,0) → Machine
        // Belt efficiency = max(0.1, 1 - 3 * 0.05) = 0.85
        // Machine output should be reduced vs a direct gear connection.
        val machine = Machine(type = MachineType.COMBAT_ARTILLERY, gridX = 3, gridY = 1)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        g.place(Component(type = ComponentType.GEAR_PULLEY, size = 4), 3, 0)
        g.addBelt(BeltConnection(fromX = 0, fromY = 0, toX = 3, toY = 0, length = 3f))
        val result = solver.solve(g, dt)

        val pulleyNode = result.nodes[3 to 0]
        assertNotNull(pulleyNode)
        assertTrue(
            "Path efficiency must be < 1.0 after belt loss",
            pulleyNode!!.pathEfficiency < 1.0f,
        )
        // Expected efficiency = 1 - 3 * 0.05 = 0.85
        assertEquals(0.85f, pulleyNode.pathEfficiency, 0.01f)
    }

    @Test fun `very long belt is floored at minimum efficiency`() {
        // Length 100 → raw efficiency = 1 - 100*0.05 = -4.0 → floored at 0.10
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.PULLEY, size = 4), 5, 3)
        g.addBelt(BeltConnection(fromX = 0, fromY = 0, toX = 5, toY = 3, length = 100f))
        val result = solver.solve(g, dt)
        val node = result.nodes[5 to 3]
        assertNotNull(node)
        assertEquals(DrivetrainSolver.BELT_MIN_EFFICIENCY, node!!.pathEfficiency, 0.001f)
    }

    @Test fun `stacked belt losses compound along multi-hop path`() {
        // Motor → Belt(len=2, eff=0.9) → GearPulley(4) at (2,0)
        //       → Belt(len=2, eff=0.9) → Pulley(4) at (2,3)
        // Path efficiency at (2,3) = 0.9 * 0.9 = 0.81
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR_PULLEY, size = 4), 2, 0)
        g.place(Component(type = ComponentType.PULLEY,      size = 4), 2, 3)
        g.addBelt(BeltConnection(fromX = 0, fromY = 0, toX = 2, toY = 0, length = 2f))
        g.addBelt(BeltConnection(fromX = 2, fromY = 0, toX = 2, toY = 3, length = 2f))
        val result = solver.solve(g, dt)
        val endNode = result.nodes[2 to 3]
        assertNotNull(endNode)
        val expected = (1f - 2f * DrivetrainSolver.BELT_LOSS_PER_UNIT).let { e1 ->
            e1 * (1f - 2f * DrivetrainSolver.BELT_LOSS_PER_UNIT)
        }
        assertEquals(expected, endNode!!.pathEfficiency, 0.01f)
    }
}

// =============================================================================
// DrivetrainSolver — energy draw and power budget
// =============================================================================

class DrivetrainSolverEnergyTest {

    private val solver = DrivetrainSolver()
    private val dt     = 1f / 60f

    @Test fun `fresh size-4 gear at motor RPM has expected energy draw`() {
        // energyDraw = size * SIZE_DRAW_FACTOR * rpmRatio * wearMul
        //            = 4 * 5 * 1.0 * 1.0 = 20.0
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0f), 1, 0)
        val result = solver.solve(g, dt)
        val node = result.nodes[1 to 0]!!
        assertEquals(20f, node.energyDraw, 0.1f)
        assertEquals(DrivetrainSolver.MOTOR_POWER - 20f, result.powerAvailable, 0.5f)
    }

    @Test fun `worn component draws more energy than fresh`() {
        val gFresh = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gFresh.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0f), 1, 0)

        val gWorn = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gWorn.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.9f), 1, 0)

        val freshDraw = solver.solve(gFresh, dt).nodes[1 to 0]!!.energyDraw
        val wornDraw  = solver.solve(gWorn,  dt).nodes[1 to 0]!!.energyDraw
        assertTrue("Worn component must draw more energy than fresh", wornDraw > freshDraw)
    }

    @Test fun `fully worn component draws 2x the fresh draw`() {
        // wearMul at wearPct=1.0: 1 + 1.0 * (2.0 - 1.0) = 2.0
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 1f), 1, 0)
        // We set wearPct=1 manually to test energy formula; solver won't expire it mid-solve.
        val draw = solver.solve(g, dt).nodes[1 to 0]!!.energyDraw
        assertEquals(40f, draw, 0.1f)  // 20 * 2.0
    }

    @Test fun `faster spinning component draws proportionally more energy`() {
        // Size-2 gear adjacent to motor → 200 RPM → rpmRatio = 2.0
        // energyDraw = 2 * 5 * 2.0 * 1.0 = 20.0 (same as size-4 at 100 RPM coincidentally)
        // But let's compare size-4 at 100 vs size-4 geared to 200 (via size-2 driver).
        // To get size-4 at 200 RPM: Motor(4) → Gear(2) → Gear(4) at (2,0)
        // Gear(4) RPM = 200 * (2/4) = 100. Back to 100 — same.
        // More cleanly: just compare gear(4) driven at 100 vs 200 directly.
        // Use size-4 at (1,0) adjacent to motor (100 RPM) vs size-4 at (1,0)
        // with motor-size 8 to double the speed... easier to just test via size.

        // Gear(2) at 200 RPM: draw = 2 * 5 * 2.0 = 20
        // Gear(4) at 100 RPM: draw = 4 * 5 * 1.0 = 20
        // Same draw! But if we compare gear(4) at 100 to gear(4) at 200:
        // We need two grid setups with different RPMs for a size-4 component.
        // Motor(4)@100 → Gear(2)@200 → Gear(4)@100. Back to 100.
        // To get a size-4 at 200, we'd need the motor at 200 — not testable directly.
        // Instead, compare gear(2) at motor (200 RPM) vs gear(4) at motor (100 RPM):
        val g2 = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g2.place(Component(type = ComponentType.GEAR, size = 2), 1, 0)

        val g4 = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g4.place(Component(type = ComponentType.GEAR, size = 4), 1, 0)

        val draw2 = solver.solve(g2, dt).nodes[1 to 0]!!.energyDraw  // size2, 200 RPM
        val draw4 = solver.solve(g4, dt).nodes[1 to 0]!!.energyDraw  // size4, 100 RPM

        // size2 at 200: 2*5*2.0 = 20. size4 at 100: 4*5*1.0 = 20. Equal by coincidence.
        // Let's verify numerically instead.
        assertEquals(
            2f * DrivetrainSolver.SIZE_DRAW_FACTOR * 2f,  // size2, rpmRatio=2
            draw2,
            0.1f,
        )
        assertEquals(
            4f * DrivetrainSolver.SIZE_DRAW_FACTOR * 1f,  // size4, rpmRatio=1
            draw4,
            0.1f,
        )
    }

    @Test fun `total energy draw reduces power available`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        // Two gears, both size 4 at motor RPM: each draws 20, total 40.
        g.place(Component(type = ComponentType.GEAR, size = 4), 1, 0)
        g.place(Component(type = ComponentType.GEAR, size = 4), 2, 0)
        val result = solver.solve(g, dt)
        assertEquals(40f, result.totalEnergyDraw, 1f)
        assertEquals(DrivetrainSolver.MOTOR_POWER - 40f, result.powerAvailable, 1f)
        assertEquals(
            (DrivetrainSolver.MOTOR_POWER - 40f) / DrivetrainSolver.MOTOR_POWER,
            result.powerFraction,
            0.005f,
        )
    }

    @Test fun `power fraction scales machine output rate`() {
        // A near-depleted energy budget should visibly reduce output rate.
        // Stack many worn gears to drain the budget.
        val machine = Machine(type = MachineType.COMBAT_ARTILLERY, gridX = 0, gridY = 1)
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = listOf(machine))
        // Row of gears along row 2+: disconnected from motor, so no draw.
        // Instead place many worn size-8 gears in a chain to eat budget.
        // Motor(4) → Gear(8)@(1,0)@wearPct=0.99 → Gear(8)@(2,0)@wearPct=0.99 → Gear(8)@(3,0)@wearPct=0.99
        // Each: draw = 8 * 5 * 0.5 * 1.99 ≈ 39.8   (rpmRatio = 50/100 = 0.5, wearMul≈2)
        // Three gears total ≈ 119.4 draw, powerFraction ≈ 0.88
        // Meanwhile machine at (0,1) gets motor RPM directly → near-peak quality for artillery.
        for (col in 1..3) {
            g.place(
                Component(type = ComponentType.GEAR, size = 8, wearPct = 0.99f),
                col, 0,
            )
        }
        val result = solver.solve(g, dt)
        assertTrue(result.powerFraction < 1f)
        // Machine rate should be less than full-budget equivalent.
        val profile  = MachineRegistry[MachineType.COMBAT_ARTILLERY]
        val rate     = result.machineOutputRates[machine.id] ?: 0f
        assertTrue(
            "Machine rate should be reduced by power drain, got $rate vs base ${profile.baseOutputRate}",
            rate < profile.baseOutputRate,
        )
    }
}

// =============================================================================
// DrivetrainSolver — wear delta accumulation
// =============================================================================

class DrivetrainSolverWearTest {

    private val solver = DrivetrainSolver()

    @Test fun `fresh component at motor RPM accumulates expected wear per second`() {
        // Over one second (60 ticks), wear should accumulate to ≈ BASE_WEAR_RATE_PER_SEC.
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0f), 1, 0)
        val result = solver.solve(g, dt = 1f)  // dt=1 for easy calculation
        val node   = result.nodes[1 to 0]!!
        assertEquals(DrivetrainSolver.BASE_WEAR_RATE_PER_SEC, node.wearDelta, 0.0001f)
    }

    @Test fun `faster component accumulates more wear per tick`() {
        // Size-2 gear at 200 RPM vs size-4 gear at 100 RPM.
        val gFast = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gFast.place(Component(type = ComponentType.GEAR, size = 2, wearPct = 0f), 1, 0)

        val gSlow = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gSlow.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0f), 1, 0)

        val wearFast = solver.solve(gFast, dt = 1f).nodes[1 to 0]!!.wearDelta
        val wearSlow = solver.solve(gSlow, dt = 1f).nodes[1 to 0]!!.wearDelta
        assertTrue("Faster component must wear faster", wearFast > wearSlow)
    }

    @Test fun `stage-2 component wears faster than stage-1`() {
        val gStage1 = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gStage1.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.10f), 1, 0)

        val gStage2 = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gStage2.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.50f), 1, 0)

        val wearS1 = solver.solve(gStage1, dt = 1f).nodes[1 to 0]!!.wearDelta
        val wearS2 = solver.solve(gStage2, dt = 1f).nodes[1 to 0]!!.wearDelta
        assertTrue("Stage-2 component must wear faster", wearS2 > wearS1)
        assertEquals(wearS1 * DrivetrainSolver.STAGE_2_WEAR_MULTIPLIER, wearS2, 0.0001f)
    }

    @Test fun `stage-3 component wears faster than stage-2`() {
        val gStage2 = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gStage2.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.50f), 1, 0)

        val gStage3 = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        gStage3.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.80f), 1, 0)

        val wearS2 = solver.solve(gStage2, dt = 1f).nodes[1 to 0]!!.wearDelta
        val wearS3 = solver.solve(gStage3, dt = 1f).nodes[1 to 0]!!.wearDelta
        assertTrue("Stage-3 component must wear faster", wearS3 > wearS2)
    }

    @Test fun `disconnected component accumulates no wear`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0f), 5, 3)
        val result = solver.solve(g, dt = 1f)
        assertNull("Disconnected component should have no node", result.nodes[5 to 3])
    }

    @Test fun `motor itself accumulates no wear`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        val result = solver.solve(g, dt = 1f)
        val motorNode = result.nodes[0 to 0]!!
        assertEquals(0f, motorNode.wearDelta, 0.0001f)
    }

    @Test fun `expiring component is flagged in result`() {
        // Component with wearPct = 0.999, delta will push it over 1.0.
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.999f), 1, 0)
        // dt=10 gives huge delta, guaranteed expiry.
        val result = solver.solve(g, dt = 10f)
        assertTrue(
            "Near-expired component should appear in expiredPositions",
            (1 to 0) in result.expiredPositions,
        )
    }
}

// =============================================================================
// WearSystem
// =============================================================================

class WearSystemTest {

    private val solver     = DrivetrainSolver()
    private val wearSystem = WearSystem()

    @Test fun `applyWear increments component wearPct`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        val comp = Component(type = ComponentType.GEAR, size = 4, wearPct = 0f)
        g.place(comp, 1, 0)
        val result = solver.solve(g, dt = 1f)
        wearSystem.applyWear(g, result, EventBus())
        assertTrue("wearPct should increase after apply", comp.wearPct > 0f)
    }

    @Test fun `applyWear removes expired component from grid`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.999f), 1, 0)
        val result = solver.solve(g, dt = 10f)  // guaranteed expiry
        val removed = wearSystem.applyWear(g, result, EventBus())
        assertNull("Expired component must be removed from grid", g.componentAt(1, 0))
        assertEquals(1, removed.size)
    }

    @Test fun `applyWear posts ComponentExpiredEvent for each expired component`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        g.place(Component(type = ComponentType.GEAR, size = 4, wearPct = 0.999f), 1, 0)
        val result = solver.solve(g, dt = 10f)

        val eventBus = EventBus()
        val events   = mutableListOf<ComponentExpiredEvent>()
        eventBus.on<ComponentExpiredEvent> { events.add(it) }

        wearSystem.applyWear(g, result, eventBus)

        assertEquals(1, events.size)
        assertEquals(1, events[0].gridX)
        assertEquals(0, events[0].gridY)
        assertEquals(ComponentType.GEAR, events[0].type)
    }

    @Test fun `applyWear clamps wearPct to 1_0 on expiry`() {
        val g    = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        val comp = Component(type = ComponentType.GEAR, size = 4, wearPct = 0.999f)
        g.place(comp, 1, 0)
        val result = solver.solve(g, dt = 10f)
        wearSystem.applyWear(g, result, EventBus())
        assertEquals(1f, comp.wearPct, 0.0001f)
    }

    @Test fun `expired component belt connections are removed from grid`() {
        val g = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())
        // Connect (1,0) to the motor via belt so the solver can reach it and
        // accumulate wear. Without a path from the motor the solver never visits
        // the node and WearSystem never triggers expiry.
        g.place(Component(type = ComponentType.PULLEY, size = 4, wearPct = 0.999f), 1, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4, wearPct = 0f),    3, 0)
        g.addBelt(BeltConnection(fromX = 0, fromY = 0, toX = 1, toY = 0)) // motor -> (1,0)
        g.addBelt(BeltConnection(fromX = 1, fromY = 0, toX = 3, toY = 0)) // (1,0) -> (3,0)
        assertEquals(2, g.beltConnections.size)

        val result = solver.solve(g, dt = 10f) // large dt guarantees expiry
        wearSystem.applyWear(g, result, EventBus())

        // Both belts touching (1,0) must be gone after its expiry.
        assertEquals(0, g.beltsAt(1, 0).size)
    }
}

// =============================================================================
// FactoryGrid — belt management
// =============================================================================

class FactoryGridBeltTest {

    private fun grid() = FactoryGrid(motorGridX = 0, motorGridY = 0, machines = emptyList())

    @Test fun `addBelt succeeds for valid endpoints`() {
        val g = grid()
        g.place(Component(type = ComponentType.PULLEY, size = 4), 1, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4), 3, 0)
        assertTrue(g.addBelt(BeltConnection(fromX = 1, fromY = 0, toX = 3, toY = 0)))
        assertEquals(1, g.beltConnections.size)
    }

    @Test fun `addBelt rejects duplicate connections`() {
        val g = grid()
        g.place(Component(type = ComponentType.PULLEY, size = 4), 1, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4), 3, 0)
        g.addBelt(BeltConnection(1, 0, 3, 0))
        assertFalse(g.addBelt(BeltConnection(1, 0, 3, 0)))  // same direction
        assertFalse(g.addBelt(BeltConnection(3, 0, 1, 0)))  // reversed
        assertEquals(1, g.beltConnections.size)
    }

    @Test fun `addBelt rejects self-connections`() {
        val g = grid()
        assertFalse(g.addBelt(BeltConnection(1, 0, 1, 0)))
    }

    @Test fun `addBelt rejects out-of-bounds endpoints`() {
        val g = grid()
        assertFalse(g.addBelt(BeltConnection(-1, 0, 2, 0)))
        assertFalse(g.addBelt(BeltConnection(0,  0, 10, 0)))
    }

    @Test fun `removeBelt works in both endpoint orderings`() {
        val g = grid()
        g.place(Component(type = ComponentType.PULLEY, size = 4), 1, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4), 3, 0)
        g.addBelt(BeltConnection(1, 0, 3, 0))
        assertTrue(g.removeBelt(3, 0, 1, 0))  // reversed order
        assertEquals(0, g.beltConnections.size)
    }

    @Test fun `beltsAt returns only connections involving that cell`() {
        val g = grid()
        g.place(Component(type = ComponentType.PULLEY, size = 4), 1, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4), 3, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4), 1, 2)
        g.addBelt(BeltConnection(1, 0, 3, 0))
        g.addBelt(BeltConnection(1, 0, 1, 2))
        assertEquals(2, g.beltsAt(1, 0).size)
        assertEquals(1, g.beltsAt(3, 0).size)
        assertEquals(0, g.beltsAt(0, 0).size)  // motor has no belts added
    }

    @Test fun `removing component also removes its belt connections`() {
        val g = grid()
        g.place(Component(type = ComponentType.PULLEY, size = 4), 1, 0)
        g.place(Component(type = ComponentType.PULLEY, size = 4), 3, 0)
        g.addBelt(BeltConnection(1, 0, 3, 0))
        g.remove(1, 0)
        assertEquals(0, g.beltConnections.size)
    }
}

// =============================================================================
// BeltConnection — geometry helpers
// =============================================================================

class BeltConnectionTest {

    @Test fun `chebyshev distance is max of abs differences`() {
        assertEquals(3f, BeltConnection.chebyshevDistance(0, 0, 3, 1), 0f)
        assertEquals(3f, BeltConnection.chebyshevDistance(0, 0, 1, 3), 0f)
        assertEquals(3f, BeltConnection.chebyshevDistance(0, 0, 3, 3), 0f)
    }

    @Test fun `default length uses chebyshev distance`() {
        val b = BeltConnection(fromX = 0, fromY = 0, toX = 3, toY = 1)
        assertEquals(3f, b.length, 0f)
    }

    @Test fun `otherEnd returns correct endpoint`() {
        val b = BeltConnection(1, 0, 3, 0)
        assertEquals(3 to 0, b.otherEnd(1, 0))
        assertEquals(1 to 0, b.otherEnd(3, 0))
        assertNull(b.otherEnd(2, 0))
    }

    @Test fun `connects returns true for either endpoint`() {
        val b = BeltConnection(1, 0, 3, 0)
        assertTrue(b.connects(1, 0))
        assertTrue(b.connects(3, 0))
        assertFalse(b.connects(2, 0))
    }
}
