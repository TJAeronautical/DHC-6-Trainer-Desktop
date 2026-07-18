package com.dhc6trainer.desktop

import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/* =====================================================================
   TwinOtterSim - pure-Kotlin DHC-6-300 / PT6A-27 systems model.

   No OpenGL, no jMonkeyEngine, no native code: this is a plain
   fixed-timestep numerical model driven from a Compose coroutine, so it
   cannot crash the renderer. It exists to make the cockpit REACT like a
   Twin Otter for MCC drills, not to fly A-to-B:

     - Constant-speed prop governing: torque = shaft power / prop speed,
       so pulling the prop levers back (lower Np) at the same power
       RAISES torque, exactly like the aircraft.
     - Ng-driven power with spool lag, ITT that follows fuel flow,
       feathering, windmilling, start sequence with hot-start ITT peaks.
     - Electrical (battery/generators/bus), fuel (boost pumps, crossfeed),
       fire detection/handles, autofeather.
     - A simple point-mass energy airframe (IAS / altitude / VS / stall)
       controlled by pitch, flaps, and the levers. No world, no scenery.
     - Instructor failure injection for two-crew drills.
   ===================================================================== */

internal enum class SimEngineSide { LEFT, RIGHT }

internal enum class SimFailure(val label: String, val side: SimEngineSide?) {
    LEFT_ENGINE_FAIL("L engine flameout", SimEngineSide.LEFT),
    RIGHT_ENGINE_FAIL("R engine flameout", SimEngineSide.RIGHT),
    LEFT_ENGINE_FIRE("L engine fire", SimEngineSide.LEFT),
    RIGHT_ENGINE_FIRE("R engine fire", SimEngineSide.RIGHT),
    LEFT_GEN_FAIL("L generator fail", SimEngineSide.LEFT),
    RIGHT_GEN_FAIL("R generator fail", SimEngineSide.RIGHT),
    LEFT_BOOST_FAIL("L boost pumps fail", SimEngineSide.LEFT),
    RIGHT_BOOST_FAIL("R boost pumps fail", SimEngineSide.RIGHT),
}

/** Pilot / instructor writable controls. Mutated from the UI thread. */
internal class TwinOtterControls {
    // Per-engine levers: index 0 = left, 1 = right.
    // Power lever: -0.3..1.0 (negative = beta/reverse range, 0 = flight idle).
    val powerLever = floatArrayOf(0f, 0f)
    // Prop lever: 0 = FEATHER, 0.08..1.0 = governed 1600..2200 rpm.
    val propLever = floatArrayOf(1f, 1f)
    val fuelLever = booleanArrayOf(false, false)     // condition lever ON/OFF
    val starter = booleanArrayOf(false, false)
    val fireHandlePulled = booleanArrayOf(false, false)
    val boostPumpFwd = booleanArrayOf(false, false)
    val boostPumpAft = booleanArrayOf(false, false)
    val generator = booleanArrayOf(false, false)

    var batteryMaster = false
    var avionicsMaster = false
    var autofeatherArmed = false
    var crossfeedOpen = false
    var brakes = true

    // Airframe controls.
    var pitchCommandDeg = 0f      // -5..15, models yoke + trim
    var flapsIndex = 0            // 0, 10, 20, 37.5 deg
    var masterCautionReset = false

    val failures = mutableSetOf<SimFailure>()
}

/** Immutable per-engine snapshot for the gauges. */
internal data class SimEngineSnapshot(
    val ngPercent: Float,
    val propRpm: Float,
    val torquePsi: Float,
    val ittC: Float,
    val fuelFlowPph: Float,
    val oilPressPsi: Float,
    val oilTempC: Float,
    val running: Boolean,
    val starting: Boolean,
    val feathered: Boolean,
    val autofeathered: Boolean,
    val fire: Boolean,
    val generatorOnline: Boolean,
    val fuelPressLow: Boolean,
    val startIttExceeded: Boolean,
)

internal data class SimAnnunciator(
    val label: String,
    val warning: Boolean,          // true = red warning, false = amber caution
)

