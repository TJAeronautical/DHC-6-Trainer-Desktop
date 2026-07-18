package com.dhc6trainer.desktop

/**
 * Data-driven DHC-6 instrument panel layout. Authored in the in-app editor
 * ([Dhc6PanelEditor]) and rendered by [Dhc6PanelStage] and (Phase 5) projected
 * into the 3D cockpit. Persisted as JSON in the user's writable data dir
 * ([Dhc6UserData]); a computed default is used when nothing is saved.
 *
 * Coordinates are in canvas pixels (top-left origin). Item images are resolved
 * relative to the vendored panel resource root
 * `assets/aircraft/dhc6_wheels/panel/` (e.g. "frame.png",
 * "instruments/alt_GA.png").
 */

internal const val PanelResourceRoot = "assets/aircraft/dhc6_wheels/panel"

/** Click behaviour of a placed control. */
internal enum class PanelAction { NONE, TOGGLE, CYCLE }

/**
 * What a placed item is. IMAGE draws an instrument texture; SWITCH, CB_PANEL and
 * LABEL are drawn procedurally (no X-Plane art exists for cockpit switches or
 * circuit-breaker panels, which X-Plane models as 3D geometry).
 */
internal enum class PanelItemKind { IMAGE, SWITCH, CB_PANEL, LABEL }

