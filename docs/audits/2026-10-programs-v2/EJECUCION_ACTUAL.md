# Ejecución actual: 25-oct-2026

Base `1d399c1fc`, rama `codex/programs-periodization-current`. Sin push ni cambios al WIP de master. El worktree histórico queda intacto; se canceló la verificación Android de ese worktree al recibir la instrucción de adaptar el plan al producto actual, sin resultado válido.

## Recuperación inicial (validación actual pendiente)

| Tarea | Commit recuperado | Adaptación |
|---|---|---|
| T-01 | `33affef65` | Alcance puro |
| T-02 | `6e8ef0571` | Textos conservando modelos vigentes |
| T-03 | `888340da7` | Etiquetas de alcance |
| T-04 | `0fa5cd612` | Alcance en pantallas actuales |
| T-10 | Ya integrado por CAL5 en la base | Conservar `StagedVolume`: etapas por fallo, no las 12 semanas por calendario del plan antiguo |
| T-11 | `bde049549` | Rotación de Cube conservando todos sus AMRAP de registro; contentVersion 4 por partir de v3 actual |
| T-12 | `6e38bcfff` | Rampa RP limitada al accesorio acordado, sin exenciones de KPKN_NATIVE |
| T-13 | `3bcf5203f` | Top sets RPE con referencias de respaldo |
| T-14 | `0f3c8e6b4` | Calgary taper; accesorios principiantes conservan calibradores existentes; power-12-3 pasa a v4 |
| T-50 | `5b7117f9c` | Decisiones del protocolo con origen PROTOCOL, sin introducir AUGE; campos multisport preservados |

Los commits recuperados no se presentan como tareas verificadas sobre esta base. La corrida dirigida actual cubre ProgramScope, ProgramProgressReport y recetas/contratos. Resultado pendiente.

Contrato actual comprobado en código: `RecipeContractInventoryTest` usa ambos techos en 18 hallazgos no exentos. El 100 del relevo antiguo ya no es el límite vigente; no se subirá ninguno de los dos techos. El gate de retirada terminó con `0 active retirement violations; positive/physical-rings control checks passed`.

Primera corrida JVM: 8 clases, 97 tests, 1 fallo, 0 errores, 0 omitidos; 288 s con cola. Log `C:/kw/logs/kpknjvm-KPKNFit-20261025-124644.log`. Único fallo: exención muerta `korte-3x3 / H5b / *`, de la comprobación retirada. Se elimina la exención y se actualizan sus matrices; la receta de ejercicios, series, cargas y repeticiones permanece intacta. Korte contentVersion 2 por el cambio de metadatos. No se debilitan tests ni techos.

Segundo intento aprobado:

```text
kpkn_jvm.sh <worktree> -PtestInclude=<8 clases y 2 fixtures explícitos> --tests <8 clases explícitas>
BUILD SUCCESSFUL in 5m 44s
[kpkn_jvm] RESUMEN: 8 clases, 97 tests, 0 fallos, 0 errores, 0 omitidos; gradle rc=0; 388 s con cola
```

Log: `C:/kw/logs/kpknjvm-KPKNFit-20261025-125239.log`. Clases: ProgramScopeTest (5), ProgramProgressReportTest (5), ProtocolRecipeFidelityTest (70), RecipeContractInventoryTest (6), ProtocolCompositionContractTest (3), ProtocolLibraryTest (3), ExemptionScopeMigrationTest (3), ProtocolExemptionMatrixTest (2). Son pruebas JVM del motor; Android y runtime no quedan acreditados.

## T-51–53: portado, pendiente de validación Android

Se recuperó sólo el diff de ViewModel, pantalla, test de ViewModel y render, más los dos componentes nuevos y la especificación. Se resolvió una importación retirada sin recuperarla. La hoja usa origen PROTOCOL y la documentación consume StrengthModel y los motores descriptivos actuales. No se copiaron archivos completos sobre el ViewModel ni la pantalla actuales.

Primera compilación Android: `:app:compileBaseDebugUnitTestKotlin --max-workers=1 -Dorg.gradle.jvmargs=-Xmx6g ...`, por `gradle_slot.sh`; `BUILD SUCCESSFUL in 15m 45s`, 25 tareas ejecutadas, exit 0. Esto acredita compilación, no ejecución de tests ni APK.

