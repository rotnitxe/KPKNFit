package com.example.kpkn.ui.entitlements

/** Error copy retained for the independently implemented program activation flow. */
fun capabilityErrorText(code: String): String {
    val spanish = java.util.Locale.getDefault().language == "es"
    return if (code in setOf("advanced_program_requires_pro", "free_weekly_session_limit")) {
        if (spanish) "Free permite una semana de hasta cinco sesiones. Los programas avanzados requieren Pro."
        else "Free supports one week with up to five sessions. Advanced programs require Pro."
    } else {
        if (spanish) "Pro incluye alimentación, programas avanzados y análisis muscular y de columna. Puedes seguir consultando, exportando y borrando tu historial."
        else "Pro includes nutrition, advanced programs, and muscle and spinal analysis. You can still read, export, and delete your history."
    }
}
