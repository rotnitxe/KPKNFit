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

### Tercer fallo: ediciones y verificaciones detenidas por §II.0, línea 303

Log `C:/kw/logs/kpknjvm-KPKNFit-20261025-151931.log`: BUILD FAILED in 8m 40s; 4 clases, 21 tests, 1 fallo, 0 errores u omisiones, rc 1, 1003 s con cola. RirWaveTest 2/2, WeeklyAdjustmentEngineTest 11/11 y WeeklyCheckinEngineTest 7/7 aprobaron. El único método de NativeProfileRecipeAndFitterTest ejecutado falló en la aserción intermedia de semana 5, línea 310. Las primeras diez líneas literales son:

```text
NativeProfileRecipeAndFitterTest > weeks_deload_and_rir_follow_section_12_1_with_the_dev_r2_07_wave FAILED
    java.lang.AssertionError: expected:<[2]> but was:<[1]>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:120)
        at org.junit.Assert.assertEquals(Assert.java:146)
        at com.example.kpkn.domain.training.NativeProfileRecipeAndFitterTest.weeks_deload_and_rir_follow_section_12_1_with_the_dev_r2_07_wave(NativeProfileRecipeAndFitterTest.kt:310)

21 tests completed, 1 failed

```

Intentos: (1) referencia estática incorrecta a una tabla de instancia; se extrajo la correspondencia sin cambiar sus valores. (2) esperado principiante constante 3 frente a la onda 3/3/3/2/2; se actualizó ese oráculo. (3) la corrección fue incompleta: el mismo método aún esperaba 2 en semana 5 de intermedio, donde la onda solicitada 3/2/2/1/1 genera 1. No se modifica esa aserción después del tercer fallo ni se lanza una cuarta corrida. Android sigue pendiente.

Corrección concreta propuesta para revisión: reemplazar las tres aserciones parciales de intermedio por la comparación explícita de las cinco semanas `[[3], [2], [2], [1], [1]]` contra `(0..4).map(::rirs)`, y corregir el comentario del caso. Después, ejecutar sólo ese método. No requiere modificar la prescripción de producción. El usuario pidió una explicación antes de autorizar; no hay aprobación adicional concedida.

El objetivo global permanece incompleto. El contrato P9 está documentado y sus archivos bajo drafts/P9 son borradores sin aplicar ni verificar. La rama y el WIP actual se conservan; sin push ni integración en master.

### Autorización adicional del usuario

Tras recibir la explicación del caso, el usuario respondió «Ok. autorizo.». Autoriza corregir el oráculo intermedio completo a 3/2/2/1/1 y una cuarta verificación sólo del método fallido, seguida de la continuación del plan. Se aplica esa corrección sin cambiar la prescripción de producción. El límite ordinario permanece para las demás verificaciones.

Cuarta verificación autorizada aprobada: `kpknjvm-KPKNFit-20261025-153939.log`, BUILD SUCCESSFUL in 57s; 1 clase, 1 test, 0 fallos/errores/omitidos, rc 0, 102 s con cola. T-20 guardada en `345621956`; T-21 en `4b4e942ad`; T-22 en `17ae0cac7`; T-23 en `4cf700de3`. Las otras clases mantienen sus resultados por clase antes descritos; no se afirma una corrida nueva completa de todo el conjunto.

La solicitud Android de ocho clases (sesión 31150) permanecía confirmada en cola, detrás de otra compilación del checkout principal. Se canceló únicamente esa solicitud propia antes de que iniciara Gradle para agrupar T-24/25 y la UI de P9 en una corrida de ola. Resultado: NOT RUN; no fue una verificación aprobada ni un fallo de tests.

## P9 adaptado: evidencia, rangos explícitos y propuestas de planificación

Código aplicado desde los borradores. Primera verificación de base: `kpknjvm-KPKNFit-20261025-155835.log`, BUILD SUCCESSFUL in 3m 37s; 5 clases, 21 tests, 0 fallos/errores/omitidos, rc 0, 279 s con cola. CalibrationEvidenceTest 6, PlanningVolumeTargetsTest 3, WeeklyCycleEvidenceTest 2, WeeklyCheckinEngineTest 8 y WeeklyCheckinJsonTest 2.

El resumen conserva registros y procedencia, filtra ejecución/fecha/zona y evita contar identidades contradictorias como cobertura. El editor de rangos cambia sólo el grupo elegido sobre el último programa; conserva sesiones, receta y perfil global. La nota de la hoja histórica se guarda en el programa y en VolumeCalibrationProfile mediante el callback de originales, después de publicar los tres valores legacy; VolumeView permanece intacto.

Para conservar la adaptación asistida prevista en T-91/94, se añaden propuestas de máximo de planificación por ciclo: hasta −2 con dos respuestas consecutivas 4/4 y dosis al menos en la referencia intermedia; hasta +2 con cinco respuestas consecutivas 1–2/4 cerca del máximo elegido. Envolvente editorial ±40 %, sin saltos mayores de 2 ni cambios automáticos del rango. La persona revisa antes/después y motivo, confirma o descarta; la decisión se registra por ejecución/ciclo/grupo, con guard de instantánea y recálculo al confirmar.

La respuesta ahora identifica la semana a la que se refiere, además de la semana de destino. Al entrar en descarga puede recogerse la respuesta de la última semana de entrenamiento, conservando la descarga. Las respuestas antiguas sin esa identidad permanecen originales y no generan propuestas de rango. Los límites confirmados conservan nota y procedencia; el ciclo por sí solo no los modifica.

