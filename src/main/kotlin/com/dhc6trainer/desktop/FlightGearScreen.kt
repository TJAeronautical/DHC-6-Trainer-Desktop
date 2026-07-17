package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Locates and launches the native FlightGear build that lives in the FGDesktop
 * workspace (the branded FlightGear executable, started through run-flightgear-direct.bat so
 * FG_ROOT/FG_HOME/FG_AIRCRAFT and the 3rd-party DLL PATH are set up by the
 * workspace's own launch script).
 *
 * This trainer is the primary source for the aircraft: the personalized DHC-6
 * under src/main/resources/flight-sim/dhc6 is deployed into the workspace's
 * app-data\aircraft before flight, and that directory is kept DHC-6-only.
 */
internal object FlightGearInstallation {

    private const val LAUNCH_SCRIPT = "run-flightgear.bat"
    private const val DIRECT_SCRIPT = "run-flightgear-direct.bat"
    private val EXECUTABLE_NAMES = listOf(
        "DHC-6 Trainer.exe",
        "DHC-6-Traine-Desktop.exe",
        "fgfs.exe",
    )

    /** The trainer repo is the primary workspace; C:\FGDesktop is a legacy fallback. */
    private val DEFAULT_ROOTS = listOf(
        "C:\\Android Studio\\DHC-6-Trainer-Desktop",
        "C:\\FGDesktop",
    )

    /** The FGDesktop workspace root, or null when none can be found. */
    val root: File? by lazy { resolveRoot() }

    private fun resolveRoot(): File? {
        System.getProperty("dhc6.fgdesktop.root")?.let { override ->
            val dir = File(override)
            if (File(dir, LAUNCH_SCRIPT).isFile) return dir
        }
        System.getenv("DHC6_FGDESKTOP_ROOT")?.let { override ->
            val dir = File(override)
            if (File(dir, LAUNCH_SCRIPT).isFile) return dir
        }
        // Walk up from the working directory (Gradle run / packaged exe inside the workspace).
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, LAUNCH_SCRIPT).isFile) return dir
            dir = dir.parentFile
        }
        // Walk up from where this code is loaded from.
        runCatching {
            var jarDir: File? = File(
                FlightGearInstallation::class.java.protectionDomain.codeSource.location.toURI()
            ).parentFile
            while (jarDir != null) {
                if (File(jarDir, LAUNCH_SCRIPT).isFile) return jarDir
                jarDir = jarDir.parentFile
            }
        }
        // Fall back to the standard install locations.
        for (candidate in DEFAULT_ROOTS) {
            val standard = File(candidate)
            if (File(standard, LAUNCH_SCRIPT).isFile) return standard
        }
        return null
    }

    private fun simulatorExecutable(): File? =
        root?.let { workspace ->
            EXECUTABLE_NAMES
                .map { name -> File(File(workspace, "install/bin"), name) }
                .firstOrNull { it.isFile }
        }

    fun simulatorExecutableName(): String = simulatorExecutable()?.name ?: EXECUTABLE_NAMES.first()

    fun fgfsBuilt(): Boolean = simulatorExecutable() != null

    fun fgdataPresent(): Boolean = root?.let { File(it, "deps/fgdata/defaults.xml").isFile } == true

    fun readyToFly(): Boolean = fgfsBuilt() && fgdataPresent()

    /**
     * A second simulator instance switches to a read-only FG_HOME and then aborts
     * during TerraSync init, so launches must be guarded by this check.
     */
    fun simRunning(): Boolean = runCatching {
        EXECUTABLE_NAMES.any { name ->
            val process = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq $name", "/FO", "CSV", "/NH").start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.contains(name, ignoreCase = true)
        }
    }.getOrDefault(false)

    /** Force-stops every running simulator instance. Returns an error message or null. */
    fun stopSim(): String? = runCatching {
        EXECUTABLE_NAMES.forEach { name ->
            ProcessBuilder("taskkill", "/IM", name, "/F")
                .start()
                .waitFor()
        }
        null
    }.getOrElse { failure -> "Could not stop FlightGear: ${failure.message}" }

    /**
     * Starts FlightGear directly (no Qt launcher) with the given simulator
     * arguments. Returns an error message, or null when the process started.
     *
     * The Qt launcher is deliberately never used: it lists every aircraft in
     * FG_ROOT as well, while this trainer guarantees a DHC-6-only simulator.
     * All flight setup happens in this screen instead.
     *
     * The script runs through `cmd /c start` so FlightGear gets its own console
     * window with real stdout/stderr. Redirecting the streams makes SimGear
     * reject console options with a modal "Simgear Error" dialog and the sim
     * never starts.
     */
    fun launch(args: List<String>): String? {
        val workspaceRoot = root ?: return "FGDesktop workspace not found. Install it at C:\\FGDesktop or set DHC6_FGDESKTOP_ROOT."
        if (!fgfsBuilt()) return "FlightGear has not been built yet. Run BUILD_DESKTOP_WINDOWS.bat in the FGDesktop workspace first."
        if (!fgdataPresent()) return "FGData is missing. Run scripts\\bootstrap-windows.ps1 in the FGDesktop workspace, then rebuild."
        if (simRunning()) {
            return "FlightGear is already running (a second instance would start read-only and crash). " +
                "Close the sim window or use Stop sim, then Fly Now again."
        }
        val script = File(workspaceRoot, DIRECT_SCRIPT)
        if (!script.isFile) return "$DIRECT_SCRIPT is missing from the FGDesktop workspace."
        return runCatching {
            // The title must contain a space: ProcessBuilder only quotes args with
            // spaces, and `start` treats its first QUOTED arg as the window title —
            // an unquoted title would be executed as the command itself.
            val command = mutableListOf("cmd", "/c", "start", "DHC-6 Trainer", script.absolutePath)
            command += args
            ProcessBuilder(command)
                .directory(workspaceRoot)
                .start()
            null
        }.getOrElse { failure ->
            "Could not start FlightGear: ${failure.message}"
        }
    }
}

