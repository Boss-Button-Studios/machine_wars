package com.bossbuttonstudios.machinewars.model.factory

/**
 * Lookup table of [MachineProfile] values, one per [MachineType].
 *
 * Parallel to [com.bossbuttonstudios.machinewars.model.unit.UnitRegistry].
 *
 * ---
 * Preferred RPM design notes (spec §5.3, §5.5):
 *
 * The motor runs at 100 RPM through its size-4 outputs. Preferred RPMs are
 * chosen so that:
 *  - Brute (50 RPM): requires gearing DOWN — player places large gears.
 *  - Artillery (100 RPM): motor default — connects cleanly without gearing.
 *  - Skirmisher (200 RPM): requires gearing UP — player places small gears.
 *  - Miner is forgiving (wide tolerance) — any reasonable ratio yields ore.
 *
 * Satisfying multiple machines simultaneously requires branching the network
 * with different gear ratios on each branch — the core puzzle.
 *
 * All values here are the balancing baseline. Numerical tuning lives in the
 * separate balancing session; only structure changes here.
 * ---
 */
object MachineRegistry {

    private val profiles: Map<MachineType, MachineProfile> = mapOf(
        // --- Combat machines ---
        // Brute: slow, high-torque preference.  Spawn tokens / sec.
        MachineType.COMBAT_BRUTE to MachineProfile(
            type            = MachineType.COMBAT_BRUTE,
            preferredRpm    = 50f,
            rpmTolerance    = 25f,
            baseOutputRate  = 1.0f,
        ),
        // Skirmisher: fast.  Spawn tokens / sec.
        MachineType.COMBAT_SKIRMISHER to MachineProfile(
            type            = MachineType.COMBAT_SKIRMISHER,
            preferredRpm    = 200f,
            rpmTolerance    = 60f,
            baseOutputRate  = 2.0f,
        ),
        // Artillery: precise rhythm, tight tolerance.  Spawn tokens / sec.
        MachineType.COMBAT_ARTILLERY to MachineProfile(
            type            = MachineType.COMBAT_ARTILLERY,
            preferredRpm    = 100f,
            rpmTolerance    = 20f,
            baseOutputRate  = 0.5f,
        ),
        // Miner: not picky, wide tolerance.  Ore / sec.
        MachineType.MINER to MachineProfile(
            type            = MachineType.MINER,
            preferredRpm    = 100f,
            rpmTolerance    = 150f,
            baseOutputRate  = 5.0f,
        ),
        // --- Boost machines (buff intensity scaled in Session 6) ---
        MachineType.BOOST_AMPLIFIER to MachineProfile(
            type            = MachineType.BOOST_AMPLIFIER,
            preferredRpm    = 100f,
            rpmTolerance    = 80f,
            baseOutputRate  = 1.0f,
        ),
        MachineType.BOOST_CAPACITOR to MachineProfile(
            type            = MachineType.BOOST_CAPACITOR,
            preferredRpm    = 100f,
            rpmTolerance    = 80f,
            baseOutputRate  = 1.0f,
        ),
        MachineType.BOOST_ARMOR_PLATER to MachineProfile(
            type            = MachineType.BOOST_ARMOR_PLATER,
            preferredRpm    = 100f,
            rpmTolerance    = 80f,
            baseOutputRate  = 1.0f,
        ),
        MachineType.BOOST_TARGETING to MachineProfile(
            type            = MachineType.BOOST_TARGETING,
            preferredRpm    = 100f,
            rpmTolerance    = 80f,
            baseOutputRate  = 1.0f,
        ),
    )

    /** Returns the [MachineProfile] for [type]. Throws if type has no entry. */
    operator fun get(type: MachineType): MachineProfile =
        profiles[type] ?: error("No MachineProfile registered for $type")

    /** All registered profiles, in MachineType declaration order. */
    val all: List<MachineProfile> get() = MachineType.entries.map { profiles.getValue(it) }
}
