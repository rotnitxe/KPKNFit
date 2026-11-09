# Seguimiento del importador guiado — 7 de noviembre de 2026

## Alcance vigente indicado por el usuario

Este seguimiento conserva evidencia de un recorrido anterior con más pantallas. La instrucción vigente lo simplifica: tras leer el origen, una sola revisión comprueba la identidad de los ejercicios y la distribución semanal; al guardar, se abre el `ProgramDetail` existente. Las escenas de Coincidencias, Distribución, Sesiones y Revisión separadas que aparecen más abajo son históricas y no son criterios del flujo activo. No se agrega un editor ni una copia de sesión paralelos; los cambios posteriores de semanas se hacen en `ProgramDetail` y, si hace falta, las sesiones se editan con el `SessionEditorScreen` existente.

Las capturas e instalación que este documento relata corresponden al APK anterior de `KPKN Dev`; no prueban el comportamiento de los cambios posteriores descritos en el código hasta que se repitan build, instalación y recorrido en emulador.

## Cambios de interfaz

- En Coincidencias, cada ejercicio ya resuelto permite abrir el catálogo con su nombre original y cambiar la variante elegida. Esto permite continuar al editor compartido de la sesión cuando una decisión necesita un ejercicio del catálogo.
- La selección de días distribuye los siete días en espacios iguales para que la fila quepa en 360 dp.
- Revisión usa la tarjeta de pasaporte visual del prototipo, el resumen compacto de decisiones y el estado de avisos. Si no hay avisos pendientes, no muestra una acción que llevaría a una hoja vacía.
- Se ajustó el encabezado de Estructura para mantenerlo en una línea en 360 dp.
- El menú de sesión se ajustó a unos 386 dp en el emulador y permite desplazarse si el estado incluye más acciones. En el estado actual, «Cancelar» queda visible sin desplazamiento.
- Coincidencias abre «Elegir ejercicio» como hoja inferior con sugerencias y búsqueda. Los porcentajes solo se muestran para sugerencias puntuadas por el matcher; las coincidencias de búsqueda manual se identifican como búsqueda del catálogo. La hoja puede abrir el catálogo compartido completo para explorar o crear.
- Error distingue enlaces públicos de archivos: adapta título, motivo, origen y acción, oculta la etapa de documento cuando no aplica y conserva la confirmación de que el borrador sigue intacto. Se reorganizó con icono, tarjeta de motivo y detalles compactos para acercarla a la escena local. Al salir del error, se restaura la procedencia previa antes de persistir la ubicación del asistente.
- Avisos ahora usa una hoja compacta con filas de 48 dp, detalle expandible y acciones de corrección. «Ignorar todos» pide confirmación; cancelar no modifica el borrador y la acción confirmada conserva la opción de deshacer. Al expandir una nota se oculta su subtítulo para no repetir el mismo texto.

## Build e instalación

- Variante: `baseDebug`, instalada como `KPKN Dev` (`com.example.kpkn.dbg`), versionCode 55.
- `testBaseDebugUnitTest --tests '*.ImportWarningsPanelTest'`: **BUILD SUCCESSFUL**, 17 pruebas, 0 fallos y 0 errores. Después, `assembleBaseDebug`: **BUILD SUCCESSFUL**, 42 tareas al día.
- APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`.
- SHA-256 del APK instalado en esta continuación: `26B94311C2644481C6EDF1FE9F4E6A65635858A7A318461A71CD1AF56FEFB245`.
- Firma SHA-256 del certificado: `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- Identidad verificada: `com.example.kpkn.dbg`, versionCode 55, `KPKN Pre Release 21`. El código de `KpknDatabase` está en Room 37 y registra la migración aditiva 36→37; el `AGENTS.md` compartido todavía dice v36. Esa discrepancia preexistía y este cambio no toca la base de datos ni sus migraciones.
- Instalación de reemplazo hecha con el helper compartido del emulador; package y certificado coinciden con KPKN Dev, no se borraron los datos de la app. El registro del emulador encontró 0 líneas de fallo de `com.example.kpkn*`. Tras la captura, se abrió `MainActivity` y quedó en Inicio; evidencia: `output/import-ui-parity-20261107/emulator/final-home-after-warning-sheet/main-activity-settled_04.png`.