/**
 * Deploys the personalized DHC-6 aircraft that ships with this trainer into
 * the FlightGear workspace's app-data\aircraft directory, and keeps that
 * directory DHC-6-only. The trainer's vendored copy (flight-sim/dhc6, stamped
 * with a .revision file) is the single source of truth.
 */
internal object Dhc6AircraftDeployment {

    private const val AIRCRAFT_ID = "dhc6"
    private const val RESOURCE_PREFIX = "flight-sim/$AIRCRAFT_ID/"

    /** On-disk source copy of the personalized aircraft, when running from source. */
    val sourceDir: File? by lazy {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            candidates += File(dir, "src/main/resources/flight-sim/$AIRCRAFT_ID")
            candidates += File(dir, "flight-sim/$AIRCRAFT_ID")
            dir = dir.parentFile
        }
        candidates.firstOrNull { File(it, "$AIRCRAFT_ID-set.xml").isFile }
    }

    /** The jar carrying the vendored aircraft resources, for packaged runs. */
    private val sourceJar: File? by lazy {
        runCatching {
            val location = File(
                Dhc6AircraftDeployment::class.java.protectionDomain.codeSource.location.toURI()
            )
            location.takeIf { it.isFile && it.extension in listOf("jar", "zip") }
        }.getOrNull()
    }

    fun sourceAvailable(): Boolean = sourceDir != null || sourceJar != null

    fun deployedDir(): File? =
        FlightGearInstallation.root?.let { File(it, "app-data/aircraft/$AIRCRAFT_ID") }

    fun sourceRevision(): String? = when {
        sourceDir != null -> File(sourceDir, ".revision").takeIf { it.isFile }?.readText()?.trim()
        else -> runCatching {
            Dhc6AircraftDeployment::class.java.classLoader
                .getResourceAsStream("$RESOURCE_PREFIX.revision")
                ?.bufferedReader()?.use { it.readText().trim() }
        }.getOrNull()
    }

    fun deployedRevision(): String? =
        deployedDir()?.let { File(it, ".revision") }?.takeIf { it.isFile }?.readText()?.trim()

    fun syncNeeded(): Boolean {
        if (!sourceAvailable()) return false
        val deployed = deployedDir() ?: return false
        if (!File(deployed, "$AIRCRAFT_ID-set.xml").isFile) return true
        return sourceRevision() != deployedRevision()
    }

    /** DHC-6 variants offered by the personalized aircraft (from the source copy when present). */
    fun variants(): List<String> {
        sourceDir?.let { dir ->
            val fromSource = dir.listFiles { f -> f.name.endsWith("-set.xml") }
                .orEmpty().map { it.name.removeSuffix("-set.xml") }
            if (fromSource.isNotEmpty()) return fromSource.filter { it.startsWith(AIRCRAFT_ID) }.sorted()
        }
        sourceJar?.let { jar ->
            runCatching {
                ZipFile(jar).use { zip ->
                    return zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith(RESOURCE_PREFIX) && it.endsWith("-set.xml") && !it.removePrefix(RESOURCE_PREFIX).contains('/') }
                        .map { it.removePrefix(RESOURCE_PREFIX).removeSuffix("-set.xml") }
                        .filter { it.startsWith(AIRCRAFT_ID) }
                        .sorted()
                        .toList()
                }
            }
        }
        val deployed = deployedDir() ?: return emptyList()
        return deployed.listFiles { f -> f.name.endsWith("-set.xml") }
            .orEmpty().map { it.name.removeSuffix("-set.xml") }
            .filter { it.startsWith(AIRCRAFT_ID) }
            .sorted()
    }

    fun variantLabel(id: String): String = when (id) {
        "dhc6" -> "Wheels"
        "dhc6F" -> "Floats"
        "dhc6S" -> "Skis"
        "dhc6jsb" -> "Wheels (JSBSim)"
        "dhc6p" -> "Wheels - Personalized"
        "dhc6pF" -> "Floats - Personalized"
        "dhc6pS" -> "Skis - Personalized"
        else -> id
    }

    /**
     * Copies the personalized aircraft into app-data\aircraft\dhc6 and removes
     * every other aircraft folder there, so the simulator only ever offers the
     * DHC-6. Runs on the caller's dispatcher; reports copied-file progress.
     * Returns an error message, or null on success.
     */
    fun sync(onProgress: (Int) -> Unit): String? {
        val aircraftRoot = deployedDir()?.parentFile
            ?: return "FGDesktop workspace not found; nothing to deploy into."
        if (!sourceAvailable()) return "Personalized DHC-6 source data not found in the trainer installation."
        aircraftRoot.mkdirs()

        val staging = File(aircraftRoot, "$AIRCRAFT_ID.staging")
        val target = File(aircraftRoot, AIRCRAFT_ID)
        return runCatching {
            if (staging.exists()) staging.deleteRecursively()

            var copied = 0
            val onDisk = sourceDir
            if (onDisk != null) {
                onDisk.walkTopDown().forEach { file ->
                    val destination = File(staging, file.relativeTo(onDisk).path)
                    if (file.isDirectory) {
                        destination.mkdirs()
                    } else {
                        destination.parentFile?.mkdirs()
                        file.copyTo(destination, overwrite = true)
                        copied++
                        if (copied % 50 == 0) onProgress(copied)
                    }
                }
            } else {
                val jar = sourceJar ?: return "Personalized DHC-6 source data not found in the trainer installation."
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence()
                        .filter { it.name.startsWith(RESOURCE_PREFIX) }
                        .forEach { entry ->
                            val destination = File(staging, entry.name.removePrefix(RESOURCE_PREFIX))
                            if (entry.isDirectory) {
                                destination.mkdirs()
                            } else {
                                destination.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { input ->
                                    destination.outputStream().use { output -> input.copyTo(output) }
                                }
                                copied++
                                if (copied % 50 == 0) onProgress(copied)
                            }
                        }
                }
            }
            if (copied == 0) return "Personalized DHC-6 source data is empty; deployment aborted."
            onProgress(copied)

            // Swap the staged copy in, then prune everything that is not the DHC-6.
            if (target.exists()) target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            aircraftRoot.listFiles()
                ?.filter { it.name != AIRCRAFT_ID }
                ?.forEach { it.deleteRecursively() }
            null
        }.getOrElse { failure ->
            "Deploying the DHC-6 failed: ${failure.message}"
        }
    }
}

