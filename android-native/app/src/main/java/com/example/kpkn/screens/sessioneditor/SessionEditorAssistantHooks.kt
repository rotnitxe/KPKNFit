package com.example.kpkn.screens.sessioneditor

/*
 * Ganchos entre la barra inferior del editor y el Asistente (Pulido v1 · EDITOR · A2).
 *
 * Decisión del lienzo: «Reglas» sale de la barra y pasa a ser la 4.ª pestaña del Asistente, y tocar los minutos de la barra abre
 * Asistente › Reglas › Tiempo. Mientras la hoja del Asistente (paquete `as`) no exponga ese destino, el gancho abre la hoja de reglas en su
 * pestaña de tiempo, que es donde vivía el anillo de minutos y desde donde se llega a las demás pestañas de reglas. Cuando el Asistente tenga
 * su 4.ª pestaña, se cambia SOLO el cuerpo de estas dos funciones y la barra no se toca.
 */

/** Los minutos de la barra: abre Asistente › Reglas › Tiempo (hoy, la hoja de reglas en la pestaña de tiempo). */
internal fun SessionEditorViewModel.openAssistantRulesTime() {
    // TODO(as): sustituir por la apertura del Asistente en su pestaña «Reglas» con la sección «Tiempo».
    openRulesSheet(initialTab = 1)
}

/** Las reglas de la sesión: Asistente › Reglas (hoy, la hoja de reglas en su primera pestaña). */
internal fun SessionEditorViewModel.openAssistantRules() {
    // TODO(as): sustituir por la apertura del Asistente en su pestaña «Reglas».
    openRulesSheet(initialTab = 0)
}