## Recorrido comprobado

En `Pixel_9_Pro_XL` con `KPKN Dev` se reanudó el borrador sintético, se abrió Coincidencias, se cambió una decisión desde el catálogo, se entró en el editor compartido y se volvió a Sesiones y Revisión. El editor mostró tres ejercicios con sus series; el retorno conservó los datos. Revisión mostró 1 semana, 1 sesión, 3 ejercicios y 0 avisos pendientes.

En la instalación final, se abrió «Elegir ejercicio» sobre Coincidencias y se comprobó la lista, sus variantes y la confianza calculada. También se abrió el catálogo compartido completo y se volvió con Atrás. Se cerró después con «Cancelar» sin elegir una variante; el borrador siguió mostrando los mismos 3 ejercicios y la misma decisión.

El cambio de catálogo forma parte del borrador de QA y no se deshizo después de reiniciar el emulador; actualmente apunta a «Sentadilla Trasera con Barra Alta». La pantalla de Revisión confirma que el programa **todavía no se ha guardado**. No se creó ni activó un programa. El ejemplo de cuatro líneas se comprobó sin enviarlo. Las nuevas capturas de Lectura, Éxito, Vacío, Texto extraído y PDF ilegible usan exclusivamente el harness efímero; al terminar, KPKN Dev se dejó en Inicio. Captura: `output/import-ui-parity-20261107/emulator/final-restored_main-after-final-apk.png`.

Capturas recientes:

- `output/import-ui-parity-20261107/emulator/session-editor-recovered/back-from-add-exercises_back-from-add-exercises.png`
- `output/import-ui-parity-20261107/emulator/session-editor-recovered/return-from-session-editor_returned.png`
- `output/import-ui-parity-20261107/emulator/session-editor-recovered/review-after-editor_review.png`
- `output/import-ui-parity-20261107/emulator/session-editor-recovered/paste-example_example-filled.png`
- `output/import-ui-parity-20261107/emulator/final-menu-build/final-386_session-menu.png`
- `output/import-ui-parity-20261107/emulator/final-build/final_catalog-sheet-centered-cancel.png`
- `output/import-ui-parity-20261107/emulator/final-build/final_closed.png`
- `output/import-ui-parity-20261107/emulator/catalog-sheet/final_full-catalog.png`
- `output/import-ui-parity-20261107/emulator/error-visual-final/final-apk_error-page.png`
- `output/import-ui-parity-20261107/emulator/visual-harness/reading-final_02.png`
- `output/import-ui-parity-20261107/emulator/visual-harness/success-final_02.png`
- `output/import-ui-parity-20261107/emulator/visual-harness/empty-final_02.png`
- `output/import-ui-parity-20261107/emulator/visual-harness/extracted-final_02.png`
- `output/import-ui-parity-20261107/emulator/visual-harness/file-error-final_02.png`
- `output/import-ui-parity-20261107/emulator/error-source-routing/final-apk-file-error_verified.png`
- `output/import-ui-parity-20261107/emulator/error-source-routing/latest-build-file-error_verified.png`
- `output/import-ui-parity-20261107/emulator/error-source-routing/latest-build-home_home.png`
- `output/import-ocr-review-20261107/single-sample/challenge-ocr-late_completed-top.png`
- `output/import-ocr-review-20261107/single-sample/challenge-ocr-late_completed-middle.png`
- `output/import-ocr-review-20261107/single-sample/challenge-ocr-late_completed-bottom.png`
- `output/import-ocr-review-20261107/single-sample/restored-home-after-ocr_home.png`

## Cobertura de capturas nativas

Las 12 pantallas y 7 hojas tienen al menos una captura de KPKN Dev. Lectura, Éxito, Vacío, Texto extraído y PDF ilegible se capturaron con el harness en memoria descrito abajo; las demás se capturaron en el flujo habitual del importador.

