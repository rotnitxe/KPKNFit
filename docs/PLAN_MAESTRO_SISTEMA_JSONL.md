# Plan Maestro — Sistema Robusto de Diagnósticos JSONL + IA

> **Fecha:** 2026-08-08 · **Estado:** Plan para implementación por fases
> **Premisa rectora:** el código actual *existe* pero funciona solo parcialmente.
> **Nada de este plan asume que algo ya funciona**: cada fase termina con verificación E2E observable.

---

## 0. Auditoría del estado real (evidencia, no supuestos)

### 0.1 Lo que existe hoy en el código

| Componente | Archivo | Estado real |
|---|---|---|
| Logger JSONL central (13 namespaces) | `data/diagnostics/KpknDiagnosticLogger.kt` | Funciona como escritor, pero **casi nadie lo usa** |
| Espejo SAF por namespace (`KPKN/<ns>/`) | `services/diagnostics/KpknDiagnosticStorage.kt` | Parcial: depende de permiso de árbol; comparte prefs con el storage de voz |
| Diagnóstico de voz por sesión | `services/workout/WorkoutVoiceDiagnosticLogger.kt` | Funciona, pero es un **sistema paralelo** con su propio SAF (`WorkoutVoiceDiagnosticStorage.kt`) |
| Telemetría de nutrición | `telemetry/nutrition/NutritionTelemetry*.kt` | Funciona, pero es un **tercer sistema paralelo** (`filesDir/nutrition_telemetry/`) |
| Reporte con comentario + contexto | `services/diagnostics/KpknReportManager.kt` + `screens/reports/ReportDialog.kt` | Crea `report-*.jsonl` en `filesDir` (invisible para el usuario) |
| Enriquecimiento con DeepSeek | `services/diagnostics/ReportEnrichmentScheduler.kt` + `data/remote/DeepSeekV4FlashClient.kt` | Corre en segundo plano y **nunca muestra nada**: sin visor, sin notificación, sin .md |
| Gesto 2 dedos / 6 s | `services/diagnostics/ReportGestureDetector.kt` (cableado en `MainActivity.kt:139-159`) | **No dispara en la práctica** (causas en 0.2) |
| Reporte por voz | `services/workout/WorkoutVoiceController.kt` (`ReportPhase`, trigger `"reportar equipo"`, línea 3809) | Flujo completo existe; trigger distinto al pedido ("CAUPOLICÁN") |
| Exportación ZIP de voz | `WorkoutScreen.kt:223-225,313-319` + `WorkoutViewModel.kt:1362-1370,2823,2960,2975` | **Bug activo**: abre el explorador SAF y bloquea la salida |

### 0.2 Causas raíz de los 4 fallos reportados

**F1 — El gesto de 2 dedos / 6 s nunca dispara.**
- `SECOND_POINTER_WINDOW_MS = 400`: si apoyás un dedo y el otro más de 400 ms después (lo normal al hacerlo despacio), el detector hace `reset()` y nunca arma.
- `HOLD_DURATION_MS = 6_000` con reset por `touchSlop` (~8dp): mantener **dos dedos perfectamente quietos 6 segundos** es casi imposible; cualquier micro-temblor reinicia.
- **Cero feedback visual** durante la espera: el usuario no sabe si está armando.
- El diálogo sí existe y guarda correctamente (`MainActivity.kt:938-967`): el eslabón roto es solo el gesto.

**F2 — DeepSeek "nunca aparece".**
- El resultado se anexa como línea JSON dentro de `filesDir/kpkn_diagnostics/reports/report-*.jsonl`: el usuario **no tiene dónde verlo** (no hay visor, ni notificación, ni archivo legible).
- Si la API key no está en `DeepSeekCredentialStore`, el worker falla en silencio (`deepseek_request_failed` queda solo como línea JSONL).
- `report_ai_failed` con `retryable=false` tampoco genera aviso alguno.

