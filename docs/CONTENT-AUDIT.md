# DHC-6 Trainer Desktop — Content Audit

_Audit date: July 2026. Source: full snapshot of `src/main/resources/assets/` at commit `37742d9` on `main`._

## Executive summary

Of 110 procedure files in the app, **56 (51%) are AI-generated placeholder shells with no aircraft-specific content**. The remaining 54 are curated from FCTM HBTS-001 ISS03 and POH/AFM material — real content.

The placeholder files carry a metadata disclaimer (`sourceNote`) that says as much, but the disclaimer has never reached the UI, so a user studying "Battery Overheat" or "Total Electrical Failure" sees a seven-step generic MCC template that looks authoritative but isn't.

**Immediate mitigation shipped in this same patch**: a red "PLACEHOLDER — NOT AN APPROVED PROCEDURE" banner now renders in the QRH detail view whenever the current procedure is one of the 56 flagged files. Users are no longer silently misled.

**Content authoring remains open work**. See "Next steps" at the bottom.

## Per-category breakdown

| Category  | Total | Real | Placeholder | Placeholder % |
|-----------|------:|-----:|------------:|--------------:|
| Normal    |    46 |   43 |           3 |            7% |
| Abnormal  |    38 |    3 |          35 |           92% |
| Emergency |    26 |    8 |          18 |           69% |
| **Total** |**110**| **54**|      **56** |       **51%** |

## What "placeholder" means, concretely

A placeholder file has the disclaimer:

> _"Generic MCC callout shell generated from procedure titles only. This is not an approved AFM/POH/QRH/checklist replacement."_

...in its `sourceNote` field. All 56 placeholder files contain the same 7-step template with the procedure title substituted in:

1. PM: _"<Procedure Name>."_
2. PF: _"I have control, maintain flight path."_
3. PF: _"Confirm condition and affected system."_
4. PM: _"Condition confirmed, affected system identified."_
5. PM: _"Monitor aircraft state, engine/system indications, terrain, traffic, and configuration."_
6. PM: _"<Procedure Name> checklist."_
7. PM: _"<Procedure Name> checklist complete."_

The template is duplicated verbatim across the `LEGACY` and `G950` variants, which artificially doubles the step-count numbers shown in the UI (a separate bug — see "Cross-cutting issues" below).

## Placeholder file list (needs real content)

### Emergency (18)
- `battery_overheat.json`
- `cockpit_or_cabin_smoke.json`
- `de_icing_system_failure.json`
- `ditching.json`
- `engine_flameout.json`
- `engine_shutdown_in_flight.json`
- `excessive_ice_accretion.json`
- `forced_landing.json`
- `high_speed_emergency_descent.json`
- `inadvertent_flight_in_severe_icing.json`
- `known_source_of_fire_or_smoke.json`
- `low_speed_emergency_descent.json`
- `one_engine_inoperative_missed_approach.json`
- `propeller_reversal.json`
- `stall_recovery.json`
- `suspected_electrical_fire.json`
- `total_electrical_failure.json`
- `unknown_source_of_smoke_or_fire.json`

### Abnormal (35)
- `400_cycle_light_illuminated.json`
- `aileron_trim_tab_runaway.json`
- `airspeed_miscompare_or_questionable_airspeed_indication.json`
- `bleed_air_temperature_indicates_above_350.json`
- `boost_pump_1_caution_light_illuminates.json`
- `both_generator_lights_illuminated.json`
- `clearing_an_engine.json`
- `doors_unlocked_light_illuminates.json`
- `duct_overheat_light_illuminates.json`
- `elevator_control_malfunction.json`
- `engine_overspeed_ng_exceeds_limit.json`
- `engine_overtemperature_t5_exceeds_limit.json`
- `failure_to_accelerate.json`
- `flapless_landing.json`
- `fuel_low_level_light_illuminated.json`
- `fuel_transfer_failure.json`
- `generator_light_fails_to_illuminate_following_start.json`
- `generator_overheat_light_illuminated.json`
- `gyro_instrument_power_failure.json`
- `high_t5_temperature.json`
- `intermittent_beta_light.json`
- `landing_with_a_flat_main_tire.json`
- `landing_with_a_flat_nose_wheel_tire.json`
- `landing_with_a_flat_tire.json`
- `left_or_right_400_cycle_light_illuminated.json`
- `low_oil_pressure.json`
- `low_system_hydraulic_pressure.json`
- `no_light_up_during_start.json`
- `oil_pressure_in_caution_range.json`
- `one_generator_light_illuminated.json`
- `pneumatic_low_pressure_light_illuminates.json`
- `precautionary_landing.json`
- `reset_props_light_illuminates.json`
- `steady_beta_light.json`
- `uncommanded_feathering.json`

### Normal (3)
- `cabin_emergency_lights_operation.json`
- `normal_air_start.json`
- `procedures_unique_to_series_300s_aircraft.json`

