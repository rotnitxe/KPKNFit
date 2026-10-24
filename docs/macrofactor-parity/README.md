# Ola MFP · paridad con lo mejor de MacroFactor

**Base:** `artifacts/macrofactor-reverse-engineering-20261020/INFORME_FINAL.md` (HEAD de partida `8ac57c9d4` de master).
**Rama:** `feat/macrofactor-parity`, worktree `C:\kw\mfp`. **Continuación 22-oct:** Codex implementa y revisa directamente por decisión delegada del usuario (método más efectivo y rápido, sin despilfarrar tokens). Se conservan las pistas N (`mfp/n`), D (`mfp/d`), T5a (`mfp/t5a`) y H (`mfp/h`). El 24-oct el usuario ordenó continuar directamente en local, con el método más efectivo y rápido y sin push. Esto sustituye la instrucción de Cloud del 22-oct. Gradle local exclusivamente por la cola global; no fusionar master. El traslado anterior queda como evidencia histórica en [CLOUD_HANDOFF.md](CLOUD_HANDOFF.md). Estado de reanudación: [LOCAL_RESUME.md](LOCAL_RESUME.md).
**Regla de la ola:** solo se trae lo que KPKN no tiene o tiene mal/básico. Lo que ya iguala o supera (AUGE, catálogo por configuraciones, generador, descarga, calentamiento, reparto semanal, registro local) no se toca.

## Decisiones tomadas (con su razón)

| # | Decisión | Razón |
|---|---|---|
| D0 | El trabajo previo sin integrar de Codex (`mf-reference-improvements-20261018`, 46 archivos sin commit, 11 tests rojos, Room 28→29, base ~800 commits atrás) NO se integra. Se construye limpio sobre master y ese trabajo es referencia de solo lectura (`C:\kw\ref\mf-wip`) para portar piezas puras. | Reescribió `VolumeView`/`NutritionScreen`, que después se rehicieron en master (`6218d4205`, `0f649f72c`, `d978c8a4b`); su migración choca con el v29 actual; nació del informe anterior, que este corrige. Integrarlo costaría más que rehacer. **El usuario puede revertir esta decisión.** |
| D1 | Escáner de códigos de barras con ML Kit `barcode-scanning` empaquetado (modelo dentro del APK, sin Play Services). | Funciona sin red ni Google, igual que el OCR (`text-recognition` empaquetado) que KPKN ya usa. Acepta el aumento de tamaño del APK (recomendación del informe). |
| D2 | El check-in semanal propone y el usuario confirma (aceptar / declinar / posponer). No hay modo automático. | Un estimador nuevo no debe cambiar solo las calorías de nadie (riesgo §7 del informe). |
| D3 | Día del check-in por defecto = inicio de semana del usuario; configurable. Disponible 3 días; posponer = al día siguiente; idempotente por semana. | Recomendación del informe; evita fijar un lunes cultural. |
| D4 | AUGE no cambia: el sueño sigue fuera de τ y el multiplicador nutricional sigue apagado. | Decisión de producto del usuario (informe §7.4); no es una «mejora» de esta ola. |
| D5 | e1RM: modelo único y versionado. Mismo costado de curva que KPKN hoy, aplicado sobre repeticiones efectivas n = reps + RIR; sin RIR registrado, RIR = 0. Se recalcula la presentación, nunca los registros. | Cierra la inconsistencia (prescripción vs estimación) sin cambiar ninguna cifra histórica sin RIR. |
| D6 | No se implementan: ciclo menstrual (dato sensible, decide el usuario) ni importación de recetas por URL (red). | Informe §7.6. |
| D7 | Umbrales del banco de simulación del estimador: los del informe §5.1 (sesgo ≤ 50 kcal/día, MAE ≤ 150 con pesaje diario, 0 inversiones de signo por un pesaje de ±0,5 kg, ≥ 95 % de abstención correcta). Máx. 3 rondas de ajuste de parámetros. | Se fijan ANTES de ajustar (anti-sumidero). |
| D8 | El test que fija «una fila corrupta deja la carga vacía» se cambia: ahora se pone en cuarentena la fila y se cargan las sanas. | Informe H-b. |
| D9 | El código huérfano (`diffSessionPlan`/`revertPlanAspect`, readiness sin consumidor) no se borra; se documenta. | «Refinar, no quitar». |
| D10 | Diferidos al final y con permiso: vocabulario controlado de acciones articulares (S11) y gobernanza de `catalogRevision` (H-i). | Tocan el catálogo aprobado, que tiene su propio plan de re-curaduría. |
| D11 | Un solo agente escribe/compila a la vez. Los paquetes de solo lectura pueden ir en paralelo. | Orden del usuario del 20-oct (RAM). |
| D12 | QA visual por paquete con hojas PNG y comprobación física pendiente. Para el Z Flip rige la orden posterior del 23-oct: única app **KPKN Dev `com.example.kpkn.dbg`**, `baseDebug`, acceso por `phone_run` y `phone.lock`; comprobar firma y compatibilidad de datos antes de actualizar con `-r`. | La orden posterior sustituye la antigua variante `.mfp`; no crear paquetes nuevos, borrar datos ni modificar la app personal. No hay nueva aceptación física en esta reanudación. |
| D13 | Myo-reps = preset de rest-pause con marca de variante; no se añade un `TechniqueType` nuevo. | ~140 usos de rest-pause y `when` exhaustivos; mismo registro, fatiga y volumen que rest-pause, sin inventar una equivalencia. |
| D14 | G13 y G6 del informe (contexto histórico y masa magra en el punto de partida) NO se implementan. | Sin datos reales no se puede validar un multiplicador, y el informe pide descartarlo si el banco no lo demuestra; los multiplicadores del informe son de otra app y están prohibidos como constantes. |
| D15 | Micronutrientes («sin dato» ≠ 0) se resuelve **sin tocar importador ni Room**: por presencia en `micronutrients` y por cobertura. Separar «cero» de «desconocido» en el catálogo se propone aparte. | El catálogo de ~80 MB no se reimporta en esta ola. |
| D16 | La búsqueda de alimentos (`LIKE`) no cambia: el ítem D8 del informe pide medir p95 en el dispositivo antes de decidir un FTS. La medición va en P2-F. | Criterio del propio informe. |
| D17 | La foto de etiqueta (ítem D10 del informe) va en P2-E como **experimental**: imagen de galería + OCR ya incluido + pantalla de revisión; nunca guarda sin que la persona lo confirme. | Sin cámara, sin permisos nuevos; se prueba con textos de etiqueta escritos a mano. |
| D18 | Las metas DRI de P2-Nb salen de una tabla ya investigada y revisada (`data/DRI_ADULTOS.csv`, su LEEME dice qué se verificó), con un test que compara las 240 filas; solo se avisa de exceso con los UL que aplican a la ingesta total. | La app no distingue comida de suplemento: un aviso con el UL de magnesio, folato, niacina o vitamina E sería una falsa alarma; y sin tabla fija un agente la reescribiría de memoria. |
| D19 | Continuar T6, P2-Ta y P2-Tb sobre la base MFP actual con cambios mínimos y aditivos. | El 22-oct se comprobó que `feat/screens-v2` no estaba fusionada en master; el usuario delegó la elección. Registrar conflictos para la integración, manteniendo el orden screens-v2 antes de MFP y el contrato Room. |