**F3 — Carpetas por apartado desordenadas.**
- Tres escritores con tres raíces y tres namings: `voice_diagnostics/kpkn-voice-*`, `nutrition_telemetry/nt-*`, `kpkn_diagnostics/<namespace>/kpkn-<ns>-*`.
- Los namespaces no coinciden con tus 5 apartados (hay 13).
- Dos configuraciones SAF distintas (Ajustes > Entrenamiento y Ajustes > Datos) escriben en lugares diferentes.

**F4 — Explorador SAF al iniciar/finalizar sesión.**
- `WorkoutViewModel.prepareVoiceDiagnosticExport()` setea `pendingVoiceDiagnosticExportName` (llamado en cierres de sesión, líneas 2960/2975) y `WorkoutScreen.kt:313-314` lanza automáticamente `CreateDocument("application/zip")` → el explorador de "Guardar como".
- `WorkoutScreen.kt:317-319` **bloquea la navegación de salida** hasta guardar/cancelar.
- Ocurre aunque la carpeta automática ya esté configurada: nadie consulta `isConfigured()` antes de pedir el ZIP manual.

---

## 1. Arquitectura objetivo (un solo pipeline)

```
Productores (ViewModels, servicios, boundaries — NUNCA domain/)
        │
        ▼
KpknLogBus  (único punto de entrada; saneamiento + campos base)
        │
        ▼
KpknLogStore  (raíz única: filesDir/kpkn_logs/<area>/<yyyyMMdd>/*.jsonl)
        │
        ├──► Espejo SAF automático:  KPKN/logs/<area>/<yyyyMMdd>/*.jsonl
        ├──► KpknReportManager (comentarios manuales/voz → reports/)
        └──► DailyReportGenerator (bundle del día → IA → KPKN/reports/daily/…)
```

**Reglas duras:**
1. **Un solo bus y un solo store.** `WorkoutVoiceDiagnosticLogger` y `NutritionTelemetry` se convierten en *adaptadores* que emiten al bus; desaparecen sus raíces propias (los archivos heredados se migran a la nueva raíz, sin borrar nada).
2. **Los 5 apartados son los 5 directorios oficiales.** Mapeo de namespaces actuales:
   - `voice` → **I. Sesión en vivo con voz**
   - `workout` → **II. Sesión en vivo sin voz** (hoy NO instrumentado: `WorkoutViewModel` no emite nada)
   - `nutrition` → **III. Registro de alimentos por descripción**
   - `performance` + `app` → **IV. Rendimiento de la app**
   - `auge` → **V. RINGS/AUGE** (hoy `AugeViewModel` no emite nada)
   - `reports` → comentarios del usuario + resultados IA.
   - Los namespaces restantes (`assistant, programs, learn, health, tts, backend`) pasan a ser campo `subsystem` dentro del área que corresponda, no carpetas top-level.
3. **Carpeta por día:** `<area>/<yyyyMMdd>/`. "Los JSONL del día" quedan físicamente localizables (requisito del Pilar 2).
4. **Esquema base v2** obligatorio por línea: `schemaVersion, eventId, timestamp, elapsedMs, area, subsystem, event, screen, sessionId, traceId, process`. `screen` ya se mantiene vía `KpknDiagnosticLogger.setCurrentScreen` (`MainActivity.kt:460`).
5. **`domain/` sigue puro**: la emisión se hace en ViewModels, repositorios o servicios.
6. **Presupuesto de escritura:** rotación 1 MB/archivo, retención 30 días / 50 MB totales; muestreo con tope por minuto en eventos de performance para no inflar los logs.
7. **Ningún picker SAF fuera de Ajustes.** El espejo usa el árbol ya configurado; si el permiso se perdió, se registra el fallo (`saf_mirror_lost`) y se ofrece re-configurar manualmente en Ajustes. Jamás se lanza `OpenDocumentTree`/`CreateDocument` desde un flujo de entrenamiento.


---

## 2. Fase 0 — Reparaciones críticas (lo que hoy rompe la confianza)