- Pantallas: Entrada (`final-build/final_entry.png`), Pegado (`session-editor-recovered/paste-example_example-filled.png`), Lectura (`visual-harness/reading-final_02.png`), Resumen (`final-build/final_summary.png`), Coincidencias (`final-build/qa_matches.png`), Distribución (`review-distribution/shot_distribution.png`), Sesiones (`final-build/qa_sessions_for_editor.png`), Editor compartido (`session-editor-recovered/shot_shared-editor-after-recovery.png`), Revisión (`final-build/qa_review.png`), Éxito (`visual-harness/success-final_02.png`), Vacío (`visual-harness/empty-final_02.png`) y Error (`error-visual-final/final-apk_error-page.png`; PDF visual: `visual-harness/file-error-final_02.png`).
- Hojas: Reanudar (`session-menu-compact/resume-existing_resume-sheet.png`), Catálogo (`final-build/final_catalog-sheet-centered-cancel.png`), Estructura (`after-chip-fit/qa_structure.png`), Pro (`warnings-empty-fix/qa_pro_sheet.png`), Menú de sesión (`final-menu-build/final-386_session-menu.png`), Avisos (`final-build/qa_warnings.png`) y Texto extraído (`visual-harness/extracted-final_02.png`).

## Alcance y límites de validación

- Se cotejaron capturas locales del prototipo y la app para Entrada, Pegado, Distribución, Coincidencias, Sesiones y Revisión. La jerarquía general y los controles principales se reconocen en ambos; los datos de los borradores y la densidad de sesiones difieren, así que esas parejas no certifican paridad de píxeles.
- En Pegado, el estado inicial nativo mantiene el campo vacío, usa la muestra como placeholder, cuenta 0 líneas y deja deshabilitado «Leer el texto». Al tocar «Usar un ejemplo», muestra las 4 líneas del prototipo y habilita el CTA. Se verificó esta pareja de estados sin iniciar la lectura; se conservó el ejemplo como acción explícita para evitar importar datos de muestra por accidente.
- Estas comparaciones parciales no certifican paridad de píxeles. El conjunto contiene 12 páginas y 7 hojas; no se completó una comparación visual nativa de las 19 escenas en este seguimiento. Lectura, Éxito, Vacío, Texto extraído y PDF ilegible tienen capturas del emulador mediante `ImportProgramVisualHarnessActivity`, una actividad solo de depuración que usa modelos ficticios en memoria y no lee ni escribe Room. Éxito es una captura visual únicamente: no significa que se haya guardado un programa ni valida el recorrido de persistencia.
- Error tiene una captura real del enlace malformado y otra visual en memoria del PDF protegido. La primera validación lo rechazó localmente y la pantalla confirma «Borrador intacto»; la segunda usa el mismo `ImportErrorPage` con evidencia ficticia y no demuestra el comportamiento del lector PDF. La primera comprobación descubrió que, al salir de Error, se persistía la procedencia del enlace fallido sobre el borrador; el código final conserva el estado previo durante el fallo y lo restaura antes de guardar la nueva ubicación. En el emulador, el borrador sintético ya recibió esa atribución errónea en una pasada anterior; conserva sus tres ejercicios, pero no sirve como fixture para demostrar la restauración desde «Texto pegado». No se guardó ni activó ningún programa. El intento de capturar la transición real de Lectura con un enlace malformado volvió a Error antes de producir un fotograma; la captura dedicada de Lectura proviene del harness en memoria.
- El menú de sesión final se capturó tras abrir el editor compartido; por eso muestra Editar, Mover, la nota de acciones estructurales y Cancelar. No se comprobó en runtime en este build el estado previo al editor con Duplicar y Quitar visibles.
- La pantalla Error ahora nombra la fuente correcta y lleva al flujo correspondiente: el archivo abre su selector, la foto abre galería, el enlace vuelve al campo del enlace y el texto pegado vuelve a editarse. En el nuevo APK se comprobó visualmente el estado de archivo con el harness en memoria; el estado de texto y foto no se reprodujo en runtime.
- `assembleBaseDebug` terminó correctamente (42 tareas; 5 ejecutadas, 37 al día). En la continuación del 7 de noviembre pasó la prueba focal `testBaseDebugUnitTest --tests '*.ImportedPerRowLoadUnitTest'`: 4 casos, 0 omisiones, 0 fallos, 0 errores. El primer intento de esa tarea no llegó a ejecutar pruebas porque dos pruebas UI existentes no proporcionaban parámetros obligatorios de sus composables; se añadieron valores de fixture (`requiresPro=false`, callback vacío, etiqueta de día y callback de cierre), sin cambiar la conducta probada, y se repitió la misma tarea con éxito. El APK final de la instalación anterior es `com.example.kpkn.dbg` versión 55 (`KPKN Pre Release 21`), SHA-256 `0E01C8EAB116058B4F1C3D060A15CDF580577927BEDCC4D84D5FA2C350DBB2CD`, certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Se instaló con actualización conservando datos; el registro de fallos del emulador reportó cero líneas. Esta UI no cambia tablas Room ni la versión Room 37.
- Los límites de OCR documentados el 6 de noviembre siguen vigentes y no se volvieron a medir: muestra impresa de Repport con 99/102 ejercicios y manuscrita con cero ejercicios reconocidos. No se amplía la afirmación de compatibilidad del OCR.
- No se abrió una ventana de navegador ni se usó el teléfono físico. El intento posterior de abrir el HTML local con el conector de navegador fue rechazado por la revisión automática: podía exponer contenido privado del espacio de trabajo. No se intentó rodear ese rechazo. El render local sin ventana anterior terminó antes de producir imágenes; por eso la comparación 1:1 completa sigue pendiente.
- El prototipo agrupa «Archivo» y «Catálogo» en la pantalla Error. La app conserva el fallo de archivo en Error, pero mantiene la indisponibilidad del catálogo como aviso recuperable en Coincidencias: conserva el borrador, permite resolver ejercicios manualmente y ofrece reintentar el emparejado. No se cambió esa navegación porque mover el fallo de catálogo a Error ocultaría las decisiones y la revisión disponibles.
- No hubo commit ni push.