### Protocolo vigente de QA (orden AGENTS, 23-oct; sustituye D12 anterior)
- Una app personal `com.example.kpkn` y una app de pruebas **KPKN Dev**, identidad estable `com.example.kpkn.dbg`. No crear variantes de paquete ni borrar datos para facilitar QA.
- Pruebas ordinarias con `baseDebug`; `baseDebug` fija identidad/nombre/firma debug estable de forma ordinaria (sin init). Antes de actualizar con `-r`, comprobar certificado instalado y compatibilidad Room/datos. Una prueba R8 conserva la misma identidad y firma.
- Acceso físico únicamente por `phone_run.py` y `phone.lock`, un dueño, sin eludir guardas de actividad/bloqueo ni apropiarse de candados. No desinstalar paquetes antiguos ni tocar la app personal. No contactar otros chats sin autorización humana específica.
- 24-oct: comprobación protegida de sólo lectura: Z Flip desconectado; no se instaló ni manejó la pantalla. La medición física P2-F y la QA física continúan pendientes. El emulador con Room34 se preserva: no se instala una base inferior encima.
- Se preservan worktrees, datos, capturas y evidencia hasta terminar la entrega; la antigua limpieza que desinstalaba `.mfp` queda subordinada a la orden actual.

### Esquema Room: contrato con la sesión de vídeo y orden de fusión (21-oct)
Otra sesión (ola VT, «Analizador de técnica y conteo de repeticiones») arma un APK de pruebas con los DATOS PERSONALES del usuario (`com.example.kpkn.dbg`, release con R8, rama `qa/todo-20261021` en `C:\kw\qa-todo`) que combina `feat/screens-v2`, `feat/rings-v4` y esta rama: **29→30 = calibración (screens-v2, `CALIBRATION_MIGRATION_29_30`), 30→31 = nutrición (esta rama), `version = 31`**. Por eso:
- **Orden de fusión a master: screens-v2 primero, esta rama después.** `nutritionDayStateMigration(from)` es `Migration(from, from + 1)`: hoy se registra con `(29)` y `version = 30`; detrás de screens-v2 va con `(30)` y `version = 31`. Además hay que dejar `app/schemas/…/30.json` el de screens-v2 (el de esta rama solo tiene nutrición), generar el `31.json` con KSP (sin editarlo a mano) y ampliar las cadenas de migración de `NutritionDayStateMigrationTest`, `WorkoutMediaMigration27To28FullRoomTest` y `VideoAnalysisMigrationValidateTest`. Si ese orden no pudiera cumplirse, avisar ANTES a esa sesión. La fusión a master la decide el usuario.
- **Esquema congelado:** `nutrition_day_marks`, `nutrition_checkins` y el índice `index_global_foods_sourceRecordId` están publicados tal cual. Ningún paquete restante toca entidades, versión, migraciones ni `app/schemas/` (comprobado en los briefs y en el diff de N3; `mfp_verify.py --since` avisa con «ESQUEMA ROOM TOCADO»). Un cambio futuro sería la versión **32** (la próxima libre), solo con permiso del usuario y sin editar la 30 ni la 31.
- **Para ese APK no sirve la punta de esta rama mientras T3 no esté cerrada:** lo único que cambia en producción desde `5d0abc72f` («N2b integrado») son los dos commits de T3 a medio cerrar (cargas prescritas sin esfuerzo declarado ≈ +7 %). Se les recomendó `git merge 5d0abc72f` y se prometió **escribirles cuando T3 esté cerrada y verificada**.

