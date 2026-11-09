# Informe para auditoría — importador guiado

Nota del 6 de noviembre: el seguimiento de la implementación Android y la validación dirigida está en [ACTUALIZACION_2026-11-06.md](ACTUALIZACION_2026-11-06.md). Las mediciones OCR de este informe siguen siendo la evidencia disponible para esas muestras.

Fecha: 5 de noviembre de 2026.
Checkout: rama `master`, `HEAD=f0816f2a4`. El árbol de trabajo tiene muchos cambios previos; identificar autoría antes de revertir, stagear o commitear.
Estado del objetivo: **activo**. La prueba física del Z Flip es una pista pendiente; queda trabajo independiente.

## Solicitud y límites

La persona pidió reunir rutinas públicas de Internet en distintas fuentes y formatos porque no tiene muestras propias ni el Z Flip conectado. El corpus local está en `android-native/app/build/tmp/importer-public-corpus-20261104/`, ignorado por Git y eliminable por `Gradle clean`; sus fuentes, transcripciones y restricciones figuran en su `README.md`.

No se usó el Z Flip, `phone_run` ni `phone.lock`. ML Kit se ejecutó en un AVD temporal API 36 por ADB aislado en puerto 5039. Se instaló solo `baseDebug`, identidad `com.example.kpkn.dbg`, versionCode 55, minSdk 24; se verificaron identidad y firma antes de `adb install -r`. No se tocó ADB 5037 ni datos del teléfono.

## Cambios y validación

- `ProgramImportParser.kt`: matriz OCR con días por columnas; infiere una cabecera `DAY` faltante en secuencia numerada, agrupa días por semana y conserva repeticiones sin inventar series. Une continuación OCR como `Jumps` a `Squats or Squat`.
- `ProgramImportParserOcrTest.kt`: regresión sintética de dos semanas; la suite pasó 24/24 en una corrida previa.
- `ImportProgramScreenTest.kt`: prueba URL → XLSX sintético → revisión de procedencia → recreación del ViewModel → guardado. Caso dirigido 1/1 y clase completa 18/18.
- `ImportOcrHarnessActivity.kt`: se añadió diagnóstico `ocrHojas/pageStarts`. Sus strings están en el APK, pero el JSON recuperado no trae el campo; no se considera probado que el metadato llegó al parser.
- Un test JUnit transitorio leyó las 30 filas reales del arnés y verificó que el parser produce ejercicios (1/1, 0.293 s). Se retiró porque dependía de `build/tmp`; solo comprobaba resultado no vacío, no exactitud por fila.
- `assembleBaseDebug` pasó: 42 tareas, 5 ejecutadas y 37 al día.

## OCR real en Android

Informe actual: `android-native/app/build/tmp/ocr-avd-isolated-20261105-run01/report-diagnostic/ocr-report.json`.

| Fuente | Resultado | Observación |
|---|---:|---|
| Repport, reto impreso de 28 días (PNG) | POBRE | 30 filas OCR; 4 semanas, 24 días de entrenamiento, 99 ejercicios frente a 102 en transcripción manual; 26 nombres distintos, 11 coincidencias confiables; 18 filas omitidas y 4 avisos de día vacío. Los 99 quedan incompletos porque la fuente no indica series. Confianza media 0.767; 18 palabras con confianza baja. |
| Repport, rutina manuscrita inclinada (JPG) | NADA | 13 filas, 52 palabras, 43 con confianza baja, media 0.386; cero ejercicios. WER 90.86 %, CER 73.42 %. |
| Scraler, PDF de cinco páginas | BIEN | 95 filas, 1 semana, 6 días y 31 ejercicios/nombres; 21 nombres confiables. Media OCR 0.848; 24 palabras con confianza baja; 17 omitidas, 13 incompletas, 3 valores ignorados y 4 días vacíos. Sin transcript patrón: BIEN no acredita exactitud. |

El reto impreso dio WER 74.67 % y CER 61.64 %. La cuadrícula OCR se aplana por filas, mientras la referencia se ordena por día; esas métricas no equivalen a exactitud de ejercicios.

## Artefacto anterior desfasado

