package com.dhc6trainer.desktop

import com.jme3.math.FastMath
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/* =====================================================================
   Dhc6FlightDynamics - real-time DHC-6 Twin Otter flight model.

   Point-mass force model (lift/drag/thrust/gravity) with attitude
   kinematics, angle-of-attack driven lift with stall, flap effects,
   dynamic-pressure-scaled control authority, and ground roll handling.
   Aerodynamic reference data comes from the bundled Kenn Borek
   Aircraft.cfg (weights, wing geometry, stall speeds, PT6A-27 power).

   Conventions (matches the jME scene):
     world: X east, Y up, Z south; aircraft heading 0 flies toward -Z.
     headingRad increases turning right; pitchRad positive nose-up;
     rollRad positive right-wing-down.
     Position tracks the main gear contact plane; y == 0 is on the runway.
   ===================================================================== */

/** Pilot inputs. Written from the Compose UI thread, read on the render thread. */
internal class Dhc6FlightControls {
    @Volatile var elevator = 0f      // -1..1, positive = pull (nose up)
    @Volatile var aileron = 0f       // -1..1, positive = roll right
    @Volatile var rudder = 0f        // -1..1, positive = yaw right
    @Volatile var throttle = 0f      // 0..1
    @Volatile var elevatorTrim = 0f  // -1..1
    @Volatile var flapsIndex = 0     // index into Dhc6Params.flapDegrees
    @Volatile var brakes = false
    @Volatile var enginesRunning = true
    @Volatile var paused = false
}

/** Snapshot published by the render thread for Compose instruments. */
internal class Dhc6Telemetry(
    val iasKnots: Float = 0f,
    val altitudeFt: Float = 0f,
    val verticalSpeedFpm: Float = 0f,
    val headingDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val bankDeg: Float = 0f,
    val aoaDeg: Float = 0f,
    val torquePercent: Float = 0f,
    val propRpm: Float = 0f,
    val flapsDeg: Float = 0f,
    val groundSpeedKt: Float = 0f,
    val onGround: Boolean = true,
    val stallWarning: Boolean = false,
    val enginesRunning: Boolean = true,
    val paused: Boolean = false,
)

/** DHC-6 reference data, with Aircraft.cfg-derived defaults. */
internal class Dhc6Params(
    val massKg: Float = 4763f,               // ~10,500 lb mid mission weight
    val wingAreaM2: Float = 39.02f,          // 420 sq ft
    val wingSpanM: Float = 19.812f,          // 65 ft
    val maxPowerWatts: Float = 972_000f,     // 2 x 652 SHP PT6A-27
    val propEfficiency: Float = 0.80f,
    val staticThrustN: Float = 15_500f,      // both engines, sea level
    val cd0: Float = 0.040f,                 // fixed gear STOL airframe
    val cl0: Float = 0.28f,
    val clAlphaPerRad: Float = 5.4f,
    val stallAlphaRad: Float = FastMath.DEG_TO_RAD * 16f,
    val oswaldEfficiency: Float = 0.74f,
    val flapDegrees: FloatArray = floatArrayOf(0f, 10f, 20f, 37.5f),
    val sourceLabel: String = "Bundled DHC-6 reference data",
) {
    val aspectRatio: Float = wingSpanM * wingSpanM / wingAreaM2
    val inducedDragK: Float = 1f / (FastMath.PI * aspectRatio * oswaldEfficiency)

    companion object {
        private const val LbToKg = 0.45359237f
        private const val FtToM = 0.3048f
        private const val Ft2ToM2 = 0.09290304f
        private const val HpToW = 745.6999f
        private const val LbfToN = 4.4482216f

        fun fromAircraftCfgText(text: String): Dhc6Params {
            fun value(key: String): Float? =
                Regex("""(?im)^\s*$key\s*=\s*(-?[\d.]+)""").find(text)
                    ?.groupValues?.get(1)?.toFloatOrNull()

            val emptyLb = value("empty_weight") ?: 7415f
            val grossLb = value("max_gross_weight") ?: 12500f
            val wingAreaFt2 = value("wing_area") ?: 420f
            val wingSpanFt = value("wing_span") ?: 65f
            return Dhc6Params(
                massKg = ((emptyLb + grossLb) / 2f) * LbToKg,
                wingAreaM2 = wingAreaFt2 * Ft2ToM2,
                wingSpanM = wingSpanFt * FtToM,
                sourceLabel = "FSX Aircraft.cfg DHC-6 data",
            )
        }

        fun fromJsbsimProfile(profile: JsbsimDhc6Profile): Dhc6Params =
            Dhc6Params(
                massKg = profile.averageMissionWeightLb * LbToKg,
                wingAreaM2 = profile.wingAreaFt2 * Ft2ToM2,
                wingSpanM = profile.wingSpanFt * FtToM,
                maxPowerWatts = profile.totalMaxPowerHp * HpToW,
                staticThrustN = profile.totalStaticThrustLb * LbfToN,
                cd0 = profile.cd0?.coerceIn(0.018f, 0.065f) ?: 0.040f,
                cl0 = profile.cl0?.coerceIn(0.05f, 0.60f) ?: 0.28f,
                clAlphaPerRad = profile.clAlphaPerRad?.coerceIn(4.0f, 7.0f) ?: 5.4f,
                oswaldEfficiency = profile.inducedDragK
                    ?.takeIf { it > 0f }
                    ?.let { k -> (1f / (FastMath.PI * ((profile.wingSpanFt * profile.wingSpanFt) / profile.wingAreaFt2) * k)).coerceIn(0.45f, 0.95f) }
                    ?: 0.74f,
                sourceLabel = "JSBSim ${profile.aircraftName} DHC-6 data",
            )

        /** Parses the bundled Aircraft.cfg, falling back to type defaults. */
        fun fromBundledAircraftCfg(): Dhc6Params {
            val text = runCatching {
                val loader = Thread.currentThread().contextClassLoader
                    ?: Dhc6Params::class.java.classLoader
                loader.getResourceAsStream("$KennBorekAircraftResourceRoot/Aircraft.cfg")
                    ?.use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }.getOrNull() ?: return Dhc6Params()
            return fromAircraftCfgText(text)
        }
    }
}

