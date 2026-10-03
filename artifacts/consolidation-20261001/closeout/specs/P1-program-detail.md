# Paquete P1-program-detail: Detalle del programa: cierre seguro al reemplazar con plantilla y propuestas de progresión legibles

LEE PRIMERO `C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\closeout\AGENT_COMMON.md` (reglas, decisiones del dueño, prohibiciones, Gradle, entrega).

## Ítems del plan de cierre (texto íntegro de ROADMAP_CIERRE.md)

#### D2.3 — T1 · «Reemplazar todo» con plantilla puede cerrar la app (guardado de copia fuera de runCatching)
- **Prioridad:** do-now · **Esfuerzo:** 60-75 min para D2.3+D2.8+D2.9 juntos (el verificador corrigió los 45 min del analista) · **Riesgo:** Bajo: cambia un cierre de app por un aviso.
- **Qué es:** Al reemplazar el programa con una plantilla, la app guarda antes una copia recuperable. Si ese guardado falla (disco lleno), la app se cierra en vez de avisar. El programa queda intacto.
- **Estado actual:** [C] ProgramDetailViewModel.kt:693 llama a pushProgramSnapshot fuera del runCatching que empieza en :695. ProgramSnapshotStore.kt:52-54 lanza excepción si commit() falla. NutritionCrashHook.kt:28-31 delega en el manejador de Android, que cierra el proceso. [C] applyProtocolOverwrite lo hace bien (L769-796). [C] Es alcanzable desde MacrocycleEditorLegacy.kt:771 y :788. [C] Si no hay almacén conectado, el reemplazo sigue sin copia (L855). [C] No hay guarda de reentrada (dos toques, dos copias).
- **Recomendación:** Meter el guardado dentro del runCatching y moverlo después de calcular la plantilla. Si no hay almacén o falla el guardado, no reemplazar y mostrar el snackbar que ya existe. Relanzar CancellationException. Añadir guarda de reentrada (3 líneas, como protocolApplyJob) y 2 tests con un SharedPreferences falso cuyo commit() devuelva false. Hacerlo junto con D2.8 y D2.9 (mismo archivo).
- **Evidencia:** [C] ProgramDetailViewModel.kt:686-735, 762-825, 854-862; [C] ProgramSnapshotStore.kt:52-54; [C] ProgramDetailViewModelTest.kt:1535-1583 (solo overwrite=false); [C] ProgramDetailScreen.kt:100-104 y 775-777 (attachSnapshotStore se llama antes, por eso exigir el almacén es seguro)

#### D2.8 — T1 · «Reemplazar todo» con plantilla no comprueba si hay una sesión en curso
- **Prioridad:** do-now · **Esfuerzo:** 10 min dentro de D2.3 (+30-45 min la mejora opcional) · **Riesgo:** Bajo.
- **Qué es:** Con protocolo, la app se niega si el programa tiene una sesión abierta. Con plantilla no.
- **Estado actual:** [C] La guardia existe en ProgramDetailViewModel.kt:770-772 y falta en applyProgramTemplate (L686-735). [C] replaceProgramSafely (ProgramRepository.kt:1663-1674) ya incluye esa guardia, de forma atómica y durable.
- **Recomendación:** Ahora: copiar la guardia al ViewModel, dentro del runCatching de D2.3, con el mismo mensaje y un test. Después (opcional, 30-45 min más tests): enrutar la rama overwrite=true por replaceProgramSafely. En ese caso hay que comprobar que ProgramTemplateEngine REPLACE_ALL (runState = null, L72-76) no deja el ciclo de ejecución distinto al del protocolo [I].
- **Evidencia:** [C] ProgramDetailViewModel.kt:686-735 frente a 770-772; [C] ProgramRepository.kt:171-180, 1663-1674; [C] ProgramTemplateEngine.kt:60-76

#### D2.9 — T1 · ProgramDetailViewModel expone un MutableStateFlow público
- **Prioridad:** do-now · **Esfuerzo:** 5 min dentro de D2.3 · **Riesgo:** Ninguno.
- **Qué es:** Incumple la regla de CLAUDE.md sobre exponer solo StateFlow de lectura.
- **Estado actual:** [C] ProgramDetailViewModel.kt:139 declara feedbacks como MutableStateFlow público. Solo se usa dentro del propio ViewModel (L167, 266, 302). loadFeedbacks (L262-269) traga cualquier error.
- **Recomendación:** Renombrar a _feedbacks privado y exponer feedbacks con asStateFlow(). Registrar el error en el catch de L267. Va en la misma pasada que D2.3.
- **Evidencia:** [C] ProgramDetailViewModel.kt:139, 167, 262-269, 302

