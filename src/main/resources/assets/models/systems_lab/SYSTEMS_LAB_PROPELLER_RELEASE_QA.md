# DHC-6 Trainer v1.2.9 Systems Lab Propeller Release QA

## Summary

- Base package: `DHC-6-Trainer-PlayStore-v1.2.9.zip`.
- App version remains `1.2.9` / versionCode `44`.
- User-supplied `propeller.glb` fused into `hartzell_propeller.glb`.
- `constant_speed_propeller.glb` replaced with mobile optimized no-Draco release asset.
- `core-res/unused_assets/models/systems_lab/propeller.glb` removed to avoid stale duplicate source
  asset.
- `scripts/validate_systems_lab_models.py` passed for the procedural training GLBs.

## GLB asset sizes

| File                               |  Size KB | Nodes | Materials | Animations | Under 15,000 KB |
|------------------------------------|---------:|------:|----------:|-----------:|-----------------|
| `beta_backup.glb`                  |  7319.82 |     2 |         1 |          0 | PASS            |
| `bleed_valve.glb`                  |  7641.88 |     2 |         1 |          0 | PASS            |
| `constant_speed_propeller.glb`     | 11343.94 |    45 |         3 |          0 | PASS            |
| `electrical_system_training.glb`   |   241.56 |    31 |        31 |          0 | PASS            |
| `environmental_bleed_training.glb` |   154.40 |    20 |        20 |          0 | PASS            |
| `flight_controls_training.glb`     |   166.21 |    28 |        28 |          0 | PASS            |
| `fuel_system_training.glb`         |   244.81 |    29 |        29 |          0 | PASS            |
| `hartzell_propeller.glb`           |  1327.91 |    70 |        18 |          0 | PASS            |
| `hydraulic_pack_training.glb`      |  8390.90 |  2206 |         1 |          0 | PASS            |
| `main_gear_assembly.glb`           |  2859.09 |    23 |         2 |          0 | PASS            |
| `nose_gear_assembly.glb`           |  7849.26 |     2 |         1 |          0 | PASS            |
| `nosewheel_steering_training.glb`  |  5642.35 |    10 |         3 |          0 | PASS            |
| `pt6a27_cutaway.glb`               | 14584.29 |   107 |        32 |          0 | PASS            |

## Notes

- No release signing files or keystore passwords are included.
- Gradle build was not executed in this packaging pass; run the signed release command locally.
- The latest uploaded `DHC-6-Trainer.zip` in this workspace is a small desktop-module subset, not
  the Android Play Store project. The Android source release was therefore patched from the existing
  v1.2.9 Play Store source package.