Verificación de esta ampliación en curso: sesión 37609, log `kpknjvm-KPKNFit-20261025-162618.log`, ocho clases dirigidas. Android, build e instalación de esta ola permanecen pendientes. El transporte iOS de los nuevos campos de Program usa opaqueFields existente; weeklyCheckinEnabled se conserva como opcional en Settings.swift. No hay swift/swiftc disponible en este host y no se atribuye compilación o QA iOS.

Verificación ampliada de P9 aprobada: `kpknjvm-KPKNFit-20261025-162618.log`, 8 clases, 41 tests, 0 fallos/errores/omitidos, BUILD SUCCESSFUL en 4m 11s, 334s con cola. T-90/91/92/94 e interoperabilidad opcional iOS guardadas; no se atribuye compilación iOS.

## Android ola B — primer intento

Sesión 21592: 735s de espera, BUILD FAILED en 12m39s; 15 clases, 228 tests, 6 fallos y 222 aprobados, rc1. Las 79 fuentes congeladas conservaron su SHA-256 (0 diferencias). XML completo en artifacts/programs-current-validation/android-wave-b/attempt1. Compilaron todas las fuentes de producción y tests. Pasaron las 89 pruebas del VM de detalle, 8 del VM de catálogo, 10 de cabecera, 8 del informe corregido y los contratos de progresión/persistencia incluidos. No se afirma un build de APK ni QA en dispositivo.

Los seis errores pertenecen a las cuatro clases Compose de hojas. Tres acciones táctiles no ejecutaron sus callbacks en gráficos LEGACY, y dos nodos de texto fusionados no tenían un ancestro Scroll. El repositorio ya documenta el mismo problema de recorte en BodyRecordSheetTest: usar GraphicsMode.NATIVE y terminar la animación de entrada. Se adopta ese entorno conservando toques reales, aserciones de datos y el flujo de confirmación; los textos informativos usan el árbol sin fusionar para desplazarse. Segunda comprobación sólo de las cuatro clases fallidas, sin cambios de producción.

Segundo intento Android, sesión 71639: 180s en cola; BUILD FAILED en 4m24s; 4 clases, 7 tests, 6 aprobados y 1 fallo, rc1. XML preservados en android-wave-b/attempt2. WeeklyCheckinSheetTest 2/2, ProgramPlanningVolumeTargetSheetTest 2/2 y PlanningRangeProposalsSheetTest 2/2 aprobados. Resta VolumeCalibrationOriginalsTest: `Semantic Node has no parent layout with a Scroll SemanticsAction` en la etiqueta «Notas opcionales». KpknTextField dibuja esa etiqueta separada del BasicTextField; seleccionar la etiqueta no selecciona el editor. Se conserva la aserción de la etiqueta y se selecciona la acción editable real para escribir la nota. Tercera comprobación únicamente de esa clase, sesión 60337, aún sin resultado.

T-24 guardada en 446ea9f13 con sus dos pruebas Compose aprobadas en el segundo intento y tres casos de persistencia/confirmación del VM aprobados en el primero. T-25 guardada en 1aa325575; ProgramHeaderModelTest 10/10 en el primer intento. La corrección de ventana de ejecución/ciclo del informe está en 69be29467 con ProgramProgressReportTest 8/8. Estos resultados por clase no representan una nueva corrida completa de 228 tests.

P3 sigue en borradores bajo drafts/P3: modelo aditivo, motor descriptivo de revisión, propuestas protegidas y lifecycle con pruebas preparadas. No se consideran implementados ni verificados. Se documentaron diferencias vigentes: startProgram no reinicia una ejecución COMPLETED, startFreshSimpleCycle destruye la estructura compleja, porcentajes ausentes no permiten afirmar un aumento en kilos y una descarga no recibe ajustes de entrenamiento. El resto de C/D/E y el cierre amplio continúan pendientes.

Tercer intento Android aprobado: sesión 60337, 735s en cola, BUILD SUCCESSFUL en 4m04s, rc0; VolumeCalibrationOriginalsTest 1 test, 0 fallos/errores/omitidos. XML guardado en android-wave-b/attempt3; las 79 fuentes conservaron sus hashes (0 diferencias respecto al snapshot de ese intento). La ola B tiene resultados verdes por clase: 222 tests en el primero, 6 reparados en el segundo y 1 reparado en el tercero (uno de esos seis ya había pasado en el primero). No se afirma una nueva corrida global de 228 ni el cierre de las siete familias.

Retirada verificada de nuevo con verify_post_rings_retirement.py: 0 active retirement violations; controles positivos/anillos físicos aprobados. Room35 conserva hash 29f2facea80c338491dc3ccd12e8a91d9a83c4a4 y data/db + schemas no tienen diff contra 1d399c1fc. APK, instalación y QA funcional de esta ola permanecen pendientes.

T-93 guardada en 33698e7fa tras esos resultados por clase.

## P3 aplicado: modelos, revisión y ajuste protegido

T-30 en 1b18558b9, T-31 en 295a8d0a9, T-32 en ff80f70e3, T-33 en ede7d54c3. Primer intento JVM: kpknjvm-KPKNFit-20261025-173255.log, compilación principal aprobada y compilación de tests fallida al omitir ExternalBlockFixture.kt del filtro; 0 tests ejecutados. Se añadió el fixture existente, sin modificarlo.

