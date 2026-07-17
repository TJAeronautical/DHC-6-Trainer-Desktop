# Replacing the DHC-6-300 and DHC-6-400 Models

## Trainer-colour Wheels model

The **Wheels** selector uses this desktop-owned assembled model:

`src/main/resources/assets/models/systems_lab/aircraft_variants/dhc6_wheels_painted_training.glb`

It includes the detailed airframe, cabin and seating, plus the existing
cockpit blockout. The runtime copy is painted with the app's white, cyan,
strong-blue and deep-navy palette. Dense exterior meshes are reduced while
the cockpit pieces remain intact. This trims the GLB from about 79.2 MB to
30.7 MB and reduces it from about 1.09 million to 420,000 faces.

The detailed cockpit layout and interactive instruments are still supplied by
the app. Inside view hides the exterior model so it cannot obstruct the
pilot's view, then displays the live cockpit instruments.

Rebuild the painted runtime model with Blender 5.1 or newer:

```powershell
& "C:\Program Files\Blender Foundation\Blender 5.1\blender.exe" `
  --background `
  --python "tools\blender_repaint_dhc6_wheels.py" -- `
  "path\to\unpainted_dhc6_wheels.glb" `
  "src\main\resources\assets\models\systems_lab\aircraft_variants\dhc6_wheels_painted_training.glb" `
  "build\wheels-repaint\dhc6_wheels_trainer_preview.png"
```

## Supplied DHC-6-300 model

The **DHC-6-300 local** selector uses the desktop-owned copy of:

`assets-source/aircraft/Twin_Otter_300_WIP.skp`

Its converted runtime model is:

`src/main/resources/assets/models/systems_lab/aircraft_variants/dhc6_300_skp.glb`

This SketchUp model is an exterior-only, low-poly wheel aircraft. It has no
usable cockpit, cabin, dashboard, or instrument geometry. The app therefore
uses it only in Outside view. Inside view hides the entire shell and displays
the app's unobstructed live flight instruments.

The conversion keeps the source proportions, normalizes millimetres to metres,
preserves the per-face colors, centers the aircraft, and places its wheels on
the ground. The finished GLB is about 168 KB and 5,810 faces.

Rebuild it locally:

```powershell
python -m pip install --target "build\openskp-python" `
  openskp==0.2.0 scipy==1.16.3

$env:PYTHONPATH = (Resolve-Path "build\openskp-python").Path
python "tools\convert_skp_to_glb.py" `
  "assets-source\aircraft\Twin_Otter_300_WIP.skp" `
  "build\aircraft-convert\twin_otter_300_raw.glb"

& "C:\Program Files\Blender Foundation\Blender 5.1\blender.exe" `
  --background `
  --python "tools\blender_prepare_dhc6_300.py" -- `
  "build\aircraft-convert\twin_otter_300_raw.glb" `
  "src\main\resources\assets\models\systems_lab\aircraft_variants\dhc6_300_skp.glb"
```

## Supplied float model

The **Floats** selector uses the optimized desktop copy of
`assets-source/aircraft/dhc-6_float.blend`. Its runtime model is:

`src/main/resources/assets/models/systems_lab/aircraft_variants/dhc6_float_user.glb`

The supplied model is an exterior shell with floats. It has no cockpit cabin,
transparent windshield, dashboard, or instrument geometry. In cockpit view,
the app hides that opaque shell and shows its live IAS, pitch, bank, altitude,
vertical speed, heading, torque, and throttle instruments.

Rebuild the runtime model with Blender 5.1 or newer:

```powershell
& "C:\Program Files\Blender Foundation\Blender 5.1\blender.exe" `
  --background "assets-source\aircraft\dhc-6_float.blend" `
  --python "tools\blender_prepare_dhc6_float.py" -- `
  "src\main\resources\assets\models\systems_lab\aircraft_variants\dhc6_float_user.glb"
```

The desktop trainer reads the FSX ZIP files as local reference and aircraft
configuration data. Their compiled MDL visuals are not used because the old
format depends on animation transforms that the desktop renderer cannot
reconstruct reliably.

## Replacement folder

Place optional replacement models here:

`%USERPROFILE%\DHC-6 Trainer Desktop\aircraft-models`

Use these exact file names:

- `dhc6-300.glb`
- `dhc6-400.glb`

The app checks this folder at startup. A custom `dhc6-300.glb` takes priority
over the bundled SketchUp model. When `dhc6-400.glb` is absent, the 400 archive
variant uses the clean trainer DHC-6 airframe.

For a portable development checkout, the app also checks:

- `app-data/aircraft-models`
- `aircraft-models`

Set `DHC6_AIRCRAFT_MODELS_DIR` to use another folder.

## Blender export setup

1. Import or edit the aircraft in Blender.
2. Put the wings along the X axis and vertical direction along Y.
3. Point the nose toward negative Z.
4. Use meters and apply rotation and scale with `Ctrl+A`.
5. Keep the aircraft centered horizontally and place the lowest wheel at Y=0.
6. Use embedded or packed PBR textures.
7. Export as glTF 2.0 binary (`.glb`).

The trainer automatically scales the model to the real DHC-6 wingspan of
19.812 meters and places its lowest point on the ground.

Keep a working `.blend` file outside the runtime model folder. Replace only the
GLB and restart the app to test a revision.

## Local X-Plane Twin Otter interiors

The app can use these archives directly from `%USERPROFILE%\Downloads`:

- `DHC61 Twin Otter T_1.zip`
- `DHC63L Twin Otter LP_1.zip`
- `DHC63S Twin Otter FP_1.zip`

When present, they appear as DHC-6-100 tundra, DHC-6-300 long nose, and
DHC-6-300 float choices. Each option uses the archive's OBJ8 exterior,
variant-specific landing gear or floats, cabin, seats, glass, panel texture,
and `Twin Otter_cockpit.obj`.

These freeware archives prohibit redistribution and derivative uploads without
written permission. They remain in Downloads and are not copied into the
repository, installer, or GitHub history. The desktop app only reads them
locally at runtime.
