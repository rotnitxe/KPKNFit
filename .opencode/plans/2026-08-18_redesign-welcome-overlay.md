---
flags: []
---

# Plan: Rediseño overlay Bienvenida — tarjetas uniformes, inputs inline y resumen

Fecha: 2026-08-18

## Rutas

- Overlay UI: `android-native/app/src/main/java/com/example/kpkn/screens/home/components/WelcomeOnboardingOverlay.kt`
- Estado y lógica: `android-native/app/src/main/java/com/example/kpkn/screens/home/HomeViewModel.kt` (OnboardingState, onboardingStateFrom, updateDisplayName, createOnboardingProgram, markProgramDone, completeOnboarding)
- Montaje / navegación: `android-native/app/src/main/java/com/example/kpkn/screens/home/HomeScreen.kt` (LaunchedEffect onboardingState, HomeGlassOverlay), `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt` (hoisted state 638-690, render 1051-1078, navegación wizard 1386-1387)
- Activación programa: `android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt` (addProgram, startProgram 240-246, activeProgramState, ActiveProgramEntity)
- Plan nutrición / meta: `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt` (activeNutritionPlan, activeNutritionPlanId, activatePlan), `android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt` (NutritionPlan, NutritionGoal, TypedBodyGoal, GoalMetric, PlanDirection), `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionWizardViewModel.kt` (save 387-495)
- Modelos settings: `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt` (username, onboarding* flags)
- Navegación: `android-native/app/src/main/java/com/example/kpkn/navigation/Navigation.kt` (NutritionWizard route)
- Estilo glass: `android-native/app/src/main/java/com/example/kpkn/ui/theme/Glass.kt` (kpknGlass) y `screens/home/components/*`

## Impacto

### 1. Título y subtítulo
- `WelcomeOnboardingOverlay.kt:92-114` Header actual: título `¡Bienvenido a KPKN!` con `FontWeight.Black` alineado Start + subtítulo `Tu suite completa de entrenamiento` (blanco 66%) + descripción + divider.
- Cambio: título centrado (`textAlign = Center`, `Modifier.fillMaxWidth()`), eliminar línea subtítulo (línea 102). Mantener descripción "Somos más que una app..." o revisarla si el usuario confirma que también se quita — por defecto se mantiene. El botón X "Cerrar por ahora" permanece arriba-derecha (absoluto) o se mueve a esquina sin afectar centrado.

### 2. Estado — ampliar OnboardingState para resumen y activación
- `HomeViewModel.kt:640-670` `OnboardingState` hoy: `show, programDone, nutritionDone, completed, displayName` + `allTasksDone = programDone && nutritionDone`.
- Ampliar (sin romper domain puro):
  ```kotlin
  data class OnboardingState(
      val show: Boolean = false,
      val programDone: Boolean = false,
      val nutritionDone: Boolean = false,
      val completed: Boolean = false,
      val displayName: String = "Usuario",
      val programName: String? = null,
      val nutritionGoalLabel: String? = null, // ej "Perder grasa · Déficit" o "Ganar músculo 78 kg"
      val nutritionDirection: PlanDirection? = null,
  )
  ```
- Derivación en `onboardingStateFrom(settings, activePlanId, ready, dismissed, programs, activeProgramState, activeNutritionPlan)`:
  - `programName`: `activeProgramState?.let { programs.find { it.id == it.programId }?.name } ?: programs.firstOrNull { it.id == settings.lastCreatedProgramId? }?.name` — preferir programa activo; fallback al último creado si aún no se activó (transición).
  - `nutritionGoalLabel`: resolver desde `activeNutritionPlan` con precedencia existente `typedBodyGoal?.metric ?: primaryGoal?.metric ?: goalType` + `typedBodyGoal?.targetValueSi ?: primaryGoal?.value ?: goalValue` y `direction` (DEFICIT/MAINTENANCE/SURPLUS). Formatear ej "Perder grasa · 72 kg" o "Mantener peso".
  - `nutritionDone` mantiene `settings.onboardingNutritionDone || activePlanId != null`.
  - Combinar nuevos flows: `repository.programs` y `repository.activeProgramState` y `nutritionRepository.activeNutritionPlan` (o `activeNutritionPlanId` + lista).
