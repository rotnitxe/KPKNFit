# Lote 5: bisagras — informe aprobado

Fecha: 2026-10-04. **21 definiciones / 59 configuraciones / 43 pares. Anatomía y textos aprobados por el usuario y aplicados mediante land.** El retiro de las cuatro variantes unilaterales del rumano sumo está aplicado por instrucción expresa del usuario.

El catálogo vigente queda con **104 CURATED / 102 LEGACY**, 206 definiciones y 523 configuraciones. `good_morning_seated` sigue LEGACY porque la evidencia no resuelve el cambio de dominante; la conservación del dato heredado no equivale a validación científica.

## Qué se aplicó

- Cuádriceps PRIMARY en convencional y sus déficits por extensión de rodilla; glúteo PRIMARY añadido en buenos días de pie, tres déficits posteriores, swings y pull-through.
- Isquios PRIMARY detrás del glúteo en las cinco configuraciones de bisagra sobre banco a 45°. Se distingue cadera dinámica con columna estable de extensión espinal con pelvis relativamente fija.
- Agarre, codos Zercher y control pélvico unilateral declarados como estabilización cuando corresponde. Se corrigen acciones articulares que confundían sostén con movimiento.
- Textos dedicados a cada configuración, instrucciones breves en cues y briefs con carga, contacto, fase y QA concretas. Los déficits describen suelo de discos más bajo que apoyo de pies; no exigen un eje bajo las plantas.

Hay cambios de dominante en **dos configuraciones de déficit**, y cambios del conjunto PRIMARY en **15 configuraciones protegidas**. El usuario aprobó estos cambios y la tabla registra el estado anterior y el aplicado. No se cambian patrones, revisión, nombres ni searchTerms.

## Volumen: criterio del usuario

> El exceso puede aceptarse si ESE EXTRA lo aporta un ejercicio donde ESE músculo es secundario o estabilizador; el extra donde es PRINCIPAL debe ajustarse.

La anatomía determina el rol; no se baja un PRIMARY para encajar la prescripción. Cada serie de trabajo aporta 1 como PRIMARY, 0,5 como SECONDARY y 0,4 como STABILIZER en el catálogo/UI. Por ejemplo, promover un secundario a principal añade **una serie directa** y **0,5 series equivalentes** por serie realizada: son contadores distintos.

La coordinación de programas implementó en el árbol actual un contador separado de todos los PRIMARY por grupo para los techos, conservando el volumen indirecto en los informes. W2 sigue sumando PRIMARY y SECONDARY en su total y omite STABILIZER; el calculador de volumen incluye la estabilización y normaliza grupos. Este informe no sustituye una materialización semanal ni declara aprobados sus topes con la anatomía propuesta.

[Impacto en sesiones reales y límites de la medición](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/integration/IMPACTO_SESIONES.md) · [Referencias de código](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/integration/CONSUMIDORES.md) · [Delta por serie con contador directo separado](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/integration/volume_delta_per_set.json)

Las dosis de recetas publicadas y planes personalizados deben comprobarse con los roles nuevos. El primer gate Android consumió el catálogo tras el retiro, antes de este land; sus 42 tests de catálogo aprobados no aprueban por adelantado el impacto de los nuevos roles.

## Principales y dominantes protegidos

