package com.bossbuttonstudios.machinewars.rendering

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.UUID
import com.bossbuttonstudios.machinewars.core.GameState
import com.bossbuttonstudios.machinewars.interfaces.Renderer
import com.bossbuttonstudios.machinewars.model.factory.ComponentType
import com.bossbuttonstudios.machinewars.model.factory.FactoryGrid
import com.bossbuttonstudios.machinewars.model.factory.MachineRegistry
import com.bossbuttonstudios.machinewars.model.factory.MachineType
import com.bossbuttonstudios.machinewars.model.map.LaneBoundary
import com.bossbuttonstudios.machinewars.model.map.Wreckage
import com.bossbuttonstudios.machinewars.model.mission.MissionType
import com.bossbuttonstudios.machinewars.model.unit.Team
import com.bossbuttonstudios.machinewars.model.unit.UnitInstance
import com.bossbuttonstudios.machinewars.model.unit.UnitState
import com.bossbuttonstudios.machinewars.model.unit.UnitType

/**
 * Prototype renderer. Implements [Renderer] by drawing to a [SurfaceView]
 * canvas. No game logic lives here — only visual representation of [GameState].
 *
 * Visual language (spec §15):
 *   Skirmisher = circle     Brute = square     Artillery = triangle
 *   Player = blue (#2196F3)   Enemy = red (#E53935)
 *   Wreckage = faded, darkened source shape
 *   HP bar above each unit; colour shifts green → amber → red with damage
 *   Lane boundaries: solid stroke for Wall, dashed stroke for Space
 *   Factory components: circles, size integer on face, rotate at their RPM
 *   Motor: yellow circle labelled "M"
 *   Machine face: output-rate bar across the bottom (spec §5.9)
 *
 * Rendering runs on the GameLoop coroutine thread (Dispatchers.Default).
 * SurfaceView.lockCanvas / unlockCanvasAndPost is thread-safe by design.
 *
 * This class owns purely-visual state (rotation angles, frame timing) and
 * must never write to [GameState].
 */
class GameView(context: Context) : SurfaceView(context), Renderer, SurfaceHolder.Callback {

    private var layout: SceneLayout? = null

    // Rotation angle in degrees for each grid position; driven by per-node RPM.
    private val rotationAngles = mutableMapOf<Pair<Int, Int>, Float>()
    private var lastRenderNanos = 0L

    // Scratch objects reused every frame — no allocation in the hot path.
    private val shapePath = Path()
    private val rect      = RectF()

    // ---- Paints -------------------------------------------------------------
    // All created once at construction; none are reallocated per frame.

    // Battlefield
    private val bfBgPaint          = solid("#1A1A1A")
    private val wallBoundaryPaint  = stroke("#888888", 4f)
    private val spaceBoundaryPaint = stroke("#666666", 4f).also {
        it.pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val dividerPaint       = stroke("#444444", 3f)

    // Enemy base strip (BASE_ATTACK)
    private val baseBlockPaint = solid("#B71C1C")
    private val baseHpPaint    = solid("#EF5350")
    private val baseEdgePaint  = stroke("#555555", 2f)

    // Units
    private val playerPaint   = solid("#2196F3")
    private val enemyPaint    = solid("#E53935")
    private val wreckagePaint = solid("#616161").also { it.alpha = 130 }

    // HP bar
    private val hpBgPaint   = solid("#2A2A2A")
    private val hpHighPaint = solid("#4CAF50")
    private val hpMidPaint  = solid("#FFC107")
    private val hpLowPaint  = solid("#F44336")

    // Factory
    private val factBgPaint       = solid("#111111")
    private val gridLinePaint     = stroke("#252525", 1f)
    private val beltPaint         = stroke("#607D8B", 3f)
    private val motorPaint        = solid("#FFEB3B")
    private val compNormalPaint   = solid("#9E9E9E")
    private val compWornPaint     = solid("#FF9800")
    private val compCriticalPaint = solid("#F44336")
    private val compStrokePaint   = stroke("#222222", 2f)
    private val machinePaint      = solid("#546E7A")
    private val machineBarBgPaint = solid("#1A1A1A")
    private val machineBarPaint   = solid("#43A047")

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val laneLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#FFEB3B")
        textAlign = Paint.Align.CENTER
    }

