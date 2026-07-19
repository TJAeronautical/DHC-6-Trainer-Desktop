package com.dhc6trainer.desktop

import androidx.compose.runtime.Composable

/**
 * Step 8 entry point. The mature MCC engine already contains the approved
 * three-track launcher, PF/PM call-and-response session, TTS, timing,
 * scoring, completion state and local logbook persistence.
 */
@Composable
internal fun DedicatedMccCalloutScreen(snapshot: ProcedureLibrarySnapshot) {
    MccCalloutScreen(snapshot)
}