- `HomeViewModel` debe observar esos flows en `combine` (hoy combina 4; pasará a 7). Usar `distinctUntilChanged`.

### 3. Activación inmediata del programa
- `HomeViewModel.createOnboardingProgram(name)` hoy solo hace `repository.addProgram(...)` y deja inactivo.
- Nuevo comportamiento requerido: al guardar nombre inline, el programa queda activado de inmediato sin pasar por Entreno.
- Implementación:
  ```kotlin
  suspend fun createAndActivateOnboardingProgram(name: String) {
      val program = buildProgram(name) // lógica actual 144-180
      repository.addProgram(program)
      repository.startProgram(program.id) // ProgramRepository.kt:240
      markProgramDone() // persiste onboardingProgramDone
  }
  ```
  - Mantener `createOnboardingProgram` como wrapper o renombrar; `HomeScreen` llamará al nuevo método en un `viewModelScope.launch(Dispatchers.IO)`.
  - Manejo de error: si `startProgram` falla, programDone queda false y la tarjeta muestra retry.
  - No tocar `ProgramRepository.addProgram` ni esquema Room (ActiveProgramEntity ya existe, v23). No requiere migración.

### 4. Rediseño UI — 3 tarjetas uniformes con check y despliegue inline
- Reemplazar bloque actual (OutlinedTextField separado 126-194 + OnboardingTaskRow 198-230 + CreateProgramNameDialog 289-338) por 3 tarjetas idénticas.
- Componente nuevo `OnboardingCard` (privado en mismo archivo):
  - Contenedor: `Surface` o `Box` con `kpknGlass` o `RoundedCornerShape(20.dp)` + `fillMaxWidth()` + `border 1.dp white 12%` cuando no done, `border teal 0xFF8FB7B8` cuando done. Padding 16dp, height min 72dp. Mismo estilo para las 3.
  - Header fila: icono circular izquierda (22dp `CheckCircle` teal si done, `Circle` blanco 45% si no / número 1/2/3), título (`labelLarge Black`), subtítulo (`labelSmall white 60%`), y chevron/expand icon derecha. `Modifier.clickable` en toda la tarjeta.
  - Check: visible siempre cuando `done == true` (teal). No animar check hasta que la acción persista.
  - Contenido expandido con `AnimatedVisibility(expandable + !done)` debajo del header, con `imePadding`.
- Tarjeta 1 — Nombre:
  - Título "Elige tu nombre" / subtítulo "Cómo te llamaremos" → cuando done muestra "¡Listo! Te llamaremos X".
  - Al pulsar tarjeta (si !done && !expanded) expande inline: `TextField` (no Outlined) con `TextFieldDefaults.colors(containerColor = Color.White.copy(alpha=0.12f), focusedIndicatorColor=Transparent, unfocusedIndicatorColor=Transparent)` — relleno semitransparente, sin trazo blanco. Placeholder "Tu nombre". `singleLine=true`, validación ≥3 chars, error text rojo suave. Botones `Guardar` (teal) y `Omitir` (guarda "Usuario") en fila derecha. Al guardar llama `onSaveName` y colapsa; `nameSaved` ya no necesita rememberSaveable separado — deriva de `state.displayName`.
  - Estado expandido local: `var nameExpanded by rememberSaveable { mutableStateOf(false) }`.
- Tarjeta 2 — Programa:
  - Título "Crea tu programa" / subtítulo "Ponle nombre y listo" → done "Programa 'Hypertrophy I' activo".
  - Expand inline idéntico: `TextField` semitransparente placeholder "Ej. Hypertrophy I". Botones "Crear y activar" / "Cancelar". `onCreateProgram` ahora es suspend y activa (punto 3). Validación no vacío. Al éxito colapsa y marca done (check aparece). Eliminar `CreateProgramNameDialog` (AlertDialog) — ya no se usa.
  - Si `state.programDone && state.programName != null`, la tarjeta no es clicable y muestra check + nombre del programa activo.
