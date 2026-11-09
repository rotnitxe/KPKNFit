package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import com.example.kpkn.data.programs.CatalogEntry

/** The catalog page, onboarding carousel, activated program, and program detail share one identity for an entry. */
internal fun catalogCoverIdentityFor(entry: CatalogEntry): CoverIdentity = coverIdentityFor(entry.id, null)
