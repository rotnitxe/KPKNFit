# AUD2 · Funcionamiento del editor y del catálogo

Auditoría del 20-10-2026 en `C:\kw\u11`, rama `feat/screens-v2`. Punto de partida: `cacde0aae`; referencia pedida: `8ac57c9d4`. Alcance: `docs/screens-v2/maps/MAPA_editor_catalogo.md`. Sin cambios de diseño, teléfono, emulador, adb ni push. El archivo de AUD1 no forma parte de este trabajo.

**Resultado final:** 28 bugs funcionales corregidos (8 S1, 19 S2 y 1 S3), 47 pruebas nuevas y 13 commits de código/pruebas, más este informe. Compilaciones y suites afectadas en verde; quedan cinco pendientes documentados y la verificación manual del orquestador en el Z Flip.

## Cobertura

| Área | Revisión y comprobación |
| --- | --- |
| Sesión y persistencia | Carga inicial, reintento, borrador, guardado automático y explícito, errores, salida, cambio de día/semana, principal y variantes A/B/C/D. |
| Estructura | Vacía, solo cardio, grupos, ejercicios sueltos, arrastre, destino desaparecido, 20 ejercicios, borrado y deshacer, superseries de 2–4 miembros y rondas vacías. |
| Series y preparación | Los siete modos, 0/1/30 series, unilateral y lados, carga/RM, duplicar, aplicar a todas, aproximación, movilidad, técnicas y unidades. |
| Herramientas | Reglas, límites, tiempo, plantillas antiguas/propias, versiones, transferir completa/parcial y APPEND/REPLACE. |
| Catálogo | Búsqueda y filtros, configuración/variantes, selección múltiple y orden, superserie, reemplazo, ejercicios propios, elección para Técnica y ficha por contexto. |
| Reutilización | Editar series/cardio en vivo, importador y su búsqueda, alternativas «Cambiar» del asistente/wizard. |

La lectura y las pruebas JVM/Robolectric no sustituyen la comprobación táctil, del teclado y del ciclo de vida real en el Z Flip. Los pasos para el orquestador están al final.

## Hallazgos

Cada reproducción se añadió y se observó en rojo antes de su corrección. La pasada aislada previa a los arreglos ejecutó 47 pruebas: 38 reproducciones fallaron y 9 controles pasaron. Los fixtures de navegación usan programas independientes por prueba; las hojas de Robolectric se accionan mediante semántica. Se añadieron después cuatro reproducciones de concurrencia/desplazamiento, también fallidas antes de sus correcciones. En total: 47 pruebas nuevas, 42 reproducciones fallidas antes del arreglo y 5 controles. Las cuatro pruebas existentes de `SessionEditorAuditDebugTest` también pasaron en la referencia inicial.

Las líneas de las causas se refieren a `cacde0aae`, antes de los arreglos. Los archivos sin prefijo pertenecen a `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/` (los componentes están en `components/`); `WorkoutStructureSheetsHost.kt` pertenece a `screens/workout/` y `ExerciseIdentity.kt` a `domain/exercises/`.