## Capturas a 360 dp y revisión de medidas · 7 de noviembre

- Se capturaron nuevas pantallas nativas en el emulador a 360×880 dp (PNG de 1080×2640): Inicio, Entrenamiento, Programas, menú de creación, Entrada guiada, Pegado vacío, Reanudar, Resumen, Coincidencias, Distribución, Sesiones y editor compartido. Los archivos nuevos están en `output/import-ui-parity-20261107/emulator/size-validation-360/`.
- Estas capturas corresponden a KPKN Dev (`com.example.kpkn.dbg`), versión 55 y certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`; el APK local asociado tiene SHA-256 `D3999D11973C30836B024308B98FF10676BDACB989D4C29B991463DBC94D5B99`.
- En conjunto, las 19 escenas tienen capturas nativas a 360×880 dp. Se añadieron Error (`source-aware-error-settled_03.png`), Pro (`pro-360_03.png`) y Avisos (`warnings-360-compact-confirmation-final_03.png`). Avisos se recapturó después de las correcciones con el APK actual; las otras 18 escenas no cambiaron en esta última edición. Los PNG están en `output/import-ui-parity-20261107/emulator/size-validation-360/` y miden 1080×2640 a 480 dpi.
- La comparación de código del prototipo local y Compose confirma coincidencia en las medidas base revisadas: encabezado de 56 dp, radio 30 dp, módulo con radio 24 dp y padding 16/18 dp, título 26/28 dp, cuerpo 13/20 dp y CTA de 48 dp, además de los colores base. Fue una lectura estática de HTML/CSS y Kotlin, no un render comparativo.
- La revisión del CSS de Avisos y su Compose confirma las medidas de la hoja: 20 dp de margen horizontal, título de 21 dp, resumen de 11,5 dp, filas de 48 dp, icono de 34 dp y separación de 11 dp. La captura nueva muestra las cuatro filas, las acciones y la pantalla Coincidencias detrás de la hoja. La confirmación aparece como estado secundario de la misma hoja. Esto valida el tamaño y el contenido de la escena nativa, no una igualdad de píxeles contra el prototipo.
- Una comprobación anterior dejó el emulador en Inicio después de que varios reintentos no generaran capturas. En esta continuación, `emu_run.py` obtuvo el mismo AVD con el lock compartido, actualizó KPKN Dev y produjo las tres capturas pendientes. No se abrió un segundo AVD ni se borraron datos.
- No se volvió a intentar abrir el prototipo con un navegador ni se usó otra ruta de renderizado. La verificación visual directa de las 19 escenas sigue pendiente; las capturas de la app y las medidas de código no sustituyen esa comparación.

## Seguimiento OCR impreso · 7 de noviembre

- Se volvió a procesar solo una copia temporal del PNG público de Repport y su transcripción manual. El nombre único `codex-audit-28day-20261107.png` se comprobó ausente en KPKN Dev antes de copiarlo; no se borró ni exportó ningún directorio preexistente. El directorio privado de entrada no se descargó después de que la revisión automática rechazara esa exportación.
- El resultado en KPKN Dev fue **POBRE**: 30 filas OCR, 99 ejercicios, 26 nombres distintos (11 confiables), 4 semanas y 24 días de entrenamiento. La referencia manual sigue teniendo 102 ejercicios. El detalle diario localiza las tres pérdidas: falta Push Ups en Day 9 (el OCR dejó `Sautps`), Day 22 y Day 27 (el OCR dejó `25 Push`). No se completan por repetición; el original OCR queda disponible para revisión.
- La lectura de esta compilación sí mostró `ocrHojas[0].iniciosPagina=[0]`. El JSON anterior de `report-diagnostic` no contenía `ocrHojas`; el harness actual lo muestra junto al resumen y ejercicios por día. El APK instalado conserva `com.example.kpkn.dbg`, versión 55 y certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`; el APK que se usó para esta corrida tiene SHA-256 `D3999D11973C30836B024308B98FF10676BDACB989D4C29B991463DBC94D5B99`.
- La etiqueta del arnés sigue siendo: NADA con cero ejercicios; POBRE si menos de la mitad de nombres distintos se empareja con confianza o si los omitidos superan los ejercicios; BIEN en los demás casos. Repport queda POBRE porque solo 11/26 nombres tienen coincidencia confiable. Esto es una clasificación de QA, no una promesa de compatibilidad general.
- En la continuación se corrigieron cuatro argumentos requeridos ausentes en dos fixtures de UI (`ImportRecoveryPagesTest` y `ImportSessionMenuTest`); la prueba focal de carga por fila terminó en 4/4. El XML local de Gradle registra `tests=4`, `skipped=0`, `failures=0`, `errors=0`.
- No se cambió el parser para inferir los tres ejercicios, ni se afirma que OCR soporte escritura manuscrita. La regresión sintética existente para filas OCR ambiguas conserva omisiones como avisos; no se volvió a ejecutar la suite de tests. Esta corrida fue una evaluación de una sola imagen en el emulador, sin Room, sin guardar un programa y sin borrar datos. El emulador volvió a Inicio y no registró fallos.

