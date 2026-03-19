# Paso 4 - Workout + Programs 1:1

Fecha: 2026-03-18

## Veredicto

- Estado: aprobado con reservas
- Objetivo cumplido: tenemos mapa preciso de brecha real para el bloque Workout + Programs.
- Objetivo no cumplido: todavia no existe paridad 1:1 total en este dominio.

## Lectura ejecutiva

RN ya tiene una base funcional importante de entrenamiento, pero el ecosistema completo PWA sigue mas grande y mas profundo.

Medida objetiva por superficie de archivos (basename):

- PWA (workout/session-editor/program-detail/program-editor/program-wizard + top-level claves): 106
- RN (screens/components equivalentes del dominio): 54
- Coincidencias por basename: 23
- Faltantes por basename: 83

Esto no significa que RN no sirva, significa que todavia no replica todo el ecosistema 1:1.

## Lo que si esta bien encaminado

### Screens RN presentes

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\ActiveSessionScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\SessionEditorScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\ProgramDetailScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\ProgramWizardScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\MacrocycleEditorScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\SplitEditorScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\SessionDetailScreen.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Programs\ProgramsScreen.tsx`

### Componentes RN workout utiles

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\WorkoutHeader.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\AugeTelemetryPanel.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\ExerciseBlockCard.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\ExerciseSetRow.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\ModernSetEditor.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\ExerciseSubstitutionSheet.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\FinishSessionModal.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\PostSessionQuestionnaireModal.tsx`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\ReadinessModal.tsx`

### Evidencia de funcionalidad real ya integrada

- Hay temporizador de descanso en sesión activa.
- Hay haptics en múltiples acciones.
- Hay sustitución de ejercicio.
- Hay flujo de cierre de sesión + cuestionario post-sesión.

## Brecha principal (hard gap)

### Diferencia de tamaño de screens core

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\WorkoutSession.tsx`: 1531 líneas
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\ActiveSessionScreen.tsx`: 407 líneas

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\SessionEditor.tsx`: 2369 líneas
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\SessionEditorScreen.tsx`: 825 líneas

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\ProgramEditor.tsx`: 2448 líneas
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\ProgramWizardScreen.tsx`: 249 líneas

Interpretación:

- existe vertical RN
- pero no hay equivalencia de profundidad 1:1 todavia

### Faltantes notorios de ecosistema PWA

Entre los faltantes por basename en RN destacan:

- `CardCarouselBar.tsx`
- `InCardTimer.tsx`
- `NumpadOverlay.tsx`
- `PostExerciseDrawer.tsx`
- `WarmupDrawer.tsx`
- `WorkoutDrawer.tsx`
- `ExerciseCardContextMenu.tsx`
- `SetDetails.tsx`
- `StartWorkoutModal.tsx`
- `StartWorkoutDrawer.tsx`
- `LogWorkoutView.tsx`
- `LogHub.tsx`
- `AnalyticsDashboard.tsx`
- `TrainingCalendarGrid.tsx`
- `SplitAdvancedEditor.tsx`
- `StructureDrawer.tsx`
- `StructureGalleryDrawer.tsx`
- `ProgramMetric*Detail.tsx` (familia completa)
- `EditorSidebar.tsx`
- `VolumeSection.tsx`
- `GoalsSection.tsx`
- `EventsSection.tsx`

## Hallazgos por subdominio

### Workout Session

- Mejoras RN reales: header, timer, modal de cierre, cuestionario post.
- Gap: faltan piezas de UX densa de la PWA (drawer/context menu/numpad/post-exercise panel/carrusel).

### Session Editor

- RN ya permite edición y warmups.
- Gap: falta buena parte del sistema modular `session-editor/*` de PWA (AUGE drawer/fab, table/grid avanzadas, drawer system, roadmap visual completo).

### Program Detail

- RN tiene tabs y vistas útiles.
- Gap: en PWA existe un ecosistema mucho más grande (`metrics details`, `structure drawers`, `calendar grid`, `protocols`, `event timeline`).

### Program Editor + Wizard

- RN tiene wizard funcional.
- Gap: la capa pesada de editor PWA (sidebar + secciones + export/events/goals/volume) no está 1:1.

## Decision del paso

Paso 4 queda cerrado como diagnostico operativo y baseline de implementación, no como paridad final.

- Sirve para avanzar sin loop.
- No autoriza decir "Workout + Programs 1:1 completado".

## Backlog oficial que deja este paso

1. Llevar `ActiveSessionScreen` a paridad funcional con `WorkoutSession.tsx` (numpad/context menu/post-exercise flow/carrusel).
2. Elevar `SessionEditorScreen` con la arquitectura modular de `components/session-editor/*`.
3. Expandir `ProgramDetailScreen` con los submódulos críticos de `program-detail/*`.
4. Elevar `ProgramWizardScreen` + `MacrocycleEditorScreen` hacia la profundidad de `ProgramEditor.tsx` y `program-editor/*`.
5. Definir qué partes serán clone 1:1 y qué partes se marcan `N/A` de forma explícita y firmada.
