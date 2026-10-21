Corrección de T2 (ronda 2 de 3). Revisé tu ronda 1 contra la base: las nueve funciones heredadas quedaron idénticas a `8ac57c9d4` y `NativeProfileRecipeAndFitterTest` volvió a su valor original. Bien. Quedan dos cosas.

## 1. Los 4 fallos de desbordamiento son TUYOS y hay que arreglarlos
Tu cambio de unidad («6 series/sem» en la cifra planificada) no cabe en la fila y tus propias comprobaciones nuevas lo detectan: `ProgramVolumeModuleTest` (`elPromedioSemanalMuestraLaMismaUnidadSinRecortesAlCientoTreinta`, `laSemanaMuestraLaMismaUnidadEnPlanificadoYReal`, `soloElTotalMuestraSeriesSinUnidadSemanal`) y `ProgramVolumeT2RenderSheetTest.renderVolumeContract` («sin recortes al 1.0: series/sem»; la aserción falla antes de guardar los PNG). No las debilites ni las borres: su intención es correcta (la unidad debe verse completa).

Arregla el **diseño de la fila**, no el test:
- La unidad nunca se abrevia ni se trunca con puntos suspensivos ni se recorta. Si cifra + unidad no caben en una línea junto al nombre del músculo, apila: la cifra grande arriba y la unidad pequeña debajo (alineadas a la derecha), o deja que el nombre del músculo ceda espacio y ocupe dos líneas; elige lo que mejor se lea al 100 % y al 130 % de letra, sin cambiar el resto del aspecto de la fila («refinar, no rediseñar»).
- Mantén las reglas de unidad: «series/sem» cuando el alcance es Semana o promedio semanal; «series» a secas solo si es un total.
- Deja que el render **guarde sus PNG** y MÍRALOS (100 % y 130 %, 360 dp): sin recortes, sin solapes, el desglose y las porciones intactos. Describe en el informe lo que ves.

## 2. Lo que NO debes arreglar (lo verifica el orquestador)
Tu corrida ancha tuvo 22 fallos de editor/catálogo (`SessionEditorManualOverrideTest`, `SessionEditorAutoSaveTest`, `ExerciseCatalogImageCoverageTest`, `CatalogDetailContentTest`). Sospecho que son de ENTORNO: este worktree no tenía la carpeta `catalog/` de la raíz del repo (varios tests leen `catalog/exercises/...`), y acabo de añadirla (ya existe en `C:\kw\mfp\catalog`). **No toques esos tests ni su código.** Solo inclúyelos en la corrida de abajo y repórtame su resultado tal cual.

## Corrida única (por `gradle_solo.sh`, como siempre)
Un solo comando con estos filtros: `com.example.kpkn.screens.programdetail.*`, `com.example.kpkn.render.ProgramVolumeT2RenderSheetTest`, `com.example.kpkn.screens.home.VolumeConsumersTest`, `com.example.kpkn.domain.training.VolumeAccountingContractTest`, `com.example.kpkn.domain.training.TrainingWindowTest`, `com.example.kpkn.screens.sessioneditor.SessionEditorManualOverrideTest`, `com.example.kpkn.screens.sessioneditor.SessionEditorAutoSaveTest`, `com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogImageCoverageTest`, `com.example.kpkn.screens.sessioneditor.components.CatalogDetailContentTest`, `com.example.kpkn.data.ExerciseDatabaseLoadTest`, `com.example.kpkn.data.ExerciseDbAuditTest`, con `KPKN_RENDER_DIR` para el render. Cuenta los tests de los XML (por suite) y dime cuáles fallan, si alguno.

## Reglas y entrega
Mismas de COMUN.md. Archivos permitidos: `ProgramVolumeRow.kt`, `ProgramVolumeModule.kt`, `ProgramVolumeModels.kt` y sus tests/render. Commit aparte `fix(programa): unidad semanal completa en la fila de volumen` con `Co-Authored-By: Codex <noreply@openai.com>`. Tope: esta es la ronda 2 de 3; si no cierra, PARA y descríbelo. Informe ≤ 250 palabras: qué cambiaste en el diseño, lo que ves en los PNG, y la tabla de tests por suite.
