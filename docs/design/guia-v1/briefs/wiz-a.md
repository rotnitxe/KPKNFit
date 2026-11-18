# wiz-a · Auditoría del wizard y arreglos W1–W9 (Sonnet high)

Worktree `C:\kw\gu-wiz`, rama `guia/wiz`. Plan: «Fase 1» de `PLAN.md`.

## Entregables
1. `docs/audits/WIZARD_AUDIT_2026-10-30.md`: el informe de la auditoría para el usuario. Por hallazgo: id, gravedad (grave/media/baja), qué ve el usuario, causa con rutas:línea, arreglo y prueba. Secciones: resumen; W1–W11 (W10–W11 los hará otro paquete: descríbelos como «en curso»); decisiones de producto Q-a…Q-g con opciones y mi recomendación; tabla «respuesta → dónde se guarda → quién la lee». Verifica cada afirmación del plan leyendo el código; si alguna es falsa, corrígela en el informe y no la arregles.
2. Arreglos W1–W9 con su prueba:
   - W1 género: `EQUATION_SEX` → `SetupUserVitalsPatch.gender` (VM ~3577). Mapeo: hombre/mujer → `Gender`; «No lo sé» con consulta hormonal → el sexo que resulte de esa consulta si es concluyente; si no, no escribir.
   - W2 embarazo/lactancia → `userVitals.pregnancyLactation` (busca el campo y su tipo).
   - W3 grasa → `userVitals.bodyFatPercentage`.
   - W4 validaciones en el paso (objetivo vs dirección, kcal y macros manuales, edad < 19 con automático, elegibilidad «No lo sé»): el paso no se confirma y muestra el mismo motivo que hoy da `reviewErrors`; mantén la comprobación final como red.
   - W5 calibración de volumen: primero confirma si sigue viva tras POST-RINGS (lee `VolumeCalibrationGate`, `VolumeCalibrationProvenanceTest`, `docs/post-rings/`). Si lo está, copia `volumeRecommendations` y `athleteProfileScore` a los programas generados, de autor y NATIVE. Si no, no toques nada y ponlo en el informe como decisión.
   - W6 guardia del enlace `FULL`/`RESUME` con el alta hecha → `NEW_PROGRAM` (`DeepLinkRouter.kt`).
   - W7 un solo id de borrador para el alta completa (`SetupEntryScreen.kt:55`, `HomeScreen.kt:258`), compatible con borradores ya guardados con uuid.
   - W8 pesaje de hoy sin duplicar la observación WEIGHT (`SetupActivationPayload.kt`).
   - W9 escribir `birthDateIso` además de la edad.

## Archivos permitidos
`screens/onboarding/**` (salvo `welcome/**` y `SetupWelcomeScreen.kt`), `data/onboarding/**`, `domain/onboarding/**`, `navigation/DeepLinkRouter.kt`, `screens/home/HomeScreen.kt` (solo la línea del id de borrador), sus pruebas, y el informe.

## Pruebas
`SetupSettingsPatchTest`, `SetupWizardValidationTest`, `DeepLinkRouterTest`, `SetupActivationContractTest`, `VolumeCalibrationProvenanceTest` y las que toques: una sola corrida Android al final con todas las clases juntas (más `kpkn_jvm.sh` para lo de `domain/`).