Segundo intento, kpknjvm-KPKNFit-20261025-173803.log: 8 clases, 87 tests, 1 fallo, 0 errores/omitidos; 166s con cola, BUILD FAILED en 1m21s. XML en jvm-p3-attempt2. El test de ajuste comparaba ProgramHierarchyLocation completos, incluidos padres que contienen el bloque modificado; se corrigió la comparación a ProgramWeek para comprobar el contenido de las semanas que deben conservarse. Se mantuvieron las aserciones de sesión protegida, RIR aplicado, receta fuente e idempotencia. También se comprobó el guard OFF explícito en el ajustador.

Tercer intento aprobado: kpknjvm-KPKNFit-20261025-174129.log, 8 clases, 87 tests, 0 fallos/errores/omitidos, rc0, 281s con cola, BUILD SUCCESSFUL en 3m57s. XML en jvm-p3-attempt3. Desglose: BlockReviewEngineTest 7, BlockReviewJsonTest 2, BlockReviewLifecycleTest 6, NextBlockAdjusterTest 4, BlockTransitionEngineCompetitionTest 5, BlockTransitionEngineTest 8, ProgramProgressCycleCloseTest 45 y ProgramProgressEngineTest 10.

Se cubre el cierre repetible: la revisión corresponde al ciclo cerrado y la propuesta al ciclo nuevo. No se ajustan descargas, sesiones iniciadas, semanas ya ejecutadas o protocolos de autor; AUTO se trata como confirmable. Las recetas sólo RIR ofrecen RIR −1 cuando mejoran los registros; no se registra un aumento ficticio de kilos. La cobertura baja ofrece inicio suave como alternativa y conserva el plan por defecto.

La ruta final de 1RM tenía otro punto de terminación: se añadió ensureContinuation también a su resolución. Corrección guardada en 57cd0eb9c.

## Continuación real: verificación pura aprobada, Android pendiente

Primera corrida de la ampliación aprobada: kpknjvm-KPKNFit-20261025-175152.log, 9 clases, 93 tests, 0 fallos/errores/omitidos, rc0, 315s con cola, BUILD SUCCESSFUL en 4m15s; XML en jvm-continuation-attempt1. Son las 8 clases anteriores con una regresión adicional del 1RM final (BlockReviewLifecycleTest 7) y ContinuationProgramFactoryTest 5.

La continuación prepara una copia independiente para no borrar recetas efectivas o decisiones de la ejecución terminada. Repetir reconstruye la receta fuente con marcas confirmadas; los cambios manuales y fechas anteriores quedan en el programa original. Sin receta, duplica la estructura con IDs nuevos. Mantenimiento prepara una semana real con aproximadamente la mitad de series de fuerza, RIR 4 y carga pendiente de elegir; el cardio conserva su prescripción. El origen se guarda como decisión y el ID de destino es estable por origen/ejecución/opción.

T-34 contiene WIP de UI y repositorio: vista previa/confirmación, guards de instantánea y sesión iniciada, transacción de dos programas para elección + copia, activación sólo tras persistir y navegación real al catálogo. Se añadió prueba de rollback después de insertar la primera fila. Nada de ello acredita todavía compilación Android ni QA. T-35 y las tareas restantes continúan pendientes.

## P7 en curso

T-70–72 aplicadas, aún sin verificar: tabla literal de variantes por fase, uso del filtro de equipo del lugar del generador y actualización conjunta de identidad de catálogo. Las variantes no heredan el 1RM/TM de otra configuración. Se reutilizan placeOf/availabilityFor del generador mediante visibilidad internal; se conservan los básicos al ajustar tiempo/volumen. Tres plantillas nuevas pasan de receta v2 a v3; no se reescriben snapshots existentes. La corrida de siete clases está en sesión 10939, log kpknjvm-KPKNFit-20261025-181203.log. No hay resultado aún.

## P7 y carga planificada: resultados consolidados

T-70 49fe43c46, T-71 c448d9d54, T-72 77da999b8. Primer intento kpknjvm-KPKNFit-20261025-181203.log: 7 clases, 145 tests, 144 aprobados y 1 fallo; 377s con cola, BUILD FAILED en 5m26s. El único fallo esperaba contentVersion 2 para las tres recetas cuya publicación v3 exige T-72. Se actualizó esa expectativa concreta conservando las demás versiones. Segundo intento kpknjvm-KPKNFit-20261025-182007.log: FidelityRegressionTest 72/72, 192s con cola, BUILD SUCCESSFUL en 1m22s. XML en jvm-p7-attempt1/attempt2. Resultado verde por clase, no nueva corrida global de 145. El techo C1–C10 sigue en 18.

Primera verificación de sustitución y proyector kpknjvm-KPKNFit-20261025-182743.log: 3 clases, 19 tests, 0 fallos/errores/omitidos; 351s con cola, BUILD SUCCESSFUL en 4m24s. ExerciseSwapOptionsTest 8, ProgramExerciseSwapTest 6, PlannedLoadProjectorTest 5. T-82 guardada en 7201e3044.

Ola Android C, primer intento, sesión 25557: 15 filtros y 112 fuentes congeladas, aún esperando cola al registrar esta nota. El filtro PlanInfoModelTest tenía un paquete incorrecto; no se contará como ejecutado aunque aprueben los demás. Se incluirá su nombre real com.example.kpkn.screens.programs.PlanInfoModelTest en la siguiente comprobación necesaria. No hay aún APK, instalación ni QA de esta ola.