## Paquetes, orden y propiedad de archivos calientes

Orden de ejecución (serie, reordenado el 21-oct): **H1 → N1 → T2 → T1 → N2a → N2b → T3 → T4 → T5a → T5b → N3 → N4 → P2-Na → P2-Nb → P2-D → P2-E → P2-F → T6 → P2-Ta → P2-Tb**.

**Por qué T6, P2-Ta y P2-Tb van al final:** la ola `screens-v2` (otra sesión, worktree `C:\kw\u11`, rama `feat/screens-v2`, desde el mismo master) está rediseñando la sesión en vivo, el editor y el catálogo (106 archivos de producción cambiados a 21-oct; p. ej. `SetExecutionCard.kt` con 464 líneas tocadas, `SetCardTechniqueBack.kt`, `WorkoutSetRecorder.kt`, `ExercisePickerV2Catalog.kt`, `WorkoutStructureSheetsHost.kt`). T6 (selector de reemplazo), P2-Ta (tarjeta de la serie y editor) y P2-Tb (`WorkoutSetRecorder`) tocan esos mismos archivos: si se hacen al final, quizá esa rama ya esté fusionada en master y se implementan sobre la interfaz nueva en vez de rehacer el trabajo. Si no lo está, se hacen sobre la base actual con cambios mínimos y aditivos y se avisa del choque. **Choque previsible aunque se reordene** (archivos tocados por las dos olas): `domain/calculations/Calculations.kt` (T3), `domain/workout/LoadSuggestionEngine.kt` (T3), `domain/training/NativeWorkoutProgressionRuntime.kt` (T1 ya, T4) y `screens/workout/WorkoutLoadSuggestionController.kt` (T3); son pocas líneas por lado, pero habrá que resolver al fusionar.