## Fixture visual alineado y corrección de confirmación · 7 de noviembre

- El arnés de depuración usa ahora el mismo escenario base del prototipo: «Bloque de fuerza · Julio», 3 semanas, 12 sesiones, 66 ejercicios y 2 pendientes. La lectura muestra 64/66 coincidencias; Éxito usa el mismo borrador sintético. El texto de la hoja extraída coincide con las seis líneas del ejemplo, y Vacío informa 2 hojas, 48 filas y ningún ejercicio.
- Avisos conserva el texto original «Press inclinado», muestra el motivo de la columna «%» y mantiene «Extensión de cuádriceps · 40 kg». «Ignorar todos» pide confirmación. La captura expuso y permitió corregir el contador duplicado «4 4 avisos».
- testBaseDebugUnitTest --tests com.example.kpkn.screens.programs.importer.ImportWarningsPanelTest: BUILD SUCCESSFUL, 19 pruebas, 0 fallos, 0 errores y 0 omitidas. Incluye el texto exacto del motivo, el nombre original de la fila y la frase de confirmación.
- assembleBaseDebug: BUILD SUCCESSFUL, 42 tareas (3 ejecutadas, 39 al día). APK: android-native/app/build/outputs/apk/base/debug/app-base-debug.apk; SHA-256 B09827AA28819EAB27274C6F7DA047A03D15161640B703EAFCC809D0135C420A. Paquete com.example.kpkn.dbg, versionCode 55 / KPKN Pre Release 21, certificado SHA-256 f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4, Room 37.
- KPKN Dev se actualizó con el helper compartido del emulador, con reemplazo de paquete firmado igual y sin limpiar datos. La captura final de Avisos está en output/import-ui-parity-20261107/emulator/qa-fixture-v4/warnings-confirmed-v2_01.png; la confirmación, en output/import-ui-parity-20261107/emulator/qa-fixture-v4/warnings-confirmation-v2_confirm.png. Hubo 0 líneas de fallo desde el arranque y KPKN Dev quedó en Inicio; captura asentada: output/import-ui-parity-20261107/emulator/qa-fixture-v4/home-settled_main.png. Las capturas home-restored-final_00/01 muestran el splash inicial, no Inicio.
- Las capturas antiguas de las otras escenas todavía contienen el borrador reducido; esta actualización no las presenta como una comparación 1:1. No se renderizó el HTML ni se abrió un navegador. La comprobación visual directa de las 19 escenas continúa pendiente.

