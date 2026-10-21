# LV-B · Tarjeta en vivo por modo y deslizadores (V6, V8–V11, B8b)

Worktree `C:\kw\u11`, rama `feat/screens-v2`, sobre LV-A. Lee primero:
- `C:\kw\u11\CLAUDE.md`, `docs/screens-v2/PLAN_IMPLEMENTACION.md` (leyes) y `docs/screens-v2/reports/LV-A.md`, el marco nuevo sobre el que trabajas.
- `docs/screens-v2/reports/PROTO_R4B_modos_vivo.md`.
- Los tableros aprobados, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **V6-VivoRM**, **V8-VivoSoloRPE**, **V9-VivoTiempo**, **V10-VivoDistancia**, **V11-VivoPersonalizado** y **B8b-SerieVivoROM**.
  - Ábrelos en un navegador local para ver su comportamiento.
- El AMRAP en vivo (V7) **no** es de este paquete: lo hace CAL3. Deja preparado el punto de extensión de la tarjeta por modo para que CAL3 añada el estado de resultado del calibrador.

## Qué construir

0. **Antes que nada: 23 pruebas unitarias en rojo.** LV-A las reprodujo idénticas en `0502af225` (detalle en `reports/LV-A.md`):
   - `WorkoutReadinessSheetAdjustTest` (16)
   - `WorkoutReadinessSheetCompactTest` (1)
   - `LiveExerciseOptionsUiTest` (2)
   - `LiveOptionsLargeFontTest` (3)
   - `SetExecutionCardVideoTest` (1)

   Averigua con `git bisect` o comparando commits desde master `8ac57c9d4` qué commit las rompió (candidatos: LQA1, AUD2, CAL1). Después decide con evidencia:
   - si la interfaz perdió una función o un comportamiento, **arregla la app**;
   - si solo cambió un texto o un diseño aprobado, **actualiza la prueba** conservando su invariante.

   `SetExecutionCardVideoTest` (restaurar el esfuerzo tras marcar fallo muscular en una serie ya registrada) huele a regresión real: compruébalo primero. Cuando termines, `screens.workout.*` debe quedar en verde.

1. **Tarjeta de serie por modo**, siempre de 312 × 425 en el marco de LV-A, con todo dentro. Si algo no cabe, se pagina o se desplaza dentro de la tarjeta.
   - **RM (V6):**
     - foco de %RM con tres cifras enlazadas y editables (reps estimadas, %RM y peso);
     - la referencia de 1RM o PR, con el TM separado que dejó CAL1;
     - el resultado real.
     - Reutiliza las fórmulas de `Calculations.kt` (≈ :132 y :288).
   - **Solo RPE (V8):**
     - la escala RPE como protagonista, con el deslizador aprobado;
     - la carga se elige al ejecutar;
     - se registran kg, reps y esfuerzo, sin sugerencia de carga fija.
   - **Tiempo (V9):**
     - reloj con inicio y detención;
     - tiempo realizado con entrada en min:s;
     - completar el objetivo espera la confirmación de registro, el ✓ de la línea.
   - **Distancia (V10):** contador en m, progreso y carga usada (por ejemplo, paseo del granjero). Se registra con la unidad DISTANCE.
   - **Personalizado (V11):** la unidad propia del ejercicio (por ejemplo, «brazadas») y la cantidad real.
   - Reps (la tarjeta actual) sigue igual en contenido, adaptada al marco.
2. **Deslizador de esfuerzo aprobado** en todos los modos que lo usan. Es el de la app (`EffortScale.kt`, `EffortFireSlider.kt`) en su versión fina:
   - pista de unos 18 dp con área táctil de 44 dp;
   - relleno con degradado y marca tenue del plan;
   - destello y chispas que crecen al acercarse al fallo;
   - respeta «reducir movimiento».
   - El esfuerzo empieza **sin marcar**, en el mínimo (ley 2).
   - La tarjeta no cambia de alto.
3. **ROM (B8b):** el deslizador de ROM cabe en la tarjeta en vivo **sin cambiar su tamaño ni su alto**.
4. Conserva todas las funciones que la tarjeta tiene hoy (`SetExecutionCard.kt` y vecinos): parciales, con ayuda, técnicas, notas, fotos/vídeo/ficha, Avanzado y modo visual. Refinar, no quitar.

## Pruebas

- **Compose/Robolectric:** por modo, registro completo, validaciones (cantidades positivas, min:s), esfuerzo sin marcar por defecto, reloj (iniciar, detener, completar) y que la tarjeta mide lo mismo en todos los modos.
- **androidTest:** adapta las pruebas de la tarjeta que dependan del diseño anterior, sin rebajar aserciones sin explicarlo. Añade renders de revisión por modo a 880 y a 740 de alto, para que el orquestador los compare con los tableros.
- **Comandos:**
  - `:app:compileBaseDebugKotlin`;
  - `:app:compileBaseDebugAndroidTestKotlin`;
  - `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*"` y tus pruebas nuevas;
  - al final, `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest -I C:/kw/tools/dbg-suffix.init.gradle`.

## Reglas

Las de siempre:
- Gradle solo con `gradle_slot.sh`, en primer plano y con la opción `-Xmx8g`.
- Sin `--stop` ni matar java, sin teléfono ni adb, nada fuera de `C:\kw\u11` y sin push.
- Commits en español con `Co-Authored-By: Codex <noreply@openai.com>`.
- No toques el editor ni el calibrador.

## Informe

En `docs/screens-v2/reports/LV-B.md`: qué cambió por modo, medidas comprobadas, pruebas, riesgos y pasos de QA en el emulador.