Revisión posterior necesaria de T-50: los enums serializados retienen tipos antiguos por compatibilidad. El informe sólo presenta auditoría y propuestas aplicadas de referencias ADJUST_TM/ADJUST_XRM como decisiones vigentes; no atribuye ajustes derivados retirados al protocolo. Se añade regresión que combina ambos tipos y espera únicamente la referencia actual. Esta corrección es posterior a las 97 pruebas JVM anteriores y está incluida en la corrida Android en curso.

Corrida Android solicitada: ProgramProgressReportTest, ProgramDetailViewModelTest, ProgramDetailFormatTest, ProgramPlanDisplaySummaryTest, ProgramHeaderModelTest, PlanInfoModelTest, PlanLabelsTest y ProgramDetailClosureRenderSheetTest, con `KPKN_RENDER_DIR` explícito. Resultado pendiente. Mantiene 6 GiB para la compilación incremental necesaria de la corrección; el init script limita a un fork de tests de 3 GiB y rota cada cinco clases.

Resultado de esa corrida: `212 tests completed, 3 failed`, `BUILD FAILED in 17m`, exit 1, tras 750 s de espera de cola. Sin errores ni omisiones en los XML. Siete clases aprobaron sus 160 pruebas: ProgramProgressReportTest (6), ProgramDetailViewModelTest (81), ProgramDetailFormatTest (20), ProgramPlanDisplaySummaryTest (29), ProgramHeaderModelTest (9), PlanLabelsTest (14), render (1). PlanInfoModelTest ejecutó 52 y falló tres esperados obsoletos: dos fijaban «polea alta y baja» donde el vocabulario vigente dice «polea»; otro esperaba Remo Convencional en polea, configuración retirada que los planes autorados ya sustituyen por Remo Gironda. Las comprobaciones de duplicados y de nombres diferenciados se conservan.

T-50 corregida: `6a8ca74ec`, seis pruebas Android aprobadas incluida la regresión de auditoría. T-51 cerrada: `c5a8af9e8`, 81 pruebas del ViewModel aprobadas, incluido su nuevo flow.

El render produjo 18 PNG (nueve escenas, escalas 100/130), sin omitir la prueba. Inspección visual: la tarjeta original partía «Cumplimiento» en 130 %. Se corrigió con métricas en filas al ampliar fuente y posición de semana independiente. Esos cambios y los esperados de PlanInfoModelTest están en el segundo intento dirigido, sólo esas dos clases (53 pruebas previstas). No se repiten las otras seis clases, cuyo código y comportamiento no cambiaron. Resultado y revisión visual corregida pendientes.

XML de la primera corrida conservados en `artifacts/programs-current-validation/android-first/`; renders originales en `render/` y corregidos previstos en `render-corrected/`. La compilación y el render no acreditan instalación ni QA en dispositivo.

Segundo intento dirigido aprobado: `BUILD SUCCESSFUL in 11m 52s`, exit 0, tras 900 s de espera. Dos XML actuales: PlanInfoModelTest 52/52 y ProgramDetailClosureRenderSheetTest 1/1, cero fallos, errores u omisiones. Se produjeron 18 PNG corregidos y se inspeccionaron progreso_s100/progreso_s130: etiqueta completa, métricas alineadas, semana sin número huérfano. El conjunto de ocho clases queda aprobado por sus resultados por clase (159 pruebas de las seis clases sin cambios, más las 53 de la repetición); no es una única corrida nueva de 212 pruebas.

Control Room: sin diferencias en `data/db` ni `app/schemas` frente a `1d399c1fc`; blob del esquema 35 idéntico `29f2facea80c338491dc3ccd12e8a91d9a83c4a4`.

## T-15: motor y dirigidas JVM aprobadas

Motor y colector RPE aplicados, con guardas de programa/run/ciclo/configuración y conservación de la deduplicación APRE. AUTO heredado sólo deja propuesta pendiente. El e1RM usa StrengthModel directamente; la tabla prescriptiva redondeada tiene techo 95 % y su inversión inflaría un single @10. Nueva regresión: 100 kg × 1 @10 conserva e1RM 100 y TM 90 sin incremento. El escalón TM conserva cero y el límite ±5 % después del redondeo.