Una ejecución anterior tras el cambio del parser reportó NADA para el impreso. El APK guardado entonces (`.../installed-base.apk`) difiere del APK recompilado en `classes11.dex` y `classes16.dex`. Tras instalar el recompilado, el mismo corpus dio POBRE y 99 ejercicios; la instalación actual coincide con el APK de salida en todos los DEX. No usar el NADA anterior como evidencia contra el código actual.

APK actual: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`.

## Corpus público ya reunido

- Repport: [reto impreso](https://getrepport.app/downloads/case-studies/workout-plan-from-image-app/28-day-challenge.png) y [rutina manuscrita](https://getrepport.app/downloads/case-studies/workout-plan-from-handwritten-notes/handwritten-plan.jpg), con transcripciones manuales.
- Scraler: [PDF](https://www.scraler.com/blog/templates/workout-plan-template-4-day.pdf) y [XLSX](https://www.scraler.com/blog/templates/workout-plan-template-4-day.xlsx).
- GymEvo: [XLSX](https://gymevo.app/files/gymevo-training-plan-template.xlsx).
- Depot Fitness: CSV público con licencia CC BY 4.0, cinco programas y 82 prescripciones.
- Vertex42: DOCX/ODS vacíos; casos de lectura sin ejercicios, no exactitud OCR.
- URL HTML: [Tom’s Guide](https://www.tomsguide.com/wellness/workouts/im-a-personal-trainer-gain-strength-and-build-muscle-with-my-4-week-full-body-program), [GymPal](https://askgympal.co.uk/blog/how-to-build-first-gym-workout-programme-beginner-template) y GymEvo. El artículo HTML de GymEvo produjo cuatro ejercicios, bajo el umbral. Informes locales registran pruebas dirigidas aprobadas para Tom’s Guide/GymPal y la descarga XLSX GymEvo; son casos concretos, no compatibilidad general de URL.

## Pendientes independientes

1. Ubicar por día las tres omisiones del reto (99/102) y guardar una regresión hermética sin incorporar material de redistribución no autorizada.
2. Revisar las 18 filas omitidas, los cuatro días de premio/descanso, series faltantes y nombres con baja confianza.
3. El único manuscrito probado falla; recopilar más muestras legibles y etiquetadas antes de afirmar soporte amplio.
4. Resolver por qué `ocrHojas/pageStarts` no aparece en el JSON y fijar procedencia de APK, entrada, parser e informe.
5. Ampliar corpus etiquetado real para medir precisión, cobertura, omisiones e invenciones; gates sintéticos no prueban metas globales.
6. Decidir `.xls` BIFF8: no hay lector compatible con minSdk 24 y límites seguros validado; se mantiene rechazo explícito. `.xlsm` tiene prueba sintética, no pública.
7. Consolidar cobertura de weekdays explícitos, superseries al modelo, semanas REST/DELOAD/días libres, más diseños Excel/URL y recorrido UI hasta guardar.
8. Después, probar en el Z Flip mediante `phone_run` + `phone.lock`, con único dueño del dispositivo e identidad fija KPKN Dev.

## Preguntas para auditoría

- ¿Aceptar la matriz con 99/102 y derivar pérdidas a revisión, o cerrar primero las tres omisiones?
- ¿Qué umbral define BIEN/POBRE/NADA cuando hay ejercicios extraídos pero solo algunos tienen match confiable?
- ¿La escritura manuscrita es requisito de producto o se limita OCR a impresos/fotos nítidas hasta tener más evidencia?
- ¿Priorizar `.xls` API 24, HTML URL o cerrar OCR/corpus?
- ¿Cómo registrar de forma reproducible `pageStarts`, transcript, APK y resultado del parser?

Recomendación: auditar primero la discrepancia del artefacto, fijar regresión 99/102 y revisar las filas omitidas; después decidir alcance de manuscritos y `.xls`. URL, hojas, parser, revisión y guardado pueden avanzar sin esperar al Z Flip. No marcar bloqueado mientras esas vías sigan abiertas.

## Evidencia local

- `android-native/app/build/tmp/ocr-avd-isolated-20261105-run01/report-diagnostic/ocr-report.json`
- `android-native/app/build/tmp/ocr-avd-isolated-20261105-run01/report-updated/ocr-report.json` — corrida anterior con DEX distinto; comparar con cautela.
- `android-native/app/build/tmp/importer-public-corpus-20261104/README.md`
- `docs/design/importador-guided-20261104/LINEA_BASE.md`