    // Selection highlight for the currently-selected machine.
    private val selectionPaint = stroke("#FFEB3B", 4f)

    // ---- Input state --------------------------------------------------------

    /** ID of the combat machine the player has tapped to select. Null = no selection. */
    private var selectedMachineId: UUID? = null

    /**
     * Most recent state snapshot, cached on each [render] call so [onTouchEvent]
     * can map touches to game objects without accessing GameState from two threads.
     * Written on the game-loop thread; read on the main thread. One-tick stale at
     * worst — acceptable for touch resolution in a single-player prototype.
     */
    @Volatile private var currentState: GameState? = null

    /**
     * Called when the player assigns a lane to a machine.
     * Set by [com.bossbuttonstudios.machinewars.GameActivity] to write into
     * [GameState.laneAssignments] and [Machine.assignedLane].
     */
    var onLaneAssigned: ((machineId: UUID, lane: Int) -> Unit)? = null

    init {
        holder.addCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout = SceneLayout(w.toFloat(), h.toFloat())
        layout?.let {
            labelPaint.textSize    = it.cellSize * 0.35f
            laneLabelPaint.textSize = it.cellSize * 0.28f
        }
    }

    // ---- Touch handling -----------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val l     = layout       ?: return true
        val state = currentState ?: return true
        val x = event.x
        val y = event.y

