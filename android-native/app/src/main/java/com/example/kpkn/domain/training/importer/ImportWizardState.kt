package com.example.kpkn.domain.training.importer

import kotlinx.serialization.Serializable

/** Steps and transient sheets in the guided program importer. The current step is stored with its recoverable draft. */
@Serializable
enum class ImportWizardPage {
    ENTRY,
    PASTE,
    READING,
    SUMMARY,
    EXERCISES,
    DAYS,
    SESSIONS,
    SESSION,
    REVIEW,
    SUCCESS,
    EMPTY,
    ERROR,
}

@Serializable
enum class ImportWizardSheet {
    RESUME,
    CATALOG,
    STRUCTURE,
    PRO,
    SESSION_MENU,
    WARNINGS,
    TEXT,
}

@Serializable
enum class ImportSourceKind {
    NONE,
    FILE,
    PHOTO,
    PASTED_TEXT,
    PUBLIC_URL,
}

enum class ImportReadingStage {
    DOCUMENT,
    STRUCTURE,
    EXERCISES,
    CATALOG,
    DAYS,
}

data class ImportReadingProgress(
    val stage: ImportReadingStage = ImportReadingStage.DOCUMENT,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val weeks: Int? = null,
    val sessions: Int? = null,
    val exercises: Int? = null,
    val matchedNames: Int? = null,
)

/** Bounded, private evidence that lets the user inspect or correct what the local reader actually received. */
@Serializable
data class ImportSourceEvidence(
    val kind: ImportSourceKind = ImportSourceKind.NONE,
    val displayName: String? = null,
    val mediaType: String? = null,
    val sourceText: String? = null,
    val discardedText: String? = null,
    val discardedTextTruncated: Boolean = false,
    val sourceTextTruncated: Boolean = false,
    val sourceUrl: String? = null,
    /** Exact counts from the local reader; null means that the source did not expose that measurement. */
    val sheetCount: Int? = null,
    val rowCount: Int? = null,
    val pageCount: Int? = null,
    /** Reader classification and private id for an original photo/PDF preview; never a provider URI or user path. */
    val documentType: ImportDocumentType = ImportDocumentType.UNKNOWN,
    val sourceMediaId: String? = null,
    val sourcePreviewUnavailable: Boolean = false,
)