## Android C — primer intento y corrección de descubrimiento

Sesión 25557: 585s en cola, BUILD FAILED en 15m05s, rc1. Producción y tests compilaron. 14 clases seleccionadas reales: 113 casos contabilizados, 112 aprobados y un initializationError; los 100 métodos de ProgramDetailViewModelTest no llegaron a ejecutarse. Las 112 fuentes mantuvieron SHA-256 idéntico (0 diferencias). XML en android-wave-c/attempt1.

Causa literal: Method planned_load_uses_the_next_prescribed_week_and_disappears_after_completion() should be void. El método nuevo terminaba en withTimeout { StateFlow.first { it == null } }, por lo que Kotlin infería un retorno nullable en vez de Unit. Se añade únicamente el tipo de retorno explícito Unit; todas las aserciones y el timeout permanecen. Segunda comprobación: clase completa del VM más PlanInfoModelTest con el paquete correcto; las otras 13 clases ya están verdes.

Desglose aprobado: TrainingPlanRecipeJsonCompatTest 19, ProgramProgressReportTest 8, BlockReviewSheet 2, ContinuationSheet 2, DayModule 5, ExerciseSwapEntry 1, HeaderCompose 5, HeaderModel 10, PlannedLoadModule 1, SwapSheet 2, DetailFormat 20, PlanDisplaySummary 29 y ProgramsViewModelTest 8. No se acredita todavía la integración del VM ni APK/QA.

Núcleos puros guardados sin editar las fuentes congeladas: sustitución T-73 b2b622062 (6 pruebas propias, más 8 de opciones existentes) y fábrica de continuación T-34 63f40a5d3 (5 pruebas). La integración compartida Android permanece WIP hasta su gate.

T-73 UI guardada en 4e62585a3 (entrada accesible, alcances y confirmación de material; 3 tests Compose verdes). T-83 módulo guardado en 4783a2ae9 (1 test Compose verde). La conexión compartida con repositorio/ViewModel espera el segundo intento Android C, sesión 84229, que tomó ranura tras 90s de espera.

Gate estático posterior a C: verify_post_rings_retirement.py terminó rc0, «0 active retirement violations; positive/physical-rings control checks passed». data/db y schemas siguen sin diff contra la base. La referencia 29f2facea80c338491dc3ccd12e8a91d9a83c4a4 de Room35 es su identificador de blob Git, idéntico al de 1d399c1fc; SHA-1 de bytes FF5232A1CE77697D59FA3DDCD145CDB344D46BDF. No confundir el hash de blob Git con SHA de bytes.

Android C segundo intento aprobado: sesión 84229, 90s de cola, BUILD SUCCESSFUL en 5m27s, rc0; 2 clases, 152 tests, 0 fallos/errores/omitidos. ProgramDetailViewModelTest 100 y PlanInfoModelTest 52. XML en android-wave-c/attempt2. Las 112 fuentes mantienen SHA-256 idéntico al snapshot de este intento (0 diferencias). Junto con las 13 clases verdes del primer intento, las 15 clases previstas tienen resultados verdes por clase; no se afirma una nueva corrida conjunta de todas.

Tras guardar el snapshot terminal se corrige el callback de MainActivity para activar ProgramCreationRequests.openSheet antes de navegar a Training: el selector real del catálogo se abre al entrar. La revisión estática confirmó que ProgramsScreen consume esa señal en su LaunchedEffect. Este ajuste de navegación posterior al test se compilará en el siguiente gate Android/build; aún no tiene QA de navegación en dispositivo.

## P10 aplicado, primera comprobación de motor

T-100–102 están aplicadas en WIP: saltos declarados con clave inequívoca ejecución/ciclo/semana/sesión, petición de sesiones sin registro, vuelta suave explícita y detector de fechas válidas. La cuenta de cobertura sigue usando logs reales. El bloque usa su ciclo/instancia real y cuenta los saltos declarados; una elección DO_THIS_WEEK permite reconciliar un backfill real cuando todo el bloque queda completo. La continuación nueva limpia las decisiones de salto de la ejecución anterior.

Primer intento kpknjvm-KPKNFit-20261025-192146.log, sesión 53388: producción compilada; compileTestKotlin falló por filtro incompleto, 0 tests ejecutados, BUILD FAILED en 3m35s, rc1. Las 9 fuentes conservan sus hashes. CatalogCompositionTestSupport requiere su archivo y CatalogV2TestFixture; BlockReviewTestFixture vive realmente dentro de NextBlockAdjusterTest.kt, no en un archivo homónimo. Se corrigen las inclusiones sin editar esos fixtures ni las aserciones. El segundo intento añadirá también los 4 casos de NextBlockAdjusterTest.

La conexión T-103 del repositorio/VM/pantalla está aplicada como WIP, sin compilación Android todavía. Tiene preview separado de confirmación, guards de programa/settings/logs/cursor/evidencia y sesión en curso, reavance con una sesión real del cursor, cinco casos nuevos de VM y tres Compose preparados. El test puro sigue en su gate; no se atribuye resultado verde a estos cambios.

