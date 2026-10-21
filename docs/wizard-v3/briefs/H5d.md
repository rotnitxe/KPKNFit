# H5d · Asistente: el teclado se cierra al confirmar un paso (2026-10-12)

Worktree `C:\kw\u3`, rama `v3/h5d` (sale de `feat/wizard-v3`, versión 41). Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y
`docs/WIZARD_PAGINA_LARGA.md`. Tareas Gradle `...Base...`. Español neutro.

## De dónde sale
Prueba de la PR8 en el emulador. Tras confirmar el paso 1, «Empecemos por ti» (alias y fecha de nacimiento), el teclado numérico sigue
abierto sobre el paso 2, «¿Cuánto mides y pesas?»: tapa la regla de peso y sube el botón del check.
- En `screens/onboarding` nadie cierra el teclado: no hay `clearFocus` ni `LocalSoftwareKeyboardController`.
- **El botón del check:** `WizardDock` en `screens/onboarding/design/WizardPageChrome.kt:221`, con el botón privado `WizardDockButton` en la
  línea 279.
  - Lo usa SOLO `SetupWizardHost.kt:484`.
  - Su `onClick` llama a `submitAliasAgePair`, `submitAnthropometryPair`, `submitCurrentStep` u `onActivate`.
- **Campos de texto del paso 1:** `SetupBasicSteps.kt`.
  - Alias: unas líneas 230-270, con `KeyboardOptions(capitalization = Words)`.
  - Fecha: unas líneas 300-330, con `KeyboardOptions(keyboardType = Number)`.
  - Ninguno tiene acción de teclado.

## Cambios
1. **`WizardDock`** (en `WizardPageChrome.kt`; así vale para todos los pasos y se puede probar sin el ViewModel):
   - al tocar el botón, antes de llamar a `onClick`, `LocalFocusManager.current.clearFocus(force = true)` y
     `LocalSoftwareKeyboardController.current?.hide()`;
   - con el botón deshabilitado no hace nada, igual que hoy.
2. **`SetupBasicSteps.kt`, paso 1:**
   - alias con `imeAction = ImeAction.Next`: «Siguiente» del teclado pasa el foco a la fecha (`focusManager.moveFocus(FocusDirection.Down)`
     o un `FocusRequester`, lo que ya encaje);
   - fecha con `imeAction = ImeAction.Done`: «Listo» cierra el teclado (`clearFocus()`). NO confirma el paso: eso sigue siendo el check.
3. **Lo demás no cambia:** la lógica de pasos, la validación, la navegación y los textos.

## Pruebas (Robolectric, escritas a mano)
- **Clase:** `WizardDockFocusTest` en `src/test/java/com/example/kpkn/screens/onboarding/design/`. Copia los imports de una prueba Compose
  que ya exista.
- **Casos:**
  - **Botón habilitado:** una columna con un `BasicTextField` y un `WizardDock(enabled = true, label = "Continuar", onClick = { pulsado = true })`.
    Se da foco al campo (`requestFocus` o `performClick`) y se toca `setup-continue`. Después, `pulsado` vale `true` y el campo está sin
    foco (`assertIsNotFocused()`).
  - **Botón deshabilitado:** con `enabled = false`, tocar no llama a `onClick` y el foco sigue donde estaba.
- **Si el paso 1 se puede pintar sin montar todo el asistente**, añade además en esa clase: alias + `performImeAction()` deja el foco en la
  fecha, y fecha + `performImeAction()` la deja sin foco. Si exige demasiado andamiaje, no lo fuerces y dilo en el informe.
- Las pruebas que ya existían y usan el dock siguen en verde: `ReviewCarouselTest`, y de paso compila todo `screens.onboarding`.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u3/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*WizardDockFocus*" --tests "*ReviewCarousel*" > /c/kw/logs/v3-h5d-1.log 2>&1`

## Commit
- Por rutas, con mensaje en español: `fix(wizard): el teclado se cierra al confirmar un paso (H5d)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- `screens/workout`: H5c va a la vez.
- `SetupWizardViewModel.kt`, `SetupWizardModels.kt` y la ruta de pasos.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, archivo por archivo.
- Pruebas: número y resultado.
- Lo que no se pudo hacer y por qué.