### 2.1 Eliminar el explorador SAF en sesiones (F4)
- **Gatear** `prepareVoiceDiagnosticExport()`: si el espejo SAF está configurado y sano, no setear `pendingVoiceDiagnosticExportName` jamás (los diagnósticos ya se copian solos).
- Si NO hay carpeta configurada: como máximo un aviso **no bloqueante** ("Diagnósticos disponibles para exportar en Ajustes").
- **Quitar el gate de navegación** de `WorkoutScreen.kt:317-319`: la exportación nunca debe condicionar `isComplete`/salida.
- Convertir la exportación ZIP en acción **manual explícita** (Ajustes > Diagnósticos), nunca automática.
- **Aceptación E2E:** con carpeta configurada, iniciar y finalizar sesión (con y sin voz) = **cero diálogos del sistema** y archivo presente en `KPKN/logs/voice/<fecha>/`. Sin carpeta: cero diálogos igualmente; solo aviso in-app.

### 2.2 Rehacer el gesto de reporte (F1)
Diseño nuevo (reemplaza los umbrales actuales):
- Ventana entre dedos: de 400 ms a **1.500 ms** (colocación deliberada cómoda).
- Hold: de 6.000 ms a **2.500 ms** (suficiente para no colisionar con gestos de scroll/zoom de dos dedos, que suelen moverse, no quedarse quietos).
- Tolerancia de movimiento: slop ×4 (~32dp) en vez de ~8dp; el gesto se cancela por desplazamiento real, no por temblor.
- **Feedback visual obligatorio**: anillo de progreso en overlay + haptics escalonados (armado → 50% → confirmado). Sin feedback, el usuario asume que no funciona.
- Entrada alternativa garantizada: botón "Reportar problema" en Ajustes > Diagnósticos y opción de botón flotante en builds debug. El gesto es atajo, no única vía.
- `ReportGestureDetector` ya es JVM-puro: **test unitario exhaustivo** de la máquina de estados (ventana, slop, cancelaciones, rotación de punteros) antes de tocar la UI.
- **Aceptación E2E:** 10/10 activaciones consecutivas en 3 pantallas distintas (home, workout, nutrition) hechas por el usuario real, incluyendo colocar los dedos "uno tras otro despacio".

### 2.3 Hacer visible a DeepSeek (F2)
- Estado de IA en Ajustes > Diagnósticos: clave configurada sí/no, último análisis, último error, pendientes.
- Al completarse un enriquecimiento: **notificación local** + entrada visible en el nuevo visor de reportes.
- El resultado se escribe además como **`.md` legible** en `KPKN/reports/` (espejo SAF) — no solo línea JSONL.
- `report_ai_failed` no reintentable → aviso visible con botón "Reintentar" (reenqueue manual).
- **Aceptación E2E:** guardar un reporte manual con clave válida → en ≤2 minutos (con red) existe `.md` visible en la carpeta SAF y en el visor; con clave inválida/ausente → aviso explícito, nunca silencio.

---

## 3. Fase 1 — Consolidación del pipeline (Pilar 1)

1. **`KpknLogStore` único** (evolución de `KpknDiagnosticLogger`): raíz `filesDir/kpkn_logs/<area>/<yyyyMMdd>/`, rotación y retención actuales, `schemaVersion=2`.
2. **Adaptadores de migración:**
   - `WorkoutVoiceDiagnosticLogger` deja de escribir en `voice_diagnostics/`; emite al área `voice` conservando su evento por sesión y su espejo por línea.
   - `NutritionTelemetry` deja `nutrition_telemetry/`; conserva sanitizer, trazas por análisis, marcadores in-flight y crash hook (se re-etiquetan al área `nutrition`).
   - Importador único de archivos heredados a la nueva raíz (mover, no copiar; idempotente).