- Tarjeta 3 — Nutrición:
  - Título "Plan de nutrición" / subtítulo "Te guiamos paso a paso" → done muestra meta resumida.
  - No despliega input; al pulsar llama `onNavigateToNutritionWizard`. Mientras wizard está abierto, overlay permanece hoisted pero invisible porque `currentRoute != Home` (MainActivity 684). Al volver, `activeNutritionPlan != null` hace `nutritionDone=true` y aparece check. Mantener `launchSingleTop`.
  - Si done, mostrar `state.nutritionGoalLabel` como subtítulo de confirmación.

### 5. Flujo SIGUIENTE → resumen
- Eliminar `LaunchedEffect(state.allTasksDone)` con delay 1800ms + auto `onAllTasksDone`.
- Nuevo flujo:
  - Cuando `state.programDone && state.nutritionDone && state.displayName != "Usuario"`? No — nombre puede ser omitido ("Usuario" cuenta como completado según lógica actual 143-194). Definir `nameDone = state.displayName.isNotBlank()` (siempre true tras omitir) o añadir `nameDone` explícito al state (derivado de `username != ""`). Para uniforme, las 3 tarjetas contribuyen: `allTasksDone = nameDone && programDone && nutritionDone` donde `nameDone = displayName.isNotBlank() && displayName != ""` (incluye "Usuario" si se omitió). Alternativa: no exigir nombre para SIGUIENTE — el summary mostrará "Usuario" igualmente. Decisión propuesta: SIGUIENTE habilitado cuando `programDone && nutritionDone` (nombre opcional, como hoy). El check de la tarjeta nombre indica si se eligió nombre personalizado.
  - Cuando `allTasksDone` true, mostrar botón `SIGUIENTE` (fullWidth, 52dp height, `RoundedCornerShape(16.dp)`, fondo teal `0xFF8FB7B8`, texto `FontWeight.Black`, `letterSpacing 0.8.sp`, `onClick = { showSummary = true }`).
  - Estado local `var showSummary by rememberSaveable { mutableStateOf(false) }`. Si `!allTasksDone` y `showSummary==true`, reset a false (LaunchedEffect).
  - Vista resumen (segundo apartado del mismo overlay, sin navegación nueva):
    - Reusa misma tarjeta glass (no cerrar overlay). Contenido: título centrado "¡Todo listo!" o "Resumen", subtítulo "Así quedó tu configuración".
    - 3 filas resumen con icono check teal + label + valor: `Nombre: ${state.displayName}`, `Programa: ${state.programName ?: "—"}`, `Meta nutrición: ${state.nutritionGoalLabel ?: directionLabel}`.
    - Botón final "EMPEZAR" (`onAllTasksDone` → `viewModel.completeOnboarding()` que persiste `onboardingCompleted=true` y oculta overlay). Opcional botón secundario "Volver".
    - El resumen debe ser scrolleable y respetar `imePadding`.
  - Alternativa si se prefiere navegación: mantener overlay hoisted y solo cambiar contenido interno; no crear nueva ruta en NavGraph.

### 6. Cableado HomeScreen / MainActivity
- `HomeScreen.kt:163-203` LaunchedEffect: pasar `onCreateProgram` que llame `viewModel.createAndActivateOnboardingProgram` en scope IO. Añadir `onCompleteOnboarding` distinto de dismiss.
- `WelcomeOnboardingOverlay` firma nueva:
  ```kotlin
  fun WelcomeOnboardingOverlay(
      state: OnboardingState,
      hazeState: HazeState,
      onDismiss: () -> Unit,
      onSaveName: (String) -> Unit,
      onCreateProgram: (String) -> Unit, // ahora activa
      onNavigateToNutritionWizard: () -> Unit,
      onAllTasksDone: () -> Unit, // solo desde resumen EMPEZAR
  )
  ```
  - Internamente maneja `showSummary` y decide qué página renderizar.
- `MainActivity.kt` sin cambios estructurales; `showHomeGlassOverlays` y zIndex 102 se mantienen.