#### H-UI — T1 · Las propuestas de progresión muestran nombres técnicos y casi no se descubren
- **Prioridad:** do-now · **Esfuerzo:** 1,5-2 h (el verificador midió corridas dirigidas de 33-40 min y subió el 1-1,5 h del analista); con insignia 2,5-3 h · **Riesgo:** Bajo. Si no se hace, la función existe pero el usuario ve códigos y probablemente no la encuentra.
- **Qué es:** La tarjeta donde aceptas o rechazas una subida de carga imprime el identificador interno del ejercicio.
- **Estado actual:** [C] ProgramDetailScreen.kt:1299 imprime algo como «Progresión KPKN · high_bar_back_squat__barbell · F». El texto de la propuesta no nombra el ejercicio y usa jerga (NativeWorkoutProgressionRuntime.kt:225-228, 263-267). [C] Solo se dibuja en Detalle del programa, pestaña Semana (ProgramDetailScreen.kt:691-702). [C] Al aplicar o rechazar no hay confirmación, y el último aviso queda visible para siempre (ProgramDetailScreen.kt:1242, 1308-1313; ProgramDetailViewModel.kt:1253-1270).
- **Recomendación:** Hacerlo ya, versión barata. Resolver el nombre del ejercicio al dibujar desde el catálogo o el programa, o guardarlo como campo opcional con valor por defecto (sin migración). Texto llano: «Press de banca con mancuernas: lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna». Quitar la línea del identificador, confirmar al responder y hacer que el aviso caduque. Opcional: insignia «N progresiones por revisar» en el programa activo (+1 h). Va antes de H-VERIF para que la pasada en emulador vea ya el texto nuevo.
- **Evidencia:** [C] ProgramDetailScreen.kt:691-702, 1241-1242, 1291-1314; [C] NativeWorkoutProgressionRuntime.kt:225-228, 263-267; [C] ProgramDetailViewModel.kt:1253-1270


## Instrucciones específicas del paquete P1
Archivos que te corresponden: `screens\programdetail\ProgramDetailViewModel.kt`, `ProgramDetailScreen.kt` (y componentes de `screens\programdetail\` que hagan falta), `domain\training\NativeWorkoutProgressionRuntime.kt` (SOLO los textos/estructura de la propuesta: otro paquete posterior cambiará la identidad/destinos en ese mismo archivo), modelo de propuesta (campo OPCIONAL con valor por defecto, sin migración Room: Program/WorkoutLog son JSON), tests de ProgramDetail/ProgressionRuntime.
Orden: D2.3+D2.8+D2.9 juntos → H-UI. Para H-UI: nombre legible del ejercicio (resuelto al dibujar desde el programa/catálogo o campo opcional), texto llano («Press de banca con mancuernas: lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna» / «elige una carga un poco mayor» si no hay pesas declaradas), quitar la línea con el identificador técnico, confirmación al aceptar/rechazar y caducidad del aviso (userFacingNotice) para que no quede visible para siempre; la insignia «N progresiones por revisar» es opcional: hazla SOLO si el resto va bien (+1 h). No cambies reglas de cuándo se propone (eso es de otro paquete).
D2.3: `pushProgramSnapshot` dentro de `runCatching` DESPUÉS de calcular la plantilla; sin almacén o con fallo de guardado → no reemplazar y mostrar el snackbar existente; relanzar `CancellationException`; guarda de reentrada; 2 tests con `SharedPreferences` falso cuyo `commit()` devuelve false. D2.8: guardia de sesión en curso copiada de `applyProtocolOverwrite` + test. D2.9: `_feedbacks` privado + `feedbacks` con `asStateFlow()`, registrar el error en el catch. Tests JVM al final (una corrida): `ProgramDetailViewModelTest` y vecinos, `NativeWorkoutProgressionRuntimeTest`, `ProgramRepositoryConsolidationTest`, y los nuevos. LogName prefijo `P1-`.