        when {
            l.isFactoryTouch(y) -> {
                val (col, row) = l.gridCellAt(x, y) ?: run {
                    selectedMachineId = null; return true
                }
                val machine = state.factory.machineAt(col, row)
                selectedMachineId = if (machine != null && machine.isCombatMachine) {
                    // Tap same machine again to deselect.
                    if (machine.id == selectedMachineId) null else machine.id
                } else {
                    null
                }
            }
            l.isBattlefieldTouch(y) -> {
                val sel  = selectedMachineId ?: return true
                val lane = l.laneAt(x, y)   ?: run {
                    selectedMachineId = null; return true
                }
                onLaneAssigned?.invoke(sel, lane)
                selectedMachineId = null
            }
        }
        return true
    }

    // ---- Renderer -----------------------------------------------------------

    override fun render(state: GameState, interpolation: Float) {
        if (!holder.surface.isValid) return
        val l = layout ?: return

        val now = System.nanoTime()
        val frameDt = if (lastRenderNanos == 0L) SceneLayout.TICK_DURATION
                      else ((now - lastRenderNanos) / 1_000_000_000f).coerceAtMost(0.1f)
        lastRenderNanos = now

        currentState = state
        advanceRotation(state, frameDt)

        val canvas = holder.lockCanvas() ?: return
        try {
            drawBattlefield(canvas, state, l, interpolation)
            drawDivider(canvas, l)
            drawFactory(canvas, state, l)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    // ---- Battlefield --------------------------------------------------------

    private fun drawBattlefield(
        canvas: Canvas,
        state: GameState,
        l: SceneLayout,
        interpolation: Float,
    ) {
        canvas.drawRect(0f, 0f, l.screenWidth, l.battlefieldBottom, bfBgPaint)
        drawEnemyBase(canvas, state, l)
        drawBoundaries(canvas, state, l)
        state.wreckage.filterNot { it.isCleared }.forEach { drawWreckage(canvas, it, l) }
        state.livingUnits.forEach { drawUnit(canvas, it, l, interpolation) }
    }

    private fun drawEnemyBase(canvas: Canvas, state: GameState, l: SceneLayout) {
        if (state.mission.type != MissionType.BASE_ATTACK) return
        val stripH = l.battlefieldHeight * 0.045f
        canvas.drawRect(0f, 0f, l.screenWidth, stripH, baseBlockPaint)
        val fraction = (state.enemyBaseHp / GameState.BASE_HP).coerceIn(0f, 1f)
        canvas.drawRect(0f, 0f, l.screenWidth * fraction, stripH, baseHpPaint)
        canvas.drawRect(0f, 0f, l.screenWidth, stripH, baseEdgePaint)
    }

    private fun drawBoundaries(canvas: Canvas, state: GameState, l: SceneLayout) {
        val lPaint = if (state.mission.mapConfig.leftBoundary  is LaneBoundary.Wall) wallBoundaryPaint  else spaceBoundaryPaint
        val rPaint = if (state.mission.mapConfig.rightBoundary is LaneBoundary.Wall) wallBoundaryPaint else spaceBoundaryPaint
        canvas.drawLine(l.leftBoundaryX,  0f, l.leftBoundaryX,  l.battlefieldBottom, lPaint)
        canvas.drawLine(l.rightBoundaryX, 0f, l.rightBoundaryX, l.battlefieldBottom, rPaint)
    }

    private fun drawUnit(canvas: Canvas, unit: UnitInstance, l: SceneLayout, interpolation: Float) {
        val teamSign = if (unit.team == Team.PLAYER) 1f else -1f
        val speed    = if (unit.state == UnitState.ADVANCING) unit.stats.speed else 0f
        val cx = l.laneCenterX(unit.lane)
        val cy = l.interpolatedUnitY(unit.position, speed, teamSign, interpolation)
        val r  = unitRadius(l)
        val paint = if (unit.team == Team.PLAYER) playerPaint else enemyPaint
        drawShape(canvas, unit.type, cx, cy, r, paint, alpha = 255)
        drawHpBar(canvas, cx, cy, r, unit.hpFraction)
    }

    private fun drawWreckage(canvas: Canvas, w: Wreckage, l: SceneLayout) {
        val cx = l.laneCenterX(w.lane)
        val cy = l.unitY(w.position)
        val r  = unitRadius(l)
        drawShape(canvas, w.sourceType, cx, cy, r, wreckagePaint, alpha = 130)
    }

    private fun drawShape(
        canvas: Canvas,
        type: UnitType,
        cx: Float, cy: Float, r: Float,
        paint: Paint,
        alpha: Int,
    ) {
        val saved = paint.alpha
        paint.alpha = alpha
        when (type) {
            UnitType.SKIRMISHER -> canvas.drawCircle(cx, cy, r, paint)
            UnitType.BRUTE -> {
                rect.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawRect(rect, paint)
            }
            UnitType.ARTILLERY -> {
                shapePath.reset()
                shapePath.moveTo(cx,     cy - r)
                shapePath.lineTo(cx + r, cy + r)
                shapePath.lineTo(cx - r, cy + r)
                shapePath.close()
                canvas.drawPath(shapePath, paint)
            }
        }
        paint.alpha = saved
    }

    private fun drawHpBar(canvas: Canvas, cx: Float, cy: Float, r: Float, fraction: Float) {
        val barW   = r * 2.4f
        val barH   = 5f
        val barTop = cy - r - 12f
        val left   = cx - barW / 2f
        val right  = cx + barW / 2f

        rect.set(left, barTop, right, barTop + barH)
        canvas.drawRect(rect, hpBgPaint)

        val barPaint = when {
            fraction > 0.5f  -> hpHighPaint
            fraction > 0.25f -> hpMidPaint
            else             -> hpLowPaint
        }
        rect.set(left, barTop, left + barW * fraction, barTop + barH)
        canvas.drawRect(rect, barPaint)
    }

    private fun unitRadius(l: SceneLayout): Float = l.screenWidth / 3f * 0.14f

    // ---- Divider ------------------------------------------------------------

    private fun drawDivider(canvas: Canvas, l: SceneLayout) {
        canvas.drawLine(0f, l.battlefieldBottom, l.screenWidth, l.battlefieldBottom, dividerPaint)
    }

    // ---- Factory ------------------------------------------------------------

    private fun drawFactory(canvas: Canvas, state: GameState, l: SceneLayout) {
        rect.set(0f, l.battlefieldBottom, l.screenWidth, l.screenHeight)
        canvas.drawRect(rect, factBgPaint)

        drawGridLines(canvas, l)
        drawBelts(canvas, state, l)
        drawMotor(canvas, state, l)
        drawComponents(canvas, state, l)
        drawMachines(canvas, state, l)
    }

    private fun drawGridLines(canvas: Canvas, l: SceneLayout) {
        for (col in 0..FactoryGrid.COLS) {
            val x = l.factoryOffsetX + col * l.cellSize
            canvas.drawLine(
                x, l.factoryOffsetY,
                x, l.factoryOffsetY + l.cellSize * FactoryGrid.ROWS,
                gridLinePaint,
            )
        }
        for (row in 0..FactoryGrid.ROWS) {
            val y = l.factoryOffsetY + row * l.cellSize
            canvas.drawLine(
                l.factoryOffsetX, y,
                l.factoryOffsetX + l.cellSize * FactoryGrid.COLS, y,
                gridLinePaint,
            )
        }
    }

    private fun drawBelts(canvas: Canvas, state: GameState, l: SceneLayout) {
        for (belt in state.factory.beltConnections) {
            canvas.drawLine(
                l.cellCenterX(belt.fromX), l.cellCenterY(belt.fromY),
                l.cellCenterX(belt.toX),   l.cellCenterY(belt.toY),
                beltPaint,
            )
        }
    }

    private fun drawMotor(canvas: Canvas, state: GameState, l: SceneLayout) {
        val col   = state.factory.motorGridX
        val row   = state.factory.motorGridY
        val cx    = l.cellCenterX(col)
        val cy    = l.cellCenterY(row)
        val r     = l.cellSize * 0.38f
        val angle = rotationAngles[col to row] ?: 0f

        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawCircle(cx, cy, r, motorPaint)
        canvas.drawCircle(cx, cy, r, compStrokePaint)
        canvas.restore()

        canvas.drawText("M", cx, cy + labelPaint.textSize * 0.38f, labelPaint)
    }

    private fun drawComponents(canvas: Canvas, state: GameState, l: SceneLayout) {
        for ((pos, component) in state.factory.components) {
            val (col, row) = pos
            val cx    = l.cellCenterX(col)
            val cy    = l.cellCenterY(row)
            val r     = l.cellSize * 0.38f
            val angle = rotationAngles[pos] ?: 0f
            val fill  = wearPaint(component.wearPct)

            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, compStrokePaint)
            canvas.restore()

            // Size integer on face (spec §5.3). Belts show "B" as they have no ratio.
            val label = if (component.type == ComponentType.BELT) "B" else component.size.toString()
            canvas.drawText(label, cx, cy + labelPaint.textSize * 0.38f, labelPaint)
        }
    }

    private fun drawMachines(canvas: Canvas, state: GameState, l: SceneLayout) {
        val pad  = l.cellSize * 0.10f
        val barH = l.cellSize * 0.16f

        for (machine in state.factory.machines) {
            val left   = l.factoryOffsetX + machine.gridX * l.cellSize + pad
            val top    = l.factoryOffsetY + machine.gridY * l.cellSize + pad
            val right  = left + l.cellSize - pad * 2f
            val bottom = top  + l.cellSize - pad * 2f

            rect.set(left, top, right, bottom)
            canvas.drawRect(rect, machinePaint)

            // Selection highlight.
            if (machine.id == selectedMachineId) {
                rect.set(left - 3f, top - 3f, right + 3f, bottom + 3f)
                canvas.drawRect(rect, selectionPaint)
            }

            // Output rate bar at the bottom of the machine face (spec §5.9).
            val barTop    = bottom - barH - pad * 0.5f
            val barBottom = bottom - pad * 0.5f
            rect.set(left, barTop, right, barBottom)
            canvas.drawRect(rect, machineBarBgPaint)

            val baseRate = MachineRegistry[machine.type].baseOutputRate
            val fraction = (machine.outputRatePerSec / baseRate).coerceIn(0f, 1f)
            rect.set(left, barTop, left + (right - left) * fraction, barBottom)
            canvas.drawRect(rect, machineBarPaint)

            // Machine type abbreviation.
            val labelCy = top + (barTop - top) / 2f + labelPaint.textSize * 0.38f
            canvas.drawText(machineLabel(machine.type), (left + right) / 2f, labelCy, labelPaint)

            // Lane assignment indicator — top-right corner of machine face.
            if (machine.isCombatMachine) {
                val laneLabel = when (machine.assignedLane) {
                    0    -> "L"
                    1    -> "C"
                    2    -> "R"
                    else -> "-"
                }
                canvas.drawText(
                    laneLabel,
                    right - laneLabelPaint.textSize * 0.7f,
                    top   + laneLabelPaint.textSize,
                    laneLabelPaint,
                )
            }
        }
    }

    // ---- Rotation animation -------------------------------------------------

    private fun advanceRotation(state: GameState, frameDt: Float) {
        val motorPos = state.factory.motorGridX to state.factory.motorGridY
        val motorRpm = state.drivetrainResult.nodes[motorPos]?.rpm ?: IDLE_MOTOR_RPM
        rotationAngles[motorPos] = advance(rotationAngles[motorPos], motorRpm, frameDt)

        for ((pos, _) in state.factory.components) {
            val rpm = state.drivetrainResult.nodes[pos]?.rpm ?: 0f
            rotationAngles[pos] = advance(rotationAngles[pos], rpm, frameDt)
        }

        // Remove stale entries for components no longer on the grid.
        val live = state.factory.components.keys + motorPos
        rotationAngles.keys.retainAll(live)
    }

    private fun advance(current: Float?, rpm: Float, dt: Float): Float =
        ((current ?: 0f) + rpm * RPM_TO_DEG_PER_SEC * dt) % 360f

    // ---- Helpers ------------------------------------------------------------

    private fun wearPaint(wearPct: Float): Paint = when {
        wearPct < WEAR_STAGE2 -> compNormalPaint
        wearPct < WEAR_STAGE3 -> compWornPaint
        else                  -> compCriticalPaint
    }

    private fun machineLabel(type: MachineType): String = when (type) {
        MachineType.COMBAT_BRUTE       -> "BRT"
        MachineType.COMBAT_SKIRMISHER  -> "SKR"
        MachineType.COMBAT_ARTILLERY   -> "ART"
        MachineType.MINER              -> "MNR"
        MachineType.BOOST_AMPLIFIER    -> "AMP"
        MachineType.BOOST_CAPACITOR    -> "CAP"
        MachineType.BOOST_ARMOR_PLATER -> "ARM"
        MachineType.BOOST_TARGETING    -> "TGT"
    }

    private fun solid(hex: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(hex)
        style = Paint.Style.FILL
    }

    private fun stroke(hex: String, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor(hex)
        strokeWidth = width
        style       = Paint.Style.STROKE
    }

    // ---- SurfaceHolder.Callback (lifecycle driven by GameLoop/Activity) ----

    override fun surfaceCreated(holder: SurfaceHolder)                                       = Unit
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder)                                     = Unit

    companion object {
        /** 1 RPM = 1 revolution per minute = 6 degrees per second. */
        private const val RPM_TO_DEG_PER_SEC = 6f

        /** Fallback motor RPM for rotation animation before the first drivetrain solve. */
        private const val IDLE_MOTOR_RPM = 60f

        private const val WEAR_STAGE2 = 0.33f  // spec §5.6: Functional → Worse for wear
        private const val WEAR_STAGE3 = 0.66f  // spec §5.6: Worse for wear → Critical
    }
}
