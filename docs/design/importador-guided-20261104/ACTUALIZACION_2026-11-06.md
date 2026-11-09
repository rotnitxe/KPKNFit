# Seguimiento del importador guiado — 6 de noviembre de 2026

Checkout observado: `C:\Users\valen\Documents\KPKNFit`, rama `master`, `HEAD=f0816f2a4`. Se conservaron los cambios previos y el resto del WIP. Room sigue en versión 37; no se modificó el esquema ni se borraron datos del dispositivo.

## Flujo Android

- `ImportProgramGuidedScreen.kt` y `ImportProgramWizardPages.kt` implementan las 12 pantallas del prototipo: entrada, pegado, lectura, resumen, coincidencias, días, sesiones, editor compartido, revisión final, éxito, vacío y error.
- `ImportWizardState.kt` declara también las siete hojas: reanudar, catálogo, estructura, Pro, menú de sesión, avisos y texto extraído.
- La lectura expone cinco etapas. El borrador y la procedencia local sobreviven a recuperación; el editor de sesiones es el editor compartido de KPKN. La revisión presenta volumen estimado y series por grupo muscular primario, y no cuenta ejercicios propios o sin grupo como si tuvieran un músculo conocido.
- La entrada explica que el OCR funciona mejor con texto impreso enfocado y que la escritura manual puede requerir corrección. No se amplía la afirmación de soporte manuscrito.
- Se corrigió el reintento del catálogo desde Revisión: el ViewModel antes descartaba la respuesta porque solo aceptaba que la pantalla activa fuera Coincidencias. El borrador y las decisiones permanecen intactos.

## Validación dirigida

Se ejecutó, mediante la cola global de Gradle:

```text
:app:testBaseDebugUnitTest
  --tests *.ImportProgramScreenTest
  --tests *.ImportProgramMatchesPageTest
  --tests *.ImportRecoveryPagesTest
  --tests *.ImportedEditorDraftPersistenceTest
  --tests *.ImportFileReaderTest
  --tests *.ExerciseNameMatcherTest
  --tests *.ImportedProgramBuilderTest
  --tests *.ProgramImportParserOcrTest
  --tests *.ImportWeekdaysTest
:app:assembleBaseDebug
```

Resultado: **158/158 tests**, sin fallos ni errores; `assembleBaseDebug` pasó. Los tests de pantalla son Robolectric/Compose. El APK de salida es `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`.