Segundo intento P10 aprobado: kpknjvm-KPKNFit-20261025-192719.log, sesión 56809, 10 clases, 93 tests, 0 fallos/errores/omitidos; 103s con cola, BUILD SUCCESSFUL en 48s, rc0. XML en jvm-p10/attempt2; las 12 fuentes conservan sus hashes. Desglose: MissedSessionPolicyTest 6, InactivityDetectorTest 2, MissedSessionJsonTest 1, ProgramProgressEngineTest 10, ProgramProgressCycleCloseTest 45, BlockTransitionEngineTest 8, BlockTransitionEngineCompetitionTest 5, ContinuationProgramFactoryTest 5, BlockReviewLifecycleTest 7 y NextBlockAdjusterTest 4. La UI/VM/repositorio T-103 sigue pendiente de gate Android de la ola D.

T-100/101 guardadas en ba690ce0f y T-102 en 435c33be2. T-103 sigue en WIP Android; todavía no hay compilación/test de esa conexión.

Primera verificación de P11 puro + T120 aprobada: kpknjvm-KPKNFit-20261025-193125.log, sesión 5011, 3 clases, 10 tests, 0 fallos/errores/omitidos; 412s con cola, BUILD SUCCESSFUL en 4m36s, rc0. CompetitionPlanningTest 5, CompetitionModelsJsonTest 1 y SeasonAdvisorTest 4; XML en jvm-meet-season/attempt1. Las 9 fuentes conservaron sus hashes. Pesaje es una extrapolación aritmética, no una garantía ni pauta de recorte. Las referencias de front squat, mancuernas/máquina y trap-bar no se transfieren a SBD de competición. Android/persistencia/UI de P11 y temporada aún pendientes.

Primer intento de T122/T123/T116: kpknjvm-KPKNFit-20261025-195024.log, sesión 87672, 5 clases/36 tests, 4 fallos, 0 errores/omitidos; 251s con cola, BUILD FAILED en 3m20s, rc1. 9 fuentes sin cambios de hash. XML en jvm-season-weekly/attempt1. Aprobaron WeeklyAdjustmentEngineTest 14, WeeklyCheckinEngineTest 9, WeeklyCheckinJsonTest 2 y HomeCompetitionResolverTest 5; SetupNutritionPreparationTest 2/6.

Los tres casos antiguos que esperaban kcal de fuerza contradicen el contrato vigente explícito de NutritionTrainingCalendarAdapter: fuerza/sesiones mixtas no son estimables. Se cambian a NotEstimable (conservando la distinción frente a Rest) y el reparto provisional uniforme, sin inventar gasto. Se mantienen READY, presupuesto exacto 14000, macros, fechas y snapshot semanal. El cuarto fallo nuevo suponía erróneamente que un manual completo sin dirección debía quedar sin plan: la preparación canónica ya usa MAINTENANCE como fallback manual. La política de sugerencia se separa como suggestedDirection; sólo strength añade esa sugerencia y HYPERTROPHY/sin programa conservan null, sin bloquear la preparación canónica ni alterar explícitos. TRACKING_ONLY sigue sin plan. Se repetirá sólo SetupNutritionPreparationTest, segundo intento; las otras 4 clases permanecen verdes por clase.

T110 guardada en b2338e58f, motores T111–114 en 46e56e2c2 y T120 en dedc4103c. P11 repositorio/UI y P12 integración continúan WIP sin validación Android. El transporte Swift es opcional y conserva los campos nuevos al copiar un record; su test de JSON está preparado, sin compilación ni QA Swift disponibles.

Segundo intento de preparación nutricional aprobado: kpknjvm-KPKNFit-20261025-195659.log, sesión 6748, 1 clase/6 tests, 0 fallos/errores/omitidos; 214s con cola, BUILD SUCCESSFUL en 2m54s, rc0. XML en jvm-season-weekly/attempt2; 9 fuentes sin cambios de hash. Las cinco clases de la ampliación tienen evidencia verde por clase: 30 tests en el primero y los 6 de preparación en el segundo, sin afirmar nueva corrida global de 36.

La integración P12 lee la dirección, objetivo y eerKcal del plan nutricional activo confirmado en la misma Room, junto con observaciones corporales originales para measuredWeeklyRate. Un observador de invalidación refresca la tarjeta después de persistir cambios, sin modificar contabilidad ni metas. La confirmación semanal vuelve a comprobar ese contexto dentro de la transacción que escribe el programa; no sólo antes de esperar el candado. Su nueva prueba provoca una edición nutricional justo antes de ese chequeo. Todo ello sigue WIP pendiente de Android D.

## Android D — primer intento

Sesión 34851, BUILD FAILED en 15m34s, rc1: 18 clases, 212 tests, 207 aprobados y 5 fallos; 0 errores/omitidos. Las 139 fuentes conservaron SHA-256 exacto. XML en android-wave-d/attempt1. Los 5 fallos están sólo en ProgramRepositoryFinalizeWorkoutTest (6/11): finalizar programas sin receta exigía resolver CompositionMetadataHolder aunque esa ruta no materializa una receta. Se conserva el guard estricto para sourceRecipe != null y se permite el provider opcional para programas sin receta; resolver SKIP/DO_THIS_WEEK tampoco requiere inventar metadatos. El caso puro de salto ahora pasa null explícito y mantiene sus aserciones de avance y ausencia de logs falsos.

