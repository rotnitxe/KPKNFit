# W3 — grasa corporal: «≈ N %» visible tras «Omitido»

Defecto: con el paso en estado «Omitido», el selector compartido seguía mostrando «≈ 25 % de grasa corporal (estimación visual)» a plena opacidad y parecía un dato declarado.

Corrección (edición mínima, `screens/onboarding/SetupBasicSteps.kt`): en `SetupVisualBodyFat` el `WizardPhysiqueSelector` va envuelto en un `Box` con `Modifier.alpha(bodyFatSelectorAlpha(bodyFatState))`. Mientras el estado es `SKIPPED` el selector queda atenuado (0,35: «sin usar», pero legible y tocable; moverlo vuelve a declarar una estimación y recupera la opacidad plena). Resto de estados: 1,0. `WizardPhysiqueSelector` y su texto NO se tocaron (`WizardGateComponentsUiTest` intacto). La línea de estado «Omitido» y su ayuda siguen a plena opacidad bajo la figura.

Test JVM del estado de presentación: `SetupBodyFatStepTest.theFigureSelectorIsDimmedOnlyWhileTheStepIsSkipped` (atenuado solo en SKIPPED; vuelve a 1,0 tras declarar de nuevo).

Archivos: `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupBasicSteps.kt` (modificado), `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupBodyFatStepTest.kt` (modificado).

Gradle: `W3-assemble-1.log` (`assembleBaseDebug assembleBaseDebugAndroidTest`) y `W3-tests-1.log` (SetupBodyFatStepTest, SetupWizardBodyFatViewModelTest, SetupStepAnswersTest, SetupWizardFullJourneyTest, SetupWizardActivationGateTest, WizardReviewAndSweepTest); resultados en los recibos. Sin emulador: la comprobación visual queda para quien coordina (el estado atenuado debería verse con el «≈ N %» claramente apagado).