Smoke nativo previo en Android 36: en `KPKN_Corrections_API36` se verificó que el APK instalado y el compilado compartían certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`, `versionCode` 55 y el hash de esquema Room 37. `adb install -r` terminó correctamente y `MainActivity` quedó en primer plano, sin crash de la app. El AVD repitió el diálogo de ANR de System UI y Windows registró cierres de QEMU con código `0xc0000005` durante la interacción. El smoke llegó al formulario Datos básicos del onboarding y solo se escribió un alias sintético; no se alcanzó el importador. En esa corrida no se tocó el Z Flip ni el Pixel 9 Pro XL.

## Límites que siguen abiertos

- El OCR impreso de Repport sigue en 99/102 ejercicios frente a la transcripción manual, con 18 filas omitidas y 4 días vacíos. La fila/día exacta de las tres diferencias todavía no tiene una regresión hermética.
- La rutina manuscrita probada sigue dando cero ejercicios y baja confianza. La muestra es insuficiente para afirmar soporte amplio.
- El arnés contiene el diagnóstico `ocrHojas/pageStarts`, pero la corrida guardada no incluye ese campo. No se considera demostrado que el metadato llegue al parser.
- `.xls` cuenta con trabajo local para BIFF8 y pruebas sintéticas dirigidas; aún falta validar archivos reales, límites de memoria y compatibilidad en API 24. `.xlsm` sigue validado solo con un libro sintético.
- Las pruebas de URL y los libros públicos verifican casos concretos, no compatibilidad general con páginas, JS o formatos de terceros.
- Las cifras de precisión y cobertura globales siguen sin gate con corpus etiquetado. Los tests sintéticos no demuestran esos objetivos.
- En las corridas iniciales, `KPKN_Corrections_API36` llegó a Datos básicos y el primer smoke en `Pixel_9_Pro_XL` solo alcanzó el prólogo del onboarding; esos recorridos no llegaron al importador. El seguimiento final más abajo sí comprobó la entrada y Resumen en el Pixel. La comparación visual 1:1 contra el HTML sigue abierta. No se tocó el Z Flip.

## Seguimiento posterior de UI y emulador — 6 de noviembre

- Se ajustó la UI guiada al prototipo v4: barra inferior con acción secundaria y nota, prueba de entrada con métricas reales o ejemplos etiquetados, resumen semanal, confirmación de distribución, aviso de carga semanal y filtros Pendientes/Todas/Avisos en Coincidencias. El flujo conserva las 12 pantallas y las 7 hojas descritas arriba.
- Se ejecutaron de nuevo los cinco grupos afectados: `ImportProgramMatchesPageTest` (2), `ImportProgramScreenTest` (25), `ImportProSheetTest` (2), `ImportRecoveryPagesTest` (5) e `ImportWeekdaysTest` (9): **43/43, sin fallos ni errores**. En esa misma invocación se generó `assembleBaseDebug`; el APK quedó actualizado y `apksigner` lo verificó.
- APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`, paquete `com.example.kpkn.dbg`, `versionCode` 55 / `KPKN Pre Release 21`. El certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4` coincide con el instalado. La app instalada y el código actual usan Room v37; no hubo migración de esquema. Se actualizó con `adb install -r`, sin borrar datos.
- Smoke adicional en el AVD existente `Pixel_9_Pro_XL` (Android API 36): la instalación terminó correctamente, `MainActivity` siguió viva en primer plano y no apareció `FATAL EXCEPTION` ni ANR atribuible a KPKN en el tramo revisado de logcat. Se avanzó desde la bienvenida hasta el prólogo del onboarding, pero la navegación a Programas está condicionada a completar el perfil. No se inventaron edad, estatura ni peso; el importador no se alcanzó ni se validó visualmente en el dispositivo. El Pixel fue usado en esta corrida; no se interactuó con el Z Flip ni con el emulador que quedó fuera de línea.
- Sigue pendiente comparar la captura nativa del importador contra el prototipo renderizado. La limitación del navegador embebido al abrir el HTML local y el onboarding incompleto impiden afirmar paridad visual 1:1; las pruebas Robolectric/Compose y la compilación no sustituyen esa revisión visual.

## Cierre de recuperación y validación final — 6 de noviembre por la noche

- Se comprobó la recuperación tras reinstalar y relanzar el proceso: la entrada mostró `Mi programa importado` con 1 semana, 1 sesión y 3 ejercicios. La hoja `Reanudar borrador` indicó `3 ejercicios · Resumen`, y `Continuar` abrió Resumen con el nombre del programa, la estructura y las 3 decisiones pendientes. La inspección anterior había quedado en el selector de fuentes previo al asistente, no en la entrada; por eso no demostraba una pérdida del borrador.
- Esa reanudación reveló un caso real de ruta obsoleta: un borrador antiguo que guardó `Pegar texto` regresaba al formulario transitorio. Ahora, abrir `Pegar texto` con un borrador ya importado conserva la última ruta revisable; la restauración de rutas antiguas no durables regresa a Resumen o Sesiones y descarta una hoja asociada a esa ruta. `transientPasteRouteRestoresTheExistingDraftReviewPage` cubre ambos casos.
- La corrida final ejecutó `ImportProgramScreenTest`: **26/26 tests**, sin fallos, errores ni saltos, y `assembleBaseDebug` pasó. Sumado al grupo anterior de 43 tests, son **44 tests enfocados únicos** aprobados en las dos corridas finales. El conteo anterior de 42 en este informe queda corregido a 43 porque `ImportWeekdaysTest` ya tenía 9 casos.
- APK final: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`; SHA-256 `46A5B2845B362996E045BB0E99D7C1F5DA04A0911C275ED8005750CB3DE98B1C`. `apksigner` confirmó el mismo certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`; `aapt` confirmó `com.example.kpkn.dbg`, `versionCode` 55 y Room fuente 37.
- Se instaló mediante el corredor compartido con reemplazo conservando datos en `emulator-5554` (Pixel_9_Pro_XL, API 36). `MainActivity` arrancó en frío, Home apareció tras cargar y el registro de fallos de KPKN quedó vacío. No se usó el Z Flip ni se borraron datos.
- En el APK final se inspeccionaron entrada, selector de fuentes, pegado, reanudar borrador y Resumen. Los contadores muestran `1 semana`, `1 sesión` y `3 ejercicios`; los tres nombres quedan pendientes porque pueden corresponder a variantes distintas, sin asignación automática especulativa. No se guardó un programa.
- **No se declara paridad visual 1:1**: se inspeccionaron pantallas nativas, pero no se obtuvo una captura renderizada del HTML para compararlas lado a lado, y este recorrido no verificó cada una de las 12 pantallas y 7 hojas en dispositivo. El render comparativo del prototipo v4 sigue siendo el único gate visual pendiente. No hubo commit ni push.

## Reproducción OCR y auditoría del prototipo — 6 de noviembre

- Se repitió el corpus en el APK actual `com.example.kpkn.dbg` (`KPKN Dev`), conservando los datos del emulador. APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`, SHA-256 `6E219A47F8EB90502F0543279E4399F508530EB8C9FBF271A6AA50EB783855CA`; el asset `exercise_catalog_v2.json` del APK coincide con el de fuente.
- El JSON del arnés ya incluye `ocrHojas.iniciosPagina`: `[0]` para cada imagen y `[0, 25, 55, 65, 75]` para el PDF de cinco páginas. Esto confirma que el diagnóstico llega al informe de esta corrida.
- Resultados de runtime: Repport impreso **POBRE**, 99 ejercicios y 24 días frente a 102 en la transcripción; manuscrito **NADA**, cero ejercicios; PDF de Scraler **BIEN**, 31 ejercicios sin transcripción de referencia. `BIEN` aquí solo describe el umbral local, no exactitud demostrada.
- Las tres diferencias del impreso quedan identificadas: `Sautps` en Día 9 y `25 Push` en Días 22 y 27. El analizador las conserva en `SKIPPED_ROW`; no completa el nombre por repetición entre días. `ProgramImportParserOcrTest` añade una regresión hermética para las tres celdas y pasó **25/25**, sin fallos ni saltos. Se conserva la discrepancia 99/102 para revisión humana, sin adivinar los nombres truncados.
- El contador de coincidencias confiables difiere en una entre entornos: runtime Android **11/26** y el reporte JVM **12/26**; ambos dan **POBRE** y 99 ejercicios. La diferencia queda limitada a la confianza de `Squats oršquat`; se toma el conteo del emulador como evidencia de runtime y esa variante sigue sin selección automática.
- El HTML declara 19 escenas: 12 páginas y 7 hojas; los enum Android contienen esas mismas cantidades y las 19 rutas se encuentran conectadas. También coinciden los tokens base del prototipo para texto, módulos, acento, estados y píldoras. Las capturas disponibles son del emulador. No se obtuvo render del HTML para comparación de píxeles, por lo que **sigue sin certificarse 1:1 visual**; la compilación y las pruebas de Compose no sustituyen ese gate.
- No se guardó una rutina del corpus ni se tocó el teléfono físico. No hubo commit ni push.