Las otras 17 clases aprobaron: VM 112, ScheduleModule 3, CompetitionModule 3, receta/dbJson 20, modelos competición 1, recordatorios 1, AddProgramNow 5, MutateProgramNow 3, CompetitionPlanning 5, SetupNutritionPreparation 6, ContinuationFactory 5, WeeklyAdjustment 14, WeeklyCheckinEngine 9, HomeCompetitionResolver 5, Inactivity 2, MissedSessionJson 1 y MissedSessionPolicy 6. No se afirma cierre de D: queda la reparación de finalización y la revisión posterior SBD/ruta de ficha.

Después del resultado terminal se aplica también la revisión de UX documentada: sólo POWERLIFTING o CUSTOM con programa POWERLIFTING ofrece las herramientas SBD; otras modalidades abren su ficha. MainActivity conecta la ruta existente CompetitionDetail. Se añade un caso Compose de otra modalidad y un guard de preview de marcas. El segundo intento incluirá VM, CompetitionModule, FinalizeWorkout y MissedSessionPolicy; el resto conserva evidencia por clase.

Gate estático posterior a D: verify_post_rings_retirement.py rc0, 0 active retirement violations, controles positivos/anillos físicos aprobados. data/db y schemas siguen sin diff contra 1d399c1fc. No hay APK, instalación ni QA de D.

Transporte iOS complementario T94: AppSettings conserva volumeCalibrationProfile como JSONValue opcional (incluidas notes y claves futuras), sin ejecutar motores de calibración. Se añade un caso de round trip sobre un Settings completo; queda preparado, NO ejecutado: no hay compilador Swift en Windows. Program ya preserva campos JSON nuevos mediante opaqueFields. La síntesis Codable histórica de otros campos de Settings queda fuera del alcance de esta adición.

Android D segundo intento: Gradle BUILD SUCCESSFUL en 19m22s tras 645s de cola; XML frescos guardados en android-wave-d/attempt2. Cuatro clases, 133 tests, 0 fallos/errores/omitidos: ProgramDetailViewModelTest112, ProgramCompetitionModuleTest4, ProgramRepositoryFinalizeWorkoutTest11, MissedSessionPolicyTest6. Las 139 fuentes conservan SHA-256 exacto, sin deriva. Se acredita compilación y pruebas unitarias, no APK ni instalación/QA. Junto a las clases verdes del primer intento, D tiene evidencia verde por clase. Documentación T1176753c7175, T124923c57c90, T61919ee60d6; transporte opcional iOS de calibración ee08fa19e, prueba preparada no ejecutada.

D quedó guardada en 9eda33090 (integración compartida T103/115/121) y 7cdd02d75 (Inicio T116). Se agrupan los hooks compartidos de transacciones/VM/pantalla para conservar su coherencia en un único cambio revisable; excepción al reparto de un commit por tarea, sin incluir WIP ajeno. El wrapper D terminó rc0 después del resultado Gradle y del recibo de XML/hashes.

Ola E aplicada: T40/41 dos grupos con catálogo plegado y alcance de fases reales; T42 entrada de biblioteca presente en estado vacío y existente, ruta interna TRAINING_ONLY sin preselección; T43 flag defaultfalse, setter/fingerprint, fit y activación con duración literal independiente del evento. Se conservan las rutas del editor manual y el guard de deep links. Se añaden pruebas de oferta/familias, interruptor, huella y activación literal. Pesaje negativo/infinito conserva el original e indica proyección no disponible. E1: sesión52978, ocho filtros,154 fuentes congeladas. Todavía sin resultado, APK ni instalación/QA.

Revisión estática de transporte backend: models/common.py declara Program con ConfigDict(extra="allow"), por lo que los campos JSON nuevos no requieren ampliar su DTO para conservarse. No hay API de CompetitionRecord en las búsquedas de esta tarea. No se cambió backend ni se ejecutó su suite; el runtime Python disponible no tiene pytest. No se atribuye a esta revisión validación funcional del backend.

Control de arquitectura:41 archivos Kotlin de dominio modificados frente a la base,0 imports android.*. Se inventariaron36 archivos Android de pruebas nuevos en ESTADO.md; las ampliaciones en clases existentes están en sus recibos y no se cuentan como clases nuevas.

Android E primer intento aprobado: sesión52978, BUILD SUCCESSFUL en15m16s, rc0;8 clases/76 tests,0 fallos/errores/omitidos. XML frescos en android-wave-e/attempt1;154 fuentes sin deriva. SetupWizardEntrenoPlan17,ReadyWeek5,FullJourney2,EntrenoPlanStep19,CompetitionStep12,PostDischargeRouting7,ProgramsViewModel8,CompetitionPlanning6. T43 guardada2bd829db5,T4060c5a55b7,T41cdcf441ae,T423c3ea6d29 y protección T112944a3b20d.

Tras E se aplica un único ajuste visual de revisión: no pintar GeneralReveal ni Otra versión si sólo hay catálogo viable, conservando pliegue/selección. Se añade caso Compose. Este ajuste NO está acreditado por los76 tests anteriores: su prueba va en el cierre conjunto.

Cierre Android iniciado, sesión57124,154 fuentes congeladas: assembleBaseDebug + testBaseDebugUnitTest con las siete familias de II.14, las nueve clases Android exigidas y EntrenoPlanStepTest (incluye el caso nuevo). Primer intento del cierre actual; todavía sin resultado ni APK/instalación/QA. Se usa el prefijo :app:test para que el clasificador de coste de la cola reconozca la corrida amplia; la llamada E sin prefijo fue clasificada como1 por el wrapper aunque Gradle sí ejecutó sus ocho filtros correctamente. No se modifica el script ni sus guardas.