Primer intento: main compilado, tests no compilados por omitir ConfirmedProtocolTestSupport y Cal5TestSupport del testInclude. Log `kpknjvm-KPKNFit-20261025-142814.log`, BUILD FAILED in 3m 6s. Segundo intento incorporó el fixture de confirmación, pero aún faltaba Cal5TestSupport: BUILD FAILED in 2m 48s, 0 clases/0 tests, rc 1, 184 s con cola (`kpknjvm-KPKNFit-20261025-143319.log`). Se identificaron y leyeron ambos fixtures reales, sin cambiar su contenido. Tercer y último intento con los cuatro fixtures explícitos en curso. No hay commit de T-15 ni resultado verde todavía. ProgramAutoregulationResolutionTest queda para el gate Android por exclusión real del arnés.

Tercer intento aprobado: `BUILD SUCCESSFUL in 28s`; `[kpkn_jvm] RESUMEN: 3 clases, 43 tests, 0 fallos, 0 errores, 0 omitidos; gradle rc=0; 64 s con cola`. Log `C:/kw/logs/kpknjvm-KPKNFit-20261025-143746.log`. Clases ejecutadas: RpeTopSetEngineTest, ProgramAutoregulationEngineTest, ProgressionConsumerCoverageTest. El cambio queda validado en JVM y se conserva pendiente la clase de resolución Android; no se contabiliza como una cuarta clase ejecutada.

El cierre amplio JVM del comando literal del plan NO aprobó ni ejecutó pruebas: `compileTestKotlin` no puede resolver dependencias de pantallas/Room excluidas del main del arnés. Log `C:/kw/logs/kpknjvm-KPKNFit-20261025-142111.log`; `BUILD FAILED in 3m 56s`; resumen `0 clases, 0 tests; gradle rc=1; 276 s con cola`. Ejemplos: PlanNamingUniquenessTest → SetupPlanReveals; ProgramCoverRepositoryTest → CoverAssetSource/ProgramCoverRepository; AuthoredPlanMaterializerTest → toEntity/toProgram. No se modifica el arnés compartido, no se debilitan esas pruebas y no se interpreta 0 tests como éxito. La compilación Android de todas las fuentes de tests ya aprobó. La ejecución amplia de las siete familias se traslada al gate Android de Ola B junto a sus clases de integración. Ola A tiene dirigidas aprobadas y cierre amplio pendiente de ese gate; se continúa trabajo puro que no depende de las clases ausentes.

T-04, esperados actuales: `99cfda6aa`; T-52 tarjeta/decisiones: `5759d6c98`; T-53 especificación: `c74897ed9`. Las verificaciones Android y render descritas arriba están aprobadas. Se ejecuta el cierre amplio del motor de Ola A con los siete globs de §II.14; las olas B–E adaptadas siguen pendientes. T-104 sigue aplazada por el propio plan. No hay instalación ni QA de dispositivo de esta rama.

## T-16: inventario de fidelidad

Commit `1287cac47`: tabla de 30 protocolos visibles, incluidos APRE y los cuatro PHUL/PHAT autorados diferenciados de los legacy. Describe recetas, referencias, reglas, hooks y adaptaciones actuales; las URLs son fuentes declaradas en código, sin atribuir una recertificación web. Conserva GZCLP por fallos y las convenciones actuales de Calgary/APRE.

## T-17: aviso editorial de volumen aprobado

Commit `4d088a16c`. C11 cuenta series de trabajo equivalentes (primario 1, secundario 0,5, contribución máxima por grupo), omite aproximaciones y no evalúa descargas. Es SOFT incluso si se pide severidad HARD. No altera recetas ni afirma medir recuperación personal. Los techos C1–C10 permanecen en 18; C11 se informa aparte (319 avisos en el inventario actual).

Intento 1: main no compiló por el nombre antiguo ExerciseTraitResolver.atomOf; se usa el objeto vigente CatalogTraits. Log `kpknjvm-KPKNFit-20261025-144845.log`, 0 tests, rc 1. Intento 2: 110 tests, un fallo por una expectativa preexistente que exigía H5a retirada (`kpknjvm-KPKNFit-20261025-145206.log`). Se conserva el oráculo diferencial de porcentajes, se exige que H5a/W3 sigan ausentes y se mantienen H9/W4 vigentes. Intento 3 aprobado: BUILD SUCCESSFUL in 3m 42s; 4 clases, 110 tests, 0 fallos, errores u omisiones, rc 0, 283 s con cola; log `C:/kw/logs/kpknjvm-KPKNFit-20261025-145628.log`.

