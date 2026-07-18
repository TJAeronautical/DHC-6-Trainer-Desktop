package com.dhc6trainer.desktop

import java.io.File

/**
 * User-writable data directory for author-your-own content (panel layouts,
 * 3D cockpit control maps). Lives at `~/.dhc6trainer/`, created on demand.
 * Resources shipped in the jar are read-only, so anything the user authors in
 * the app is written here and loaded in preference to the bundled defaults.
 */
internal object Dhc6UserData {
    val dir: File by lazy {
        File(System.getProperty("user.home"), ".dhc6trainer").also { it.mkdirs() }
    }

    private val panelLayoutFile: File get() = File(dir, "panel_layout.json")
    private val cockpit3dMapFile: File get() = File(dir, "cockpit3d_map.json")

    fun readPanelLayout(): String? =
        panelLayoutFile.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    fun writePanelLayout(json: String): Boolean = runCatching {
        dir.mkdirs()
        panelLayoutFile.writeText(json)
        true
    }.getOrDefault(false)

    fun deletePanelLayout(): Boolean = runCatching { panelLayoutFile.delete() }.getOrDefault(false)

    fun readCockpit3dMap(): String? =
        cockpit3dMapFile.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    fun writeCockpit3dMap(json: String): Boolean = runCatching {
        dir.mkdirs()
        cockpit3dMapFile.writeText(json)
        true
    }.getOrDefault(false)

    /** The saved panel layout, or the computed default when none is stored. */
    fun loadPanelLayoutOrDefault(): PanelLayout =
        readPanelLayout()?.let { PanelLayout.fromJson(it) } ?: PanelLayout.default()
}

/**
 * Applies a placed control's action to the shared cockpit sim state. Maps the
 * layout item's [PanelItem.stateKey] onto the corresponding
 * [DesktopCockpitSimState] field. Returns the (possibly unchanged) new state.
 */
internal fun applyPanelAction(state: DesktopCockpitSimState, item: PanelItem): DesktopCockpitSimState {
    if (item.action == PanelAction.NONE || item.stateKey.isBlank()) return state
    return when (item.stateKey) {
        "batteryMaster" -> state.copy(batteryMaster = !state.batteryMaster)
        "avionicsMaster" -> state.copy(avionicsMaster = !state.avionicsMaster)
        "leftDcGenerator" -> state.copy(leftDcGenerator = !state.leftDcGenerator)
        "rightDcGenerator" -> state.copy(rightDcGenerator = !state.rightDcGenerator)
        "fwdBoost1" -> state.copy(fwdBoost1 = !state.fwdBoost1)
        "aftBoost1" -> state.copy(aftBoost1 = !state.aftBoost1)
        "fwdBoost2" -> state.copy(fwdBoost2 = !state.fwdBoost2)
        "aftBoost2" -> state.copy(aftBoost2 = !state.aftBoost2)
        "leftFuelLeverOn" -> state.copy(leftFuelLeverOn = !state.leftFuelLeverOn)
        "rightFuelLeverOn" -> state.copy(rightFuelLeverOn = !state.rightFuelLeverOn)
        "fireDetectionArmed" -> state.copy(fireDetectionArmed = !state.fireDetectionArmed)
        "leftFireHandlePulled" -> {
            val pulled = !state.leftFireHandlePulled
            state.copy(leftFireHandlePulled = pulled, leftFuelLeverOn = if (pulled) false else state.leftFuelLeverOn)
        }
        "rightFireHandlePulled" -> {
            val pulled = !state.rightFireHandlePulled
            state.copy(rightFireHandlePulled = pulled, rightFuelLeverOn = if (pulled) false else state.rightFuelLeverOn)
        }
        "crossfeed" -> state.copy(crossfeed = state.crossfeed.next())
        "leftPower" -> state.copy(leftPower = state.leftPower.next())
        "rightPower" -> state.copy(rightPower = state.rightPower.next())
        "flaps" -> state.copy(flaps = state.flaps.next())
        else -> state
    }
}

/** Whether this control currently reads as "on/active" (for highlight cues). */
internal fun panelStateActive(state: DesktopCockpitSimState, item: PanelItem): Boolean =
    when (item.stateKey) {
        "batteryMaster" -> state.batteryMaster
        "avionicsMaster" -> state.avionicsMaster
        "leftDcGenerator" -> state.leftDcGenerator
        "rightDcGenerator" -> state.rightDcGenerator
        "fwdBoost1" -> state.fwdBoost1
        "aftBoost1" -> state.aftBoost1
        "fwdBoost2" -> state.fwdBoost2
        "aftBoost2" -> state.aftBoost2
        "leftFuelLeverOn" -> state.leftFuelLeverOn
        "rightFuelLeverOn" -> state.rightFuelLeverOn
        "fireDetectionArmed" -> state.fireDetectionArmed
        "leftFireHandlePulled" -> state.leftFireHandlePulled
        "rightFireHandlePulled" -> state.rightFireHandlePulled
        "crossfeed" -> state.crossfeed != CockpitCrossfeedPosition.NORMAL
        "leftPower" -> state.leftPower != CockpitPowerLeverPosition.IDLE
        "rightPower" -> state.rightPower != CockpitPowerLeverPosition.IDLE
        "flaps" -> state.flaps != CockpitFlapSetting.UP
        else -> false
    }

/** Human-readable current value of a control, for the inspector/readout. */
internal fun panelStateLabel(state: DesktopCockpitSimState, item: PanelItem): String? =
    when (item.stateKey) {
        "" -> null
        "crossfeed" -> state.crossfeed.label
        "leftPower" -> state.leftPower.label
        "rightPower" -> state.rightPower.label
        "flaps" -> state.flaps.label
        else -> if (panelStateActive(state, item)) "ON" else "OFF"
    }