3. **Una sola configuración SAF** (la de Ajustes > Datos pasa a ser la oficial; la de Ajustes > Entrenamiento redirige a la misma). Espejo evento-a-evento como hace hoy `KpknDiagnosticStorage`, pero con la nueva jerarquía por fecha.
4. **Health-check de escritura:** evento diario `logs_health_check` por área (último archivo, tamaño, errores). Si un área activa no escribió en 24 h, se marca en el panel de Diagnósticos. Esto convierte "funciona al 100%" en algo **observable**.
5. **Tests:** store JVM (rotación, retención, sanitización, esquema v2), migración de heredados, espejo SAF con fake. Script `scripts/validate_jsonl_schema.py` para validar líneas reales exportadas contra el esquema.


---

## 4. Fase 2 — Instrumentación por apartado (Pilar 1, contenido)

Cada apartado declara su **cobertura** (cubierto / pendiente) en `docs/JSONL_COBERTURA.md`, actualizado en cada PR que toque eventos. Catálogo detallado de eventos en `docs/PLAN_JSONL_CONTRATOS_IA.md`.

### I. Voz (`area=voice`) — mayormente cubierto, normalizar
- Re-etiquetar los eventos actuales de `WorkoutVoiceDiagnosticLogger` al esquema v2.
- Añadir `session_summary` al cerrar: duración, comandos entendidos/no entendidos, fallbacks nativos usados, reportes de voz emitidos (con `reportId`), crash/exit heredado de `WorkoutVoiceExitInfoCollector`.

### II. Sin voz (`area=workout`) — pendiente: instrumentar desde cero
Puntos de emisión (en `WorkoutViewModel`, `WorkoutSetRecorder`, `WorkoutFinishController`; nunca en `domain/`):
- `session_started / session_finished / session_abandoned` (con causa).
- `set_recorded` (ejercicio, índice, lado, peso, reps/tiempo, RPE/RIR, técnica especial: dropset/rest-pause/unilateral) y `set_persistence_succeeded/failed` (hoy solo existen en el JSONL de voz).
- `rest_started / rest_skipped / rest_finished`, `exercise_swapped`, `superset_created/dissolved`, `edit_during_live`.
- `finish_blocked_empty_session` (guard P0 ya existente) y cualquier `error` con stack saneado.

### III. Nutrición (`area=nutrition`) — parcial, consolidar
- NutriTelemetry ya registra el pipeline de descripción (spans de etapa, % resueltos, confianza IA, fallback). Se migra al área y se añade `analysis_verdict` final: motor usado, items resueltos/no resueltos, `reviewRequired`, divergencia macros IA vs heurística (el crash histórico del regex de `FoodTemplateMatcher` queda como caso de prueba de regresión).

### IV. Rendimiento (`area=performance`) — pendiente: instrumentar
- `cold_start` (Application→primer frame), `screen_open` con duración por ruta.
- `frame_jank` muestreado (Choreographer, solo pantallas pesadas: workout, session-editor, nutrition), con presupuesto de muestreo.
- `memory_pressure`, `room_query_slow` (>250 ms), `catalog_load` (el cache de CatalogV2 ya mide 34/90 ms; emitirlo).
- `KpknTelemetry.Trace` ya existe: adoptarlo como API oficial de spans de performance.

### V. AUGE/RINGS (`area=auge`) — pendiente: instrumentar
Emitir en `AugeViewModel`/`AugeRepository` (boundary), por cómputo:
- `auge_computed`: hash de inputs, outputs por motor (fatiga, recuperación, TTC, readiness, interferencia), duración.
- `auge_divergence`: comparación preview editor vs vivo (las auditorías de `docs/audits/2026-08-editor-sesiones/` ya hallaron divergencias ~2× en unilaterales: ese tipo de hallazgo debe quedar auto-registrado).
- `rings_state`: valor de cada anillo, umbrales, y transiciones anómalas (ej. "99% no guarda" de la memoria 2026-08-07).

**Aceptación transversal de Fase 2:** por cada apartado, existe test de contrato JVM que verifica que el evento se emite con los campos v2; y en dispositivo, un recorrido guiado genera ≥1 evento de cada tipo en la carpeta del día correcta.


---

## 5. Fase 3 — Reportes diarios por apartado con IA (Pilar 2)