internal class Dhc6FlightModel(
    private val params: Dhc6Params = Dhc6Params.fromBundledAircraftCfg(),
) {
    val position = Vector3f()
    val velocity = Vector3f()
    var headingRad = 0f
        private set
    var pitchRad = 0f
        private set
    var rollRad = 0f
        private set
    var propAngleRad = 0f
        private set

    @Volatile var telemetry = Dhc6Telemetry()
        private set

    private var smoothedTorque = 0f
    private var timeAccumulator = 0f
    private var onGround = true
    private var stalled = false

    private val attitudeQuat = Quaternion()
    private val scratchForward = Vector3f()
    private val scratchUp = Vector3f()
    private val scratchBody = Vector3f()
    private val scratchForce = Vector3f()

    companion object {
        private const val Gravity = 9.80665f
        private const val AirDensity = 1.225f
        private const val SubstepSeconds = 1f / 120f
        private const val MetersPerSecToKnots = 1.9438445f
        private const val MetersToFeet = 3.2808399f
    }

    /**
     * Runway spawn point; the scene overrides this to match whichever world
     * is loaded (procedural strip vs VRMM). Departure is along -Z.
     */
    val runwayStart = Vector3f(0f, 0f, 520f)

    init {
        resetOnRunway()
    }

    fun resetOnRunway() {
        position.set(runwayStart)
        velocity.set(0f, 0f, 0f)
        headingRad = 0f
        pitchRad = 0f
        rollRad = 0f
        onGround = true
        stalled = false
        smoothedTorque = 0f
    }

    fun resetOnFinal() {
        // Three-mile final at 1,000 ft AGL, 85 knots.
        position.set(0f, 305f, 5000f)
        headingRad = 0f
        pitchRad = 0f
        rollRad = 0f
        velocity.set(0f, -2.2f, -43.7f)
        onGround = false
        stalled = false
    }

    fun resetInCruise() {
        position.set(0f, 915f, 0f)
        headingRad = 0f
        pitchRad = FastMath.DEG_TO_RAD * 2f
        rollRad = 0f
        velocity.set(0f, 0f, -72f)
        onGround = false
        stalled = false
    }

    /** Advances the simulation; call once per rendered frame. */
    fun update(controls: Dhc6FlightControls, tpf: Float) {
        if (controls.paused) {
            publishTelemetry(controls)
            return
        }
        timeAccumulator = (timeAccumulator + tpf.coerceIn(0f, 0.25f))
        var guard = 0
        while (timeAccumulator >= SubstepSeconds && guard < 40) {
            step(controls, SubstepSeconds)
            timeAccumulator -= SubstepSeconds
            guard++
        }
        publishTelemetry(controls)
    }

    /** World-space attitude for the scene graph. */
    fun attitude(): Quaternion {
        attitudeQuat.fromAngles(0f, -headingRad, 0f)
        attitudeQuat.multLocal(Quaternion().fromAngles(pitchRad, 0f, 0f))
        attitudeQuat.multLocal(Quaternion().fromAngles(0f, 0f, -rollRad))
        return attitudeQuat
    }

    private fun forwardDir(out: Vector3f): Vector3f {
        val cp = cos(pitchRad)
        out.set(sin(headingRad) * cp, sin(pitchRad), -cos(headingRad) * cp)
        return out
    }

    private fun step(controls: Dhc6FlightControls, dt: Float) {
        val q = attitude()
        val forward = forwardDir(scratchForward)
        val up = q.mult(Vector3f.UNIT_Y, scratchUp)

        val speed = velocity.length()
        val dynamicPressure = 0.5f * AirDensity * speed * speed
        // Control authority scales with dynamic pressure (reference q at ~55 m/s).
        val qFactor = (dynamicPressure / 1850f).coerceIn(0f, 1.6f)

        val flapsDeg = params.flapDegrees[controls.flapsIndex.coerceIn(0, params.flapDegrees.lastIndex)]
        val flapFrac = flapsDeg / 37.5f

        // ---- Angle of attack ------------------------------------------------
        val alpha: Float
        if (speed > 2f) {
            val vBody = q.inverse().mult(velocity, scratchBody)
            alpha = atan2(-vBody.y, -vBody.z)
        } else {
            alpha = 0f
        }

        // ---- Lift coefficient with flap increment and stall ------------------
        val stallAlpha = params.stallAlphaRad - FastMath.DEG_TO_RAD * 3f * flapFrac
        var cl = params.cl0 + params.clAlphaPerRad * alpha + 1.05f * flapFrac
        stalled = alpha > stallAlpha
        if (stalled) {
            val past = ((alpha - stallAlpha) / (FastMath.DEG_TO_RAD * 8f)).coerceIn(0f, 1f)
            cl *= (1f - 0.45f * past)
        }
        cl = cl.coerceIn(-1.2f, 2.9f)

        val cdInduced = params.inducedDragK * cl * cl
        val cd = params.cd0 + cdInduced + 0.055f * flapFrac

        // ---- Forces ---------------------------------------------------------
        scratchForce.set(0f, -Gravity * params.massKg, 0f)

        if (speed > 0.5f) {
            val velDir = velocity.normalize()
            // Lift acts perpendicular to the airflow, in the aircraft's up-plane.
            val liftDir = up.subtract(velDir.mult(up.dot(velDir)))
            if (liftDir.lengthSquared() > 1e-6f) {
                liftDir.normalizeLocal()
                scratchForce.addLocal(liftDir.multLocal(dynamicPressure * params.wingAreaM2 * cl))
            }
            scratchForce.addLocal(velDir.mult(-dynamicPressure * params.wingAreaM2 * cd))
        }

        // Thrust: static value low-speed, power-limited at speed.
        val throttle = if (controls.enginesRunning) controls.throttle.coerceIn(0f, 1f) else 0f
        val powerLimited = params.propEfficiency * params.maxPowerWatts / speed.coerceAtLeast(18f)
        val thrust = throttle * minOf(params.staticThrustN, powerLimited)
        scratchForce.addLocal(forward.mult(thrust))

        // ---- Integrate linear motion ----------------------------------------
        velocity.addLocal(scratchForce.multLocal(dt / params.massKg))

        // ---- Attitude rates --------------------------------------------------
        val elevatorCmd = (controls.elevator + controls.elevatorTrim * 0.35f).coerceIn(-1f, 1f)
        val pitchAuthority = FastMath.DEG_TO_RAD * 22f
        val rollAuthority = FastMath.DEG_TO_RAD * 62f
        val yawAuthority = FastMath.DEG_TO_RAD * 16f

        if (!onGround) {
            pitchRad += elevatorCmd * pitchAuthority * qFactor * dt
            // Natural pitch stability: nose seeks trimmed AoA.
            pitchRad -= (alpha - FastMath.DEG_TO_RAD * 2.5f) * 0.35f * qFactor * dt
            rollRad += controls.aileron * rollAuthority * qFactor * dt
            // Roll stability (dihedral): slowly wings-level.
            rollRad -= rollRad * 0.16f * dt
            // Coordinated turn plus rudder yaw.
            if (speed > 8f) {
                headingRad += (Gravity * tan(rollRad.coerceIn(-1.2f, 1.2f)) / speed) * dt
            }
            headingRad += controls.rudder * yawAuthority * qFactor * 0.5f * dt
            // Stall behavior: nose drops, wing may drop.
            if (stalled) {
                pitchRad -= FastMath.DEG_TO_RAD * 9f * dt
                rollRad += FastMath.DEG_TO_RAD * 5f * dt * sin(position.x * 0.37f + position.z * 0.13f)
            }
        } else {
            // Ground: rudder steers, aerodynamic pitch control past ~40 kt.
            val steerAuthority = FastMath.DEG_TO_RAD * (45f / (1f + speed * 0.24f))
            headingRad += controls.rudder * steerAuthority * dt
            val groundPitchRate = elevatorCmd * pitchAuthority * qFactor
            pitchRad = (pitchRad + groundPitchRate * dt).coerceIn(0f, FastMath.DEG_TO_RAD * 14f)
            if (elevatorCmd <= 0.05f) {
                pitchRad = (pitchRad - FastMath.DEG_TO_RAD * 10f * dt).coerceAtLeast(0f)
            }
            rollRad *= (1f - 6f * dt).coerceAtLeast(0f)
        }
        pitchRad = pitchRad.coerceIn(-FastMath.HALF_PI * 0.94f, FastMath.HALF_PI * 0.94f)
        rollRad = wrapAngle(rollRad)
        headingRad = wrapAngle(headingRad)

        // ---- Ground contact ---------------------------------------------------
        position.addLocal(velocity.x * dt, velocity.y * dt, velocity.z * dt)
        if (position.y <= 0f) {
            position.y = 0f
            if (velocity.y < 0f) velocity.y = 0f
            if (!onGround && pitchRad < 0f) pitchRad = 0f
            onGround = true

            // Wheels roll along the heading: kill lateral slip, apply friction.
            val fwdFlat = Vector3f(sin(headingRad), 0f, -cos(headingRad))
            val fwdSpeed = velocity.dot(fwdFlat)
            val friction = if (controls.brakes) 3.4f else 0.35f
            val decel = friction * dt
            val newFwd = when {
                fwdSpeed > decel -> fwdSpeed - decel
                fwdSpeed < -decel -> fwdSpeed + decel
                else -> 0f
            }
            velocity.set(fwdFlat.multLocal(newFwd))
        } else if (position.y > 0.05f) {
            onGround = false
        }

        // ---- Engine visuals ----------------------------------------------------
        val targetTorque = if (controls.enginesRunning) 8f + throttle * 92f else 0f
        smoothedTorque += (targetTorque - smoothedTorque) * (2.2f * dt).coerceAtMost(1f)
        val propRps = if (controls.enginesRunning) 12f + throttle * 21f else 0f
        propAngleRad = wrapAngle(propAngleRad + propRps * FastMath.TWO_PI * dt)
    }

    private fun publishTelemetry(controls: Dhc6FlightControls) {
        val speed = velocity.length()
        val flapsDeg = params.flapDegrees[controls.flapsIndex.coerceIn(0, params.flapDegrees.lastIndex)]
        val groundSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        telemetry = Dhc6Telemetry(
            iasKnots = speed * MetersPerSecToKnots,
            altitudeFt = position.y * MetersToFeet,
            verticalSpeedFpm = velocity.y * MetersToFeet * 60f,
            headingDeg = (FastMath.RAD_TO_DEG * headingRad + 360f) % 360f,
            pitchDeg = FastMath.RAD_TO_DEG * pitchRad,
            bankDeg = FastMath.RAD_TO_DEG * rollRad,
            aoaDeg = 0f,
            torquePercent = smoothedTorque,
            propRpm = if (controls.enginesRunning) 1900f + controls.throttle * 300f else 0f,
            flapsDeg = flapsDeg,
            groundSpeedKt = groundSpeed * MetersPerSecToKnots,
            onGround = onGround,
            stallWarning = stalled && !onGround,
            enginesRunning = controls.enginesRunning,
            paused = controls.paused,
        )
    }

    private fun wrapAngle(a: Float): Float {
        var v = a
        while (v > FastMath.PI) v -= FastMath.TWO_PI
        while (v < -FastMath.PI) v += FastMath.TWO_PI
        return v
    }
}