/** Full aircraft snapshot published once per tick for Compose. */
internal data class TwinOtterSnapshot(
    val left: SimEngineSnapshot,
    val right: SimEngineSnapshot,
    val iasKt: Float = 0f,
    val altitudeFt: Float = 0f,
    val verticalSpeedFpm: Float = 0f,
    val pitchDeg: Float = 0f,
    val aoaDeg: Float = 0f,
    val flapsDeg: Float = 0f,
    val onGround: Boolean = true,
    val stallWarning: Boolean = false,
    val busVolts: Float = 0f,
    val busPowered: Boolean = false,
    val avionicsOn: Boolean = false,
    val autofeatherArmed: Boolean = false,
    val autofeatherActive: Boolean = false,
    val hydraulicPsi: Float = 0f,
    val annunciators: List<SimAnnunciator> = emptyList(),
    val masterWarning: Boolean = false,
    val masterCaution: Boolean = false,
    val elapsedSec: Int = 0,
)

internal val SimFlapDetents = floatArrayOf(0f, 10f, 20f, 37.5f)

private class SimEngineState {
    var ng = 0f                 // gas generator %
    var np = 0f                 // prop rpm
    var itt = 15f               // deg C
    var fuelFlow = 0f           // pph
    var oilPress = 0f
    var oilTemp = 15f
    var running = false
    var starting = false
    var autofeathered = false
    var startPeakItt = 0f
    var startIttExceeded = false
    var shaftPowerHp = 0f
}

internal class TwinOtterSimModel {
    val controls = TwinOtterControls()

    private val engines = arrayOf(SimEngineState(), SimEngineState())

    // Airframe state.
    private var iasMps = 0f
    private var altitudeM = 0f
    private var vsMps = 0f
    private var aoaDeg = 0f
    private var onGround = true
    private var stallWarn = false
    private var elapsed = 0f

    private var masterWarnLatched = false
    private var masterCautionLatched = false
    private var lastWarnKeys = emptySet<String>()
    private var lastCautionKeys = emptySet<String>()

    @Volatile var snapshot = TwinOtterSnapshot(emptyEngine(), emptyEngine())
        private set

    companion object {
        // PT6A-27 / DHC-6-300 reference numbers.
        const val MaxShp = 620f
        const val NgIdle = 52f
        const val NgMax = 96.5f
        const val NpMax = 2200f
        const val NpGovMin = 1600f
        const val TorqueRedlinePsi = 50f
        const val IttRedlineC = 725f
        const val IttStartLimitC = 1090f
        const val PropFeatherDetent = 0.06f

        private const val MassKg = 5250f          // ~11,600 lb mission weight
        private const val WingAreaM2 = 39.02f
        private const val Cd0 = 0.046f
        private const val InducedK = 0.0455f
        private const val ClAlphaPerDeg = 0.0925f
        private const val PropEfficiency = 0.78f
        private const val Gravity = 9.80665f
        private const val KtToMps = 0.514444f
        private const val MToFt = 3.28084f

        private fun emptyEngine() = SimEngineSnapshot(
            ngPercent = 0f, propRpm = 0f, torquePsi = 0f, ittC = 15f,
            fuelFlowPph = 0f, oilPressPsi = 0f, oilTempC = 15f,
            running = false, starting = false, feathered = false,
            autofeathered = false, fire = false, generatorOnline = false,
            fuelPressLow = false, startIttExceeded = false,
        )
    }

    /* ------------------------------------------------------------------ */
    /* Preset states for drills                                            */
    /* ------------------------------------------------------------------ */

    fun resetColdAndDark() {
        controls.apply {
            powerLever.fill(0f); propLever.fill(1f)
            fuelLever.fill(false); starter.fill(false); fireHandlePulled.fill(false)
            boostPumpFwd.fill(false); boostPumpAft.fill(false); generator.fill(false)
            batteryMaster = false; avionicsMaster = false; autofeatherArmed = false
            crossfeedOpen = false; brakes = true
            pitchCommandDeg = 0f; flapsIndex = 0
            failures.clear()
        }
        engines.forEach {
            it.ng = 0f; it.np = 0f; it.itt = 15f; it.fuelFlow = 0f
            it.oilPress = 0f; it.oilTemp = 15f; it.running = false; it.starting = false
            it.autofeathered = false; it.startPeakItt = 0f; it.startIttExceeded = false
            it.shaftPowerHp = 0f
        }
        iasMps = 0f; altitudeM = 0f; vsMps = 0f; onGround = true; elapsed = 0f
        masterWarnLatched = false; masterCautionLatched = false
        publish()
    }

