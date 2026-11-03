# SUPLEMENTOS · informe de implementación (parcial)
Hecho: dominio (catálogo de 11, pila en Settings.supplementPila, tomas = NutritionLog `sup:<id>`, techo con «Registrar igual», semana, «toca ahora»), VM (pila, tomas, deshacer, alta/baja), módulo de Nutrición reescrito, hojas Ajustar / Aviso de techo / Añadir. Tests: SupplementEngineTest, NutritionQuickSaveTest (adaptado), SuplementosRenderSheetTest. Gradle: 2 corridas.
Abierto (DESV-1301): Inicio (franja y hoja rápida), detalle y detalle cafeína, alta/propio/edición/baja, notificación y recordatorios, huella de micronutrientes (vit. D, magnesio… no suman aún a nutrientes), racha de creatina sobre `sup:creatina`.
Riesgos: sin comprobar en dispositivo (gesto de 480 ms, hojas reales, deshacer).