| Configuración | PRIMARY antes → aplicado | Dominante cambia | Código / tests |
|---|---|---|---|
| `conventional_deadlift__bilateral__barbell` | gluteus_maximus, hamstrings → gluteus_maximus, hamstrings, quadriceps | No | Sí / Sí |
| `conventional_deadlift__unilateral__barbell` | gluteus_maximus, hamstrings → gluteus_maximus, hamstrings, quadriceps | No | No / Sí |
| `conventional_deadlift__bilateral__dumbbells` | gluteus_maximus, hamstrings → gluteus_maximus, hamstrings, quadriceps | No | No / Sí |
| `conventional_deadlift__bilateral__hex_bar` | gluteus_maximus, hamstrings → gluteus_maximus, hamstrings, quadriceps | No | Sí / No |
| `good_morning__bilateral__barbell` | hamstrings → hamstrings, gluteus_maximus | No | Sí / Sí |
| `good_morning__unilateral__safety_bar` | hamstrings → hamstrings, gluteus_maximus | No | No / Sí |
| `glutes_hiperextension_45__plate` | gluteus_maximus → gluteus_maximus, hamstrings | No | Sí / No |
| `hams_pull_through__default` | hamstrings → hamstrings, gluteus_maximus | No | Sí / Sí |
| `hams_peso_muerto_convencional_deficit__default` | hamstrings → gluteus_maximus, hamstrings, quadriceps | Sí | Sí / Sí |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | hamstrings → hamstrings, gluteus_maximus | No | No / Sí |
| `hams_peso_muerto_sumo_deficit__default` | hamstrings → gluteus_maximus, hamstrings, quadriceps | Sí | No / Sí |
| `hams_swing_kettlebell_dos_manos__default` | hamstrings → hamstrings, gluteus_maximus | No | No / Sí |
| `hams_swing_kettlebell_unilateral__default` | hamstrings → hamstrings, gluteus_maximus | No | No / Sí |
| `hams_peso_muerto_rumano_deficit__default` | hamstrings → hamstrings, gluteus_maximus | No | No / Sí |
| `hams_peso_muerto_rumano_sumo_deficit__default` | hamstrings → hamstrings, gluteus_maximus | No | No / Sí |

[Todos los roles y articulaciones: antes y después](C:/Users/valen/Documents/KPKNFit/catalog/exercises/v2/curation/lotes/LOTE_05_ANATOMIA.md) · [Los 21 textos y las 59 configuraciones completos](C:/Users/valen/Documents/KPKNFit/catalog/exercises/v2/curation/lotes/LOTE_05_TEXTOS.md)

## Decisiones y variantes condicionadas

1. **Buenos días sentado:** apartado del lote. PMID 34496710 estudia torque isométrico en cadera 0/45°, no dominancia dinámica de una bisagra sentada con cadera más flexionada. Queda LEGACY; la propuesta original se conserva para investigación posterior.
2. **Rumano sumo unilateral:** retirado por el usuario. Las cuatro bilaterales y su default conservan sus IDs y anatomía vigente. Se quita el chip singleton `stance`; el perfil sigue BILATERAL. Las selecciones guardadas se migran al rumano unilateral con el mismo implemento, con definición y perfil coherentes y receta conservada.
3. **Hex bar:** asas bajas y espacio real para la base sumo o la pierna libre son condiciones del montaje. No cualquier marco permite esas ejecuciones. El texto común ya reconoce el agarre lateral, sin imponer manos entre muslos al marco.
4. **Buenos días en máquina/unilateral:** se conserva el montaje con apoyo anterior pélvico y palanca en espalda alta, con QA explícita. El PNG de apoyo posterior falla; no se cambió el brief para aprobarlo. La variante unilateral depende de la estación y no queda probada por un PNG bilateral.
5. **Banco glúteo en Smith y Jefferson en Smith/polea:** la guía vertical y la tracción oblicua condicionan rango y colocación. Se conservan con esas restricciones; no se proclama equivalencia con carga libre ni compatibilidad universal.
6. **Extensión lumbar default:** la identidad heredada es banco romano sin lastre; equipment=machine y guided_external_load describen imperfectamente ese montaje. Se reporta la clasificación para una decisión posterior; no se transforma silenciosamente en una máquina lumbar sentada.
7. **Zercher lumbar/glúteo:** se mantienen separados por columna dinámica frente a bisagra de cadera con perfil vertebral estable. Si el producto pretendía una ejecución única, el posible duplicado requiere otra decisión estructural.

Las extrapolaciones se declaran en las whys/fuentes: EMG no establece automáticamente dominancia, el multífido medido por ultrasonido no es EMG de erectores y flexion-relaxation no equivale a contracción máxima al final del Jefferson. No se atribuyen beneficios clínicos no demostrados.

**Dos excepciones de herencia aprobadas:** bíceps estabilizador en buenos días Zercher y antebrazo distinto en disco frente a Zercher sobre banco. Se incorporaron mediante merge puntual que preserva las demás reglas, con sus razones y fuentes.

## Revisión e imágenes

Tres agentes cubrieron cinco grupos de autoría. Dos hicieron revisión cruzada exclusivamente de definiciones que no escribieron; no se presenta como contexto completamente virgen. Contrastaron fuentes reales y los **35 PNG originales**, con hashes y **234 respuestas QA**. Revisiones [A](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/review_a/REPORT.md) y [B](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/review_b/REPORT.md).

