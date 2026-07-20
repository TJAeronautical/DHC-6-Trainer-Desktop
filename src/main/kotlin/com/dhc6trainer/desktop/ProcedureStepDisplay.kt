package com.dhc6trainer.desktop

/**
 * The current loader flattens all aircraft variants into one step list.
 * Identical LEGACY and G950 base-AFM steps must be shown and drilled once.
 *
 * This is an interim presentation safeguard until the data model exposes
 * per-variant ordered procedure sequences directly.
 */
internal fun List<ProcedureStep>.withoutDuplicateVariantSteps(): List<ProcedureStep> =
    distinctBy { step ->
        ProcedureStepDisplayKey(
            number = step.number?.toString().orEmpty(),
            action = step.action,
            crewRole = step.crewRole.orEmpty(),
            intent = step.intent.orEmpty(),
            reference = step.reference.orEmpty(),
            requiresConfirmation = step.requiresConfirmation?.toString().orEmpty(),
        )
    }

private data class ProcedureStepDisplayKey(
    val number: String,
    val action: String,
    val crewRole: String,
    val intent: String,
    val reference: String,
    val requiresConfirmation: String,
)