## T-18 y T-20–25: código en curso, validación pendiente

T-18 añade keepExact con identidad del programa conservada y texto veraz: «Se mantiene el protocolo tal cual, sin ajustarlo a la fecha de competición.» No afirma que la fecha de fin sea distinta si casualmente coincide. T-20 prescribe la onda RIR 3/3/3/2/2 y 3/2/2/1/1, con descarga 4; no cambia NativeWorkoutProgressionRuntime.

T-21–23 añaden respuestas originales y peticiones con run/ciclo/semana, propuesta de accesorios T3, validación de respuestas, idempotencia y protección de sesiones ejecutadas/ediciones manuales. El guard excluye también AUTHORED_EXACT sin sourceProtocolId. La tabla de músculos se extrae de la clase SimpleCyclePersonalizer a una función pura compartida, sin cambiar sus pares.

Las referencias MEV/MRV son límites editoriales. El motor admite límites aportados explícitamente, pero la integración no reutiliza volumeRecommendations históricas sin procedencia como capacidad personal. El ejemplo sintético de reducción de 3 a 2 series declara mínimo 2: el mínimo editorial real de dorsales es 10 y no permite esa reducción; se prueba esa diferencia en vez de cambiar constantes.

T-24 separa responder/revisar/confirmar. La vista previa no escribe; el repositorio confirma sólo si la instantánea del programa y la evidencia de sesiones iniciadas siguen idénticas. Omitir guarda una respuesta sin ajustes. No volver a preguntar usa el actual updateSettings. T-25 es un recordatorio opcional por ciclo, sin diagnóstico ni cambio automático.

Primer intento conjunto T-18/T-20–22: compileKotlin falló porque budgetMuscleGroups pertenece a una instancia de SimpleCyclePersonalizer, no a un objeto. Log `kpknjvm-KPKNFit-20261025-150208.log`; BUILD FAILED in 2m 49s, 0 clases/0 tests, rc 1, 286 s con cola. Se extrajo la tabla real; segundo intento dirigido incluye WeeklyCheckinEngineTest y está en curso (`kpknjvm-KPKNFit-20261025-150731.log`). Pantallas, repositorio y dbJson real se verificarán en Android; aún no están aprobados.

Resultado del segundo intento: BUILD FAILED in 7m 32s; 7 clases, 72 tests, 1 fallo, 0 errores u omisiones, rc 1, 714 s con cola. Aprobaron MeetRecipeFitterTest 10, NativeProgressionRealPlanTest 6, RirWaveTest 2, WeeklyCheckinJsonTest 2, WeeklyAdjustmentEngineTest 10 y WeeklyCheckinEngineTest 6. NativeProfileRecipeAndFitterTest ejecutó 36 y falló sólo el esperado principiante constante RIR 3 en semana 4; DEV-r2-07 exige 3/3/3/2/2 y se actualizó el oráculo literal, conservando SPEED 5 y la descarga. T-18 guardada en `9f6823266` con sus diez pruebas aprobadas.

Tercer intento dirigido en curso: `kpknjvm-KPKNFit-20261025-151931.log`; tras 285 s tomó ranura. Repite el único método fallido y las tres clases cuyo código cambió, con dos regresiones nuevas para accesorios compartidos y edición manual; no repite los barridos amplios sin cambios. Los XML del intento 2 están en artifacts/programs-current-validation/jvm-weekly-attempt2/.

Adaptación de identidad T-20: la receta propia recién generada usa contentVersion 2 por el cambio real de RIR, con una aserción explícita en su test; los snapshots v1 guardados permanecen intactos. Es un ajuste adicional al cambio literal de trainingRir exigido por el contrato vigente de identidad de recetas/progresión. T-22 evita duplicar un cambio sobre un accesorio con varios músculos principales y conserva ese accesorio si las respuestas de sus grupos se contradicen. T-23 excluye también procedencia ORIGINAL sin sourceProtocolId.

Gate de retirada después de RPE, volumen y ciclo semanal: `0 active retirement violations; positive/physical-rings control checks passed`, exit 0. No acredita las pruebas Android pendientes.