| ID | Qué | Depende de | Prio. |
|---|---|---|---|
| H1 | Higiene de datos de nutrición (creatina durable, cuarentena por fila, reglas de ignore) | — | P0 |
| N1 | Estimador de gasto + tendencia robusta (dominio puro) + banco de simulación | — | P0 |
| T2 | Ventana y contabilidad de volumen (contrato único) | — | P0 |
| T1 | «Mi material» (inventario de equipo) + inventario único en el runtime | — | P0 |
| N2a | Estado del día (Room v31 tras calibración v30) + marcar parcial/ayuno en el diario | H1, N1 | P0 |
| N2b | Check-in semanal + aplicar objetivo + sustituir el uso del motor antiguo | N1, N2a | P0 |
| T6 | Sustituciones inteligentes en la sesión en vivo | — | P1 |
| T3 | Modelo de fuerza versionado con RIR | T2 | P1 |
| T4 | Objetivo automático (reps, peso) sobre el inventario | T1, T3 | P1 |
| T5a | Panel de series por ventana + modo volumen en la silueta | T2 | P1 |
| T5b | Progreso por ejercicio + récords | T3, T5a | P1 |
| N3 | Escáner de códigos + búsqueda por código + alimentos propios | N2a, H1 | P1 |
| N4 | Reutilización del diario (copiar, repetir, plantillas, entrada rápida) | N2a, N3 | P1 |
| P2-Na | Recetas con rendimiento, favoritos y hora de la comida, copia de seguridad de preferencias, exportación robusta | N3, N4 | P2 |
| P2-Nb | Estado de nutrientes, cobertura, contribuyentes y metas DRI | P2-Na, N3 | P2 |
| P2-Ta | Myo-reps (preset de rest-pause) y discos por lado en la tarjeta | T1, T3 | P2 |
| P2-Tb | Verificación del calentamiento y de la descarga nativa; interruptores de entrenamiento con UI | T1 | P2 |
| P2-D | Tendencia como cifra del peso + fecha robusta, exportación CSV, hábitos, código huérfano documentado | N1, N2a/b, P2-Na | P2 (la fecha, P1) |
| P2-E | Etiqueta nutricional por foto de galería (experimental) | N3 | P2 |
| P2-F | Medición de la búsqueda de alimentos en el dispositivo (p95) | N3, P2-Na | P2 |

Archivos calientes con un único dueño por paquete (no se tocan fuera de él): `data/db/*` (N2a, luego N3), `navigation/Navigation.kt` y `MainActivity.kt` (el paquete en curso; bloques nuevos solamente), `NutritionRepository.kt`, `NutritionScreen.kt`, `NutritionViewModel.kt`.

## Estado vigente · entrega local24-oct-2026

Todos los paquetes de implementación están integrados localmente en `feat/macrofactor-parity`, fuente canónica `C:/kw/mfp`; sin push ni master. Código final df1d52776. APK **KPKN Dev** baseDebug ordinario, Room31 generado por KSP, sin init. Los párrafos históricos de relevo no describen el estado actual.

| Paquetes | Estado local y evidencia |
|---|---|
| H1,N1,T1,T2,N2a,N2b,T3 | Integrados; regresión completa ejecutada, con clasificación nominal de heredados/contaminación y contratos vigentes |
| T4 | Peso y repeticiones durables;34 JVM+58 Android focales verdes |
| T5a | 1.527 JVM+398 Android verdes;20 PNG |
| T5b,T6,P2-Ta,P2-Tb | Integrados;129 Android/14 clases verdes,40 PNG; selector7/7 posterior |
| N3,N4 | 257 Android/22 clases verdes,38 PNG; código guardado recuperado en flujo nativo |
| P2-Na | 44 JVM+73 Android verdes;18 PNG |
| P2-Nb | 1.317 JVM+12 focales+330 Android verdes; DRI240 filas preservada |
| P2-D | CSV, hábitos/medianoche, tendencia y documentación de código huérfano terminados; exportación SAF real comprobada |
| P2-E | Experimental implementado; ML Kit5/5 en APK final y galería→revisión→guardado real comprobado |
| P2-F | Banco terminado; **medición física pendiente por Z Flip desconectado** |

Último focal: **88/88,12 clases,0 omisiones**. Identidad/parser/selector27/27. JVM integrada3.067/275clases. Suite completa:13.025 pruebas,12.936 aprobadas,79 fallos clasificados,10 omitidas,1.307 PNG. No se afirma suite completa verde. QA física total pendiente; no se infiere de renders ni de emulador.

Entrega, decisiones, hallazgos y límites: [INFORME_FINAL_LOCAL.md](INFORME_FINAL_LOCAL.md). Clasificación y XML: [cambios/VALIDACION_INTEGRADA_LOCAL.md](cambios/VALIDACION_INTEGRADA_LOCAL.md). Inventario por paquete: [CAMBIOS_VISIBLES.md](CAMBIOS_VISIBLES.md). Medición física pendiente: [P2-F_RESULTADO.md](P2-F_RESULTADO.md).

D20: la orden local del24-oct sustituye la antigua continuidad Cloud. La identidad de la orden23-oct es KPKN Dev `.dbg` con firma debug estable. El contrato vigente es29→30 calibración y30→31 nutrición;31.json ya generado por KSP y validado. No se consume32. La integración a master sigue siendo una decisión posterior.

Limpieza final completada: ocho worktrees componentes/control retirados, ramas/commits conservados; builds/caches propios retirados y evidencia visual archivada/verificada en C:/kw/logs/mfp-evidencia-visual-20261024.zip. APK y LEEME entregados en G:/Mi unidad/KPKN_MFP_local_2026-10-24/.
