package com.example.kpkn.ui.entitlements

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.kpkn.domain.entitlements.PlanLevel

/** The application supplies confirmed access; standalone component previews preserve their full layout. */
val LocalPlan = staticCompositionLocalOf { PlanLevel.PRO }