    fun resetReadyForTakeoff() {
        resetColdAndDark()
        controls.apply {
            batteryMaster = true; avionicsMaster = true; autofeatherArmed = true
            boostPumpFwd.fill(true); boostPumpAft.fill(true)
            generator.fill(true); fuelLever.fill(true)
            propLever.fill(1f); powerLever.fill(0f)
            flapsIndex = 1; brakes = true
        }
        engines.forEach {
            it.running = true; it.ng = NgIdle; it.np = 950f; it.itt = 460f
            it.oilPress = 92f; it.oilTemp = 62f
        }
        publish()
    }

    fun resetCruise() {
        resetReadyForTakeoff()
        controls.apply {
            powerLever[0] = 0.62f; powerLever[1] = 0.62f
            propLever[0] = 0.55f; propLever[1] = 0.55f   // props pulled back for cruise
            flapsIndex = 0; brakes = false
            pitchCommandDeg = 2f
        }
        iasMps = 140f * KtToMps
        altitudeM = 8000f / MToFt
        onGround = false
        engines.forEach { it.ng = 85f; it.np = 1930f; it.itt = 620f }
        publish()
    }

    /* ------------------------------------------------------------------ */
    /* Main update                                                         */
    /* ------------------------------------------------------------------ */

    fun update(dtRaw: Float) {
        var remaining = dtRaw.coerceIn(0f, 0.25f)
        val h = 1f / 60f
        while (remaining > 1e-4f) {
            val dt = minOf(h, remaining)
            step(dt)
            remaining -= dt
        }
        publish()
    }

    private fun step(dt: Float) {
        elapsed += dt
        stepEngine(0, dt)
        stepEngine(1, dt)
        stepAutofeather()
        stepAirframe(dt)
    }

    private fun engineFailed(i: Int): Boolean =
        controls.failures.contains(if (i == 0) SimFailure.LEFT_ENGINE_FAIL else SimFailure.RIGHT_ENGINE_FAIL)

    private fun engineFire(i: Int): Boolean =
        controls.failures.contains(if (i == 0) SimFailure.LEFT_ENGINE_FIRE else SimFailure.RIGHT_ENGINE_FIRE)

    private fun genFailed(i: Int): Boolean =
        controls.failures.contains(if (i == 0) SimFailure.LEFT_GEN_FAIL else SimFailure.RIGHT_GEN_FAIL)

    private fun boostFailed(i: Int): Boolean =
        controls.failures.contains(if (i == 0) SimFailure.LEFT_BOOST_FAIL else SimFailure.RIGHT_BOOST_FAIL)

    private fun busPowered(): Boolean =
        controls.batteryMaster || (0..1).any { generatorOnline(it) }

    private fun generatorOnline(i: Int): Boolean =
        controls.generator[i] && engines[i].running && engines[i].ng > 50f && !genFailed(i)

    private fun fuelAvailable(i: Int): Boolean =
        controls.fuelLever[i] && !controls.fireHandlePulled[i] && !engineFailed(i)

    private fun boostPressureOk(i: Int): Boolean {
        val ownPumps = (controls.boostPumpFwd[i] || controls.boostPumpAft[i]) && !boostFailed(i)
        val other = 1 - i
        val crossPumps = controls.crossfeedOpen &&
            (controls.boostPumpFwd[other] || controls.boostPumpAft[other]) && !boostFailed(other)
        return busPowered() && (ownPumps || crossPumps)
    }