### 5.1 Qué se genera
Por cada día con actividad, en `KPKN/reports/daily/YYYY-MM-DD/` (espejo SAF + copia local + visor in-app):

```
00-index.md                (portada: salud del día, hallazgos top, enlaces)
01-voz.md                  02-sesion-sin-voz.md      03-nutricion.md
04-rendimiento.md          05-auge-rings.md          06-comentarios-usuario.md
```

Cada reporte de apartado incluye: resumen, **facts** (con evidencia), **userClaims** (comentarios manuales/de voz del día, citados), **hypotheses** (etiquetadas como tales), **missingEvidence**, y `evidenceRefs` con **ruta + rango de líneas + eventId** de los JSONL reales. Ese es exactamente el formato que ya exige el prompt de `ReportEnrichmentScheduler` (facts/userClaims/hypotheses/missingEvidence/evidenceRefs): se reutiliza el contrato, ampliado con líneas.

### 5.2 Cómo se construye el bundle (la parte que hoy no existe)
1. `DailyReportGenerator` lee los JSONL del día por área y **numera físicamente cada línea** (`archivo.jsonl#L120-L134`) al armar el bundle. Hoy los eventos no llevan número de línea; el numerado lo hace el bundler al leer, así la IA puede citar rutas/líneas reales y verificables.
2. **Pre-agregador determinista en Kotlin** antes de llamar a la IA: colapsa ráfagas repetitivas (p.ej. 300 `frame_jank`) en conteos y percentiles, conservando íntegros los eventos raros/errores/comentarios. Sin esto, un día completo no entra en contexto — no se promete lo imposible.
3. Presupuesto de tokens por apartado; si se excede, el reporte lo declara en `missingEvidence` en lugar de truncar en silencio.
4. Llamada al proveedor IA con el prompt-contrato (ver `docs/PLAN_JSONL_CONTRATOS_IA.md`), parseo estricto, y render a `.md`. Fallo → `daily_report_failed` visible en el panel, con reintento manual.
5. Disparo: WorkManager diario (solo con red y clave válida) **+ botón "Generar reportes de hoy"** en Ajustes > Diagnósticos. Nunca silencioso: al terminar, notificación con resumen de hallazgos.

### 5.3 Conexión comentarios ↔ JSONL puro (Pilar 3 dentro del Pilar 2)
- Los comentarios (manuales y de voz) ya nacen con `reportId`, `screen`, `sessionId`, `traceId` y timestamp.
- El bundle del apartado incluye los comentarios del día **intercalados por tiempo** con los eventos del área, y el prompt obliga a la IA a vincularlos explícitamente (`linkedReportIds` por hallazgo).
- `06-comentarios-usuario.md` lista todos los comentarios del día con sus vínculos resueltos (a qué eventos/archivos apuntan).

### 5.4 Camino PC (recomendado como principal) y camino on-device
- **PC (principal):** `scripts/generate_daily_reports.py` — lee la carpeta exportada `KPKN/logs/…` y `KPKN/reports/…`, numera líneas, llama a la API de DeepSeek desde el PC y escribe los mismos `.md`. Ventajas reales: sin costo de batería, contextos más grandes, y encaja con tu flujo actual de subir documentación a una IA. La clave vive en el PC, no en el teléfono.
- **On-device (conveniencia):** mismo contrato vía `DeepSeekV4FlashClient` + WorkManager. Útil cuando no hay PC a mano; sujeto a presupuesto de tokens más estricto.
- Ambos caminos producen **el mismo formato** y comparten prompts versionados (`daily-report-v1`).


---

## 6. Fase 4 — Comentario por voz con keyword "CAUPOLICÁN" (Pilar 4)

**Base real:** el flujo de reporte por voz ya existe completo en `WorkoutVoiceController` (fases `IDLE→PROMPTING→CAPTURING→AWAITING_CONFIRMATION→FINISHING`, captura libre con fallback nativo `requestNativeFallbackForUnresolved`, confirmación hablada, guardado vía `KpknReportManager` con `origin=VOICE`, y `report_voice_saved` con `reportId` en el JSONL de voz). El trigger actual es `"reportar equipo"` (línea 3809). No hay que inventar el flujo: hay que **cambiar el disparador y robustecerlo**.