Resultado de los 46 pares examinados: **14 PASA / 21 FALLA / 11 SIN_IMAGEN**. Los tres pares del sentado apartado se identifican aparte. El lote aplicado tiene **32 entradas de cola aprobadas**, con imágenes pendientes y no generadas.

[Cola completa con motivos, QA y originales](C:/Users/valen/Documents/KPKNFit/catalog/exercises/v2/curation/lotes/COLA_IMAGENES.md) · [43 briefs técnicos aprobados](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/integration/BRIEFS_VISUALES.txt)

Fallar una fase o contacto del brief no significa que toda postura mostrada sea imposible. No se aprueba una imagen unilateral a partir de su hermana bilateral. El historial guardado conserva sus antiguas instantáneas musculares: los roles aplicados introducen un corte de criterio en los gráficos; este lote no reescribe ese historial.

## Validación realizada

- Lint conjunto final: **0 errores / 0 avisos**; gate de los IDs propuestos: 0. **186 citas de 39 URLs**, proof scoped sin faltantes. Cierre de correcciones y relectura final de los revisores; sentado devuelto a LEGACY.
- Auditoría extendida de preview: 3520→2654 errores y 137→116 avisos. **Cero hallazgos en los 21 IDs propuestos**; los restantes pertenecen al resto del catálogo. Este preview permanece privado.
- Pytest scripts/backend: baseline **197 tests / 158 subtests**; tras el retiro **206 tests / 160 subtests**, todos pasaron. Hay además 19 pruebas puntuales de retiro/backend aprobadas.
- Python tras aplicar el lote: **206 tests / 160 subtests aprobados en 112.68 s**, 0 fallos/errores en JUnit.
- Retiro: igualdad exacta de originales menos las cuatro configs; rebase de una sola entrada .splice_base, otras intactas; reaplicación 0 cambios. Gate READY, compile/check y pin/check aprobados.
- Source, Android assets/resources e iOS son idénticos: `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`. Paridad de datos verificada; no se afirma compilación iOS.
- Catálogo aplicado, captura vigente: **11 suites / 67 tests**, 0 fallos / 0 errores. [Captura vigente de catálogo](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/native/final_gate50/summary.json).
- Primera captura archivada del lote aplicado: **10 suites / 58 tests**, 0 fallos / 0 errores. [Registro histórico](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/native/lote05/summary.json); conserva el alcance que se ejecutó entonces.
- Gate compartido filtrado: **50 suites / 402 tests**, 0 fallos / 0 errores / 0 omitidos. Su alcance es distinto de la suite Base completa. [Resultado del gate vigente](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/gate50/receipt.json).
- Gate compartido anterior, archivado con su alcance: **48 suites / 797 tests**, 0 fallos / 0 errores. [Registro histórico](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/gate48/receipt.json).
- Cobertura FULL: **25650 casos / 0 violaciones**.
- Validación fresca requerida de 11 suites: **Aprobado**. Captura fresca de las once suites requerida: 67 tests, 0 fallos/errores/omitidos; incluye ExerciseCatalogAuditTest. XML archivados del gate 50 y frescura posterior a 1791139274 comprobadas.
- Contexto de la evidencia Android: El lote aplicado conserva SHA1c267, 523 configuraciones y 104 CURATED/102 LEGACY. La captura propia fresca final contiene 11 suites/67 tests, 0 fallos/errores/omitidos; el gate compartido filtrado 50 contiene 402 tests, 0/0/0 y terminó BUILD SUCCESSFUL en 9m49s. Históricos 10/58 y 48/797 y cobertura FULL 25650/0 permanecen archivados. Base completa 1 es histórica FAILED 718/6327/5/0/3; Las ejecuciones completas posteriores se siguen en el chat de programas; este checkpoint no declara aprobación global de Base. APK construido e instalado, MainActivity top-resumed a las 18:13 UTC, sin mención del paquete en crash buffer fresco. Las cuatro fichas/selector siguen NOT_RUN: configuración incompleta y plan candidato aún no seleccionado; Continuar deshabilitado es coherente con ese estado y no demuestra defecto. No se activó un plan. Programas C.P14 comprobará el selector al FINAL integral con 104 CURATED/102 LEGACY. Datos restaurados: 207 archivos/WAL idéntico/app detenida a las 18:29:59 UTC. Android y APK consumieron el árbol compartido SHA1c con WIP de programas excluido de nuestros commits; SHA31cc tiene 8 suites/42 tests previos y coherencia privada, sin checkout aislado reconstruido. El checkpoint final de 39 rutas requiere integración con programas para la app completa.

