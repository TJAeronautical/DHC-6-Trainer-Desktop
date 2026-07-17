# Replacing the DHC-6-300 and DHC-6-400 Models

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

The app checks this folder at startup. When a file is absent, the corresponding
archive variant uses the clean trainer DHC-6 airframe.

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