| ID | Sev. | Flujo | Reproducción | Causa inicial | Estado / prueba |
| --- | --- | --- | --- | --- | --- |
| AUD2-01 | S2 | Eliminar y salir/cambiar | Eliminar un ejercicio; salir o cambiar día/variante antes de 5 s. Al volver sigue ahí. | `SessionEditorViewModelStructure.kt:770`: la eliminación vive solo en un temporizador; guardar/navegar no la confirma. | Arreglado `b36dc34b2`. Reproducción: `SessionEditorAuditRegressionTest` (salida, día y variante). |
| AUD2-02 | S2 | Eliminar miembro | En acciones rápidas de un miembro de superserie, pulsar «Eliminar» y esperar. El ejercicio aparece suelto. | `SessionEditorViewModelStructure.kt:571,792`: «Eliminar» usa la operación de sacar de la superserie. | Arreglado `b36dc34b2`. Reproducción: `quickActionDeleteActuallyDeletesASupersetMember`. |
| AUD2-03 | S1 | Arrastre | Comenzar un arrastre; desaparecer el grupo destino antes de aplicar el movimiento. | `SessionEditorMoveEngine.kt:35,76`: acepta un destino inexistente y retira del origen sin insertar. | Arreglado `c87d57ec0`. Reproducción: `SessionEditorMoveEngineAuditTest`. |
| AUD2-04 | S1 | Día nuevo desde B | Abrir B; crear sesión en un día vacío; intentar escribir o añadir. | `SessionEditorViewModelNavigation.kt:127`: arrastra la variante B a una sesión nueva sin B. | Arreglado `b36dc34b2`. Reproducción: `creatingANewDayFromVariantBStartsInVariantA`. |
| AUD2-05 | S1 | Restaurar versión | Abrir B; restaurar una versión sin B; editar. Restaurar después una versión que sí contiene B. | `SessionEditorViewModel.kt:1349`: sustituye la sesión sin reconciliar variante activa/disponibles ni eliminación pendiente. | Arreglado `b36dc34b2`. Reproducciones: tres casos de restauración en `SessionEditorAuditRegressionTest`. |
| AUD2-06 | S2 | Variante inexistente | Solicitar C en una sesión que solo tiene A/B. La pantalla puede mostrar el contenido de A, pero editar no funciona. | `SessionEditorViewModelVariants.kt:127`: acepta cualquier slot sin verificar existencia. | Arreglado `b36dc34b2`. Reproducción: `selectingAMissingVariantKeepsAnEditableSession`. |
| AUD2-07 | S1 | Cambiar principal con guardado bloqueado | Dejar el nombre vacío, elegir otra sesión principal y volver. | `SessionEditorViewModelNavigation.kt:29`: recarga sin asegurar el borrador de los cambios rechazados por el guardado. | Arreglado `b36dc34b2`. Reproducción: `changingTheMainSessionPreservesWorkEvenWhenSavingIsBlocked`. |
| AUD2-08 | S1 | Navegar a importada | Cambiar de día a una sesión antigua que refleja el mismo id en sueltos y grupo. | `SessionEditorViewModelNavigation.kt:267`: el cambio omite la reparación de estructura de la carga inicial. | Arreglado `b36dc34b2`. Reproducción: `changingDayNormalizesImportedMirrorsLikeTheInitialLoadDoes`. |
| AUD2-09 | S2 | Resumen por variante | Abrir A y cambiar a una B de distinta duración sin editarla. | `SessionEditorViewModelVariants.kt:127`: no actualiza los datos derivados al seleccionar variante. | Arreglado `b36dc34b2`. Reproducción: `switchingVariantRefreshesItsDurationWithoutEditingIt`. |
| AUD2-10 | S2 | Cardio en variante | En B, mover cardio al principio mientras A lo tiene al final. | `SessionEditorViewModelCardio.kt:61,72`: lee `session` (A) para la preferencia visual. | Arreglado `b36dc34b2`. Reproducción: `changingCardioPlacementInBUsesTheActiveVariant`. |
| AUD2-11 | S1 | Transferir repetidamente | APPEND dos veces desde el mismo origen con ejercicios de catálogo; guardar. | `SessionEditorCloneHelpers.kt:279`: regenera id pero conserva occurrenceId; el cortafuegos rechaza duplicados. También copia ejecución e ids de técnicas. | Arreglado `2a69c10a0`. Reproducciones: `SessionEditorAuditCopiesTest`. |
| AUD2-12 | S2 | Transferir a día vacío | Transferir A de una sesión con variantes a un día nuevo. | `SessionEditorCloneHelpers.kt:353`: copia B/C/D del origen con las mismas identidades. | Arreglado `2a69c10a0`. Reproducción: `transferToANewDayDoesNotShareSourceVariants`. |
| AUD2-13 | S2 | Añadir/duplicar serie | Abrir datos antiguos con resultados por serie; añadir o duplicar. | `SessionEditorSessionHelpers.kt:664` y `SessionEditorViewModelSets.kt:65`: copian resultados completados e identidades de técnicas. | Arreglado `2a69c10a0`. Reproducciones: `SessionEditorAuditCopiesTest`. |
| AUD2-14 | S2 | Duplicar ejercicio | Duplicar uno con aproximación y técnicas. | `SessionEditorViewModelQuickActions.kt:45`: mantiene ids de aproximación y técnicas y resultados completados. | Arreglado `2a69c10a0`. Reproducción: `duplicatingAnExerciseRegeneratesPreparationAndTechniqueIds`. |
| AUD2-15 | S2 | Añadir tras hueco de superserie | Borrar la última ronda de un miembro y añadir una serie. | `SessionEditorSessionHelpers.kt:664`: copia `isEmptySlot=true` a la nueva serie. | Arreglado `2a69c10a0`. Reproducción: `aNewSetFromASupersetHoleIsExecutable`. |
| AUD2-16 | S1 | Editar en vivo | Cambiar el modo sin confirmar; recrear actividad o recibir actualización del ejercicio origen. | `WorkoutStructureSheetsHost.kt:1581`: borrador con `remember` y clave dependiente del objeto origen. | Arreglado `525583f9d`. Reproducciones: `WorkoutEditSeriesAuditTest`. |
| AUD2-17 | S2 | Añadir serie en vivo | En modo Distancia, tener 400 m y añadir serie. | `WorkoutStructureSheetsHost.kt:1773`: no copia `plannedTargetV2` ni todo el plan de la serie. | Arreglado `525583f9d`. Reproducción: `addingADistanceSetPreservesItsPlannedValue`. |
| AUD2-18 | S2 | Propio seleccionado | Seleccionar ejercicio propio; recrear actividad; añadir selección. | `ExercisePickerV2Catalog.kt:1705`: restauración solo conoce definiciones v2 o el mapa anterior, perdido al recrear. | Arreglado `958202d97`. Reproducción: `CatalogAuditStateTest.selectedCustomExerciseSurvivesRecreation`. |
| AUD2-19 | S2 | Reemplazo pendiente | Elegir candidato sin confirmar y recrear actividad. | `ExercisePickerV2Catalog.kt:838,1096`: propuesta no sale al estado guardable del padre y se omite su restauración. | Arreglado `958202d97`. Reproducción: `proposedReplacementSurvivesRecreation`. |
| AUD2-21 | S2 | Superserie desde catálogo | Elegir cinco y pulsar Superserie. | `CatalogContracts.kt:180` y `SessionEditorViewModelStructure.kt:228`: aceptan cinco; `SupersetRules` recorta a cuatro, dejando uno suelto. | Arreglado `b36dc34b2`, `958202d97`. Reproducciones: `CatalogAuditContractsTest` y `aCatalogSupersetWithFiveMembersCannotBePartiallyCreated`. |
| AUD2-22 | S2 | Dosis Distancia/Personalizado | Cambiar de reps a esos modos e indicar cantidad. Plegar. | `SessionExerciseDose.kt:77,95`: resume reps antiguas y omite cantidad/unidad. | Arreglado `e6b2c1e58`. Reproducciones: `SessionEditorAuditDoseTest`. |
| AUD2-23 | S2 | Contadores y tiempos | Borrar una ronda dejando hueco; comparar miembros de distinta longitud y descansos por serie. | `SessionEditorDuration.kt:25`, `SessionEditorScreen.kt:708` y `domain/calculations/Calculations.kt:678,734,750`: cuentan huecos; el descanso depende del primer miembro e ignora overrides por serie. | Arreglado `e6b2c1e58`. Reproducciones: cuatro casos de conteo/tiempo en `SessionEditorAuditDoseTest`. |
| AUD2-24 | S2 | Buscar en importador | Buscar «press», luego escribir otra consulta y tocar durante la espera. | `screens/programs/import/ImportPreview.kt:420`: deja los resultados anteriores activos durante debounce/carga. | Arreglado `9f9005150`. Reproducción: `ImportCatalogSearchAuditTest`. |
| AUD2-25 | S2 | Ficha con vídeos | Abrir un ejercicio que solo tenga vídeos personales. Intentar acceder al álbum. | `ExerciseFicha.kt:254,341`: la entrada y la sección solo consideran fotos. | Arreglado `4e5cce72e`. Reproducción: `ExerciseFichaVideoAuditTest`. |
| AUD2-26 | S2 | Reemplazo asistido | Reemplazar por una dominada asistida cuyo material sea «Peso corporal». | `ExerciseIdentity.kt:325`: prioriza peso corporal antes que asistencia. | Arreglado `34f6a020d`. Reproducción: `ExerciseReplacementAuditTest`. |
| AUD2-27 | S2 | Reemplazar en B/C/D | Abrir «Cambiar» en una variante con grupo propio. | `SessionEditorScreen.kt:277`: busca el contexto en A y no envía nombre/dosis; rellena la búsqueda con el nombre a sustituir. | Arreglado `958202d97`. Reproducción: `SessionEditorCatalogLaunchAuditTest`. |
| AUD2-28 | S1 | Editar durante la navegación | Elegir otro día y cambiar el nombre o iniciar un borrado antes de terminar la carga del destino. | `SessionEditorViewModelNavigation.kt:266`: publica el destino sin comprobar las acciones nuevas sobre el origen durante las suspensiones de guardado/carga. | Arreglado `b36dc34b2`. Reproducciones: `SessionEditorNavigationRaceAuditTest` (edición y borrado/deshacer). |
| AUD2-29 | S3 | Ir al ejercicio elegido | Elegir un nativo configurado o un propio, alejarse de su fila y tocar su nombre en la lista de elegidos. No vuelve a la fila. | `ExercisePickerV2Catalog.kt:1349`: compara la clave de configuración con un id de definición y no contempla propios. | Arreglado `958202d97`. Reproducciones: dos casos de desplazamiento en `CatalogAuditStateTest`. |