| Etapa independiente | Estado guardado | Resultado y evidencia |
|---|---|---|
| Suite Base completa | Falló | Resultado: Base completa 1, ejecución histórica antes de las reparaciones posteriores. Gate Gradle exit1; no se convierte en aprobado mediante los gates filtrados.; Suites: 718; Tests: 6327; Fallos: 5; Errores: 0; Omitidos: 3; Exit: 1; Duración (s): 1888; Motivo: Cinco fallos históricos: conteo 8→4 del AuditTest, weekly volume de SessionTemplate, autoregulationHook, power_template y UncaughtExceptionsBeforeTest de WorkoutSnapshotCommitTest. El gate 50 filtrado aprobó su alcance; no vuelve verdes los 6327 tests de esta corrida. Las ejecuciones completas posteriores se siguen en el chat de programas; este checkpoint no declara aprobación global de Base.; [receipt.json](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/fullbase/receipt.json); [SUMMARY.md](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/fullbase/SUMMARY.md); [gradle.log](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/fullbase/gradle.log); archiveXmlCount: 718; archiveHashVerification: {"files": 718, "mismatches": 0, "verifiedBy": "coordinación raíz; no se repitió la verificación en esta preparación"} |
| Build / APK BaseDebug | Aprobado | Resultado: Compilación, packageBaseDebug y assembleBaseDebug completados antes de fallar los tests del gate global. APK y catálogo del ZIP comprobados; este resultado no aprueba el gate Gradle completo.; Variante: BaseDebug; Archivo: C:\Users\valen\Documents\KPKNFit\artifacts\programs-curation\resume-20261004\C\fullbase\app-base-debug.apk; SHA-256: 6237d204e2e1f8dd8cfd2259189e7b708b7fd477052fcd764501d7b31721fc72; Paquete: com.example.kpkn; Version code: 34; [apk_fullbase_archive.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/apk_fullbase_archive.json); [receipt.json](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/fullbase/receipt.json); [gradle.log](C:/Users/valen/Documents/KPKNFit/artifacts/programs-curation/resume-20261004/C/fullbase/gradle.log); catalogSha256: "1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099"; checks: "ZIP/manifest y cuatro casos del catálogo: allChecksPassed=true" |
| Instalación | Aprobado | Resultado: adb install -r del APK archivado devolvió Success.; SHA-256: 6237d204e2e1f8dd8cfd2259189e7b708b7fd477052fcd764501d7b31721fc72; Destino: emulator-5554; Paquete: com.example.kpkn; [install_fullbase_archive.log](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/install_fullbase_archive.log) |
| Fichas y selector en UI | No ejecutado | Resultado: Las cuatro fichas y el selector visual no se ejecutaron. El APK/ZIP y el launch tienen evidencia independiente.; Motivo: Configuración de Setup incompleta y plan candidato aún no seleccionado; Continuar tenía un ancestro enabled=false, comportamiento esperado para ese estado. No se activó un plan. Esto impidió llegar a Home durante esta comprobación, pero no demuestra un defecto de Setup. Las cuatro fichas y el selector corresponden a Programas C.P14 al FINAL integral con 104 CURATED/102 LEGACY.; [ui_cases.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/ui_cases.json); [resume_plan_next.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/resume_plan_next.json); [report.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/resume_plan_next/report.json); [after.xml](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/resume_plan_next/after.xml); casesRequested: ["conventional_deadlift", "romanian_sumo_deadlift", "good_morning", "glutes_hiperextension_45"]; casesExecuted: 0; catalogSha256: "1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099"; defectDemonstrated: false; followup: "Programas C.P14: selector al FINAL integral de la recuración." |
| Launch de MainActivity | Aprobado | Resultado: Launch fresco confirmado a las 18:13 UTC; la app se detuvo después al restaurar datos. Este resultado no demuestra apertura de fichas/selector.; Exit: 0; Destino: emulator-5554; Paquete: com.example.kpkn; topResumedActivity: topResumedActivity=ActivityRecord{31426936 u0 com.example.kpkn/.MainActivity t990}; [launch_final_catalog.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/launch_final_catalog.json); [report.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/launch_final_catalog/report.json); [snapshot.top-resumed.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/launch_final_catalog/snapshot.top-resumed.json); [snapshot.crash.log](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/launch_final_catalog/snapshot.crash.log); [snapshot.png](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/launch_final_catalog/snapshot.png); [snapshot.xml](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/snapshots/launch_final_catalog/snapshot.xml); endedAtUtc: "2026-10-04T18:13:01.183939+00:00"; crashBufferMentionsPackage: false |
| Restauración de datos | Aprobado | Resultado: Datos originales restaurados y verificados; app detenida.; Destino: emulator-5554; [restore_before_ui.log](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/ui_qa/restore_before_ui.log); verifiedAtUtc: "2026-10-04T18:29:59.5040481Z"; restoredFilesVerified: 207; walByteEquivalence: true; appStopped: true; originalArchiveSha256: "35f20afe63fa5fa88fec4a3f249f473fafaa49fdee207c5a6d2ce4853afd3bdf" |