1. **Keyword:** añadir `"caupolican"` como alias de trigger junto a (o en reemplazo de) `"reportar equipo"`. La normalización sin acentos ya existe (`normalizeReportText`), así que "Caupolicán" / "caupolican" matchean igual.
2. **Gramática:** incluir el token en TODOS los stages activos (`LISTENING, PROCESSING, CONFIRM_WAIT`) en `WorkoutVoiceGrammarBuilder`; actualizar `ReportVoiceContractTest` para exigirlo (hoy exige "reportar equipo").
3. **Mishearings:** registrar variantes fonéticas en `WorkoutVoiceMishearingCorrections` ("capolican", "caupolica", "caupoli kan", "caupolikán"...). Riesgo honesto: el modelo Vosk small-es puede destruir un mapudungun con ruido de gimnasio; la confirmación hablada actual ("Entendí: … Di enviar reporte, repetir o cancelar") es la red de seguridad, y "reportar equipo" queda como respaldo.
4. **Sincronización bidireccional con el JSONL:** al guardarse, además del `report_voice_saved` actual, se anexa una línea `user_comment` **dentro del JSONL de la sesión de voz activa** con `reportId` + texto: el JSONL puro queda auto-referenciado y el reporte diario puede citar ambos lados.
5. **Tests:** grammar builder (token presente), parser de trigger (alias y variantes), contrato actualizado, y entrada en el corpus de replay (`voice-replay`) cuando grabes un dictado real diciendo la keyword.

**Aceptación E2E:** en sesión de voz real, decir "CAUPOLICÁN" → la app pide el problema → dictado libre → confirmación → existe `report-<id>.jsonl` con `origin=VOICE`, línea `user_comment` en el JSONL de la sesión, y el comentario aparece vinculado en el reporte diario del apartado I y en `06-comentarios-usuario.md`.

---

## 7. Fase 5 — Visor, gobierno y documentación

1. **Pantalla Ajustes > Diagnósticos** (nueva): estado por área (archivos, tamaño, último evento, health-check), estado IA (clave, pendientes, último error), visor de reportes `.md` (diarios y puntuales), botones "Generar ahora", "Exportar todo" (único lugar con pickers SAF), toggles por área, y "Reportar problema" manual.
2. **Notificaciones locales** de finalización/fallo de análisis (canal propio, silencioso por defecto).
3. **Paridad iOS:** documentar qué aplica (`KpknTelemetry.swift` ya existe); el formato JSONL v2 es cross-platform desde el diseño.
4. **Docs finales:** `docs/DIAGNOSTICOS_JSONL.md` (guía de usuario: dónde queda cada cosa, cómo exportar, cómo subir a tu IA externa) y `docs/JSONL_COBERTURA.md` (matriz cubierto/pendiente por apartado, viva).


---

## 8. Análisis de factibilidad (sin mentiras)