    private fun stepEngine(i: Int, dt: Float) {
        val e = engines[i]
        val pl = controls.powerLever[i]
        val prop = controls.propLever[i]
        val feathered = prop <= PropFeatherDetent || e.autofeathered
        val fuelOk = fuelAvailable(i)

        // ---- Flameout conditions ----------------------------------------
        if (e.running && !fuelOk) {
            e.running = false
            e.startPeakItt = 0f
        }

        // ---- Start sequence ---------------------------------------------
        val starterActive = controls.starter[i] && busPowered() && !e.running
        e.starting = starterActive
        if (starterActive) {
            // Starter motors Ng toward ~25%.
            e.ng += (26f - e.ng) * (dt / 3.2f)
            if (fuelOk && e.ng >= 8f) {
                // Lightoff. Fuel introduced below ~12% Ng gives a hot start;
                // by 12%+ the peak stays at or under the 1090 C start limit.
                e.running = true
                e.starting = false
                e.startPeakItt = 650f + (20f - e.ng).coerceAtLeast(0f) * 55f
                if (e.startPeakItt > IttStartLimitC) e.startIttExceeded = true
            }
        }
        if (e.running && e.ng > 50f) controls.starter[i] = false

        // ---- Ng spool -----------------------------------------------------
        val ngTarget = when {
            e.running -> NgIdle + pl.coerceIn(0f, 1f) * (NgMax - NgIdle) +
                // Beta/reverse spools Ng up slightly with reverse power.
                (-pl).coerceIn(0f, 0.3f) * 40f
            starterActive -> 26f
            else -> windmillNg()
        }
        val tau = if (ngTarget > e.ng) 1.4f else 1.8f
        e.ng += (ngTarget - e.ng) * (dt / tau).coerceAtMost(1f)

        // ---- Shaft power ---------------------------------------------------
        val powerFrac = ((e.ng - NgIdle) / (NgMax - NgIdle)).coerceIn(0f, 1f)
        e.shaftPowerHp = if (e.running) MaxShp * powerFrac.pow(1.65f) else 0f

        // ---- Prop speed: governed vs free ---------------------------------
        val iasKt = iasMps / KtToMps
        val npTarget: Float = if (feathered) {
            // Feathered: little windmilling drive; decays toward near-stop.
            if (e.running) 480f else (iasKt * 0.9f).coerceAtMost(300f)
        } else if (e.running) {
            val propFrac = ((prop - PropFeatherDetent) / (1f - PropFeatherDetent)).coerceIn(0f, 1f)
            val governed = NpGovMin + propFrac * (NpMax - NpGovMin)
            // Below the governing range the prop turns as fast as power allows.
            val free = 550f + 1900f * sqrt(e.shaftPowerHp / MaxShp) + iasKt * 1.6f
            minOf(governed, free)
        } else {
            // Unfeathered windmill: driven by airspeed - the classic drag state.
            (iasKt * 11f).coerceAtMost(NpGovMin)
        }
        e.np += (npTarget - e.np) * (dt / 0.9f).coerceAtMost(1f)

        // ---- ITT -----------------------------------------------------------
        val ittTarget = when {
            e.running -> {
                val base = 400f + 310f * powerFrac.pow(1.15f)
                // Start transient decays over the first seconds after lightoff.
                if (e.startPeakItt > base) {
                    e.startPeakItt -= 160f * dt
                    maxOf(base, e.startPeakItt)
                } else base
            }
            e.ng > 5f -> 90f + e.ng * 2f
            else -> 15f
        }
        e.itt += (ittTarget - e.itt) * (dt / 1.6f).coerceAtMost(1f)

        // ---- Fuel flow, oil -----------------------------------------------
        e.fuelFlow = if (e.running) 78f + 500f * powerFrac.pow(1.25f) else 0f
        val oilTargetPress = when {
            e.running -> 88f + powerFrac * 10f
            starterActive -> 42f
            else -> 0f
        }
        e.oilPress += (oilTargetPress - e.oilPress) * (dt / 1.2f).coerceAtMost(1f)
        val oilTargetTemp = if (e.running) 58f + powerFrac * 22f else 15f
        e.oilTemp += (oilTargetTemp - e.oilTemp) * (dt / 45f).coerceAtMost(1f)
    }

    private fun windmillNg(): Float {
        val iasKt = iasMps / KtToMps
        return (iasKt * 0.12f).coerceAtMost(14f)
    }

    /** Torque follows directly from power and prop speed: Q = P / omega.
     *  Calibrated so 620 SHP at 2200 rpm reads 50 psi - the takeoff limit.
     *  Pull the props back at constant power and this number RISES. */
    fun torquePsi(i: Int): Float {
        val e = engines[i]
        if (e.np < 250f) return 0f
        val ftLb = e.shaftPowerHp * 5252f / e.np
        return ftLb / 29.6f
    }