## Resultado por severidad

| Severidad | Arreglados con reproducción | Pendientes documentados |
| --- | ---: | ---: |
| S1 | 8 | 1 (motor reservado a AUD1) |
| S2 | 19 | 3 (1 decisión y 2 de AUD1) |
| S3 | 1 | 1 (decisión de control/rango) |
| S4 | 0 | 0 |
| Total | 28 | 5 |

Los pendientes de los motores reservados se señalan por lectura de código; no se presentan como reproducciones ejecutadas ni como arreglos de esta auditoría.

## Commits

Todos en `feat/screens-v2`, con `Co-Authored-By: Codex <noreply@openai.com>` y sin push:

| Commit | Cambio |
| --- | --- |
| `2a69c10a0` | Copias y transferencias con identidades nuevas y sin ejecución previa. |
| `525583f9d` | Borradores del editor de series en vivo y objetivos completos al añadir. |
| `e6b2c1e58` | Dosis, huecos, contadores y descansos. |
| `9f9005150` | Resultados de búsqueda del importador invalidados inmediatamente. |
| `34f6a020d` | Asistencia preservada en reemplazos. |
| `c87d57ec0` | Arrastre hacia destino desaparecido sin perder ejercicios. |
| `b36dc34b2` | Persistencia, borrado/deshacer, versiones, variantes y concurrencia de navegación. |
| `958202d97` | Selección, restauración, contexto y desplazamiento del catálogo. |
| `4e5cce72e` | Acceso al álbum cuando solo hay vídeos y fixture de ficha. |
| `291db74fc` | Aislamiento del lector IO de plantillas en las pruebas Room. |
| `bc29c32d6` | El hook de pruebas espera la cancelación del lector antes del cierre de Room; elimina la reflexión de la regla. |
| `4d568be30` | El fixture de ediciones manuales cierra el lector antes de cerrar explícitamente la base. |
| `bdbb3b8ca` | Las dos suites de cargas RM que construían el ViewModel sin aislamiento adoptan `RoomTestRule`, sin cambiar aserciones. |

