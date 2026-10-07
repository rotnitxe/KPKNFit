# W · Cableado de los símbolos en los pasos y control de calidad visual en la página larga

Lee antes `docs/entreno-v2/BRIEF_COMUN.md`, `docs/WIZARD_ENTRENO_V2.md`, `docs/entreno-v2/COPY.md` y `docs/WIZARD_PAGINA_LARGA.md`. Tu worktree parte de una rama donde ya aterrizaron: la columna vertebral (S-A: ruta, borrador, reductores, funciones del ViewModel, controles provisionales
`screens/onboarding/entreno/EntrenoXxxStep.kt`) y los componentes visuales puros de los paquetes U1–U5 (`screens/onboarding/design/entreno/**`). Tu trabajo es **enganchar cada componente a su paso** y verificar en el emulador que la página larga se comporta.

## Qué haces
Para cada paso, sustituye el cuerpo provisional por el componente real, leyendo del borrador (`state.draft`) y escribiendo SOLO por las funciones del ViewModel que dejó S-A (no inventes otra vía de escritura):
| Archivo de paso | Componente | Notas de cableado |
|---|---|---|
| `EntrenoPlacesStep` | `PlaceSymbolRow` | `selected = draft.trainingPlaces`; alternar con la función del VM; con ≥ 2 lugares aparece la nota de COPY. |
| `EntrenoMaterialStep` | `EquipmentSymbolGrid` | `symbols = EquipmentSymbols.symbolsFor(places)`; `selected = EquipmentSymbols.selectedFrom(availability)`; gimnasio muestra la nota «ya contamos con lo habitual». |
| `EntrenoGoalStep` | `GoalProfileList` | `blockedReasons` desde `TrainingGoalRequirements`; `onBlockedTap` ofrece volver al material (`vm.editStep(AVAILABILITY)`). |
| `EntrenoFreshDayStep` | `FreshDayRow` | selección única. |
| `EntrenoWeekdaysStep` | `WeekCalendar` | `places`, `dayPlaces`, inicio de semana, día más fuerte. |
| `EntrenoSessionTimeStep` | `SessionClockDial` | 20..180 paso 5; pista dinámica de COPY según el valor. |
| `EntrenoCapabilitiesStep` | `CapabilitySymbols` | habilidades según el material (las decide el dominio). |
| `EntrenoMusclesStep` | `MuscleSymbolGrid` | tope 5, `suggested = MuscleSuggestions.forProfile(goalProfile)`, y **«Omitir» arriba a la derecha** (texto discreto) que confirma el paso vacío. |
| `EntrenoMarksStep` | `LiftMarksPicker` | `lifts = MarksContext.liftsFor(...)`; unidad kg/lb del borrador. |
(`EntrenoPlanStep` y `EntrenoWeekLayoutStep` los cablea el paquete S-B, no tú.)
- Todo paso respeta las marcas de prueba `setup-step-<ID>` / `setup-summary-<ID>` / `setup-continue` y las reglas 1–9 de `WIZARD_PAGINA_LARGA.md` (el control del paso no repite título ni subtítulo; el paso que asoma se desenfoca solo en su control; etc.).
- Los componentes se pintan dentro de `WizardSectionBody` con el padding de la página: revisa que ninguna ilustración se corte, que el asomo del paso siguiente se vea bien (desenfoque y fundido) y que el scroll bloqueado hacia delante siga funcionando.

## Verificación en el emulador (obligatoria)
1. Compila el APK debug con sufijo e instálalo. Para llegar a los pasos de Entreno sin recorrer los de Datos básicos, usa un arnés de depuración **solo en `src/debug`** (actividad `WizardHarnessActivity`: abre `SetupWizardScreen(mode = FULL/TRAINING_ONLY, draftId = …)` con un borrador de prueba; patrón en `docs/entreno-v2/BRIEF_COMUN.md` y en tus conocimientos del repo: `ProgramRepository.init`,
   `AugeRepository`, `NutritionRepository.init`, `CustomExerciseRepository.initialize`, `WorkoutMediaRepository.init`, `initializeExerciseDatabase`, `WikiLabRepository.initialize`, `CatalogV2ProcessCache.getOrLoad`, y `setShowWhenLocked(true)`). Usa `SetupWizardMode.TRAINING_ONLY` para entrar directo al bloque de Entreno. NUNCA llegues a «Activar» en un teléfono con datos reales (el emulador es seguro).
2. Recorre el bloque completo con `emu_run.py --actions` (toques/deslizados de ≥ 700 ms para el pager) y captura cada paso **activo, plegado en su fila-resumen y asomando** en el paso anterior. Guarda en `C:\kw\shots\w\`.
3. Critica con Read y corrige: ¿los símbolos se reconocen?, ¿la animación corre fluida (sin saltos al deslizar la página)?, ¿hay cortes, solapes o texto bajo el botón de confirmar?, ¿se ve a 360 dp y con fuente al 130 %?, ¿qué pasa con movimiento reducido (`adb shell settings put global animator_duration_scale 0`)? Hasta 3 pasadas.
4. Pasa las pruebas de pantalla del wizard que tocaste (`com.example.kpkn.screens.onboarding.*`) y deja los `androidTest` compilando.

## Informe
Capturas (rutas) de cada paso en sus tres estados, defectos de los componentes que encontraste al integrarlos (qué componente, qué pasa) y cómo los resolviste o dónde quedan pendientes.