## Real content (verified sample OK)

Spot-checked several: `engine_fire_in_flight`, `engine_failure_prior_to_rotation`, `engine_failure_airborne_prior_to_vmc`, `engine_oil_pressure_light_illuminates`, `one_engine_inoperative_landing`. All contain proper aircraft-specific actions, correct references (POH/AFM sections, FCTM section IDs), and standard MCC callout language.

Real emergency procedures (8): `engine_failure_airborne_after_vmc`, `engine_failure_airborne_prior_to_vmc`, `engine_failure_during_flight`, `engine_failure_prior_to_rotation`, `engine_fire_in_flight`, `engine_fire_on_ground`, `one_engine_inoperative_landing`, `uncommanded_feathering`.

Real abnormal procedures (3): `engine_oil_pressure_light_illuminates`, `propeller_overspeed_np_exceeds_limit`, `propeller_overspeed_np_exceeds_set_rpm`.

Real normal procedures (43): full flow from `before_entering_aircraft` through `preflight_inspections`, `cockpit_preparation`, `starting_engines`, `taxi/take_off`, `climb/cruise/descent`, `approach/landing`, various tests (electrical, pneumatic, beta backup, autofeather, overspeed governor), and shutdown.

## Flashcards, systems, limitations (spot check — all real content)

- **15 flashcard decks / 177 total cards** — all curated, technically accurate on the samples I checked. E.g. hydraulic fluid MIL-H-5606 (red), hydraulic reservoir ~2/3 US gallon, PT6A operating envelope items.
- **23 system descriptions** — proper POH-derived descriptions with modification variants (Mod 6/1329, Mod 6/1470 etc.) and references.
- **11 CAS message libraries** — legacy and G950 variants for electrical, fuel, hydraulic, etc.
- **1 limitations file** — sourced from POH PSM 1-63-1A Revision 53 Section 2 plus HBFO-002 QRH G950 ISS01REV02. Airspeed and Vref tables verified against public references.

## Cross-cutting issues (not procedure content itself, but related)

1. **Step-count inflation** — `ProcedureContentLoader.parseSteps` uses a regex that matches all `stepNumber` blocks across variants (LEGACY + G950). Since the two variants usually contain identical steps, the reported `stepCount` on `ProcedureSummary` is typically 2× the real value. This is why the QRH detail rows show inflated counts like "106 items" for Engine Fire in Flight. Fix: partition matches by variant, or dedupe.

2. **Flashcard count mismatch** — Dashboard shows 340 cards; JSON files contain 177 across 15 decks. Not verified where the doubling comes from — likely a similar variant-counting bug in `FlashcardContentLoader` or its downstream consumer. Also on the roadmap.

3. **Filename vs `rawName` mismatch** — at least one file has a misleading name: `engine_failure_airborne_prior_to_vmc.json` has `rawName: "Engine Fire/Failure Airborne, After V1"`. These are different scenarios. Needs a triage pass across all procedure files.

## What's shipping in this patch (Polish 3)

- `ProcedureSummary` gains a `sourceNote: String` field + `isPlaceholder: Boolean` computed helper.
- `DedicatedQrhScreen.QrhProcedureDetail` renders a red **PLACEHOLDER** banner between the title and the step list when `procedure.isPlaceholder` is true.
- Restored: Material Icon `Star / StarBorder` for the favorite toggle (Polish 2 change that drifted back to a text `★/☆`).

## Next steps (not in this patch — needs Trevor decision)

For each placeholder file, options:

- **3a. Trevor authors from source materials** (best accuracy, requires his time)
- **3b. Claude drafts a first pass from public references + explicit assumptions** (fast; needs SME review before merge)
- **3c. External SME** (best quality, slowest, may cost money)

Recommended priority order for content authoring (emergency first, safety-critical items topmost):

**Tier 1 — safety-critical, generic templates are actively misleading:**
- `total_electrical_failure`
- `engine_flameout`
- `engine_shutdown_in_flight`
- `cockpit_or_cabin_smoke`
- `suspected_electrical_fire`
- `known_source_of_fire_or_smoke`
- `unknown_source_of_smoke_or_fire`
- `ditching`

**Tier 2 — emergency but less time-critical:**
- `stall_recovery`
- `forced_landing`
- `high_speed_emergency_descent`
- `low_speed_emergency_descent`
- `one_engine_inoperative_missed_approach`
- `propeller_reversal`
- `battery_overheat`

**Tier 3 — icing, rare emergencies:**
- `de_icing_system_failure`
- `excessive_ice_accretion`
- `inadvertent_flight_in_severe_icing`

**Tier 4 — abnormals with real CAS message triggers** (35 files). Should be authored alongside the CAS library that already exists in `assets/cas-library/`.

**Tier 5 — the 3 remaining normal procs.**