## Pendientes y decisiones

Se descartó AUD2-20 tras completar la lectura del llamador: los propios ya esperan confirmación en reemplazo (`editingExisting && replacement == null`). Se conserva `customReplacementWaitsForTheConfirmButton` como prueba de protección; no se atribuye una corrección a ese comportamiento.

| ID | Sev. | Flujo / reproducción | Causa | Motivo |
| --- | --- | --- | --- | --- |
| AUD2-P01 | S3 | Abrir descanso importado de más de 23 min y aceptar sin cambiar. | `ui/components/KpknNativeTimePickerDialog.kt:136,153`: representa minutos como horas de un reloj 0–23. | Definir control o rango de duración admitido; cambiar el selector requiere decisión de UI. |
| AUD2-P02 | S2 | Tiempo global menor que el número de grupos; «Repartir global en grupos». | `SessionEditorViewModelVariants.kt:49,51`: fuerza al menos 1 min por grupo y puede superar el total. | Decidir si se admiten grupos de 0 min o se exige un presupuesto mínimo; sin imponer una decisión de producto. |
| AUD2-P03 | S2 | Editar/cambiar rápido mientras se calcula Impacto. | `SessionEditorViewModel.kt:1065,1081,1132`: publica un cálculo sin comprobar que sigue perteneciendo a la sesión/variante actual. | Motor reservado a AUD1; sin cambios aquí. Evidencia de lectura, pendiente reproducción de concurrencia específica. |
| AUD2-P04 | S2 | Impacto/Volumen tras eliminar rondas. | `SessionEditorAnalytics.kt:87,125,173`: varios filtros excluyen inefectivas pero no huecos vacíos. | Motor reservado a AUD1; no se modifica. El contador del marco y el desglose de tiempo se tratan en AUD2-23. |
| AUD2-P05 | S1 | Crear/editar propio con fallo de almacenamiento. | `data/repository/CustomExerciseRepository.kt:50,59`: publica optimistamente y consume errores de Room sin comunicar ni revertir. | Flujo de Crear ejercicio reservado a AUD1; evidencia de lectura, sin tocar motor/persistencia. |

