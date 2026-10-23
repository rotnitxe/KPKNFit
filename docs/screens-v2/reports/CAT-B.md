# CAT-B · E7 y fichas F1/F2

Estado: implementación y pruebas preparadas, **sin compilación ni ejecución local**. La validación la centraliza el orquestador; no se declara verde. Worktree `C:/kw/u19`, rama `codex/sv2-catb`, base provisional `acc75872c` con CAT-A. Falta incorporar la integración completa verde antes de validar/entregar esta rama, según el encargo.

## Fuentes y límites

Se aplican el brief CAT-B, las decisiones aprobadas y los tableros reales `E7-CrearEjercicio.dc.html`, `F1-FichaCatalogo.dc.html` y `F2-FichaSesion.dc.html` (los dos últimos nombres del brief estaban desactualizados). Se reutiliza MOT: `SmartExerciseCreator.preview`, `referenceResult`, ranking y `ExerciseCoverResolver`; no se cambia el motor ni los corpus.

Room permanece congelado: ningún Entity, DAO, schema, migración o versión se modifica. Tampoco G1, ED-A, cálculos APRE/TM, datasets, scripts de build, worktrees ajenos o informes de otras pistas. Sin Gradle, JVM, adb, instalación, push o master.

## E7 · Entrada, revisión y borrador

Antes, `SmartCreateSuggestion` ejecutaba `createAutomatic` y publicaba/seleccionaba el resultado de inmediato. Ahora abre E7 con la búsqueda precargada. La página usa el portal existente para ocupar la pantalla sin duplicar la cabecera de búsqueda; Atrás/Cancelar vuelven al catálogo sin seleccionar ni guardar. La consulta y el identificador del formulario sobreviven a una recreación; el estado del ViewModel conserva el borrador.

`CustomExerciseEditorViewModel` expone StateFlow readonly y usa constructor injection. El UUID se fija una vez, el cálculo MOT va por Default y los resultados obsoletos no se publican. Material, estación, lateralidad, descripción y músculos siguen editables. Las opciones usan los IDs/etiquetas del motor existente. Quitar el último músculo conserva el vacío explícito, sin volver a inferirlo.

Los parecidos mantienen el orden de MOT. `CONFIRMED` permite descripción derivada; `SIMILAR` muestra su explicación y opciones sin copiar una referencia incierta; `NONE` no ofrece candidatos, aunque el motor los conserve para medir ranking. La descripción manual se respeta; Regenerar vuelve a usar exclusivamente la configuración confirmada. Un patrón heurístico se nombra como sugerido/por confirmar y nunca genera por sí solo descripción o métricas.

«Usar» reutiliza la configuración exacta existente y las confirmaciones/selecciones CAT-A. Si no hay configuración exacta, «Ver opciones» vuelve al catálogo con la definición abierta, consulta canónica y filtros despejados, para que la persona elija. La revisión de material/patrón/músculos es obligatoria antes de guardar; no convierte una semejanza en dato confirmado.

## Guardado durable

`CustomExerciseRepository.upsertAndAwait` no confirma éxito por haber lanzado una coroutine: espera la escritura existente de Room y publica el resultado después. Se conserva `upsert` optimista para sus consumidores previos; ambas rutas comparten escritor serializado. Un fallo optimista antiguo no revierte una publicación diferente más nueva. `lastPersistError` continúa disponible y se actualiza con el fallo; el formulario muestra el error de su propio intento para no confundirlo con otros guardados.

El formulario solo llama `onSave` al recibir éxito durable. Durante el guardado no cambia el borrador ni se descarta; tras fallo mantiene nombre, descripción, músculos, UUID y portada, y ofrece Reintentar. Se preservan imágenes/vídeos, metadata manual y UUID del propio editado mediante el modelo existente.

## Cuatro fuentes de portada

- Documento/foto del dispositivo: `OpenDocument(image/*)` selecciona el URI como borrador. No se importa ni cambia la portada persistida al elegirlo. Guardar usa `WorkoutMediaRepository.submitUriCapture(...).await()` y el almacén privado existentes.
- Foto ya privada: la biblioteca solo ofrece PHOTO con original existente y ruta gestionada. La selección guarda el ID del medio, sin reasignar ni alterar su metadata.
- Imagen del parecido: solo se ofrece con `suggestReferenceCover`, referencia confirmada, configuración y material compatibles e imagen disponible. Editar descripción no pierde esa elección; cambiar la variante la retira para evitar una imagen equivocada.
- Ilustración del patrón: respaldo explícito y cuando falte el medio o la referencia no sea válida.

No se usa `setCover` sobre un preview cancelable: el cover elegido se incorpora una sola vez al ejercicio en el guardado durable. Si se importó una foto y después falla Room del ejercicio, el reintento reutiliza el mismo medio/UUID. Una importación de foto ya completada pertenece al álbum privado; cancelar después de ese fallo no borra medios de la persona. Cancelar antes de Guardar no inicia importación ni publica un ejercicio.

`customExerciseCoverModel` resuelve en IO. La portada privada exige el original PHOTO gestionado, no una miniatura huérfana. La referencia exige revisión vigente, configuración con imagen y material exacto. Al fallar muestra ilustración y motivo; nunca utiliza la imagen incompatible. La política está separada en `presentCustomExerciseCover` para probar esos hechos sin crear una base o medios reales.

## F1/F2 y funciones conservadas

`ExerciseFicha` conserva efectos de scroll, cierre, historial, vídeos, álbum y acciones por contexto: CATALOG Añadir/Quitar; REPLACEMENT Elegir; SESSION/WIZARD sin selector. El hero admite portada privada o referencia, con procedencia y abstención explícita. Los propios sin músculos conservan su objeto de información para no perder portada/procedencia.