| Idea | Veredicto | Fundamento |
|---|---|---|
| Gesto 2 dedos / 6 s | **Factible, pero mal calibrado hoy** | El mecanismo es correcto (nivel `dispatchTouchEvent`, máquina JVM testeable). Los umbrales (400 ms entre dedos, 6 s quietos, sin feedback) lo hacen inusable. Con los nuevos umbrales + progreso visual pasa a ser confiable; se valida 10/10 antes de darlo por hecho. |
| DeepSeek V4 Flash para reportes | **Factible, ya integrado** | `DeepSeekV4FlashClient` (`deepseek-v4-flash`, `api.deepseek.com`), credencial en Keystore y worker con reintentos ya existen. Lo que faltaba era superficie visible y bundles con líneas numeradas. No puedo verificar desde el código que ese modelo exista en la API hoy; el cliente ya abstrae endpoint/modelo, así que cambiar el nombre del modelo es una constante. |
| "Muse Spark 1.2 (Contributor)" | **NO verificable: no existe en el repo ni puedo confirmar el modelo** | **Alternativa:** se define una interfaz `AiReportProvider` ( DeepSeek = implementación por defecto). Si "Muse Spark" resulta tener API accesible, se enchufa como segundo proveedor para revisión/discrepancia entre modelos (rol "Contributor": comenta sobre el reporte de DeepSeek, no lo reemplaza). Si no existe, el sistema funciona igual con un solo proveedor y el rol queda documentado como pendiente. El plan **no depende** de él. |
| Reporte diario por apartado, on-device | **Factible con límites honestos** | Requiere red + clave + presupuesto de tokens; el pre-agregador determinista es obligatorio para que el día quepa en contexto. Por eso el camino PC es el principal y el on-device la conveniencia. |
| Keyword "CAUPOLICÁN" en sesión de voz | **Factible** | El pipeline de reporte por voz y la gramática por stages ya existen; es un alias + variantes de mishearing + tests. Riesgo residual: reconocimiento de la palabra exacta bajo ruido; mitigado con variantes y confirmación hablada. |
| Numerar líneas para evidenceRefs | **Factible sin cambiar el formato de escritura** | El bundler numera al leer; los JSONL siguen siendo append-only. |
| Monitoreo de rendimiento sin librerías nuevas | **Factible** | Choreographer, ActivityManager, timestamps de arranque; todo con APIs de plataforma, local-first, con muestreo acotado. |
| Cobertura "al 100%" | **Redefinida honestamente** | No se promete cobertura total: se define como (a) cada área con eventos de contrato testeados, (b) health-check diario que avisa si un área activa no escribió, (c) aceptación E2E por fase superada en dispositivo real. |

## 9. Riesgos y mitigaciones

1. **Volumen de logs → costo de tokens.** Mitigación: pre-agregador, presupuesto por apartado, `missingEvidence` explícito.
2. **Privacidad:** los JSONL contienen texto reconocido por voz y comentarios. Todo queda on-device; el envío a DeepSeek es por tu clave y bajo tu control (on-device opt-in o script PC). El sanitizer de secretos existente se mantiene y se extiende al store único.
3. **Permiso SAF revocado/expirado** (cambio de carpeta, restore): health-check lo detecta (`saf_mirror_lost`), aviso en panel; nunca picker automático.
4. **Regresiones de voz** al tocar gramática: tests de contrato + replay corpus antes de merge.
5. **Batería** por performance sampling: presupuestos por minuto y muestreo solo en pantallas clave.
6. **Duplicados durante la migración** de los 3 sistemas: importador idempotente + período de doble escritura controlado solo en debug.

## 10. Orden, dependencias y Definition of Done global

**Orden:** Fase 0 (reparaciones) → Fase 1 (pipeline) → Fase 2 (instrumentación, paralelizable por apartado) → Fase 3 (reportes IA; depende de 1 y de II/V mínimos) → Fase 4 (CAUPOLICÁN; independiente, puede ir tras Fase 0) → Fase 5 (visor; puede empezar en Fase 3).

**DoD global (todo verificable):**
1. Cero pickers SAF fuera de Ajustes (prueba manual + grep de `CreateDocument/OpenDocumentTree` fuera de settings).
2. Gesto: 10/10 activaciones reales + test unitario de la máquina de estados.
3. Un día de uso real produce `KPKN/logs/<5 áreas>/<fecha>/…` con esquema v2 validado por `validate_jsonl_schema.py`.
4. Reporte manual → `.md` visible (SAF + visor) con al menos un `evidenceRef` que apunta a una línea real.
5. Reportes diarios: 6 `.md` generados en un día con actividad, cada hallazgo con evidencia trazable; comentarios del día vinculados.
6. "CAUPOLICÁN" E2E en sesión real (sección 6) + tests de gramática en verde.
7. Panel de Diagnósticos muestra salud por área sin intervención manual.