## Validación

| Comprobación final | Resultado |
| --- | --- |
| `:app:compileBaseDebugKotlin` | Pasa |
| `:app:compileBaseDebugAndroidTestKotlin` | Pasa (solo compilación, sin dispositivo) |
| `:app:testBaseDebugUnitTest` con los filtros indicados | 1263 casos: 1260 pasan, 3 omitidos, 0 fallos/errores |
| Pruebas nuevas de AUD2 | 47 pasan, 0 omitidas |


Todas las órdenes Gradle se ejecutan en primer plano mediante `C:\kw\tools\gradle_slot.sh`, sin timeout corto ni builds lanzados en segundo plano. Logs, resúmenes JSON y XML completos permanecen como evidencia local sin versionar en `artifacts/aud2-editor-catalogo-20261020/`. Código, pruebas e informe quedan en los commits de esta auditoría.

- `baseline.log`: la compilación terminó con `Java heap space` tras 44 min 13 s con el límite predeterminado de 4 GB; no llegó a ejecutar pruebas.
- `red.log`: producción e instrumentadas compilaron con 8 GB. Primera ejecución de reproducciones: 47 pruebas, 39 fallos, incluyendo problemas de los fixtures nuevos.
- `red-isolated.log` y `red-isolated-xml/`: fuentes de producción idénticas a `cacde0aae`, fixtures aislados; 47 pruebas, 38 fallos de reproducción y 9 controles en verde. Aquí se basa la evidencia previa al arreglo.
- `green-suites.log` y `green-first-xml/`: ambas compilaciones pasaron; 1008 pruebas, 8 fallos y 1 omitida. Dos fallos reprodujeron AUD2-28; los restantes correspondían a tres fixtures táctiles/estado, un lector de plantillas que sobrevivía al cierre de Room y dos manifiestos ausentes por el checkout parcial. Se corrigieron los fixtures, se canceló/esperó el lector en la regla de pruebas y se materializaron los tres manifiestos exactos de Git.
- `red-navigation-final.log` y `red-navigation-final-xml/`: 30 pruebas, exactamente 4 fallos esperados (AUD2-28 y AUD2-29), sin errores ni omisiones. Vídeo, músculos, plantillas, carrusel y cobertura de imágenes pasaron con sus fixtures y manifiestos corregidos.
- `final-suites.log` y `final-attempt1-xml/`: ambas compilaciones pasaron; 1263 pruebas, 1 fallo y 3 omisiones. Las 47 pruebas nuevas pasaron. El fallo del carrusel recibió excepciones previas de hidratación de plantillas contra Room ya cerrado: `resetForTests()` cancelaba sin esperar, y los fixtures explícitos perdían la referencia antes de que actuara la regla. Se corrigió el hook de pruebas y el orden de cierre del fixture de ediciones manuales; las operaciones de producción de plantillas permanecen iguales.
- `final-verified-suites.log` y `final-attempt2-xml/`: ambas compilaciones pasaron; 1263 pruebas, 1 fallo y 3 omisiones. Quedó una excepción previa de la misma consulta, ahora recibida al entrar en `CatalogAuditStateTest`. Se localizaron `SessionEditorRmLoadEditTest` y `SessionEditorRulesRmLoadViewModelTest`: construían el ViewModel sin regla Room ni cierre del lector. Se añadió la misma regla a ambos fixtures. Todos los fixtures de ViewModel seleccionados tienen ya regla común o cierre explícito con espera.
- `final-complete-suites.log`, `final-summary.json` y `final-xml/`: **BUILD SUCCESSFUL en 5 min 41 s**. Estado de código/pruebas `bdbb3b8ca`: **1263 casos, 1260 pasados, 0 fallos, 0 errores y 3 omisiones documentadas**. Las **47 pruebas nuevas pasan, sin omisiones**. Ambas compilaciones exigidas pasan; los fuentes permanecen sin cambios después de esa validación.

