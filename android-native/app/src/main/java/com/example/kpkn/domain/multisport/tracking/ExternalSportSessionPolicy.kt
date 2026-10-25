package com.example.kpkn.domain.multisport.tracking

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionCaptureMode
import com.example.kpkn.domain.multisport.HybridSessionDurationPolicy
import com.example.kpkn.domain.multisport.HybridSessionDurationPolicyResult

/** A declared, valid external prescription may progress through the native program/history. */
object ExternalSportSessionPolicy {
    fun isRecordable(session: Session, registry: SportTrackingRegistry = NativeSportTrackingEngines.registry): Boolean =
        session.captureMode == SessionCaptureMode.EXTERNAL && session.sportDocuments.isNotEmpty() &&
            session.sportDocuments.any { it.durationContribution != SportDurationContribution.NONE.name } &&
            registry.validateAll(session.sportDocuments, setOf(SportDocumentPurpose.PLAN)).isEmpty() &&
            HybridSessionDurationPolicy.resolve(session, null, registry) is HybridSessionDurationPolicyResult.Ready
}
