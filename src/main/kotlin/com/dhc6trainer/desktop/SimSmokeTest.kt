package com.dhc6trainer.desktop

/**
 * Headless smoke test of the Twin Otter systems model (no UI, no OpenGL).
 * Run with: gradle runSimSmokeTest
 *
 * Verifies the behaviors the Flight Deck Sim trains:
 *   - constant-speed governing: props back at constant power -> torque rises
 *   - flameout -> windmill, feather -> prop spools down
 *   - takeoff energy model produces a positive climb
 *   - normal vs early-fuel (hot) start ITT behavior
 */
fun main() {
    val sim = TwinOtterSimModel()
    sim.resetReadyForTakeoff()
    fun run(sec: Float) { var t = 0f; while (t < sec) { sim.update(1f / 30f); t += 1f / 30f } }

    // Climb power, props full forward.
    sim.controls.powerLever[0] = 0.75f; sim.controls.powerLever[1] = 0.75f
    sim.controls.propLever[0] = 1f; sim.controls.propLever[1] = 1f
    run(25f)
    val s1 = sim.snapshot
    println("CLIMB  props FULL : Tq=%.1f psi Np=%.0f rpm Ng=%.1f%% ITT=%.0fC FF=%.0f".format(
        s1.left.torquePsi, s1.left.propRpm, s1.left.ngPercent, s1.left.ittC, s1.left.fuelFlowPph))

    // Pull props back at SAME power - torque must RISE.
    sim.controls.propLever[0] = 0.45f; sim.controls.propLever[1] = 0.45f
    run(10f)
    val s2 = sim.snapshot
    println("CLIMB  props BACK : Tq=%.1f psi Np=%.0f rpm".format(s2.left.torquePsi, s2.left.propRpm))
    check(s2.left.propRpm < s1.left.propRpm - 100f) { "Np should drop with props back" }
    check(s2.left.torquePsi > s1.left.torquePsi + 3f) { "Torque should RISE with props back" }

    // Fail the right engine, feather it: torque to zero, prop spools down.
    sim.controls.failures.add(SimFailure.RIGHT_ENGINE_FAIL)
    run(5f)
    val s3 = sim.snapshot
    println("R FAILED unfthr  : Tq=%.1f psi Np=%.0f rpm running=%b".format(
        s3.right.torquePsi, s3.right.propRpm, s3.right.running))
    check(!s3.right.running) { "Right engine should flame out" }
    sim.controls.propLever[1] = 0f
    run(8f)
    val s4 = sim.snapshot
    println("R FAILED feather : Tq=%.1f psi Np=%.0f rpm feathered=%b".format(
        s4.right.torquePsi, s4.right.propRpm, s4.right.feathered))
    check(s4.right.feathered && s4.right.propRpm < 400f) { "Feathered prop should spool down" }

    // Takeoff: full power, rotate, expect airborne with positive VS.
    sim.resetReadyForTakeoff()
    sim.controls.brakes = false
    sim.controls.powerLever[0] = 1f; sim.controls.powerLever[1] = 1f
    run(20f)
    sim.controls.pitchCommandDeg = 8f
    run(15f)
    val s5 = sim.snapshot
    println("TAKEOFF          : IAS=%.0f kt ALT=%.0f ft VS=%.0f fpm ground=%b".format(
        s5.iasKt, s5.altitudeFt, s5.verticalSpeedFpm, s5.onGround))
    check(!s5.onGround && s5.verticalSpeedFpm > 100f) { "Should be climbing after rotation" }

    // Cold & dark battery start with early fuel = hot start warning path.
    sim.resetColdAndDark()
    sim.controls.batteryMaster = true
    sim.controls.boostPumpFwd[0] = true
    sim.controls.starter[0] = true
    var t = 0f
    while (t < 30f && !sim.snapshot.left.running) {
        if (sim.snapshot.left.ngPercent >= 13f) sim.controls.fuelLever[0] = true
        sim.update(1f / 30f); t += 1f / 30f
    }
    run(10f)
    val s6 = sim.snapshot
    println("BATTERY START    : running=%b Ng=%.1f%% ITT=%.0fC hotStart=%b".format(
        s6.left.running, s6.left.ngPercent, s6.left.ittC, s6.left.startIttExceeded))
    check(s6.left.running && !s6.left.startIttExceeded) { "Normal start should not exceed ITT" }

    // Fuel in too early (before 12% Ng) must produce a hot start.
    sim.resetColdAndDark()
    sim.controls.batteryMaster = true
    sim.controls.boostPumpFwd[1] = true
    sim.controls.fuelLever[1] = true      // fuel already on before cranking
    sim.controls.starter[1] = true
    run(20f)
    val s7 = sim.snapshot
    println("EARLY-FUEL START : running=%b hotStart=%b".format(s7.right.running, s7.right.startIttExceeded))
    check(s7.right.startIttExceeded) { "Early fuel should cause a hot start" }

    println("ALL SIM CHECKS PASSED")
}
