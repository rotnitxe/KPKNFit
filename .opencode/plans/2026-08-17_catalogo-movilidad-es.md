---
flags: []
---

# Plan ejecutable: catálogo de movilidad en español

Estado: implementado en Android nativo el 2026-08-17.

## Rutas

- Catálogo editorial: `android-native/app/src/main/java/com/example/kpkn/data/models/MobilityExerciseCatalog.kt`
- Selector del editor: `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/TemplatesAndQuickActionSheets.kt`
- Recomendaciones durante el entrenamiento: `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/WorkoutMobilityOverlay.kt`
- Pruebas unitarias: `android-native/app/src/test/java/com/example/kpkn/data/models/MobilityExerciseCatalogTest.kt`

## Impacto

- Sustituir el catálogo limitado por una suite editorial de 162 movimientos, con cobertura de cuello, hombro/escápula, codo, antebrazo, muñeca/mano, columna, pelvis, cadera, aductores, cadena posterior, rodilla, tobillo, Aquiles y pie.
- Mantener los identificadores históricos para no romper sesiones serializadas ni recomendaciones existentes.
- Ampliar cada ficha con articulaciones, musculatura objetivo, objetivo, instrucciones, material, nivel, precauciones y alias de búsqueda; el texto visible queda en español y los alias conocidos solo ayudan a encontrar el movimiento.
- Añadir búsqueda sin sensibilidad a tildes, filtros anatómicos traducidos y ficha expandible en el selector; mostrar el objetivo de cada recomendación en el entrenamiento.
- No requiere tablas nuevas ni migración Room: `MobilitySeries` continúa embebida en la sesión serializada.

## Pruebas

- `MobilityExerciseCatalogTest`: cobertura anatómica, IDs únicos, campos editoriales, vínculos a molestias, búsqueda con/sin tildes y alias.
- Regresiones: `SessionSerializationTest`, `WorkoutAuditRegressionFixesTest` y `WorkoutStepRulesTest`.
- `assembleBaseDebug` con `BUILD SUCCESSFUL`.
- Instalación y relanzamiento en `emulator-5554`; inspección del selector, búsqueda `dorsiflexion` y árbol UI; sin marcadores de crash en logcat.

## Riesgos

- El catálogo es orientación de movilidad y no diagnostica ni sustituye una evaluación clínica; la advertencia queda visible en el selector.
- La validación física en un teléfono real, además de voz, GPS o Bluetooth, queda fuera de esta entrega; solo se verificó el flujo visual y funcional en el emulador.
- Los alias en inglés se conservan únicamente para búsquedas familiares; no cambian los nombres ni descripciones visibles en español.
