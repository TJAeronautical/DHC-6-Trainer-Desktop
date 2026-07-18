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
) {
    val resourcePath: String get() = "$PanelResourceRoot/$image"
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
            val canvasW = 2048f
            val canvasH = 1152f

            fun inst(file: String) = "instruments/$file"

            // Standard six across the top, 6 x 236px, centred.
            val six = listOf(
                Triple("asi", "ASI_adap_GA_dig.png", "Airspeed indicator"),
                Triple("ai", "horizon_GA_elec_flag_adj.png", "Attitude indicator"),
                Triple("alt", "alt_GA.png", "Altimeter"),
                Triple("turn", "turn.png", "Turn & slip"),
                Triple("hsi", "HSI_1_GA.png", "HSI / heading"),
                Triple("vsi", "VVI_3000_GA.png", "Vertical speed"),
            )
            val sixW = 236f
            val sixGap = 24f
            val sixTotal = six.size * sixW + (six.size - 1) * sixGap
            var sx = (canvasW - sixTotal) / 2f
            six.forEach { (id, img, role) ->
                items += PanelItem(id, inst(img), sx, 70f, sixW, sixW, role = role)
                sx += sixW + sixGap
            }

            // Engine gauges row, 7 x 168px.
            val eng = listOf(
                "trq" to "engine_TRQ.png", "itt" to "engine_ITT.png", "n1" to "engine_N1.png",
                "prop_rpm" to "engine_RPM_prop.png", "ff" to "engine_FF.png",
                "oilp" to "engine_OILP.png", "oilt" to "engine_OILT.png",
            )
            val engW = 168f
            val engGap = 18f
            val engTotal = eng.size * engW + (eng.size - 1) * engGap
            var ex = (canvasW - engTotal) / 2f
            eng.forEach { (id, img) ->
                items += PanelItem(id, inst(img), ex, 360f, engW, engW, role = "Engine ${id.uppercase()}")
                ex += engW + engGap
            }

            // Nav / compass / fuel row.
            items += PanelItem("compass", inst("compass_GA.png"), 300f, 590f, 200f, 200f, role = "Magnetic compass")
            items += PanelItem("hsi2", inst("HSI_1_GA.png"), 540f, 590f, 200f, 200f, role = "Course / OBS")
            items += PanelItem("fuel_qty", inst("fuel_round_GA.png"), 1310f, 590f, 200f, 200f, role = "Fuel quantity")
            items += PanelItem("clock", inst("clock_GA.png"), 1550f, 590f, 200f, 200f, role = "Clock")

            // Interactive controls row (drive the shared sim state).
            items += PanelItem("battery", inst("but_fuel_on_off.png"), 300f, 850f, 150f, 150f, role = "Battery master", action = PanelAction.TOGGLE, stateKey = "batteryMaster")
            items += PanelItem("fuel_l", inst("but_fuel_on_off.png"), 480f, 850f, 150f, 150f, role = "L fuel lever", action = PanelAction.TOGGLE, stateKey = "leftFuelLeverOn")
            items += PanelItem("fuel_r", inst("but_fuel_on_off.png"), 660f, 850f, 150f, 150f, role = "R fuel lever", action = PanelAction.TOGGLE, stateKey = "rightFuelLeverOn")
            items += PanelItem("fire_l", inst("but_fire_extinguisher.png"), 900f, 850f, 150f, 150f, role = "L fire handle", action = PanelAction.TOGGLE, stateKey = "leftFireHandlePulled")
            items += PanelItem("fire_r", inst("but_fire_extinguisher.png"), 1080f, 850f, 150f, 150f, role = "R fire handle", action = PanelAction.TOGGLE, stateKey = "rightFireHandlePulled")
            items += PanelItem("hyd", inst("but_hydraulic_pump.png"), 1320f, 850f, 150f, 150f, role = "Hydraulic pump", action = PanelAction.TOGGLE, stateKey = "fwdBoost1")
            items += PanelItem("brakes", inst("but_brakes.png"), 1500f, 850f, 150f, 150f, role = "Brakes", action = PanelAction.NONE)
            items += PanelItem("flaps", inst("indicate_flap_linear-1.png"), 1700f, 850f, 120f, 150f, role = "Flap selector", action = PanelAction.CYCLE, stateKey = "flaps")

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
