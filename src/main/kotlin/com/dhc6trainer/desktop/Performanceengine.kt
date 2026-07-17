package com.dhc6trainer.desktop

import kotlin.math.pow
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// POH Section 5 reference anchors used for this model
//   5.19  Ground roll example: +29°C, 1000 ft PA, 12 500 lb, 6 kt HW → 975 ft
//   5.3.6 To-50-ft: SL, ISA, MTOW, no wind → 1 490 ft   (ratio ≈ 1.86)
//   5.3.8 Landing from 50 ft: SL, ISA, MLW, no wind → 1 510 ft
//   5.3.5 Vs: MTOW, 0° flap → 73 KIAS; flaps 10° → 66; full flap → 56
//   5.2.2 Landing flap multipliers: 20° ×1.30, 10° ×1.80, 0° ×2.30
// CAUTION: planning purposes only - always use AFM Section 5 for operations.
// ---------------------------------------------------------------------------

internal data class TakeoffResult(
    val groundRollFt: Int,
    val distanceTo50Ft: Int,
    val vLofKt: Int,
    val v2Kt: Int,
    val disclaimer: String = "Planning only - refer to AFM Section 5 for operations.",
)

internal data class LandingResult(
    val distanceFrom50Ft: Int,
    val vRefKt: Int,
    val disclaimer: String = "Planning only - refer to AFM Section 5 for operations.",
)

internal object PerformanceEngine {

    // Back-calculated SL, ISA 15°C, 12 500 lb, no wind, paved dry ≈ 808 ft ground roll
    // (from POH example with corrections reversed: 975/(1.033 alt × 1.029 temp × 0.90 HW) ≈ 808)
    private const val BASE_GR_FT = 808.0          // ground roll, MTOW, SL, ISA, no wind
    private const val TO50_RATIO = 1.84            // 1490 / 808 ≈ 1.84
    private const val MTOW_LB = 12_500.0
    private const val MLW_LB = 12_300.0
    private const val BASE_LDG_FT = 1_510.0       // from 50 ft, MLW, SL, ISA, no wind, flaps 37.5°

    // VLOF at MTOW from POH 5.19 inset = 75 kt.  Scale with √(W/MTOW).
    private const val VLOF_MTOW_KT = 75.0

    // Vs full-flap at MLW from POH Table 5-3 = 56 kt.  Vref ≈ 1.30 × Vs.
    private const val VS_MLW_FULL_FLAP_KT = 56.0

    /**
     * Compute takeoff performance.
     * @param elevationFt   Airport pressure altitude (ft)
     * @param oatC          Outside air temperature (°C)
     * @param weightLb      Aircraft gross weight (lb)
     * @param windKt        Wind component: positive = headwind, negative = tailwind (max 10 kt tailwind per POH)
     * @param surface       Surface type string ("PAVED DRY", "WET", "GRASS", "GRAVEL", "SOFT")
     */
    fun computeTakeoff(
        elevationFt: Int,
        oatC: Int,
        weightLb: Int,
        windKt: Int,
        surface: String,
    ): TakeoffResult {
        val w = weightLb.toDouble().coerceIn(7_000.0, MTOW_LB)

        // Weight effect: approximately W^1.4 relationship (turboprop field performance)
        val weightFactor = (w / MTOW_LB).pow(1.4)

        // Pressure altitude: +3.5% per 1 000 ft (density / power loss)
        val altFactor = 1.0 + (elevationFt / 1_000.0) * 0.035

        // ISA deviation: ISA lapse rate 1.98°C / 1 000 ft
        val isaTemp = 15.0 - (elevationFt * 0.00198)
        val tempDev = (oatC - isaTemp).coerceAtLeast(0.0)
        val tempFactor = 1.0 + (tempDev / 10.0) * 0.020  // +2% per 10°C above ISA

        // Wind: POH shows ~10% reduction per ~10 kt headwind; +5% per kt tailwind (max 10 kt)
        val windFactor = when {
            windKt > 0 -> (1.0 - windKt.coerceAtMost(20) * 0.010).coerceAtLeast(0.72)
            windKt < 0 -> (1.0 + (-windKt).coerceAtMost(10) * 0.030).coerceAtMost(1.40)
            else -> 1.0
        }

        // Surface contamination
        val surfaceFactor = when {
            surface.contains("WET", ignoreCase = true) -> 1.05
            surface.contains("GRASS", ignoreCase = true) -> 1.15
            surface.contains("GRAVEL", ignoreCase = true) -> 1.12
            surface.contains("SOFT", ignoreCase = true) -> 1.30
            else -> 1.0  // paved dry
        }

        val gr = (BASE_GR_FT * weightFactor * altFactor * tempFactor * windFactor * surfaceFactor)
        val to50 = (gr * TO50_RATIO)

        // V-speeds
        val vLof = (VLOF_MTOW_KT * sqrt(w / MTOW_LB)).toInt().coerceAtLeast(60)
        val v2 = (vLof * 1.04).toInt()

        return TakeoffResult(
            groundRollFt = gr.toInt(),
            distanceTo50Ft = to50.toInt(),
            vLofKt = vLof,
            v2Kt = v2,
        )
    }

    /**
     * Compute landing performance.
     * @param elevationFt   Airport pressure altitude (ft)
     * @param oatC          Outside air temperature (°C)
     * @param weightLb      Aircraft gross weight (lb)
     * @param windKt        Wind component: positive = headwind, negative = tailwind
     * @param flaps         Flap setting string ("37.5", "20", "10", "0")
     */
    fun computeLanding(
        elevationFt: Int,
        oatC: Int,
        weightLb: Int,
        windKt: Int,
        flaps: String,
    ): LandingResult {
        val w = weightLb.toDouble().coerceIn(7_000.0, MLW_LB)

        val weightFactor = (w / MLW_LB).pow(1.4)
        val altFactor = 1.0 + (elevationFt / 1_000.0) * 0.030
        val isaTemp = 15.0 - (elevationFt * 0.00198)
        val tempDev = (oatC - isaTemp).coerceAtLeast(0.0)
        val tempFactor = 1.0 + (tempDev / 10.0) * 0.015

        val windFactor = when {
            windKt > 0 -> (1.0 - windKt.coerceAtMost(20) * 0.008).coerceAtLeast(0.75)
            windKt < 0 -> (1.0 + (-windKt).coerceAtMost(10) * 0.040).coerceAtMost(1.45)
            else -> 1.0
        }

        // POH 5.2.2 flap multipliers applied to full-flap base
        val flapFactor = when {
            flaps.contains("20") -> 1.30
            flaps.contains("10") -> 1.80
            flaps.contains("0") && !flaps.contains("37") -> 2.30
            else -> 1.0  // full flap 37.5°
        }

        val dist = (BASE_LDG_FT * weightFactor * altFactor * tempFactor * windFactor * flapFactor)

        // Vref ≈ 1.30 × Vs (full-flap), Vs scales with √(W/MLW)
        val vRef = (VS_MLW_FULL_FLAP_KT * sqrt(w / MLW_LB) * 1.30).toInt().coerceAtLeast(62)

        return LandingResult(
            distanceFrom50Ft = dist.toInt(),
            vRefKt = vRef,
        )
    }
}