Comprobación de límites: los seis archivos congelados principales y dos helpers de cálculos adicionales siguen idénticos a la base. El único diff bajo screens/sessioneditor es1 cadena de T02 en ClonerSaveWarmupSheets.kt.

Gate estático sobre el código del cierre: scripts/verify_post_rings_retirement.py terminó rc0, «0 active retirement violations; positive/physical-rings control checks passed», log android-closure/retirement.log. physical-rings se refiere a que se conservan ejercicios/material de anillas/TRX; no es QA de hardware ni del teléfono.

assembleBaseDebug terminó durante el cierre conjunto. APK local: android-native/app/build/outputs/apk/base/debug/app-base-debug.apk,507542230 bytes,SHA256085CBF23FF0AF6CDB73F0847D77A20923F603D885C58BE3241E1D2071785A88B. aapt confirmó com.example.kpkn.dbg, KPKN Dev en idioma base/en/es y MainActivity com.example.kpkn.MainActivity. Versión heredada52/KPKN Pre Release18; no se subió versión. apksigner verify aprobó; certificado público SHA256f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4. Recibos runtime-qa/apk.json,apk-badging.txt,apk-certificate.txt. Todavía no instalado; pruebas amplias en curso. La aceleración WHPX del emulador es utilizable al comprobar fuera del sandbox; no se modificó Windows.

Cierre Android1: sesión57124,465s de cola, BUILD FAILED en39m58s, rc1.286 clases/3074 tests,3069 aprobados,5 fallos,0 errores/omitidos;154 fuentes sin deriva. XML y summary/failures.json en android-closure/attempt1. RecipeContractInventoryTest6/6 y EntrenoPlanStepTest20/20 aprobaron, junto con las nueve clases Android exigidas. La corrección de sección vacía se guarda58ec7525d.

Los cinco mensajes se revisaron juntos. Reparaciones sólo en tests: (1) Cube tiene tres olas y semana Test de autor de1semana por T11: se quita de finiteSimpleIds y se admite únicamente ese par protocolo/bloque, sin cambiar C1–C10/techos; (2) se eliminan las tres excepciones muertas Rings/Tus RINGS/intro del test de copy, reforzando la lista cerrada tras la retirada; producción y test de ese catálogo estaban intactos frente a la base, sin coincidencia en ROJOS_BASE.txt; (3) power-12-3 publica contenido4 tras T14, mientras power-16-4 y power-20-5 mantienen3: se conserva la comprobación individual y todos los asserts de AMRAP/calibración; (4) se fijan ondas principiante3/3/3/2/2 e intermedio3/2/2/1/1, sin cambiar las seis semanas/descarga ni la implementación RirWave; (5) el fixture de persistencia nativa instala el proveedor de composición de pruebas y restaura el anterior, como el catálogo inicializado de la app. Ningún assert de persistencia/propuesta se elimina; NativeWorkoutProgressionRuntime productivo sigue congelado.

El diagnóstico del trabajador mostró avance en RedistributionSweepTest/DayAssigner.localSearch y renovación de procesos por forkEvery5, no bloqueo. Se verificó autorización humana específica en el chat Auditar sesión de entreno en vivo y se coordinó la cola con ese chat sin interrumpir ni modificar sus procesos/archivos. El cierre1 liberó la ranura normalmente.

Cierre Android2 encolado: sesión96842,159 fuentes congeladas; mismo conjunto completo y assembleBaseDebug. Producción conserva exactamente los hashes del APK verificado; sólo cambian las cinco clases de pruebas. Aún sin resultado.

Las nueve clases Android exigidas sí se ejecutaron y aprobaron252 tests en cierre1. El paquete real de HomeCompetitionResolverTest es com.example.kpkn.domain.training (5/5); quedó cubierto por domain.training.*. El filtro screens.home era redundante y no coincidía; no se cuenta como otra clase ejecutada. Recibo required-android-classes.json con los nueve nombres reales.

Se inicia el AVD propio KPKN_Programs_Current_QA en emulator-5580, sin ventana ni borrado de datos. El nombre del AVD se verifica por consola antes de instalar. El emulador5590 ya abierto y el teléfono físico se conservan sin interacción. La regresión2 está en cola detrás de la integración autorizada. Producción sigue idéntica al APK de cierre1.

Cierre Android2 aprobado: sesión96842,795s de cola, BUILD SUCCESSFUL en22m33s, rc0.286 clases/3074 tests,0 fallos/errores/omitidos;159 fuentes sin deriva. XML/summary en android-closure/attempt2. Cobertura real: training210clases/2038tests; competitions6/18; season1/4; protocols13/142; programs9/101; onboarding32/424; templates6/80, más9 clases de pantallas. Las cinco correcciones de tests quedan en36ae90fc7. El APK Room35 sigue idéntico y válido, sin instalar en el teléfono.

Orden posterior del usuario: usar Z Flip por depuración inalámbrica y cerrar todos los emuladores. Se cerraron5580/5590 por sus consolas y no quedan procesos de emulador;5562 es sólo una entrada offline del servidor. No se reinicia el servidor ADB para conservar la conexión inalámbrica.

