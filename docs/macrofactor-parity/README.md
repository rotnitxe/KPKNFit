# Ola MFP · paridad con lo mejor de MacroFactor

**Base:** `artifacts/macrofactor-reverse-engineering-20261020/INFORME_FINAL.md` (HEAD de partida `8ac57c9d4` de master).
**Rama:** `feat/macrofactor-parity`, worktree `C:\kw\mfp`. **Orquesta:** Claude. **Escribe:** Codex CLI (Sol 6.1 Max), uno a la vez, compilando por `gradle_solo.sh`.
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
| D12 | QA visual: hojas de render PNG por paquete (Codex) + revisión del orquestador en el Flip5 con una variante propia **`com.example.kpkn.mfp`** (init script `C:\kw\tools\mfp-suffix.init.gradle`, ejecutor `C:\kw\tools\mfp_phone.py`), al cerrar cada paquete de UI. | Orden del usuario del 20-oct (noche): puedo probar en el Flip siempre que no afecte a la prueba en segundo plano de otro agente; otra app = otros datos, sin roce. |

### Protocolo de QA en el Flip5 (D12)
- **Aislamiento:** solo `com.example.kpkn.mfp`. Nunca `.dbg` (el banco de vídeos corre como servicio en primer plano ahí), nunca `.vtqa`, ni la app de producción. No toco el candado `C:\kw\phone.lock` (lo tiene el banco), no hago `adb kill-server` ni `logcat -c`, y solo lanzo comandos sobre mi paquete y lecturas de `dumpsys`.
- **Antes de cada sesión** `python C:\kw\tools\mfp_phone.py --check` (batería, temperatura, bloqueo, audio, actividad de la persona, dueño del candado). Regla de 20 min de la persona intacta. Si el foco deja de ser el mío, la sesión se corta sin enviar Inicio ni apagado.
- **Datos:** la variante tiene su propia base (vacía). Se pueblan con las herramientas de depuración que ya existen (`WizardHarnessActivity persona=qa activate=1500`, `DemoSeedActivity`) y con las vistas previas que cada paquete de UI entrega (`<Nombre>HarnessActivity`, ver COMUN.md).
- **Al terminar la ola:** desinstalo `com.example.kpkn.mfp` del teléfono y borro APK y capturas pesadas.

## Paquetes, orden y propiedad de archivos calientes

Orden de ejecución (serie): **H1 → N1 → T2 → T1 → N2a → N2b → T6 → T3 → T4 → T5a → T5b → N3 → N4 → P2-N → P2-T → P2-D**.

| ID | Qué | Depende de | Prio. |
|---|---|---|---|
| H1 | Higiene de datos de nutrición (creatina durable, cuarentena por fila, reglas de ignore) | — | P0 |
| N1 | Estimador de gasto + tendencia robusta (dominio puro) + banco de simulación | — | P0 |
| T2 | Ventana y contabilidad de volumen (contrato único) | — | P0 |
| T1 | «Mi material» (inventario de equipo) + inventario único en el runtime | — | P0 |
| N2a | Estado del día (Room v30) + marcar parcial/ayuno en el diario | H1, N1 | P0 |
| N2b | Check-in semanal + aplicar objetivo + sustituir el uso del motor antiguo | N1, N2a | P0 |
| T6 | Sustituciones inteligentes en la sesión en vivo | — | P1 |
| T3 | Modelo de fuerza versionado con RIR | T2 | P1 |
| T4 | Objetivo automático (reps, peso) sobre el inventario | T1, T3 | P1 |
| T5a | Panel de series por ventana + modo volumen en la silueta | T2 | P1 |
| T5b | Progreso por ejercicio + récords | T3, T5a | P1 |
| N3 | Escáner de códigos + búsqueda por código + alimentos propios | N2a, H1 | P1 |
| N4 | Reutilización del diario (copiar, repetir, plantillas, entrada rápida) | N2a, N3 | P1 |
| P2-N / P2-T / P2-D | Resto de P2 agrupado (nutrición / entrenamiento / datos) | según | P2 |

Archivos calientes con un único dueño por paquete (no se tocan fuera de él): `data/db/*` (N2a, luego N3), `navigation/Navigation.kt` y `MainActivity.kt` (el paquete en curso; bloques nuevos solamente), `NutritionRepository.kt`, `NutritionScreen.kt`, `NutritionViewModel.kt`.

## Estado

| ID | Estado | Commits | Notas |
|---|---|---|---|
| H1 | pendiente | | |
| N1 | pendiente | | |
| T2 | pendiente | | |
| T1 | pendiente | | |
| N2a | pendiente | | |
| N2b | pendiente | | |
| T6 | pendiente | | |
| T3 | pendiente | | |
| T4 | pendiente | | |
| T5a | pendiente | | |
| T5b | pendiente | | |
| N3 | pendiente | | |
| N4 | pendiente | | |
| P2-N | pendiente | | |
| P2-T | pendiente | | |
| P2-D | pendiente | | |
