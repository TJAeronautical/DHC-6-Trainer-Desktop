package com.dhc6trainer.desktop

internal data class SystemLearningContent(
    val objective: String,
    val components: List<String>,
    val operation: List<String>,
    val failures: List<String>,
    val recognition: List<String>,
    val qrhBridge: String,
)

private fun lesson(
    objective: String,
    components: String,
    operation: String,
    failures: String,
    recognition: String,
    qrh: String,
) = SystemLearningContent(
    objective = objective,
    components = components.split("|") ,
    operation = operation.split("|"),
    failures = failures.split("|"),
    recognition = recognition.split("|"),
    qrhBridge = qrh,
)

internal fun systemsLearningContent(family: String): SystemLearningContent = when (family) {
    "Engine" -> lesson(
        "Trace PT6A airflow, distinguish gas generator from free power turbine, and interpret Ng, ITT, torque and propeller RPM together.",
        "Compressor, reverse-flow combustor and compressor turbine|Free power turbine, reduction gearbox and propeller shaft|Fuel control, ignition, oil and starting systems|Torque, ITT, Ng, propeller RPM and oil indications",
        "Starter airflow accelerates the gas generator before fuel and ignition|The compressor turbine sustains Ng while the free turbine drives the propeller|The governor changes blade angle to hold selected RPM|Engine instruments are interpreted as a connected set",
        "Hot, hung or no-light-off start|Power loss or flameout|Oil, governing, feather or autofeather abnormality",
        "Cross-check torque, ITT, Ng, propeller RPM, yaw and performance|Separate engine power loss from propeller or indication faults|Monitor start acceleration and temperature continuously",
        "Use the approved engine-failure, engine-fire, abnormal-start or propeller procedure.",
    )
    "Propeller" -> lesson(
        "Understand governing, feather, beta, reverse and autofeather.",
        "Primary and overspeed governors|Beta valve and blade-angle feedback|Propeller and power lever ranges|Autofeather sensing and arming",
        "The governor changes blade angle to maintain selected RPM|Feather reduces drag after shutdown or failure|Beta and reverse provide ground blade-angle control",
        "Overspeed or underspeed|Failure to feather|Autofeather or beta malfunction",
        "Compare RPM, torque, sound, yaw and lever position|Confirm engine power before diagnosing propeller response",
        "Use the approved propeller, autofeather, engine-failure or beta/reverse procedure.",
    )
    "Fuel" -> lesson(
        "Trace fuel from tanks to each engine and recognise pressure, quantity and configuration faults.",
        "Tanks and quantity indication|Boost pumps and selectors|Crossfeed and valves|Filters, pressure sensing and engine-driven pumping",
        "Normal feed positively supplies each engine|Boost pumps provide pressure and redundancy|Crossfeed is used only under approved procedures|Quantity, pressure and flow are compared with engine response",
        "Boost-pump or pressure loss|Low quantity, imbalance or suspected leak|Incorrect selector or crossfeed configuration",
        "Check annunciations, pressure, flow, quantity trend and engine response|Treat unexplained quantity loss as a possible leak",
        "Select the approved pressure, quantity, imbalance, leak or flameout procedure matching the indications.",
    )
    "Electrical" -> lesson(
        "Understand sources, buses and protection so faults can be isolated without removing serviceable power.",
        "Battery, generators and external power|Distribution buses and contactors|Current limiters and protective devices|Load and voltage indication",
        "Battery supports start and standby supply|Generators normally carry aircraft load|Protection isolates faults|Crew monitoring confirms source and bus status",
        "Single or dual generator failure|Bus or contactor fault|Battery, external-power or limiter abnormality",
        "Identify the affected source and bus before load shedding|Cross-check annunciations, voltage/load and equipment loss",
        "Use the approved generator, bus, battery or electrical-smoke procedure and operator load-shedding sequence.",
    )
    "Hydraulics" -> lesson(
        "Relate hydraulic pressure to flaps, brakes and steering, and anticipate the operational effect of pressure loss.",
        "Hydraulic source, reservoir and lines|Pressure indication|Flap actuation and sensing|Brakes, steering and emergency provisions",
        "Pressure powers connected services|Flap selection requires movement and symmetry monitoring|Braking and steering depend on system and surface condition",
        "Pressure loss|Abnormal or asymmetric flap operation|Brake or steering degradation",
        "Compare selector position, pressure, movement and final indication|Anticipate landing-distance and directional-control effects early",
        "Use the approved hydraulic, flap, brake or steering procedure and associated performance considerations.",
    )
    "FlightControls" -> lesson(
        "Understand primary control runs, trim and control-lock hazards.",
        "Aileron, elevator and rudder cable runs|Trim and servo tabs|Cockpit controls, stops and locks",
        "Pilot input is transmitted mechanically|Trim reduces sustained control force|Full, free and correct movement is confirmed before flight",
        "Restriction, jam or abnormal force|Trim runaway or ineffective trim|Control or gust lock not fully disengaged",
        "Identify the affected axis and type of restriction|Avoid forcing a jammed control",
        "Use the approved flight-control, trim or restriction procedure while protecting airspeed and configuration.",
    )
    "Fire" -> lesson(
        "Understand detection, warning, isolation and extinguishing provisions.",
        "Detection sensors and warning circuitry|Fire handles and isolation functions|Extinguishing bottle and discharge indication",
        "Detection monitors designated zones|Isolation removes fuel, bleed air and ignition sources as installed|Agent discharge follows the checklist sequence",
        "Confirmed engine fire|Conflicting warning indications|Detection or extinguisher fault",
        "Treat a fire warning as genuine until assessed|Cross-check supporting indications without delaying memory actions",
        "Fire memory items and follow-up actions must exactly match the approved operator QRH.",
    )
    "LandingGear" -> lesson(
        "Understand fixed gear, tyres, brakes and nosewheel steering for safe ground operations.",
        "Main and nose gear|Tyres, wheels and brakes|Steering and ground-handling interfaces",
        "Directional control transitions between aerodynamic control, steering and braking|Surface condition changes stopping and handling margin",
        "Tyre or brake failure|Steering malfunction|Damage after hard landing or surface impact",
        "Monitor directional response, braking symmetry, vibration and noise|Avoid excessive speed or braking with a suspected fault",
        "Use the approved brake, steering, tyre or landing-gear inspection procedure.",
    )
    "Pneumatics" -> lesson(
        "Trace bleed-air and pneumatic services used for anti-ice and conditioning.",
        "Bleed extraction and ducting|Anti-ice valves and indications|Conditioning services as installed",
        "Bleed air is routed through controlled valves|Anti-ice use affects engine load and performance|Indications confirm expected response",
        "Bleed leak or overheat|Anti-ice supply or valve failure|Conditioning malfunction",
        "Look for warnings, odour, temperature changes, performance effects and asymmetry|In icing, loss of protection changes the safe strategy immediately",
        "Use the approved bleed-air, anti-ice or smoke/fumes procedure and leave icing when protection is inadequate.",
    )
    "Aircraft" -> lesson(
        "Compare wheel, float and ski variants and their effects on handling, performance and systems.",
        "Series 300 airframe|Wheel, float and ski configurations|Variant-specific controls and performance data",
        "Basic systems remain recognisable across variants|Variant equipment changes drag, weight, CG and technique|Only applicable AFM supplements may be used",
        "Variant-specific steering or structural abnormality|Use of incorrect performance assumptions",
        "Confirm actual aircraft configuration before using data|Treat unusual handling as a possible configuration issue",
        "Use the checklist and AFM supplement applicable to the installed configuration.",
    )
    else -> lesson(
        "Understand system architecture, normal flow and common failure consequences.",
        "Sources and controls|Distribution path and indications|Protection and crew interfaces",
        "Identify source, path, consumer and indication|Confirm normal configuration before troubleshooting",
        "Loss of source|Distribution or control failure|Incorrect indication or configuration",
        "Cross-check more than one indication|Stabilise the aircraft before troubleshooting",
        "Use the approved QRH procedure matching the actual indications and aircraft configuration.",
    )
}