## Captura nativa adicional — 6 de noviembre

- En `Pixel_9_Pro_XL`, KPKN Dev volvió a mostrar el Resumen con la ventana temporal de **360 × 880 dp** (`1080 × 2640` a 480 dpi). El CTA **«Revisar coincidencias» aparece completo**; no se requiere un ajuste por truncamiento. El tamaño de pantalla se restauró al terminar la captura.
- La selección de catálogo que se introdujo durante la inspección se deshizo: el borrador de QA quedó nuevamente con **3 pendientes y 0 decididos**. Se abrió la hoja Estructura y se cerró sin editar; no se guardó el programa.
- Evidencia local: `output/import-ui-parity-20261106/continuation-summary_360x880.png` y `output/import-ui-parity-20261106/continuation-sheet_structure.png`.
- Esta comprobación mejora la cobertura nativa, pero no compara píxeles con el prototipo renderizado. La certificación visual 1:1 sigue pendiente.

## Fila de sugerencias y reanudación en emulador — 6 de noviembre

- La fila de sugerencias en Coincidencias ahora usa tipografía compacta para nombre (12,5 sp), equipo (11 sp) y porcentaje (10 sp), en línea con los tamaños definidos por `.cand-main` y `.cand-conf` en el prototipo. No se modificó ningún otro dato ni esquema.
- `testBaseDebugUnitTest --tests *.ImportProgramMatchesPageTest` pasó **3/3**, incluida `suggestedExerciseCandidatesStayCompact` (altura de fila ≤72 dp). La compilación de fuentes principales estaba al día; `assembleBaseDebug` ya había pasado tras el ajuste de producción.
- En `Pixel_9_Pro_XL` con `KPKN Dev`, se reanudó el borrador existente y se comprobó en pantalla la sugerencia «Sentadilla Trasera con Barra Alta» con equipo y confianza. Tras deshacer una selección temporal de QA, el estado quedó en **3 pendientes y 0 decididos**. No se guardó un programa. El registro de fallos KPKN tras el arranque quedó vacío.
- Evidencia: `output/import-ui-parity-20261106/continuation-after-style-test_suggestions-loaded.png` y `output/import-ui-parity-20261106/continuation-after-style-test_undone.png`.
- El código y el emulador confirman la jerarquía y compacidad de esta fila. No se abrió navegador ni se hizo comparación renderizada de píxeles; la certificación visual 1:1 del conjunto sigue pendiente.
