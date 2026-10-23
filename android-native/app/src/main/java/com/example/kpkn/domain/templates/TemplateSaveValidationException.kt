package com.example.kpkn.domain.templates

/** Input failures must be actionable; retrying storage cannot repair an incomplete session. */
enum class TemplateSaveValidationIssue { NAME_REQUIRED, SESSION_REQUIRED, SESSION_INCOMPLETE }

class TemplateSaveValidationException(val issue: TemplateSaveValidationIssue) :
    IllegalArgumentException(issue.name)