El argumento de memoria de las pasadas posteriores es el autorizado: `"-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8"`. No se ejecutó `--stop` ni se mató Java.

Comparación de partida: `git diff --stat 8ac57c9d4 cacde0aae -- <editor> <domain/exercises> <data> <tests relacionados>` solo muestra 6 líneas añadidas y 3 eliminadas en `ExerciseFichaSummary.kt` y `ExerciseFicha.kt`; el resto de esas áreas coincide con la referencia. No se atribuye un fallo a la referencia sin comprobar su prueba y causa.

`reference-8ac-hashes.json` registra la comparación de blobs de `ExerciseCatalogImageCoverageTest`, `CatalogCarouselSettleTest`, `SessionTemplateRepository` y los tres manifiestos de imágenes: todos coinciden entre `8ac57c9d4` y la partida. Los manifiestos tenían el indicador `S` del checkout parcial; se materializaron desde Git sin cambiar su contenido versionado. Las correcciones de fixtures mantienen las aserciones y no omiten nuevas pruebas.

Para la pasada final también se materializó `catalog/exercises/v2/curation/evidence/legacy/exercise_database.json`, ausente por el mismo checkout parcial: 257 filas, blob `2643ba351e23d606da0abbdca81666157b2fb0dd`, idéntico a `8ac57c9d4`. Así la prueba histórica `ExerciseDatabaseLoadTest` dispone de su fixture real, sin cambiar su condición ni sus aserciones.

La pasada final incluye `screens.sessioneditor.*`, `domain.exercises.*`, `data.repository.ProgramRepository*`, `data.sessions.*`, modelos Session/Cardio/Movilidad, `data.exercises.*`, `data.Exercise*`, entidades y álbumes relacionados, `data.media.*` y `data.importer.*`. También ejecuta las suites afectadas de importador, editor de series en vivo, cálculos, estimación de duración, técnicas programadas y reglas de superserie.

Las tres omisiones existentes del importador corresponden a dos informes optativos sin sus corpus (`KPKN_IMPORT_CORPUS` y `KPKN_OCR_ROWS`) y al PDF sintético de `OcrImportSourceTest`: su `assumeNoException` preexistente se activa tras `IllegalStateException: document is closed!` en Robolectric. Los tres archivos de prueba coinciden con `8ac57c9d4` (blobs registrados en `reference-8ac-hashes.json`). No se valida por ello la lectura real de ese PDF; se añade una comprobación manual. Ninguna de las 47 pruebas nuevas usa omisiones.