[Evidencia nativa consolidada](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/native_evidence.json). La instalación y el launch/UI tienen resultados propios; el build o un gate de tests no los acreditan.

### Qué árbol y qué materiales se verificaron

Las pruebas Android y el APK consumieron el **árbol de trabajo compartido**, con catálogo SHA-256 `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`. Ese árbol contiene cambios WIP de programas excluidos de los dos commits de catálogo. La igualdad del catálogo acredita esos datos; no acredita igualdad del árbol completo con un commit intermedio.

El checkpoint previo conserva catálogo `31ccbddd4943362bd4258679900ed21549516484985ff01d613360e30c04c562`, **523 configuraciones / 83 CURATED / 123 LEGACY** y 4 configuraciones del rumano sumo. Sus materiales incluyen el retiro y el literal de AuditTest 8→4. Su fuente tiene evidencia previa de **8 suites / 42 tests / 0 fallos / 0 errores** ([captura previa del retiro](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/native/retirement/summary.json)) y comprobaciones de coherencia de source, fichas, manifiesto, artefactos y pins en las copias privadas del checkpoint.

No se reconstruyó ni ejecutó un checkout aislado del primer checkpoint. Los gates posteriores que consumen el catálogo SHA1c no se atribuyen al catálogo SHA31cc ni a un árbol idéntico al commit previo.

El segundo checkpoint contiene **39 rutas** y catálogo `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099` con **104 CURATED / 102 LEGACY**. La app completa requiere integrar también los commits de programas. La coherencia privada de los materiales de catálogo, las ejecuciones nativas del árbol compartido y la integración completa tienen alcances propios.

[JUnit Python tras retiro](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/after_retirement_pytest.xml) · [JUnit Python tras aplicar lote](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/after_lote05_pytest.xml) · [Resultado Python post-land](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/after_lote05_pytest.log) · [Retiro y logs exactos](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/retirement/REPORT.md) · [Lint final](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/integration/lint.log) · [Resumen reproducible](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/integration/summary.json)

## Rutas de los checkpoints autorizados

Estas rutas delimitan los materiales del catálogo involucrados en el retiro y la aplicación aprobados. Varias ya contenían cambios de los lotes anteriores o trabajo concurrente; el alcance exacto y los bloques excluidos se registran en los materiales privados de cada checkpoint.

