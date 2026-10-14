package com.example.kpkn.screens.sessioneditor.components

/** Mm:ss con cero a la izquierda, acotado a 99:59. La usa la prueba de formato del tiempo HIIT. */
internal fun formatMmSs(totalSeconds: Int): String {
    val capped = totalSeconds.coerceIn(0, 99 * 60 + 59)
    val m = capped / 60
    val s = capped % 60
    return "%02d:%02d".format(m, s)
}
