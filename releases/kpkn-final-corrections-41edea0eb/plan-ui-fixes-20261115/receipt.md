# Recibo de cierre de hallazgos UI — 2026-11-15

## Estado

Correcciones verificadas en Android contra el árbol basado en `41edea0eb`:

- El perfil general del asistente muestra las tres variantes a medida en un carrusel, centra y marca la recomendación sin elegirla, conserva las razones de la variante seleccionada/recomendada, expone «Otra versión» y mantiene accesible la sección plegable del catálogo.
- La hoja de estructura usa «Ciclos», «Tus ciclos», «Nuevo ciclo», «Semanas de ciclo» y «Ciclos listos». La vista previa de plantillas también dejó de mostrar «loop» en sus textos visibles.

## JVM

Comando: `testBaseDebugUnitTest --tests *.EntrenoPlanStepTest --tests *.ProgramStructureSheetsTest` mediante `run-gradle-locked.ps1`.

- `EntrenoPlanStepTest`: 20/20, cero fallos/errores/omitidos.
- `ProgramStructureSheetsTest`: 8/8, cero fallos/errores/omitidos.
- Total de esta repetición: 28/28. No se suma al total único de la matriz sin deduplicar las suites.
- XML: `TEST-com.example.kpkn.screens.onboarding.entreno.EntrenoPlanStepTest.xml` SHA-256 `7290A7BB66A28076BDE7EE9DCA7DF665BD15822D08BA27C3158452D8912DA3B3`.
- XML: `TEST-com.example.kpkn.screens.programdetail.design.structure.ProgramStructureSheetsTest.xml` SHA-256 `A8185DF6EF5A0030A1AB25F13964FCEF0492FC5FD6640F6E1A6415633CF2BADE`.
- Log completo del Gradle: `gradle-full.log`.

## AVD

Comando: `connectedBaseDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesSheetDeviceTest`.

- Destino: únicamente `emulator-5580` (`Pixel_9_Pro_XL`, API 36), con servidor ADB aislado en puerto 15045 y `ANDROID_SERIAL=emulator-5580`.
- `ProgramCyclesSheetDeviceTest`: 1/1, cero fallos/errores/omitidos. Comprueba título, grupo, botón y secciones de semanas/plantillas visibles con un modelo sintético en `ComponentActivity`; no usa Room ni modifica datos de la app personal.
- XML de instrumentación: `avd-cycles-test.xml`, SHA-256 `8E3175C4AFA107B2B197A7A245047A9CD8CC4AFD7FACD544B220F54D7C9FE78E`.
- Logcat: `avd-cycles-logcat.txt`, SHA-256 `8232832F11FF5888C6DD6B833295CEF080D6209C5F209A2E8962DAB5C616BD68`.
- Log de Gradle: `cycles-device-test-final.log`, SHA-256 `DB3D3FA7E3124C5670EC752FD887F13A8B44959E172412A95CAC6F1B2BAA4DA7`.
- El AVD quedó en Owner 0, con 10 GB libres en `/data`; al cerrar, `com.example.kpkn` seguía instalado y `com.example.kpkn.dbg` no quedó instalado. No se accedió al teléfono ni a otro serial.

## Intentos de test conservados

Los dos primeros intentos de `ProgramCyclesSheetDeviceTest` fallaron por selectores de test: uno esperaba minúsculas aunque el diseño renderiza los encabezados en mayúsculas, y otro pedía `performScrollTo` aunque el contenido cabía en la hoja y no exponía acción de scroll. La prueba se ajustó a la semántica/visibilidad real y la ejecución final pasó 1/1. Los logs de ambos intentos están conservados junto al log final.

## Límite de aceptación

Estos resultados cierran los defectos de UI identificados en el lote dirigido, no las 13 rutas de aceptación completas. El recorrido de Setup que usa Room real sigue protegido por una guarda de QA10; el AVD autorizado estaba en Owner 0 y no se creó otro usuario para eludirla. La matriz conserva explícitamente los criterios Android pendientes.
