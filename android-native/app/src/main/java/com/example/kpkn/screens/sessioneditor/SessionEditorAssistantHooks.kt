package com.example.kpkn.screens.sessioneditor

/*
 * Ganchos entre la barra inferior del editor y el Asistente (Pulido v1 · EDITOR · A2).
 *
 * Decisión del lienzo: «Reglas» sale de la barra y pasa a ser la 4.ª pestaña del Asistente, y tocar los minutos de la barra abre
 * Asistente › Reglas › Tiempo. El Asistente (paquete `as`) ya tiene su 4.ª pestaña y estas dos funciones lo abren directamente en ella.
 */

/** Los minutos de la barra: abre Asistente › Reglas › Tiempo . */
internal fun SessionEditorViewModel.openAssistantRulesTime() {
    // Paquete `as` (pasada B/C): `openRulesSheet` ya abre el Asistente en su 4.ª pestaña, Reglas, con la sección Tiempo.
    openAssistant(AssistantEntry(tab = AssistantTab.RULES, rules = RulesSection.TIME))
}

/** Las reglas de la sesión: Asistente › Reglas . */
internal fun SessionEditorViewModel.openAssistantRules() {
    openAssistant(AssistantEntry(tab = AssistantTab.RULES, rules = RulesSection.VALUES))
}