### 7. Estilo inputs semitransparentes
- Reemplazar todos `OutlinedTextField` del overlay por `TextField` con `colors = TextFieldDefaults.colors(focusedContainerColor = Color.White.copy(0.10f), unfocusedContainerColor = Color.White.copy(0.08f), disabledContainerColor = ..., cursorColor = teal, focusedTextColor = White, unfocusedTextColor = White, focusedPlaceholderColor = White 50%, ... , focusedIndicatorColor = Transparent)`, `shape = RoundedCornerShape(14.dp)`. Mantener contraste AA.

### 8. Orden y animaciones
- `Column` con `verticalScroll` + `AnimatedVisibility` por tarjeta expandida (expandVertically + fadeIn). `animateContentSize`.
- Scrim 55% y `pointerInput` que consume toques se mantiene.

## Pruebas

- Compilación: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "assembleBaseDebug"` (fallback `gradlew.bat --no-daemon --console=plain --warning-mode=summary assembleBaseDebug`, timeout 600000, workdir android-native).
- Tests dirigidos:
  - `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.HomeViewModelTest*'"` si existe; si no, `testBaseDebugUnitTest --tests '*.OnboardingState*'` y `testBaseDebugUnitTest --tests '*.ProgramRepositoryTest*'` para verificar `startProgram` tras creación.
  - Crear test nuevo `WelcomeOnboardingOverlayTest` (Compose UI) o `HomeViewModelOnboardingTest`: verifica `onboardingStateFrom` con `activeProgramState` y `activeNutritionPlan` correctos, `allTasksDone` requiere 3 condiciones, `createAndActivateOnboardingProgram` activa y marca done.
- Manual en emulador (solo con autorización): verificar centrado título, ausencia subtítulo, tarjetas uniformes, check aparece, input semitransparente sin trazo, programa queda activo en Entreno sin acción extra, wizard marca nutrición done al volver, SIGUIENTE lleva a resumen con nombre/programa/meta correctos, EMPEZAR cierra overlay y no reaparece tras restart (Settings.onboardingCompleted).
- Snapshot / screenshot diff si existe Paparazzi.

## Riesgos

- **Activación implícita rompe flujo existente:** `startProgram` cambia `ActiveProgramState` y puede desplazar programa activo previo del usuario. Mitigación: solo activar onboarding si `activeProgramState == null`; si ya hay programa activo, preguntar o no sobrescribir (decisión: si hay activo, `createAndActivate` reemplaza — documentar y confirmar con usuario).
- **Derivación de meta nutrición:** `nutritionGoalLabel` depende de `typedBodyGoal`/`primaryGoal`/`goalType`/`direction` — formato inconsistente puede mostrar label vacío. Mitigación: fallback a `direction.name` o "Plan activo" y test unitario para cada variante.
- **Expansión inline + IME:** `imePadding` + `verticalScroll` puede ocultar botón SIGUIENTE tras teclado. Mitigación: `bringIntoViewRequester` en TextField focus, probar en dispositivo pequeño.
- **Compatibilidad con dismiss:** `onDismiss` (X) hoy solo pone `_onboardingDismissed=true` (reaparece al reiniciar). Con resumen, asegurar que dismiss no persiste `onboardingCompleted` y que SIGUIENTE/EMPEZAR sí lo persiste.
- **Eliminación de auto-cierre 1800ms:** usuarios que esperaban autocierre verán botón SIGUIENTE — es cambio intencional pero comunicar en release notes.
- **Room sin migración:** no se toca esquema, pero `ActiveProgramEntity` y `NutritionActiveStateEntity` se escriben desde onboarding; verificar que `startProgram` y `activatePlan` son atómicos (ya lo son) y no pierden datos en rotación.
- **Tope auditoría 5 rondas:** cambios solo en `screens/home/` y `HomeViewModel`, sin flags sensibles, por lo que `kpkn-gate` no bloquea; `audit-loop` requiere BUILD SUCCESSFUL tras último cambio de producto antes de `submit_audit`.