## Pruebas manuales para el orquestador (Z Flip)

1. **Borrado y deshacer:** borrar un ejercicio y deshacer; repetir y salir antes de 5 s; volver. Repetir cambiando de día y de variante. Deshacer permanece disponible durante la ventana de 5 s en el editor activo; al salir o pausar se aplica el borrado al origen.
2. **Superserie:** crear una de cuatro; «Eliminar» desde acciones rápidas elimina al miembro. La acción específica de sacar de la superserie y el deslizamiento mantienen su función de sacar. Eliminar última ronda y añadir otra: debe existir una serie editable y el contador omite huecos.
3. **Arrastre:** mover ejercicios sueltos y bloques entre grupos, al inicio/final, con 20 ejercicios; no deben desaparecer ni duplicarse ejercicios. Probar borde de cardio/fuerza.
4. **Variantes y día vacío:** abrir B, crear sesión en un día vacío, escribir nombre y añadir. Volver a B. Comprobar duración y colocación de cardio al cambiar A/B/C/D.
5. **Versiones:** desde B restaurar una versión sin B; editar y guardar. Restaurar otra con B; B vuelve a poder seleccionarse. Repetir después de iniciar un borrado sin esperar los 5 s.
6. **Principal y errores:** dejar nombre vacío para bloquear guardado; elegir otra principal; volver. Lo escrito debe seguir en el borrador. Dar nombre válido y comprobar que se guarda.
   Repetir un cambio de día y editar el nombre o iniciar un borrado durante la carga del destino: permanece el origen con la edición o el deshacer disponible y un aviso para volver a elegir destino.
7. **Importadas:** abrir desde Semana un día importado antiguo con ejercicios reflejados en sueltos/grupos. No se duplica ni se cierra la lista.
8. **Transferencias:** completa/parcial, añadir/reemplazar, destino vacío/ocupado; repetir APPEND desde el mismo origen. Guardar, salir y volver; no aparece bloqueo de identidades ni variantes ajenas compartidas.
9. **Siete modos y series:** probar Reps, AMRAP, RM, Solo RPE, Tiempo, Distancia y Personalizado; 1 y 30 series, unilateral, aplicar a todas, duplicar, aproximación/movilidad y técnicas. Duplicar no trae resultados ya completados.
10. **En vivo:** editar modo/series o cardio sin confirmar; plegar/desplegar y recrear actividad. Confirmar conserva lo editado. Añadir serie de distancia/personalizada conserva la cantidad y la configuración.
11. **Catálogo y restauración:** seleccionar nativo y propio, reordenar, recrear actividad y añadir. La selección y su orden deben continuar. Alejarse de la fila, desplegar los elegidos y tocar el nombre: vuelve a la fila tanto para un propio como para una configuración nativa. Elegir cuatro para superserie funciona; con cinco no se debe crear parcialmente.
12. **Reemplazar:** elegir propio o nativo sin confirmar; recrear actividad. Nada se reemplaza antes del botón final. En B/C/D se ve el contexto del ejercicio correcto. Una dominada asistida mantiene modo asistido.
13. **Importador:** escribir una segunda búsqueda; los resultados de la primera dejan de poder elegirse inmediatamente. Editar carga, reps y descanso, guardar y deshacer en importador y wizard.
14. **Ficha y Técnica:** abrir ficha desde catálogo, reemplazo, sesión y wizard; probar imágenes y álbum con solo vídeo; comprobar el toque real en «Ver todos» los músculos y en el álbum; elegir un ejercicio para Técnica entrega una sola selección.
15. **PDF real del importador:** abrir un PDF de dos páginas y comprobar que ambas se leen en orden y dejan revisar/editar la sesión importada. Esta ruta complementa el caso sintético omitido por la asunción existente en Robolectric.
