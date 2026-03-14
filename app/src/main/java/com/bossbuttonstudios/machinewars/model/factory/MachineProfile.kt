package com.bossbuttonstudios.machinewars.model.factory

/**
 * Drive properties for one machine type — the characteristics the drivetrain
 * solver uses to compute output rate.
 *
 * Parallel to [com.bossbuttonstudios.machinewars.model.unit.UnitStats].
 *
 * @param type              The machine type these properties describe.
 * @param preferredRpm      RPM at which output rate is maximised (Gaussian peak).
 * @param rpmTolerance      Standard deviation of the Gaussian quality curve.
 *                          Tighter tolerance = narrower sweet spot, harsher
 *                          penalty for being off-ratio.
 * @param baseOutputRate    Output rate (units or ore per second) when RPM
 *                          quality = 1.0 and power fraction = 1.0.
 *                          Combat machines: spawn tokens/sec.
 *                          Miner: ore/sec.
 *                          Boost machines: buff intensity unit/sec (scaled in Session 6).
 */
data class MachineProfile(
    val type: MachineType,
    val preferredRpm: Float,
    val rpmTolerance: Float,
    val baseOutputRate: Float,
)