## Lote final de escenas con fixture común · 7 de noviembre

- La inspección del prototipo se mantuvo en lectura estática de su HTML/CSS. Abrir un navegador no era necesario para esta tarea; no se volvió a intentar. La captura de las pantallas nativas se hizo únicamente en el emulador.
- `assembleBaseDebug` terminó con **BUILD SUCCESSFUL** en 7 min 26 s: 42 tareas, 5 ejecutadas y 37 al día. APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`; SHA-256 `9F19AB050ED76B242C43FAF49818268F22D653AE7AB41031B4A99B93A5D76504`. Identidad verificada antes de instalar: `com.example.kpkn.dbg`, versionCode 55 (`KPKN Pre Release 21`), certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- El mismo APK se instaló en KPKN Dev mediante el helper y el candado compartido del emulador. El helper usó reemplazo conservando datos; no se instaló otro paquete ni se borró la app. La pantalla Inicio arrancó y el registro de `com.example.kpkn*` reportó 0 líneas de fallo.
- El directorio `output/import-ui-parity-20261107/emulator/qa-fixture-v5-final/` contiene capturas asentadas de las 18 escenas del arnés (11 páginas y 7 hojas) y una del editor compartido; las otras dos capturas son de Inicio y solo preparan/restablecen el editor. Todas las escenas usan un fixture efímero común: «Bloque de fuerza · Julio», 3 semanas, 12 sesiones, 66 ejercicios, 2 por decidir y 4 avisos. Resumen muestra esos conteos, Coincidencias muestra 64/66 y Avisos conserva el nombre y el detalle originales.
- La captura nueva de Distribución verifica los títulos «Empuje», «Tirón», «Pierna» y «Superior» sin el prefijo redundante «Día N —». El editor es el `SessionEditorScreen` compartido y muestra seis ejercicios, 19 series y unos 50 minutos; sus datos de QA se construyen en memoria. No se pulsó Guardar ni se creó/activó un programa.
- KPKN Dev terminó de vuelta en Inicio: `output/import-ui-parity-20261107/emulator/qa-fixture-v5-final/home-final_settled.png`. El último registro de fallos volvió a mostrar 0 líneas. No se usó el teléfono físico.
- Esta compilación y las capturas validan construcción y ejecución de esas escenas nativas, pero no prueban igualdad píxel a píxel con el HTML. La comparación visual directa de las 19 escenas sigue pendiente porque el prototipo solo se inspeccionó como texto/CSS. No se volvió a ejecutar una suite; la prueba focal `ImportWarningsPanelTest` documentada arriba ya había pasado 19/19, y el cambio de etiquetas semanales se comprobó con la compilación y su captura nativa.
- No hubo commit ni push.

## Flujo reducido a coincidencias, semana y Program Detail · 7 de noviembre

La instrucción más reciente del usuario prevalece sobre el prototipo largo anterior. Después de leer el origen, el asistente abre directamente una sola pantalla con coincidencias de ejercicio y distribución semanal; se quita el resumen intermedio. El usuario solo puede guardar cuando los nombres ambiguos están resueltos y todas las sesiones tienen un día válido. «Guardar y abrir programa» crea el programa canónico una sola vez y la ruta existente de `MainActivity` abre directamente `ProgramDetail`; los ajustes posteriores de semanas quedan en ese detalle. El título también se puede cambiar en la pantalla conjunta. No se crea un editor paralelo de sesiones ni otra copia completa de `Program` en el borrador. Los borradores antiguos que apuntaban al resumen se reanudan en la pantalla conjunta.

### Verificación focal

- Una corrida focalizada final de **7 métodos** terminó en **BUILD SUCCESSFUL**, 0 fallos, 0 errores y 0 omitidas. Dos métodos de `ImportProgramScreenTest` comprueban que la lectura llega a la pantalla conjunta y que borradores antiguos la reanudan; cuatro de `ImportBoardG3Test` verifican el CTA único, cambio de día, guardado con ID para abrir el programa y ausencia de editor de sesiones en la importación; uno de `ImportedEditorDraftPersistenceTest` comprueba la ruta por defecto de borradores v1. Una corrida previa que incluyó las clases enteras agotó 600 s antes de producir XML; no se cuenta como aprobada.
- `assembleBaseDebug`: **BUILD SUCCESSFUL**. APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`; SHA-256 `C11C24BD0429D163C991DC00297C75D789475437422F9F00E949C41386C8E690`.
- Instalación verificada sobre la única app de pruebas existente: `com.example.kpkn.dbg` (KPKN Dev), versionCode 55 / `KPKN Pre Release 21`, certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. El APK instalado previamente tenía el mismo certificado; ambos usan Room 37. Se actualizó esa misma identidad sin borrar datos ni instalar otro paquete.

