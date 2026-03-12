package com.bossbuttonstudios.machinewars.model.mission

import com.bossbuttonstudios.machinewars.model.map.MapConfig
import com.bossbuttonstudios.machinewars.model.unit.UnitType

/**
 * Describes one enemy wave: which units spawn, in what quantities, and how
 * quickly they appear.
 *
 * @param composition   Map of UnitType to count for this wave.
 * @param spawnInterval Seconds between each individual unit spawn.
 */
data class WaveDefinition(
    val composition: Map<UnitType, Int>,
    val spawnInterval: Float = 1.5f,
)

/**
 * Full configuration for a single mission.
 *
 * @param missionNumber     Campaign position (1-indexed).
 * @param type              BASE_ATTACK, TIMED_SURVIVAL, or RESOURCE_HUNT.
 * @param mapConfig         Which of the four lane-boundary configurations to use.
 * @param waves             Ordered list of enemy waves.
 * @param timeLimitSeconds  For TIMED_SURVIVAL: seconds until the end condition
 *                          triggers. Ignored for other types.
 * @param oreTarget         For RESOURCE_HUNT: treasury ore needed to win.
 *                          Ignored for other types.
 */
data class MissionConfig(
    val missionNumber: Int,
    val type: MissionType,
    val mapConfig: MapConfig,
    val waves: List<WaveDefinition>,
    val timeLimitSeconds: Float = 0f,
    val oreTarget: Int = 0,
)