private enum class SimTimeOfDay(val label: String, val arg: String?) {
    REAL("Real time", null),
    DAWN("Dawn", "dawn"),
    MORNING("Morning", "morning"),
    NOON("Noon", "noon"),
    DUSK("Dusk", "dusk"),
    NIGHT("Night", "night"),
}

private enum class SimSeason(val label: String, val arg: String?) {
    SUMMER("Summer", null),
    WINTER("Winter", "winter"),
}

private enum class SimGraphics(val label: String, val args: List<String>) {
    FAST(
        "Fast",
        listOf(
            "--disable-ai-models",
            "--disable-ai-traffic",
            "--prop:/sim/rendering/particles=false",
            "--prop:/sim/rendering/random-objects=false",
            "--prop:/sim/rendering/random-buildings=false",
            "--prop:/sim/rendering/random-vegetation=false",
            "--prop:/sim/rendering/clouds3d-enable=false",
            "--prop:/sim/rendering/specular-highlight=false",
            "--prop:/sim/rendering/multi-sample-buffers=false",
            "--prop:/sim/rendering/fog=fastest",
        ),
    ),
    STANDARD("Standard", emptyList()),
}

private enum class SimWeather(val label: String, val args: List<String>) {
    LIVE("Live METAR", listOf("--enable-real-weather-fetch")),
    FAIR(
        "Fair",
        listOf(
            "--disable-real-weather-fetch",
            "--metar=XXXX 012345Z 15003KT 19SM FEW072 SCT350 21/08 Q1023",
        ),
    ),
    ROUGH(
        "Rough",
        listOf(
            "--disable-real-weather-fetch",
            "--metar=XXXX 012345Z 25018G28KT 3SM -RA BKN008 OVC015 12/11 Q0998",
        ),
    ),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlightGearScreen() {
    val root = FlightGearInstallation.root
    val fgfsBuilt = remember { FlightGearInstallation.fgfsBuilt() }
    val fgdataPresent = remember { FlightGearInstallation.fgdataPresent() }
    val variants = remember { Dhc6AircraftDeployment.variants() }
    val sourceRevision = remember { Dhc6AircraftDeployment.sourceRevision() }
    var deployedRevision by remember { mutableStateOf(Dhc6AircraftDeployment.deployedRevision()) }
    var syncNeeded by remember { mutableStateOf(Dhc6AircraftDeployment.syncNeeded()) }
    var syncRunning by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0) }

    var selectedVariant by remember {
        mutableStateOf(
            variants.firstOrNull { it == "dhc6p" } ?: variants.firstOrNull { it == "dhc6" } ?: variants.firstOrNull()
        )
    }
    var airport by remember { mutableStateOf("BIKF") }
    var runway by remember { mutableStateOf("29") }
    var timeOfDay by remember { mutableStateOf(SimTimeOfDay.REAL) }
    var season by remember { mutableStateOf(SimSeason.SUMMER) }
    var weather by remember { mutableStateOf(SimWeather.LIVE) }
    var graphics by remember { mutableStateOf(SimGraphics.FAST) }

    var launchMessage by remember { mutableStateOf<String?>(null) }
    var launchIsError by remember { mutableStateOf(false) }
    var simRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(launchMessage) {
        if (launchMessage != null && !launchIsError) {
            delay(8_000)
            launchMessage = null
        }
    }

    // Keep the running-state pill and the Fly Now guard fresh.
    LaunchedEffect(Unit) {
        while (true) {
            simRunning = withContext(Dispatchers.IO) { FlightGearInstallation.simRunning() }
            delay(5_000)
        }
    }

    fun buildLaunchArgs(): List<String> {
        val args = mutableListOf<String>()
        selectedVariant?.let { args += "--aircraft=$it" }
        val icao = airport.trim().uppercase()
        if (icao.isNotEmpty()) {
            args += "--airport=$icao"
            val rwy = runway.trim().uppercase()
            if (rwy.isNotEmpty()) args += "--runway=$rwy"
        }
        timeOfDay.arg?.let { args += "--timeofday=$it" }
        season.arg?.let { args += "--season=$it" }
        args += weather.args
        args += graphics.args
        // Show loading progress instead of a black window while scenery,
        // shaders and the aircraft initialize.
        args += "--prop:/sim/startup/splash-screen=true"
        return args
    }

    fun deployAndLaunch(launchAfterSync: Boolean) {
        scope.launch {
            if (Dhc6AircraftDeployment.syncNeeded()) {
                syncRunning = true
                syncProgress = 0
                launchIsError = false
                launchMessage = "Deploying the personalized DHC-6 into the simulator..."
                val error = withContext(Dispatchers.IO) {
                    Dhc6AircraftDeployment.sync { copied -> syncProgress = copied }
                }
                syncRunning = false
                deployedRevision = Dhc6AircraftDeployment.deployedRevision()
                syncNeeded = Dhc6AircraftDeployment.syncNeeded()
                if (error != null) {
                    launchIsError = true
                    launchMessage = error
                    return@launch
                }
                launchMessage = "Personalized DHC-6 deployed ($syncProgress files)."
            }
            if (!launchAfterSync) return@launch
            val error = withContext(Dispatchers.IO) { FlightGearInstallation.launch(buildLaunchArgs()) }
            launchIsError = error != null
            launchMessage = error ?: "FlightGear is starting ${selectedVariant ?: "the DHC-6"} - " +
                "first start can take a minute while the aircraft initializes."
            if (error == null) simRunning = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DetailCard {
            Text("Full FlightGear simulator", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "This trainer carries the personalized DHC-6 Twin Otter and deploys it into the " +
                    "native FlightGear 2024.2 build in the FGDesktop workspace. The simulator is " +
                    "DHC-6-only by design: deployment removes every other aircraft, and flights are " +
                    "always started with the variant selected below. Use the Technical Lab, " +
                    "Checklists, QRH and Drill sections for systems study while flying.",
                color = Dhc6DesktopColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(14.dp))
            ReadinessRow("FGDesktop workspace", root != null, root?.absolutePath ?: "not found - install at C:\\FGDesktop")
            ReadinessRow(
                "Simulator executable built",
                fgfsBuilt,
                if (fgfsBuilt) "install\\bin\\${FlightGearInstallation.simulatorExecutableName()}" else "run BUILD_DESKTOP_WINDOWS.bat",
            )
            ReadinessRow("FGData base package", fgdataPresent, if (fgdataPresent) "deps\\fgdata" else "run scripts\\bootstrap-windows.ps1")
            ReadinessRow(
                "Personalized DHC-6 source",
                Dhc6AircraftDeployment.sourceAvailable(),
                sourceRevision?.let { "revision $it" } ?: "flight-sim\\dhc6 not found",
            )
            ReadinessRow(
                "Deployed to simulator",
                !syncNeeded && deployedRevision != null,
                when {
                    syncRunning -> "deploying... ($syncProgress files)"
                    !syncNeeded && deployedRevision != null -> "revision $deployedRevision - up to date"
                    deployedRevision != null -> "revision $deployedRevision - update available"
                    else -> "not deployed yet - Fly Now deploys automatically"
                },
            )
            if (syncNeeded && !syncRunning) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { deployAndLaunch(launchAfterSync = false) },
                    enabled = root != null && Dhc6AircraftDeployment.sourceAvailable(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Deploy personalized DHC-6 now", fontWeight = FontWeight.Black, color = Dhc6DesktopColors.Accent)
                }
            }
        }

        if (variants.isNotEmpty()) {
            DetailCard {
                Text("Aircraft variant", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Personalized variants use the PT6A-34 engine package, procedural lights and " +
                        "Collins radios from this trainer. The simulator offers no aircraft other " +
                        "than the DHC-6.",
                    color = Dhc6DesktopColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    variants.forEach { variant ->
                        FilterChipLike(
                            label = "$variant - ${Dhc6AircraftDeployment.variantLabel(variant)}",
                            selected = variant == selectedVariant,
                            onClick = { selectedVariant = variant },
                        )
                    }
                }
            }
        }

        DetailCard {
            Text("Flight setup", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                    "Replaces the FlightGear launcher: position, time and weather are set here and " +
                    "passed straight to the simulator. Terrain sync is off by default so flights " +
                    "start from the app-local profile without network scenery.",
                color = Dhc6DesktopColors.TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    airport,
                    { airport = it },
                    label = { Text("Airport ICAO") },
                    modifier = Modifier.width(180.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    runway,
                    { runway = it },
                    label = { Text("Runway") },
                    modifier = Modifier.width(140.dp),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Time of day", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimTimeOfDay.entries.forEach { option ->
                    FilterChipLike(option.label, option == timeOfDay) { timeOfDay = option }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Season", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SimSeason.entries.forEach { option ->
                            FilterChipLike(option.label, option == season) { season = option }
                        }
                    }
                }
                Column {
                    Text("Weather", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SimWeather.entries.forEach { option ->
                            FilterChipLike(option.label, option == weather) { weather = option }
                        }
                    }
                }
                Column {
                    Text("Graphics", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SimGraphics.entries.forEach { option ->
                            FilterChipLike(option.label, option == graphics) { graphics = option }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Fast graphics disables AI traffic, particles, random buildings/vegetation and 3D " +
                    "clouds for a much higher frame rate; Standard keeps FlightGear defaults.",
                color = Dhc6DesktopColors.TextMuted,
                fontSize = 11.sp,
            )
        }

        DetailCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Launch", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Fly Now deploys the personalized DHC-6 when needed, then starts the sim " +
                            "with the setup above. Both use the app-local profile " +
                            "(app-data\\profile); the trainer stays open so checklists and QRH " +
                            "remain available while flying.",
                        color = Dhc6DesktopColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Spacer(Modifier.width(16.dp))
                if (simRunning) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { FlightGearInstallation.stopSim() }
                                launchIsError = error != null
                                launchMessage = error ?: "FlightGear stopped."
                                simRunning = withContext(Dispatchers.IO) { FlightGearInstallation.simRunning() }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Stop sim", fontWeight = FontWeight.Black, color = Dhc6DesktopColors.Red)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = { deployAndLaunch(launchAfterSync = true) },
                    enabled = FlightGearInstallation.readyToFly() && !syncRunning && !simRunning && selectedVariant != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                ) {
                    Text(if (simRunning) "Sim is running" else "Fly Now", fontWeight = FontWeight.Black)
                }
            }
            launchMessage?.let { message ->
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Dhc6DesktopColors.BorderSoft)
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    color = if (launchIsError) Dhc6DesktopColors.Red else Dhc6DesktopColors.Green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReadinessRow(label: String, ok: Boolean, detail: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (ok) Dhc6DesktopColors.GreenDark else Dhc6DesktopColors.Red,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (ok) "OK" else "!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(190.dp))
            Text(detail, color = Dhc6DesktopColors.TextMuted, fontSize = 12.sp)
        }
    }
}