Preparación y Ejecución usan los cues reales que ya transporta `CatalogDetailModel`; `FichaCues` existía sin llamadas. No se generan instrucciones. Los propios tienen acceso a Ficha junto a sus acciones existentes; su identidad de historial/medios es su UUID, nunca la referencia de creación. Sin historial no aparecen métricas de ejemplo ni una mejor serie ficticia. No se agregan costes técnicos ni campos Room.

## Pruebas preparadas y fixtures

Las nuevas pruebas usan `MotCatalog` y el asset aprobado, el corpus MOT existente y facts de archivo/imagen inyectados en la política de portada. No se retocan corpus, puntuaciones ni métricas. La disponibilidad visual del recurso se inyecta en el cuerpo nativo para comprobar el botón de referencia independientemente del checkout disperso, manteniendo la regla de compatibilidad MOT real.

- `CustomExerciseSaveCoordinatorTest`: espera efectiva del escritor; fallo sin publicar y reintento con UUID/metadata conservados.
- `CustomExerciseEditorViewModelTest`: ranking compartido, NONE/ambiguo, descripción confirmada/manual, revisión obligatoria, espera durable, fallo/reintento sin importar dos veces, importación fallida sin guardar el ejercicio, cancelación del borrador/metadata intacta, portada que sigue compatible y vacío muscular explícito.
- `CustomExerciseCoverPresentationTest`: original borrado con thumb aún referenciada, vídeo/ID incorrectos, material/revisión/imagen exactos y respaldo de patrón.
- `CatbScreensComposeTest`: clicks nativos, revisión/Guardar/error/Reintentar/Cancelar, las cuatro fuentes de portada, Usar existente, selectores de material/estación/lateralidad; geometría de E7/F1/F2 en 360×740 y 360×880, fuentes 100/130, objetivos48 y texto completo con TextLayoutResult. Cues reales y selectores de contexto, sin métricas inventadas.
- Caso nuevo en `CatalogScreensV2Test`: desde búsqueda vacía abre E7 precargado; Cancelar no crea ni selecciona y conserva la consulta.
- `CatalogDetailContentTest.crearEjercicioConNombreVacioNoGuarda`: mismo contrato, con cuerpo de página acotado y toque nativo sobre el botón deshabilitado; se retira solo el scroll infinito del arnés anterior. No se debilita la condición de no guardar.

Clases concretas para la corrida centralizada, además de `:app:compileBaseDebugKotlin`:

```text
com.example.kpkn.data.repository.CustomExerciseSaveCoordinatorTest
com.example.kpkn.data.repository.CustomExerciseListOpsTest
com.example.kpkn.screens.sessioneditor.design.CustomExerciseEditorViewModelTest
com.example.kpkn.screens.sessioneditor.design.CustomExerciseCoverPresentationTest
com.example.kpkn.screens.sessioneditor.design.CatbScreensComposeTest
com.example.kpkn.screens.sessioneditor.components.CatalogScreensV2Test
com.example.kpkn.screens.sessioneditor.components.CatalogDetailContentTest
com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogV2InfoTest
com.example.kpkn.screens.sessioneditor.components.ExerciseFichaVideoAuditTest
com.example.kpkn.domain.exercises.ExerciseReferenceFinderTest
com.example.kpkn.domain.exercises.CustomExerciseCreationTest
com.example.kpkn.domain.exercises.ExerciseCoverResolverTest
com.example.kpkn.domain.exercises.SmartExerciseCreatorTest
com.example.kpkn.domain.exercises.SmartExerciseCreatorRegressionTest
```

## Renders y evidencia pendiente

Con `KPKN_RENDER_DIR=C:/kw/shots/catb`, `CatbScreensComposeTest` escribe 24 PNG: E7 inicio/revisión y F1/F2 inicio/cues en cada una de las cuatro combinaciones de altura/fuente. Son renders de los cuerpos reales. Las aserciones se ejecutan incluso sin la variable; solo escribir PNG es opcional. Hay que abrirlos y cotejarlos con E7/F1/F2 antes de declarar validación visual.

Revisión estática: `git diff --check` sin errores. Ninguna prueba nueva ni build ha sido ejecutado desde esta rama. Las métricas heredadas 36/36, fresco top1 93,3 % y top3 97,8 % proceden del informe MOT y todavía no se han repetido aquí; no se presentan como resultado nuevo. La cola central debe confirmar compilación, pruebas y capturas después de actualizar la base provisional.

## Correccion textual tras validacion central

La validacion CAT-B integrada en u11@5b4a6e842 paso 13 clases, 80/0 y wrapper exit0. La revision visual posterior detecto un defecto real: la descripcion generada de F1/F2 mostraba `knee_dominant` porque `reference.force` contiene un ID tecnico en la proyeccion MOTv2. Se cambia exclusivamente la etiqueta del movimiento por el nombre humano de la referencia CONFIRMADA (o `Movimiento` si no existe nombre), sin inferir del texto solicitado, traducir IDs con tablas propias ni alterar ranking, corpus, metricas o instrucciones. Material, primarios y primera cue siguen siendo datos confirmados. Las descripciones manuales permanecen literales y NONE sigue sin generarlas.

Regresion nueva en SmartExerciseCreatorRegressionTest con MotCatalog real: evidencia force=knee_dominant, nombre confirmado visible, sin ID en texto generado, cue intacta, nota manual literal y abstencion. CatbScreensComposeTest refuerza F1/F2 en los cuatro viewports/fuentes existentes para comprobar que su descripcion visible no expone IDs. Este ajuste aun no fue compilado ni ejecutado localmente; no se repite ni extiende el resultado80/0 anterior. Sin Gradle/JVM/adb.