```text
android-native/app/src/main/assets/exercise_catalog_v2.json
android-native/app/src/main/java/com/example/kpkn/domain/exercises/catalogv2/ExerciseCatalogV2Resolver.kt
android-native/app/src/main/java/com/example/kpkn/domain/exercises/catalogv2/SessionCatalogNameReconciler.kt
android-native/app/src/main/resources/exercise_catalog_v2.json
android-native/app/src/test/java/com/example/kpkn/data/ExerciseCatalogContractTest.kt
android-native/app/src/test/java/com/example/kpkn/domain/exercises/catalogv2/AprendeCatalogAuditTest.kt
android-native/app/src/test/java/com/example/kpkn/domain/exercises/catalogv2/ExerciseCatalogV2ResolverTest.kt
android-native/app/src/test/java/com/example/kpkn/domain/exercises/catalogv2/SessionCatalogNameReconcilerTest.kt
backend/tests/test_exercises_catalog_v2.py
catalog/exercises/v2/curation/AUTHORING_FICHA.md
catalog/exercises/v2/curation/EDITORIAL_GUIDE.md
catalog/exercises/v2/curation/INDEX.md
catalog/exercises/v2/curation/STATUS.md
catalog/exercises/v2/curation/anatomy_rules.json
catalog/exercises/v2/curation/fichas/hinge_deadlift.json
catalog/exercises/v2/curation/fichas/hinge_good_morning.json
catalog/exercises/v2/curation/fichas/hinge_rdl.json
catalog/exercises/v2/curation/fichas/lower_hip_extension.json
catalog/exercises/v2/curation/fichas/lower_hip_hinge.json
catalog/exercises/v2/curation/fichas/lower_hip_hinge_deficit.json
catalog/exercises/v2/curation/fichas/lower_hip_hinge_explosive.json
catalog/exercises/v2/curation/fichas/lower_hip_hinge_lengthened.json
catalog/exercises/v2/curation/fichas/lower_romanian_deadlift.json
catalog/exercises/v2/curation/fichas/lower_romanian_deadlift_deficit.json
catalog/exercises/v2/curation/fichas/lower_spinal_extension.json
catalog/exercises/v2/curation/fichas/lower_spinal_flexion.json
catalog/exercises/v2/curation/lotes/COLA_IMAGENES.md
catalog/exercises/v2/curation/lotes/LOTE_05_ANATOMIA.md
catalog/exercises/v2/curation/lotes/LOTE_05_BISAGRAS.md
catalog/exercises/v2/curation/lotes/LOTE_05_TEXTOS.md
catalog/exercises/v2/curation/sources_verified.json
catalog/exercises/v2/source/catalog_v2.json
catalog/exercises/v2/source/families/hinge_deadlift.json
catalog/exercises/v2/source/families/hinge_good_morning.json
catalog/exercises/v2/source/families/hinge_rdl.json
catalog/exercises/v2/source/families/lower_hip_extension.json
catalog/exercises/v2/source/families/lower_hip_hinge.json
catalog/exercises/v2/source/families/lower_hip_hinge_deficit.json
catalog/exercises/v2/source/families/lower_hip_hinge_explosive.json
catalog/exercises/v2/source/families/lower_hip_hinge_lengthened.json
catalog/exercises/v2/source/families/lower_romanian_deadlift.json
catalog/exercises/v2/source/families/lower_romanian_deadlift_deficit.json
catalog/exercises/v2/source/families/lower_spinal_extension.json
catalog/exercises/v2/source/families/lower_spinal_flexion.json
catalog/exercises/v2/source/manifest.json
ios-native/KPKNFit/KPKNFit/exercise_catalog_v2.json
scripts/catalog_v2_axis_order.py
scripts/catalog_v2_gate.py
scripts/catalog_v2_retire_romanian_sumo_unilateral.py
scripts/tests/test_catalog_v2_retire_romanian_sumo_unilateral.py
```

[Alcance exacto de los materiales de checkpoint](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/approved_land/commit_scope.json): **61 rutas del baseline previo y 39 rutas del lote 5**, con sus SHA y bloques concurrentes excluidos. [Detalle de los dos checkpoints](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/approved_land/CHECKPOINT_SCOPE.md). La lista del generador que aparece arriba es orientativa; las rutas exactas están en ese registro.

Las rutas de recetas, wizard y política de volumen pertenecen a la coordinación de programas; este checkpoint no atribuye sus cambios a la curación del catálogo.

## Aplicación y evidencia de los checkpoints

El plan del usuario exige: **«Cada lote cierra con un informe; se aplica y se hace commit solo con OK»**. El usuario respondió **«Apruebo aplicar el lote y su commit acotado»**. Se comprobaron hashes/base, incorporaron 21 fichas, dos excepciones y 17 proofs faltantes mediante merges puntuales, y se ejecutó land. El OK autoriza los dos checkpoints acotados descritos arriba. La evidencia Git final se conserva en [approved_land/commit_state](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote05-20261004/approved_land/commit_state); allí se registra el resultado de cada fase y el SHA de cada commit. Este informe conserva la validación archivada al preparar el checkpoint.
