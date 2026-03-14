package com.bossbuttonstudios.machinewars.drivetrain

import com.bossbuttonstudios.machinewars.model.factory.BeltConnection
import com.bossbuttonstudios.machinewars.model.factory.Component
import com.bossbuttonstudios.machinewars.model.factory.ComponentType
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.factory.Machine
import com.bossbuttonstudios.machinewars.model.factory.MachineRegistry
import java.util.UUID
import kotlin.math.exp

/**
 * Stateless drivetrain network solver.
 *
 * Called once per game tick via [solve]. Traverses the component graph
 * outward from the motor using BFS, computing at each node:
 *   - RPM (from cumulative gear ratios along the path)
 *   - Path efficiency (product of belt efficiency losses along the path)
 *   - Energy draw (friction cost subtracted from the motor power budget)
 *   - Wear delta (increment to apply to component.wearPct this tick)
 *
 * After traversal, machine output rates are computed from RPM quality
 * (Gaussian curve around each machine's preferred RPM) and the global
 * power fraction remaining after friction losses.
 *
 * ---
 * Connectivity rules (spec §5.4):
 *   - GEAR / GEAR_PULLEY / MOTOR: have gear teeth; mesh with adjacent
 *     gear-teeth nodes (4-directional adjacency).
 *   - PULLEY / GEAR_PULLEY / MOTOR: have a pulley groove; connect to
 *     other pulley-groove nodes via explicit [BeltConnection]s.
 *   - Machines expose a gear interface driven by whichever component is
 *     adjacent to them. They are terminal (leaf) nodes — power does not
 *     propagate through a machine to further components.
 *
 * Machine RPM rule:
 *   The machine runs at the RPM of the component directly driving it.
 *   No additional ratio step is applied between the driving component and
 *   the machine (the "size-4 interface" is a physical connection point,
 *   not a ratio stage). The player controls machine RPM by routing the
 *   right-sized component adjacent to each machine.
 *
 * Power model:
 *   The motor supplies a fixed total power budget (MOTOR_POWER). Each
 *   component in the reachable network draws friction energy from this
 *   budget. The remainder (powerAvailable) is expressed as powerFraction
 *   ∈ [0, 1] and applied as a multiplier to all machine output rates.
 *   Belt efficiency losses are tracked per-path and also applied to output
 *   rates (machines reachable only via inefficient belt runs produce less).
 * ---
 */
class DrivetrainSolver {

    companion object {
        /** RPM at the motor's output shafts. */
        const val MOTOR_RPM = 100f

        /** Total power units the motor supplies per tick. */
        const val MOTOR_POWER = 1_000f

        /** Built-in size of the motor's output gear and output pulley. */
        const val MOTOR_INTERFACE_SIZE = 4

        /** Efficiency lost per grid unit of belt length (5 % / unit). */
        const val BELT_LOSS_PER_UNIT = 0.05f

        /** Floor efficiency for any belt run regardless of length (10 %). */
        const val BELT_MIN_EFFICIENCY = 0.10f

        /**
         * Base friction draw per size unit at motor RPM, fresh component.
         * A size-4 gear at motor RPM draws 4 × 5 = 20 power units.
         * A full 4×6 grid of size-4 components draws at most ~480 of the
         * 1000-unit budget, leaving meaningful headroom.
         */
        const val SIZE_DRAW_FACTOR = 5f

        /** Wear rate per second at motor RPM, stage 1. At this rate a fresh
         *  component running at motor RPM expires in ~333 seconds (≈ 5.5 min).
         *  Doubles for stage-2 acceleration; triples for stage-3. */
        const val BASE_WEAR_RATE_PER_SEC = 0.003f

        // Degradation stage thresholds (spec §5.6)
        const val STAGE_2_THRESHOLD = 0.33f
        const val STAGE_3_THRESHOLD = 0.66f
        const val STAGE_2_WEAR_MULTIPLIER = 1.5f
        const val STAGE_3_WEAR_MULTIPLIER = 3.0f
    }

    // -------------------------------------------------------------------------
    // Internal BFS state — allocated per solve call, never shared
    // -------------------------------------------------------------------------