### Recorrido en el emulador

- Se usó el ejemplo local «Día 1 — Pierna» en KPKN Dev. `output/import-direct-handoff-20261107/shot_final-combined-review-settled.png` muestra que la lectura llega directamente a la revisión conjunta, con título editable, coincidencias y progreso de un solo paso. `output/import-direct-handoff-20261107/shot_final-weekday-moved.png` muestra la misma sesión movida de lunes a martes en el calendario de esa pantalla. «Guardar y abrir programa» queda deshabilitado porque `Sentadilla`, `Prensa` y `Peso muerto rumano` siguen ambiguos.
- No se pulsó el guardado con ese ejemplo ni se creó o activó un programa de QA. El borrador sintético se descartó desde la acción de la app. La lista mantuvo el programa existente «Base sólida» y KPKN Dev terminó en Inicio en `output/import-direct-handoff-20261107/shot_final-home-settled.png`. El helper reportó 0 líneas de fallo de `com.example.kpkn*` después de instalar y abrir el APK actualizado.
- La navegación real de un guardado con ejercicios ya confirmados no se simuló en el dispositivo porque este ejemplo no contiene identidades suficientes para hacerlo con seguridad. El test focal verifica que la acción de guardado devuelve el ID correcto, y `MainActivity` enruta ese ID a `ProgramDetail`. No se usó el teléfono físico ni un navegador.
- No se ejecutó la suite completa, no hubo commit ni push.