Preflight Z Flip bajo phone.lock: firmaf14259... coincide, KPKN Dev instalada, Room36 identidad907a5e4df31b4ead1ee0f8d86ce559cf. No se instaló Room35 ni se abrió/modificó la app. Copia privada sólo de lectura de la DB/WAL para comprobar metadatos, fuera del repositorio. Se usa una copia local de phone_run con un modo de inspección y acciones UI/log acotadas;11 funciones originales de candado/identidad/guardas mantienen AST idéntico y el script compartido C:/kw/tools/phone_run.py no se modifica. La orden actual del usuario autoriza la sesión supervisada.

QA física requiere compatibilidad36: worktree aislado programs-phone-room36 creado desde58ec7525d, integra origin/master1d5ba0ce7 sin conflictos para conservar los campos actuales de historial. Sólo allí se copian6 archivos de soporte del esquema36 existente (manifest/DAOs/helper/KpknDatabase/NutritionCatalogStorage/36.json), con hashes antes/después y misma identidad del teléfono. Se excluye Entities.kt WIP porque su cambio sólo es conversión de micronutrientes, ajeno a la compatibilidad. El WIP oficial y la rama original Room35 permanecen intactos. Cinco fixtures corregidos se trasladan también a la copia. Build/validación QA1: sesión39622,121 fuentes de integración/overlay congeladas, assembleBaseDebug más VM/EntrenoPlan/ReadyWeek/FullJourney/NativeRuntime; todavía sin resultado. Esa copia no está fusionada ni publicada en master.

El usuario confirmó expresamente «Listo, abierto y desbloqueado» tras la guarda de teléfono plegado. Nueva lectura supervisada previa a instalar: KPKN Dev versionCode52, misma firmadebug, Room36/907a5e4df31b4ead1ee0f8d86ce559cf. Snapshot privado local fuera del repositorio; huellas de originales disponibles en phone-before-install/preflight.json: workout_logs4,training_observations4,body_observations2,workout_media7; nutrition_logs/competition_records/nutrition_food_usage0. Se comprobará conservación tras QA sin publicar los datos privados.

El KpknDatabase_Impl generado en la copia de QA confirma la identidad907a5e4df31b4ead1ee0f8d86ce559cf (no sólo el JSON copiado). Compatibilidad36 del esquema compilado acreditada; todavía sin instalación de este nuevo APK en el Z Flip.


## Cierre final T60/62 — 25-oct-2026

QA1 compiló el APK compatible36 pero KSP de tests falló antes de ejecutar casos: el fixture publicado MFP31 reutilizaba el NutritionDao actual, que ahora referencia nutrition_food_usage. Se separó un DAO histórico de cuatro métodos únicamente en el fixture QA, sin cambiar versión31, lista de entidades ni esquemas publicados. QA2 terminó rc0, BUILD SUCCESSFUL5m10s: seis clases/181 tests, cero fallos/errores/omitidos,122 fuentes sin deriva. Producción/APK no cambiaron por esa reparación de test. La rama original conserva el cierre conjunto3074/3074 y Room35.

APK QA SHA256347EFEAAC5006C0387E1978EDD8BBEE1FEAEEED9270A54325FA26F2D18A3802E: com.example.kpkn.dbg/KPKN Dev, versión52, certificadof14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4, Room36 identidad907a5e4df31b4ead1ee0f8d86ce559cf. Actualización-r Success y MainActivity Statusok, arranque frío4168ms. Sin borrar datos, desinstalar, migrar a una versión inferior ni tocar la app personal.

QA supervisada mediante phone_run/phone.lock: biblioteca con entrada a medida, navegación de datos básicos del asistente y salida, alcance por bloques, progreso comparable y contexto de temporada. No se inventaron datos personales ni se activó otro programa. La sesión en curso no se reanudó ni canceló. Home tuvo un fallo de espera de inactividad de UiAutomator; las capturas y dumps estándar posteriores funcionaron desde la ruta normal de entrenamiento. La revisión automática rechazó un helper adicional de UIAutomation por riesgo de eludir el control de acceso; no se creó ni ejecutó. Se continuó sólo con UiAutomator estándar bajo las guardas originales.

Después de QA: siete tablas originales conservan exactamente cantidades y SHA256; también ongoing_workout, programs y settings. setup_drafts pasa de0 a1 por el borrador generado aquí; Salir lo guarda automáticamente y no hay llamada de descarte accesible en la UI actual. Se conserva sin activar, sin manipular la DB para limpiarlo. Los logs capturados del proceso no contienen marcadores FATAL EXCEPTION/AndroidRuntime/SQLiteException/ANR; no equivalen a auditoría global de crashes. Capturas/DB privadas fuera de Git.

El cierre global cumple las siete familias y las nueve clases Android exigidas en una corrida verde. Los contratos congelados y data/db/esquemas del entregable permanecen iguales a1d399c1fc. iOS/backend mantienen los límites de evidencia descritos; T104 y apartados retirados no se declaran implementados. Índice T60, arquitectura T61 y estado T62 disponibles. Sin push ni integración de esta ola en master. Evidencia portable en VALIDACION_FINAL.json; recibos completos y borradores históricos en el archivo local programs-evidence indicado allí.

Snapshot de la copia QA guardado localmente en1743c645f, detached HEAD, estado limpio, sin push. Se conserva el worktree como procedencia revisable del APK instalado. La comprobación de whitespace del merge encontró una línea vacía final en ProgramRepositoryStartupFlushTest entrante de master; se preservó sin alterar código ajeno. El chat vuelve a usar programs-current. Cierre documental finalizado26-oct.