internal data class PanelItem(
    val id: String,
    val image: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rot: Float = 0f,
    val role: String = "",
    val action: PanelAction = PanelAction.NONE,
    /** Field of [DesktopCockpitSimState] this control drives (empty = inert). */
    val stateKey: String = "",
    val kind: PanelItemKind = PanelItemKind.IMAGE,
    /** CB_PANEL: comma-separated breaker names. LABEL: the caption text. */
    val text: String = "",
) {
    val resourcePath: String get() = "$PanelResourceRoot/$image"
    val cbBreakers: List<String> get() = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

internal data class PanelLayout(
    val canvasW: Float,
    val canvasH: Float,
    val items: List<PanelItem>,
) {
    fun withItem(item: PanelItem): PanelLayout =
        copy(items = items.map { if (it.id == item.id) item else it })

    fun addItem(item: PanelItem): PanelLayout = copy(items = items + item)

    fun removeItem(id: String): PanelLayout = copy(items = items.filterNot { it.id == id })

    fun toJson(): String = buildString {
        append("{\n")
        append("  \"canvasW\": ").append(canvasW.fmt()).append(",\n")
        append("  \"canvasH\": ").append(canvasH.fmt()).append(",\n")
        append("  \"items\": [\n")
        items.forEachIndexed { index, item ->
            append("    {")
            append("\"id\": ").append(item.id.jsonStr())
            append(", \"image\": ").append(item.image.jsonStr())
            append(", \"x\": ").append(item.x.fmt())
            append(", \"y\": ").append(item.y.fmt())
            append(", \"w\": ").append(item.w.fmt())
            append(", \"h\": ").append(item.h.fmt())
            append(", \"rot\": ").append(item.rot.fmt())
            append(", \"role\": ").append(item.role.jsonStr())
            append(", \"action\": ").append(item.action.name.jsonStr())
            append(", \"stateKey\": ").append(item.stateKey.jsonStr())
            append(", \"kind\": ").append(item.kind.name.jsonStr())
            append(", \"text\": ").append(item.text.jsonStr())
            append("}")
            if (index != items.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n")
        append("}\n")
    }

    companion object {
        fun fromJson(text: String): PanelLayout? = runCatching {
            val root = MiniJson.parse(text) as? Map<*, *> ?: return null
            val items = (root["items"] as? List<*>).orEmpty().mapNotNull { raw ->
                val m = raw as? Map<*, *> ?: return@mapNotNull null
                PanelItem(
                    id = m.str("id") ?: return@mapNotNull null,
                    image = m.str("image") ?: return@mapNotNull null,
                    x = m.num("x"),
                    y = m.num("y"),
                    w = m.num("w"),
                    h = m.num("h"),
                    rot = m.num("rot"),
                    role = m.str("role").orEmpty(),
                    action = runCatching { PanelAction.valueOf(m.str("action") ?: "NONE") }.getOrDefault(PanelAction.NONE),
                    stateKey = m.str("stateKey").orEmpty(),
                    kind = runCatching { PanelItemKind.valueOf(m.str("kind") ?: "IMAGE") }.getOrDefault(PanelItemKind.IMAGE),
                    text = m.str("text").orEmpty(),
                )
            }
            PanelLayout(
                canvasW = (root.num("canvasW")).takeIf { it > 0f } ?: 2048f,
                canvasH = (root.num("canvasH")).takeIf { it > 0f } ?: 1152f,
                items = items,
            )
        }.getOrNull()

        /**
         * Tidy non-overlapping starting layout: standard six, engine gauges,
         * nav/compass/fuel, and a row of interactive controls. The user then
         * rearranges it in the editor.
         */
        fun default(): PanelLayout {
            val items = mutableListOf<PanelItem>()
            val canvasW = 2600f
            val canvasH = 1600f

            fun inst(file: String) = "instruments/$file"

            // --- Overhead switch panel (top): electrical, fuel, start, deice ---
            items += PanelItem("lbl_ovhd", "", 40f, 20f, 2520f, 44f, role = "Overhead panel",
                kind = PanelItemKind.LABEL, text = "OVERHEAD  ·  ELECTRICAL / FUEL / START / DE-ICE")
            data class Sw(val id: String, val label: String, val key: String = "")
            val overhead = listOf(
                Sw("sw_batt", "BATTERY", "batteryMaster"), Sw("sw_dcmaster", "DC MASTER"),
                Sw("sw_lgen", "L GEN", "leftDcGenerator"), Sw("sw_rgen", "R GEN", "rightDcGenerator"),
                Sw("sw_bustie", "BUS TIE"), Sw("sw_inv", "INVERTER"),
                Sw("sw_fwdboost", "FWD BOOST", "fwdBoost1"), Sw("sw_aftboost", "AFT BOOST", "aftBoost1"),
                Sw("sw_lfuel", "L FUEL", "leftFuelLeverOn"), Sw("sw_rfuel", "R FUEL", "rightFuelLeverOn"),
                Sw("sw_lign", "L IGNITER"), Sw("sw_rign", "R IGNITER"),
                Sw("sw_lstart", "L START"), Sw("sw_rstart", "R START"),
                Sw("sw_propdeice", "PROP DEICE"), Sw("sw_intake", "INTAKE A/I"),
                Sw("sw_pitot", "PITOT HEAT"), Sw("sw_nav", "NAV LT"),
                Sw("sw_beacon", "BEACON"), Sw("sw_land", "LANDING LT"),
            )
            val swW = 232f; val swH = 118f; val swGapX = 20f; val swGapY = 16f
            val perRow = 10
            overhead.forEachIndexed { i, sw ->
                val col = i % perRow; val row = i / perRow
                val sx = 60f + col * (swW + swGapX)
                val sy = 76f + row * (swH + swGapY)
                items += PanelItem(
                    sw.id, "", sx, sy, swW, swH, role = sw.label,
                    kind = PanelItemKind.SWITCH,
                    action = if (sw.key.isBlank()) PanelAction.NONE else PanelAction.TOGGLE,
                    stateKey = sw.key,
                )
            }

            // --- Main instrument panel: standard six (big) + engine cluster ---
            items += PanelItem("lbl_main", "", 40f, 372f, 2520f, 40f, role = "Main panel",
                kind = PanelItemKind.LABEL, text = "MAIN INSTRUMENT PANEL")
            val six = listOf(
                Triple("asi", "ASI_adap_GA_dig.png", "Airspeed indicator"),
                Triple("ai", "horizon_GA_elec_flag_adj.png", "Attitude indicator"),
                Triple("alt", "alt_GA.png", "Altimeter"),
                Triple("turn", "turn.png", "Turn & slip"),
                Triple("hsi", "HSI_1_GA.png", "HSI / heading"),
                Triple("vsi", "VVI_3000_GA.png", "Vertical speed"),
            )
            val sixW = 300f; val sixGap = 28f
            val sixTotal = six.size * sixW + (six.size - 1) * sixGap
            var sx = (canvasW - sixTotal) / 2f
            six.forEach { (id, img, role) ->
                items += PanelItem(id, inst(img), sx, 430f, sixW, sixW, role = role)
                sx += sixW + sixGap
            }
            val eng = listOf(
                "trq" to "engine_TRQ.png", "itt" to "engine_ITT.png", "n1" to "engine_N1.png",
                "prop_rpm" to "engine_RPM_prop.png", "ff" to "engine_FF.png",
                "oilp" to "engine_OILP.png", "oilt" to "engine_OILT.png", "fuel_qty" to "fuel_round_GA.png",
            )
            val engW = 220f; val engGap = 20f
            val engTotal = eng.size * engW + (eng.size - 1) * engGap
            var ex = (canvasW - engTotal) / 2f
            eng.forEach { (id, img) ->
                items += PanelItem(id, inst(img), ex, 760f, engW, engW, role = "Engine ${id.uppercase()}")
                ex += engW + engGap
            }
            items += PanelItem("compass", inst("compass_GA.png"), 300f, 760f, 220f, 220f, role = "Magnetic compass")
            items += PanelItem("clock", inst("clock_GA.png"), 2080f, 760f, 220f, 220f, role = "Clock")

            // --- Interactive control row (fire/hyd/flaps drills) ---
            items += PanelItem("fire_l", inst("but_fire_extinguisher.png"), 1040f, 1010f, 150f, 150f, role = "L fire handle", action = PanelAction.TOGGLE, stateKey = "leftFireHandlePulled")
            items += PanelItem("fire_r", inst("but_fire_extinguisher.png"), 1210f, 1010f, 150f, 150f, role = "R fire handle", action = PanelAction.TOGGLE, stateKey = "rightFireHandlePulled")
            items += PanelItem("hyd", inst("but_hydraulic_pump.png"), 1400f, 1010f, 150f, 150f, role = "Hydraulic pump", action = PanelAction.TOGGLE, stateKey = "fwdBoost1")
            items += PanelItem("flaps", inst("indicate_flap_linear-1.png"), 1580f, 1010f, 120f, 160f, role = "Flap selector", action = PanelAction.CYCLE, stateKey = "flaps")

            // --- Circuit-breaker panels (left DC/engine, right avionics) ---
            items += PanelItem(
                "cb_left", "", 60f, 1200f, 1220f, 360f, role = "Left CB panel (DC / engine / fuel)",
                kind = PanelItemKind.CB_PANEL,
                text = "BATT,L GEN,R GEN,INVERTER,BUS TIE,L FUEL PUMP,R FUEL PUMP,FWD BOOST,AFT BOOST," +
                    "L START,R START,L IGN,R IGN,GEAR,FLAP MOTOR,HYD PUMP,STALL WARN,ANN LTS",
            )
            items += PanelItem(
                "cb_right", "", 1320f, 1200f, 1220f, 360f, role = "Right CB panel (avionics / deice)",
                kind = PanelItemKind.CB_PANEL,
                text = "NAV 1,NAV 2,COMM 1,COMM 2,ADF,DME,XPDR,AUDIO,AUTOPILOT,GPS," +
                    "PITOT HT,PROP DEICE,INTAKE,WSHLD HT,FIRE DET,FUEL QTY,ENG INST,PANEL LTS",
            )

            return PanelLayout(canvasW, canvasH, items)
        }
    }
}

/** One selectable image in the editor palette. */
internal data class PanelInstrumentAsset(val file: String, val category: String) {
    val resourcePath: String get() = "$PanelResourceRoot/instruments/$file"
    val displayName: String get() = file.removeSuffix(".png")
}

internal object Dhc6PanelAssets {
    /** Bindable [DesktopCockpitSimState] fields offered in the editor inspector. */
    val stateKeys: List<String> = listOf(
        "", "batteryMaster", "avionicsMaster", "leftDcGenerator", "rightDcGenerator",
        "fwdBoost1", "aftBoost1", "fwdBoost2", "aftBoost2",
        "leftFuelLeverOn", "rightFuelLeverOn", "fireDetectionArmed",
        "leftFireHandlePulled", "rightFireHandlePulled",
        "crossfeed", "leftPower", "rightPower", "flaps",
    )

    /** The vendored instrument art, read from the shipped index. */
    val instruments: List<PanelInstrumentAsset> by lazy {
        val loader = Thread.currentThread().contextClassLoader
        val text = loader?.getResourceAsStream("$PanelResourceRoot/instruments_index.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: return@lazy emptyList()
        val root = MiniJson.parse(text) as? Map<*, *> ?: return@lazy emptyList()
        (root["instruments"] as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val file = m.str("file") ?: return@mapNotNull null
            PanelInstrumentAsset(file, m.str("category").orEmpty())
        }.sortedWith(compareBy({ it.category }, { it.file }))
    }
}

/* ---- helpers ---- */

private fun Float.fmt(): String = if (this % 1f == 0f) toInt().toString() else "%.2f".format(this)

private fun String.jsonStr(): String = buildString {
    append('"')
    this@jsonStr.forEach { c ->
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}

private fun Map<*, *>.str(key: String): String? = this[key] as? String
private fun Map<*, *>.num(key: String): Float = when (val v = this[key]) {
    is Number -> v.toFloat()
    is String -> v.toFloatOrNull() ?: 0f
    else -> 0f
}

/** Minimal, dependency-free JSON reader (objects, arrays, strings, numbers, bool, null). */
private object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.parseValue()
        p.skipWs()
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun parseValue(): Any? {
            skipWs()
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBool()
                'n' -> { i += 4; null }
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (s[i] == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs(); i++ // :
                map[key] = parseValue()
                skipWs()
                when (s[i]) {
                    ',' -> { i++; continue }
                    '}' -> { i++; break }
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (s[i] == ']') { i++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (s[i]) {
                    ',' -> { i++; continue }
                    ']' -> { i++; break }
                }
            }
            return list
        }

        private fun parseString(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(s[i])
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++ // closing quote
            return sb.toString()
        }

        private fun parseBool(): Boolean =
            if (s[i] == 't') { i += 4; true } else { i += 5; false }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