    private data class SearchEntry(
        val pos: Pair<Int, Int>,
        val rpm: Float,
        val pathEfficiency: Float,  // cumulative product of belt efficiencies on path from motor
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Solve the drivetrain network for [grid] over a tick of [dt] seconds.
     *
     * Pure function — does not modify [grid] or any component. All mutations
     * (wear application, expiry removal, machine rate updates) are performed
     * by [WearSystem] and the caller after inspecting the result.
     *
     * @param grid  The factory grid to solve.
     * @param dt    Tick delta in seconds (normally [GameLoop.TICK_DELTA]).
     * @return      A [DrivetrainResult] describing the network state this tick.
     */
    fun solve(grid: FactoryGrid, dt: Float): DrivetrainResult {
        val motorPos = grid.motorGridX to grid.motorGridY

        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<SearchEntry>()
        val nodes = mutableMapOf<Pair<Int, Int>, DrivetrainNode>()

        // RPM and path efficiency recorded for each machine during BFS;
        // output rates are computed after the full traversal when we know
        // total energy draw and therefore powerFraction.
        val machineRpms = mutableMapOf<UUID, Float>()
        val machinePathEfficiencies = mutableMapOf<UUID, Float>()

        var totalEnergyDraw = 0f

        // Seed the BFS from the motor.
        queue.add(SearchEntry(motorPos, MOTOR_RPM, 1f))
        visited.add(motorPos)

        // Store a minimal node for the motor itself (it is the source, no draw).
        nodes[motorPos] = DrivetrainNode(
            gridPos        = motorPos,
            rpm            = MOTOR_RPM,
            energyDraw     = 0f,
            wearDelta      = 0f,
            pathEfficiency = 1f,
            reachable      = true,
        )

        while (queue.isNotEmpty()) {
            val (pos, rpm, pathEff) = queue.removeFirst()
            val (x, y) = pos
            val isMotor = (pos == motorPos)
            val component = if (isMotor) null else grid.componentAt(x, y)
            val machine = if (isMotor) null else grid.machineAt(x, y)

            // --- Compute node metrics ---

            val energyDraw = computeEnergyDraw(isMotor, machine, component, rpm)
            val wearDelta  = computeWearDelta(isMotor, machine, component, rpm, dt)
            totalEnergyDraw += energyDraw

            // Store node (motor node already stored above; overwrite is fine).
            if (!isMotor) {
                nodes[pos] = DrivetrainNode(
                    gridPos        = pos,
                    rpm            = rpm,
                    energyDraw     = energyDraw,
                    wearDelta      = wearDelta,
                    pathEfficiency = pathEff,
                    reachable      = true,
                )
            }

            // --- Machines are leaf nodes ---

            if (machine != null) {
                machineRpms[machine.id] = rpm
                machinePathEfficiencies[machine.id] = pathEff
                continue  // do not propagate through a machine
            }

            val mySize = cellSize(grid, x, y)

            // --- Gear-mesh neighbors ---
            // Applies to: MOTOR, GEAR, GEAR_PULLEY

            if (isMotor || component?.type == ComponentType.GEAR
                       || component?.type == ComponentType.GEAR_PULLEY) {
                for ((nx, ny) in cardinalNeighbors(x, y)) {
                    val nPos = nx to ny
                    if (nPos in visited || !grid.isInBounds(nx, ny)) continue

                    val neighborIsMachine   = grid.machineAt(nx, ny) != null
                    val neighborHasGearTeeth = hasGearTeeth(grid, nx, ny)

                    if (!neighborIsMachine && !neighborHasGearTeeth) continue

                    val neighborRpm = if (neighborIsMachine) {
                        // Machine receives the driving component's RPM directly —
                        // no ratio step between the component and the machine interface.
                        rpm
                    } else {
                        rpm * (mySize.toFloat() / cellSize(grid, nx, ny).toFloat())
                    }

                    visited.add(nPos)
                    queue.add(SearchEntry(nPos, neighborRpm, pathEff))
                }
            }

            // --- Belt neighbors ---
            // Applies to: MOTOR, PULLEY, GEAR_PULLEY

            if (isMotor || component?.type == ComponentType.PULLEY
                       || component?.type == ComponentType.GEAR_PULLEY) {
                for (belt in grid.beltConnections) {
                    val otherEnd = belt.otherEnd(x, y) ?: continue
                    if (otherEnd in visited) continue

                    val (ox, oy) = otherEnd
                    if (!grid.isInBounds(ox, oy)) continue

                    // The other end must be a pulley-groove component.
                    val otherComp = grid.componentAt(ox, oy)
                    if (!hasPulleyGroove(grid, ox, oy) || otherComp == null) continue

                    val beltEff     = beltEfficiency(belt)
                    val neighborRpm = rpm * (mySize.toFloat() / otherComp.size.toFloat())

                    visited.add(otherEnd)
                    queue.add(SearchEntry(otherEnd, neighborRpm, pathEff * beltEff))
                }
            }
        }

        // --- Post-BFS: compute power fraction and machine output rates ---

        val powerAvailable = (MOTOR_POWER - totalEnergyDraw).coerceAtLeast(0f)
        val powerFraction  = powerAvailable / MOTOR_POWER

        val machineOutputRates = mutableMapOf<UUID, Float>()
        for ((machineId, rpm) in machineRpms) {
            val machine  = grid.machines.find { it.id == machineId } ?: continue
            val profile  = MachineRegistry[machine.type]
            val pathEff  = machinePathEfficiencies[machineId] ?: 1f
            val quality  = rpmQuality(rpm, profile.preferredRpm, profile.rpmTolerance)
            machineOutputRates[machineId] = profile.baseOutputRate * quality * powerFraction * pathEff
        }

        // Identify components that will expire after wear is applied.
        val expiredPositions = nodes.values
            .filter { node -> node.wearDelta > 0f }
            .mapNotNull { node ->
                val (cx, cy) = node.gridPos
                val comp = grid.componentAt(cx, cy) ?: return@mapNotNull null
                if (comp.wearPct + node.wearDelta >= 1f) node.gridPos else null
            }

        return DrivetrainResult(
            nodes              = nodes,
            totalEnergyDraw    = totalEnergyDraw,
            powerAvailable     = powerAvailable,
            powerFraction      = powerFraction,
            machineOutputRates = machineOutputRates,
            expiredPositions   = expiredPositions,
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** The four cardinal neighbors of (x, y). */
    private fun cardinalNeighbors(x: Int, y: Int): List<Pair<Int, Int>> =
        listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)

    /**
     * True if the cell at (x, y) has gear teeth — i.e., it can mesh with
     * adjacent gear-teeth nodes.
     */
    private fun hasGearTeeth(grid: FactoryGrid, x: Int, y: Int): Boolean {
        if (x == grid.motorGridX && y == grid.motorGridY) return true
        return when (grid.componentAt(x, y)?.type) {
            ComponentType.GEAR, ComponentType.GEAR_PULLEY -> true
            else -> false
        }
    }

    /**
     * True if the cell at (x, y) has a pulley groove — i.e., it can be a
     * belt endpoint.
     */
    private fun hasPulleyGroove(grid: FactoryGrid, x: Int, y: Int): Boolean {
        if (x == grid.motorGridX && y == grid.motorGridY) return true
        return when (grid.componentAt(x, y)?.type) {
            ComponentType.PULLEY, ComponentType.GEAR_PULLEY -> true
            else -> false
        }
    }

    /**
     * Effective size of the component at (x, y) for ratio calculations.
     * Motor and machine interfaces are always size [MOTOR_INTERFACE_SIZE].
     */
    private fun cellSize(grid: FactoryGrid, x: Int, y: Int): Int {
        if (x == grid.motorGridX && y == grid.motorGridY) return MOTOR_INTERFACE_SIZE
        return grid.componentAt(x, y)?.size ?: MOTOR_INTERFACE_SIZE
    }

    /**
     * Friction power drawn by the component at this node.
     *
     * Scales with:
     *   - Component size (larger = more surface area = more friction)
     *   - RPM ratio (faster = more frictional heating)
     *   - Wear state (worn components have more friction, spec §5.5)
     *
     * Motor and machines draw nothing from the budget here; their power
     * relationship is handled separately.
     */
    private fun computeEnergyDraw(
        isMotor: Boolean,
        machine: Machine?,
        component: Component?,
        rpm: Float,
    ): Float {
        if (isMotor || machine != null || component == null) return 0f
        val rpmRatio  = rpm / MOTOR_RPM
        val wearMul   = 1f + component.wearPct *
                        (Component.MAX_WEAR_FRICTION_MULTIPLIER - Component.BASE_FRICTION_MULTIPLIER)
        return component.size * SIZE_DRAW_FACTOR * rpmRatio * wearMul
    }

    /**
     * Wear increment to add to [Component.wearPct] for this tick.
     *
     * Scales with RPM (faster = more degradation) and accelerates through
     * stages 2 and 3 (spec §5.6).
     */
    private fun computeWearDelta(
        isMotor: Boolean,
        machine: Machine?,
        component: Component?,
        rpm: Float,
        dt: Float,
    ): Float {
        if (isMotor || machine != null || component == null) return 0f
        val rpmFactor = rpm / MOTOR_RPM
        val stageMul  = when {
            component.wearPct >= STAGE_3_THRESHOLD -> STAGE_3_WEAR_MULTIPLIER
            component.wearPct >= STAGE_2_THRESHOLD -> STAGE_2_WEAR_MULTIPLIER
            else                                   -> 1f
        }
        return BASE_WEAR_RATE_PER_SEC * rpmFactor * stageMul * dt
    }

    /**
     * Efficiency of a belt run [0, 1]. Longer belts lose more power.
     * Floored at [BELT_MIN_EFFICIENCY] so even a very long belt still
     * transmits something.
     */
    private fun beltEfficiency(belt: BeltConnection): Float =
        (1f - belt.length * BELT_LOSS_PER_UNIT).coerceAtLeast(BELT_MIN_EFFICIENCY)

    /**
     * Gaussian RPM quality curve ∈ [0, 1].
     *
     * Returns 1.0 when [rpm] == [preferredRpm]. Falls off symmetrically
     * as RPM diverges, with the rate of fall-off controlled by [tolerance]
     * (the Gaussian σ). Tighter tolerance → sharper peak (Artillery).
     * Wide tolerance → forgiving plateau (Miner).
     */
    internal fun rpmQuality(rpm: Float, preferredRpm: Float, tolerance: Float): Float {
        if (tolerance <= 0f) return 0f
        val diff     = (rpm - preferredRpm).toDouble()
        val sigma    = tolerance.toDouble()
        val exponent = -(diff * diff) / (2.0 * sigma * sigma)
        return exp(exponent).toFloat()
    }
}
