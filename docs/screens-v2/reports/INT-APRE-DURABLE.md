# INT-APRE-DURABLE — correcciones S3/S4

Estado: implementación y 19 pruebas nuevas preparadas en `codex/sv2-apre-durable`, base `fecc21b29`. Sin ejecución de Gradle/JVM/adb. No se declara compilación, Room ni UI verdes. Root integrará fase3 completa antes de validar.

## Correcciones concretas

- P1: `refreshCorrectedCalibrations` ya calculaba la captura contextual nueva de S4, pero el controlador de voz/vídeo descartaba sus `completedSets`. Ahora fusiona las entradas modificadas por el helper en el candidato durable y en la publicación posterior a Room. Conserva las demás entradas y la eliminación de undo. El resultado `Committed.set`, usado para sincronizar el vídeo, también es la serie confirmada con la captura nueva.
- El ViewModel ejecuta ese refresh cuando hay decisiones, capturas APRE o un bloque APRE válido, incluso cuando se registró S4 saltando S3. El guard está extraído para cubrir también el caso aún sin captura.
- P2: `manualLoadRequiredSides` se consulta por lado: `left`/`right` afectan únicamente ese lado; `bilateral` bloquea ambos. `side == null` o vacío conserva el contrato anterior de cualquier marcador. Overrides y drafts manuales mantienen su comportamiento.
- Guardas de fuente: S3 no propone si fue calentamiento; S3/S4 no atribuyen un mínimo grabado explícito incompatible a la variante actual. El mínimo null legado sigue admitido. No se convierte XRM en 1RM ni TM y S3 conserva `capacityKg = null`.

## Pruebas nuevas preparadas

19 métodos nuevos, además de las regresiones existentes; no son los 94 anteriores:

- `com.example.kpkn.screens.workout.ApreDurableRoomTest` — 9: recorder real, controlador compartido y Room real. S3 aplicar/restaurar/revertir conservando S4 hecha; conservar/reintento sin duplicar; fallo y retry de recorder S3 y S4; corrección S4 con/sin S3 con igualdad de candidato, Room observado en callback, UI y resultado retornado; undo elimina captura; fallo de corrección revierte UI/Room y reintento recalcula; corrección S3 sustituye decisión aplicada y undo retira efecto; cambio de kg S4 preserva otras entradas.
- `com.example.kpkn.screens.workout.ApreDurableViewModelRoomTest` — 3: ViewModel real con `LiveSessionVmFixture`, corrección compartida vídeo/voz con y sin decisión S3, igualdad Room/UI tras corrección y recreación; aplicar S3, recrear y revertir sin escribir 1RM/TM.
- `com.example.kpkn.screens.workout.ApreLateralCalibrationTest` — 3: marcador right permite Mann left y protege right, bilateral y null; guard de refresh sin decisión/captura; fuente calentamiento/purpose/mínimo incompatible y legado null.
- `com.example.kpkn.screens.workout.components.ApreCardUiTest` — 4: componente real `LiveCalibrationResult` dentro de `LiveSessionCardFrame`, viewport físico360x740/880, fuentes100/130. Cada método recorre APRE3/6/10, mide marco312x425, objetivos48 y límites, prueba clicks NATIVE aplicar/revertir/conservar/continuar/editar, scroll del cuerpo y texto Mann completo (`visibleEnd`, no elipsis ni clipping de altura, límites de línea ±1px). Sin cambiar layout de producción.

Regresiones centrales sugeridas:

```
com.example.kpkn.screens.workout.ApreLiveCalibrationTest
com.example.kpkn.domain.training.ApreXrmCaptureFactoryTest
com.example.kpkn.screens.workout.WorkoutCalibrationTest
com.example.kpkn.screens.workout.WorkoutCalibrationRecorderRoomTest
com.example.kpkn.screens.workout.WorkoutVoiceSetMutationRoomTest
com.example.kpkn.screens.workout.WorkoutVoiceSetMutationsTest
com.example.kpkn.screens.workout.Cal3ViewModelRoomTest
com.example.kpkn.screens.workout.components.Cal3CardUiTest
```

## Renders y límites

Con `KPKN_RENDER_DIR` la clase `ApreCardUiTest` emite 12 PNG `apre-v7-{3|6|10}-{740|880}-{100|130}-proposed.png`. Las aserciones de geometría/texto/clicks se ejecutan aunque no se pida escribir PNG. Root debe abrirlos tras la corrida; aquí no se han generado ni inspeccionado píxeles. No hay evidencia visual nueva todavía.

Se reutilizan los arneses CAL3 de Room y el componente V7 real, sin migraciones/DAO/entidades, datasets ni scripts. No se modifican `ProgramAutoregulationEngine`, `ProgressEngine`, `PlanMaterializer` ni `ApreXrmPrescription`. No se modifica u19, u20, u11 ni otros worktrees. `git diff --check` es verificación estática, no compilación.


## Validación central de integración · 23-oct

La entrega de origen descrita arriba fue revisada e integrada en u11. CAL4-V.md e INT-4.md registran las correcciones productivas, de compilación y de fixtures, con su evidencia real. Producción420acaf4c: 1989 casos puros únicos verdes en completa más repetición de un fixture; Android final int-apre-android-codex-20261023-2.log:14 clases/201 casos,0 fallos/errores/omitidas, BUILD8min11s, wrapper exit0. XML archivados; tres renders finales revisados. Pendientes el barrido ampliado y la QA de dispositivo; no se declara una única corrida completa JVM verde ni la suite entera del repositorio.


## Identidad de la variante activa descubierta por CAL7 · 23-oct

El recorrido Room de variante B reveló que resolveCalibration buscaba el ejercicio y sus IDs S3/S4 en la sesión base A. Aunque el registro era de B, la validación rechazaba la propuesta y permanecía PROPOSED. Se resuelven ahora las prescripciones desde la variante activa A/B/C/D, con el mismo fallback a base de la sesión en vivo; la identidad de ejecución/log continúa siendo la base. La misma selección se aplica a correcciones de voz/vídeo, reconstrucción de la captura S4 y cargas de la ruta, que también estaban leyendo A. No se relaja la validación de contexto ni de IDs.

WorkoutCalibrationVariantTest añade cinco casos: aplicar/ruta/revertir en B/C/D con IDs propios, rechazar el registro B en A, rechazar un S4 reemplazado, corregir S3 conservando identidad B y corregir S4 conservando el XRM físico. APRE10:150×12→155;145×12→150;S4 155×17→162,5 y150×17→157,5. CAL7 conserva su APRE6:120×9→122,5 y XRM125. Preparado, aún sin compilar ni ejecutar; prueba central posterior obligatoria. Room y tablas intactos.