    private fun stepAutofeather() {
        val armed = controls.autofeatherArmed
        val active = armed && controls.powerLever[0] > 0.8f && controls.powerLever[1] > 0.8f
        if (!active) return
        for (i in 0..1) {
            val other = 1 - i
            if (!engines[i].autofeathered &&
                torquePsi(i) < 9f && torquePsi(other) > 30f
            ) {
                engines[i].autofeathered = true
            }
        }
    }

    private fun stepAirframe(dt: Float) {
        val flapsDeg = SimFlapDetents[controls.flapsIndex.coerceIn(0, SimFlapDetents.lastIndex)]
        val flapFrac = flapsDeg / 37.5f
        val rho = 1.225f * (1f - altitudeM * MToFt * 6.87e-6f).pow(4.26f).coerceIn(0.4f, 1f)

        // Thrust from both props; reverse produces negative thrust in beta.
        var thrustN = 0f
        for (i in 0..1) {
            val e = engines[i]
            val watts = e.shaftPowerHp * 745.7f * PropEfficiency
            val v = iasMps.coerceAtLeast(12f)
            val feathered = controls.propLever[i] <= PropFeatherDetent || e.autofeathered
            var t = if (feathered && !e.running) 0f else watts / v
            t = t.coerceAtMost(14_000f)   // static thrust cap per engine
            if (controls.powerLever[i] < -0.02f) {
                t = controls.powerLever[i] / 0.3f * 9000f * (e.ng / NgMax)
            }
            // Windmilling unfeathered dead prop = extra drag.
            if (!e.running && !feathered) t -= 55f * iasMps
            thrustN += t
        }

        val q = 0.5f * rho * iasMps * iasMps
        if (onGround) {
            aoaDeg = 0f
            stallWarn = false
            val rollDrag = if (controls.brakes) 0.10f else 0.02f
            val dragN = q * WingAreaM2 * (Cd0 + 0.03f * flapFrac) +
                rollDrag * MassKg * Gravity
            val accel = (thrustN - dragN) / MassKg
            iasMps = (iasMps + accel * dt).coerceAtLeast(0f)
            vsMps = 0f
            altitudeM = 0f

            // Rotation: enough speed plus nose-up command lifts off.
            val vrMps = (78f - 14f * flapFrac) * KtToMps
            if (iasMps > vrMps && controls.pitchCommandDeg > 3f) {
                onGround = false
                vsMps = 1.5f
            }
        } else {
            // Point-mass with pitch as the control: gamma = pitch - alpha.
            val clNeeded = MassKg * Gravity /
                (q.coerceAtLeast(140f) * WingAreaM2)
            val cl0 = 0.25f + 1.05f * flapFrac
            aoaDeg = ((clNeeded - cl0) / ClAlphaPerDeg).coerceIn(-6f, 22f)
            val stallAlpha = 16f - 3.5f * flapFrac
            stallWarn = aoaDeg > stallAlpha - 1.5f

            val cl = (cl0 + ClAlphaPerDeg * aoaDeg.coerceAtMost(stallAlpha + 2f))
            val cd = Cd0 + InducedK * cl * cl + 0.055f * flapFrac
            val dragN = q * WingAreaM2 * cd

            var gammaDeg = controls.pitchCommandDeg - aoaDeg
            if (aoaDeg > stallAlpha) {
                // Stalled: nose drops regardless of command.
                gammaDeg -= (aoaDeg - stallAlpha) * 3f
            }
            val gammaRad = gammaDeg.coerceIn(-25f, 20f) * (Math.PI.toFloat() / 180f)

            val accel = (thrustN - dragN) / MassKg - Gravity * sin(gammaRad)
            iasMps = (iasMps + accel * dt).coerceIn(0f, 115f)
            vsMps = iasMps * sin(gammaRad)
            altitudeM += vsMps * dt
            if (altitudeM <= 0f) {
                altitudeM = 0f
                onGround = true
                vsMps = 0f
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Snapshot / annunciators                                             */
    /* ------------------------------------------------------------------ */

    private fun engineSnapshot(i: Int): SimEngineSnapshot {
        val e = engines[i]
        val feathered = controls.propLever[i] <= PropFeatherDetent || e.autofeathered
        return SimEngineSnapshot(
            ngPercent = e.ng,
            propRpm = e.np,
            torquePsi = torquePsi(i),
            ittC = e.itt,
            fuelFlowPph = e.fuelFlow,
            oilPressPsi = e.oilPress,
            oilTempC = e.oilTemp,
            running = e.running,
            starting = e.starting,
            feathered = feathered,
            autofeathered = e.autofeathered,
            fire = engineFire(i) && !controls.fireHandlePulled[i],
            generatorOnline = generatorOnline(i),
            fuelPressLow = controls.fuelLever[i] && !boostPressureOk(i),
            startIttExceeded = e.startIttExceeded,
        )
    }

    private fun publish() {
        val left = engineSnapshot(0)
        val right = engineSnapshot(1)
        val bus = busPowered()

        val warnings = mutableListOf<SimAnnunciator>()
        val cautions = mutableListOf<SimAnnunciator>()

        if (left.fire) warnings += SimAnnunciator("L ENG FIRE", true)
        if (right.fire) warnings += SimAnnunciator("R ENG FIRE", true)
        if (stallWarn && !onGround) warnings += SimAnnunciator("STALL", true)
        if (left.ittC > IttRedlineC && left.running) warnings += SimAnnunciator("L ITT LIMIT", true)
        if (right.ittC > IttRedlineC && right.running) warnings += SimAnnunciator("R ITT LIMIT", true)
        if (left.startIttExceeded) warnings += SimAnnunciator("L HOT START", true)
        if (right.startIttExceeded) warnings += SimAnnunciator("R HOT START", true)

        if (!bus) cautions += SimAnnunciator("DC BUS OFF", false)
        if (bus && left.running && !left.generatorOnline) cautions += SimAnnunciator("L DC GEN", false)
        if (bus && right.running && !right.generatorOnline) cautions += SimAnnunciator("R DC GEN", false)
        if (left.fuelPressLow) cautions += SimAnnunciator("L FUEL PRESS", false)
        if (right.fuelPressLow) cautions += SimAnnunciator("R FUEL PRESS", false)
        if (left.running && left.oilPressPsi < 80f) cautions += SimAnnunciator("L OIL PRESS", false)
        if (right.running && right.oilPressPsi < 80f) cautions += SimAnnunciator("R OIL PRESS", false)
        if (left.torquePsi > TorqueRedlinePsi) cautions += SimAnnunciator("L TORQUE", false)
        if (right.torquePsi > TorqueRedlinePsi) cautions += SimAnnunciator("R TORQUE", false)
        if (left.autofeathered) cautions += SimAnnunciator("L AUTOFEATHER", false)
        if (right.autofeathered) cautions += SimAnnunciator("R AUTOFEATHER", false)
        if (controls.crossfeedOpen) cautions += SimAnnunciator("CROSSFEED OPEN", false)
        if (controls.powerLever[0] < -0.02f || controls.powerLever[1] < -0.02f) {
            cautions += SimAnnunciator("BETA RANGE", false)
        }

        // Master lamps latch on NEW alerts; reset button clears the lamp only.
        val warnKeys = warnings.map { it.label }.toSet()
        val cautionKeys = cautions.map { it.label }.toSet()
        if ((warnKeys - lastWarnKeys).isNotEmpty()) masterWarnLatched = true
        if ((cautionKeys - lastCautionKeys).isNotEmpty()) masterCautionLatched = true
        if (warnKeys.isEmpty()) masterWarnLatched = false
        if (controls.masterCautionReset) {
            masterWarnLatched = false
            masterCautionLatched = false
            controls.masterCautionReset = false
        }
        lastWarnKeys = warnKeys
        lastCautionKeys = cautionKeys

        val autofeatherActive = controls.autofeatherArmed &&
            controls.powerLever[0] > 0.8f && controls.powerLever[1] > 0.8f

        snapshot = TwinOtterSnapshot(
            left = left,
            right = right,
            iasKt = iasMps / KtToMps,
            altitudeFt = altitudeM * MToFt,
            verticalSpeedFpm = vsMps * MToFt * 60f,
            pitchDeg = controls.pitchCommandDeg,
            aoaDeg = aoaDeg,
            flapsDeg = SimFlapDetents[controls.flapsIndex.coerceIn(0, SimFlapDetents.lastIndex)],
            onGround = onGround,
            stallWarning = stallWarn && !onGround,
            busVolts = when {
                (0..1).any { generatorOnline(it) } -> 28.5f
                controls.batteryMaster -> 24f
                else -> 0f
            },
            busPowered = bus,
            avionicsOn = bus && controls.avionicsMaster,
            autofeatherArmed = controls.autofeatherArmed,
            autofeatherActive = autofeatherActive,
            hydraulicPsi = when {
                left.running || right.running -> 2850f
                bus -> 500f
                else -> 0f
            },
            annunciators = warnings + cautions,
            masterWarning = masterWarnLatched || warnKeys.isNotEmpty(),
            masterCaution = masterCautionLatched,
            elapsedSec = elapsed.toInt(),
        )
    }
}

/* =====================================================================
   MCC drill scripts - two-crew scenarios evaluated live from sim state.
   ===================================================================== */

internal data class SimDrillGate(
    val label: String,
    val hint: String,
    val passed: (TwinOtterSimModel) -> Boolean,
)

internal enum class SimDrill(
    val title: String,
    val briefing: String,
    val setup: (TwinOtterSimModel) -> Unit,
    val armFailureAfterSec: Int,
    val failure: SimFailure?,
) {
    FREE_PRACTICE(
        title = "Free practice",
        briefing = "No script. Start engines, set power, and watch the aircraft react. " +
            "Try pulling the prop levers back at constant power and watch torque rise.",
        setup = { it.resetReadyForTakeoff() },
        armFailureAfterSec = 0,
        failure = null,
    ),
    ENGINE_START(
        title = "Battery start",
        briefing = "From cold and dark: battery ON, boost pumps ON, starter, fuel lever at 12%+ Ng, " +
            "watch the ITT peak, then generator ON. Introducing fuel too early causes a hot start.",
        setup = { it.resetColdAndDark() },
        armFailureAfterSec = 0,
        failure = null,
    ),
    ENGINE_FAILURE_TAKEOFF(
        title = "Engine failure after T/O",
        briefing = "PF flies, PM runs the drill. Right engine fails shortly after liftoff: " +
            "power up, clean up, identify - verify - feather, then secure the engine.",
        setup = { it.resetReadyForTakeoff() },
        armFailureAfterSec = 18,
        failure = SimFailure.RIGHT_ENGINE_FAIL,
    ),
    ENGINE_FIRE(
        title = "Engine fire in flight",
        briefing = "In cruise the left engine fire warning triggers. Memory items: power lever idle, " +
            "prop lever feather, fuel lever off, fire handle pull.",
        setup = { it.resetCruise() },
        armFailureAfterSec = 10,
        failure = SimFailure.LEFT_ENGINE_FIRE,
    ),
    FUEL_PRESSURE(
        title = "Fuel pressure loss",
        briefing = "In cruise the right boost pumps fail. Confirm the caution, open crossfeed " +
            "to restore pressure from the left pumps.",
        setup = { it.resetCruise() },
        armFailureAfterSec = 8,
        failure = SimFailure.RIGHT_BOOST_FAIL,
    ),
    GENERATOR_FAILURE(
        title = "Generator failure",
        briefing = "In cruise the left generator drops offline. Confirm the caution, attempt a reset " +
            "(cycle the switch), and keep the bus powered on the right generator.",
        setup = { it.resetCruise() },
        armFailureAfterSec = 8,
        failure = SimFailure.LEFT_GEN_FAIL,
    );

    fun gates(): List<SimDrillGate> = when (this) {
        FREE_PRACTICE -> listOf(
            SimDrillGate("Both engines running", "Fuel levers ON with pressure") { m ->
                m.snapshot.left.running && m.snapshot.right.running
            },
            SimDrillGate("Torque above 20 psi each side", "Advance the power levers") { m ->
                m.snapshot.left.torquePsi > 20f && m.snapshot.right.torquePsi > 20f
            },
            SimDrillGate("Props pulled back below 2000 rpm", "Torque rises as Np drops") { m ->
                m.snapshot.left.propRpm < 2000f && m.snapshot.left.running &&
                    m.snapshot.right.propRpm < 2000f && m.snapshot.right.running
            },
        )
        ENGINE_START -> listOf(
            SimDrillGate("Battery master ON", "Electrical panel") { it.controls.batteryMaster },
            SimDrillGate("Boost pumps ON both sides", "Fuel panel") { m ->
                (m.controls.boostPumpFwd[0] || m.controls.boostPumpAft[0]) &&
                    (m.controls.boostPumpFwd[1] || m.controls.boostPumpAft[1])
            },
            SimDrillGate("Left engine running, no hot start", "Starter then fuel above 12% Ng") { m ->
                m.snapshot.left.running && !m.snapshot.left.startIttExceeded
            },
            SimDrillGate("Right engine running, no hot start", "Starter then fuel above 12% Ng") { m ->
                m.snapshot.right.running && !m.snapshot.right.startIttExceeded
            },
            SimDrillGate("Both generators online", "After start, gens ON") { m ->
                m.snapshot.left.generatorOnline && m.snapshot.right.generatorOnline
            },
        )
        ENGINE_FAILURE_TAKEOFF -> listOf(
            SimDrillGate("Live engine at max power", "Power up") { m ->
                m.controls.powerLever[0] > 0.92f
            },
            SimDrillGate("Flaps retracted", "Clean up when safe") { m ->
                m.controls.flapsIndex == 0
            },
            SimDrillGate("Dead engine power lever idle", "Identify: dead leg, dead engine") { m ->
                m.controls.powerLever[1] <= 0.05f
            },
            SimDrillGate("Dead engine prop feathered", "Verify then feather") { m ->
                m.snapshot.right.feathered
            },
            SimDrillGate("Dead engine fuel lever OFF", "Secure the engine") { m ->
                !m.controls.fuelLever[1]
            },
            SimDrillGate("Positive climb maintained", "Pitch for blue-line, keep it climbing") { m ->
                !m.snapshot.onGround && m.snapshot.verticalSpeedFpm > 50f
            },
        )
        ENGINE_FIRE -> listOf(
            SimDrillGate("Left power lever idle", "Memory item 1") { m ->
                m.controls.powerLever[0] <= 0.05f
            },
            SimDrillGate("Left prop lever feather", "Memory item 2") { m ->
                m.controls.propLever[0] <= TwinOtterSimModel.PropFeatherDetent
            },
            SimDrillGate("Left fuel lever OFF", "Memory item 3") { m ->
                !m.controls.fuelLever[0]
            },
            SimDrillGate("Left fire handle pulled", "Memory item 4") { m ->
                m.controls.fireHandlePulled[0]
            },
            SimDrillGate("Fire warning extinguished", "Confirm the light is out") { m ->
                !m.snapshot.left.fire
            },
        )
        FUEL_PRESSURE -> listOf(
            SimDrillGate("R FUEL PRESS caution confirmed", "Monitor annunciators") { m ->
                m.controls.failures.contains(SimFailure.RIGHT_BOOST_FAIL)
            },
            SimDrillGate("Crossfeed opened", "Feed the right engine from the left pumps") { m ->
                m.controls.crossfeedOpen
            },
            SimDrillGate("Right fuel pressure restored", "Caution clears") { m ->
                !m.snapshot.right.fuelPressLow
            },
            SimDrillGate("Right engine still running", "No flameout") { m ->
                m.snapshot.right.running
            },
        )
        GENERATOR_FAILURE -> listOf(
            SimDrillGate("L DC GEN caution confirmed", "Monitor annunciators") { m ->
                m.controls.failures.contains(SimFailure.LEFT_GEN_FAIL)
            },
            SimDrillGate("Reset attempted (switch cycled OFF)", "Try one reset") { m ->
                !m.controls.generator[0] || m.snapshot.left.generatorOnline
            },
            SimDrillGate("Bus held by right generator", "28V maintained") { m ->
                m.snapshot.right.generatorOnline && m.snapshot.busVolts > 27f
            },
        )
    }
}
