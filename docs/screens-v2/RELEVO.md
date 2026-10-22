# Relevo de la ola screens-v2 (21-oct-2026, noche)

## Inventario 2026-10-22 (relevo por fases)

Comprobado al arrancar esta sesión, antes de tocar nada:

- **Procesos:** no hay `opencode`, `gradle_slot` ni `java` activos; la cola v10 está vacía. Sí sigue vivo un `python.exe` (PID 17668) que mantiene `C:\kw\phone.lock` desde las 00:04; no se ha matado (aviso para el usuario).
- **u11 · LV-C:** 11 archivos modificados y 6 nuevos sin commitear (≈925 líneas añadidas; `WarmupMobilityLiveCards.kt` es el grueso). Sin compilar. Los tres encargos extra están empezados: `WarmupLoadBase.kt`, textos de tope en `CalibrationEngine.kt` y espera de 15 s en `VoiceFirstUseDeviceTest`.
- **u12 · MOT + ED-E:** limpio; `f042464ba` más el brief `MOT.md` sin seguimiento. Hecho.
- **u13 · PROG + FIX-TM:** limpio. INV sin empezar: `RecipeContractInventoryTest` sigue en rojo y no hay cambios.
- **u14 · ED-A:** hecho y fusionado; ED-B sin empezar (solo `briefs/ED-B.md` sin seguimiento).
- **u15 · CAT-A:** 7 archivos modificados y 4 nuevos de código/pruebas (≈262 líneas); brief y un informe preliminar. Iba por la prueba de plegado e4.
- **u16 · G:** 9 archivos modificados y 5 nuevos de código/pruebas (≈1001 líneas); brief sin commitear. Iba a compilar producción.
- **u17 · ED-D:** 1 commit (retroques) y 11 archivos modificados + 4 pruebas nuevas (≈731 líneas), C2 y C3 a medias. C4–C7 pendientes.
- **u18 · ED-C:** 4 archivos modificados y 3 nuevos de código/pruebas (≈143 líneas); brief sin commitear.
- **Rama de integración:** `feat/screens-v2` en u11, con MOT y PROG ya fusionados.
- **Tableros:** la carpeta temporal del lienzo no es accesible desde este entorno; se trabaja con los briefs, los informes y los renders existentes, y se anota la limitación.

Lo escribe el orquestador (Claude Opus) al acercarse al tope semanal de cuota de Claude. **Copia el prompt de la sección 4 en un agente nuevo.**

## 1. Qué es esta ola

En KPKN Fit (Android, Kotlin y Compose) se perfecciona, pantalla por pantalla:
1. sesión en vivo;
2. editor de sesiones y catálogo de ejercicios, desde los prototipos aprobados;
3. después, Program Detail, Nutrición e Inicio.

Hay que añadir un calibrador AMRAP real, fiel a cada programa y **sin propagación a otros ejercicios**.

- Lienzo aprobado: https://claude.ai/artifact/1sjmv6uyMRxEYBHNqFqbyA (v24, 71 tableros).
- Fuentes de los tableros: `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\*.dc.html`. Son de solo lectura; si desaparecen, se pueden leer desde el lienzo.
- Documentos rectores en `C:\kw\u11\docs\screens-v2\`:
  - `PLAN_IMPLEMENTACION.md`: leyes, 18 paquetes, **esquema de Room congelado**.
  - `CALIBRADOR_V1.md`: especificación, con las decisiones §4 (APRE: manda Mann) y §5b.6 (sin TM, se pide; no se infiere).
  - `DECISIONES_PROTOTIPOS_2026-10-20.md`.
  - `audits/`, `briefs/` y `reports/`.

## 2. Estado por worktree (rama de integración `feat/screens-v2` en `C:\kw\u11`)

| Worktree | Rama | Paquete | Estado |
|---|---|---|---|
| u11 | `feat/screens-v2` | CAL1–CAL3, LV-A, LV-B, LV-AF, LV-BF; MOT y PROG fusionados | Hecho. QA de CAL3 en emulador: `reports/QA_EMU_CAL3.md`. **LV-C en curso** (agente Claude Sonnet), con 18 archivos sin commitear. Lleva además 3 encargos extra: el texto de tope del calibrador (`CalibrationEngine` ≈ :121), la espera de 15 s en `VoiceFirstUseDeviceTest` y la base de las aproximaciones (`recordWarmupHeavinessAndAwait`: % del peso de trabajo, no del 1RM). |
| u12 | `sv2/motores` | MOT + ED-E (hojas D1–D8) | **Hecho** (`f042464ba`, informe `reports/ED-E.md`). Sin fusionar en u11. |
| u13 | `sv2/programas` | PROG + FIX-TM, con `feat/screens-v2` fusionado (`3ec697594`) | Hecho. Integración: 8.844 pruebas, 13 fallos; 10 son de entorno (imágenes de catálogo y hashes de `domain.biomechanics.golden` por CRLF). **Falta `RecipeContractInventoryTest`** (108 > 100 y 21 > 18). Lo arreglaba un agente DeepSeek (OpenCode, log `C:\kw\logs\oc-inv-1.log`), sin subir los techos, con informe `reports/INV-PROG.md`. Después hay que fusionar u13 en u11. |
| u14 | `sv2/editor` | ED-A hecho (`5ede20061`, `reports/ED-A.md`); fusionado con u13 (`db5aa08cf`) | **ED-B en curso** con GLM 5.3 Flash (OpenCode, log `oc-edb-1.log`). Brief: `briefs/ED-B.md`. La corrida base es `C:\kw\logs\edb-1.log`. |
| u15 | `sv2/catalogo` | CAT-A | **En curso** (agente Claude Sonnet), 14 archivos sin commitear. |
| u16 | `sv2/otros` | G (G1–G4) | **En curso** (agente Claude Sonnet), 15 archivos sin commitear. Brief: `briefs/G.md`. |
| u17 | `sv2/cardio` | ED-D (C2–C7 y 2 retoques de ED-A) | **En curso** con MiniMax M3.1 Flash (OpenCode, `oc-edd-2.log`); MiMo se cortó por un error de herramienta. Hecho `0943266b8`; 15 archivos sin commitear. |
| u18 | `sv2/prep` | ED-C (H1–H4, B6, D9) | **En curso** con DeepSeek v4.1 Flash (OpenCode, `oc-edc-1.log`), 8 archivos sin commitear. |

**Paquetes pendientes:**
- CAT-B: creador de ejercicio E7 con selector de foto del álbum, fichas F1 y F2.
- CAL4-V: aplicar en vivo S3→S4 de APRE con la tabla de Mann, sobre la tarjeta V7.
- CAL5b: TM por ejercicio y visible en vivo, en el editor y en el detalle; petición en la preparación.
- CAL7: punta a punta.
- Notas de `PlanObserver` para las reglas nuevas.
- Integración final de todas las ramas en u11.
- Regresión.
- QA en el teléfono o en el emulador.
- APK.

Después: Program Detail, Nutrición e Inicio.

### Estado al parar (cuota al 94 %)

Todo está parado: los agentes Claude, los de OpenCode y las corridas de Gradle en cola de esta sesión. Nada a medias está commiteado, y lo que no tiene commit sigue en cada worktree:

| Worktree | Trabajo a medias |
|---|---|
| u11 · LV-C | 18 archivos, sin compilar. Estaba con la función única de base de las aproximaciones. |
| u14 · ED-B | GLM no llegó a cambiar código; solo está `briefs/ED-B.md`, sin seguimiento. |
| u15 · CAT-A | 14 archivos. Ya probaba la protección de plegado (e3 pasaba; iba por e4). |
| u16 · G | 15 archivos. Iba a compilar producción. |
| u17 · ED-D | 15 archivos (C2 y C3, superseries). |
| u18 · ED-C | 8 archivos. |
| u13 · inventario | DeepSeek no llegó a cambiar nada. `RecipeContractInventoryTest` sigue en rojo. |

Cada agente de relevo arranca con `git diff` en su worktree y lo termina con su brief.

## 3. Reglas que no se negocian

- **Siempre en español.** Sin push ni fusión en master sin orden del usuario. La firma del APK la hace el orquestador con `C:\kw\tools\signed_release.py`; nunca se piden credenciales.
- **Room congelado.** `CalibrationDecisionEntity` y `CALIBRATION_MIGRATION_29_30` (v30) están publicados en el APK personal del usuario, `com.example.kpkn.completo`, que lleva nutrición en v31.
  - Al fusionar en master: calibración 30, nutrición 31.
  - Cualquier cambio nuevo va de la 32 en adelante.
  - **Nunca tocar `com.example.kpkn` ni `com.example.kpkn.completo`.** La `.dbg` es la de QA.
- **Gradle:** solo por la cola v10 (`C:\kw\tools\GRADLE_GUIA.md`).
  - Dominio y datos puros: `kpkn_jvm.sh`.
  - Pantallas: `gradle_slot.sh` con clases concretas.
  - Nunca `--stop` ni matar java.
- **Agentes, por orden del usuario:**
  1. primero modelos **gratuitos Flash** de OpenCode (`deepseek-v4.1-flash-free`, `mimo-v2.6-flash-free`, `glm-5.3-flash-free`, `minimax-m3.1-flash-free`), con `bash /c/kw/tools/oc_run.sh <wt> <prompt> <prefijo> <modelo>` y prompts que lleven `scratchpad/free_common.md`;
  2. **nunca Nemotron ni LongCat**;
  3. si hace falta Claude, Haiku 5.5 siguiendo `C:\kw\tools\HAIKU_GUIA.md`;
  4. Sonnet u Opus solo si Haiku falla dos veces.
  - Gemini (Antigravity) se quedó sin cuota.
  - Codex no tiene cuota hasta el 27-oct a las 12:38.
- **Diseño:** «refinar, no quitar», nada inventado, objetivos táctiles de al menos 48 dp, tarjeta en vivo de 312 × 425 dp con todo dentro y la paleta KPKN.
- **QA:** emulador `ZFlip5_QA`, puerto 5580.
  - Arranque: `emulator -avd ZFlip5_QA -port 5580 -no-snapshot -no-boot-anim -gpu host -memory 3072`.
  - Tras instalar, `cmd package compile -m speed -f com.example.kpkn.dbg`.
  - Al terminar, borrar los qcow2 y los snapshots.
  - El teléfono (Z Flip5) solo con `phone_run.py` y la regla de 20 min.

## 4. Prompt de relevo (cópialo tal cual)

> Eres el orquestador de la ola screens-v2 de KPKN Fit. Respondes siempre en español. No escribes código del repo: escribes briefs y documentación, lanzas agentes, revisas sus diffs y renders, ejecutas las pruebas por la cola de Gradle e integras con merges en `C:\kw\u11` (rama `feat/screens-v2`). Lee primero `C:\kw\u11\docs\screens-v2\RELEVO.md`, `PLAN_IMPLEMENTACION.md` y `CALIBRADOR_V1.md`, y respeta sus reglas: Room congelado, sin push, agentes gratuitos primero y Gradle por la cola v10.
>
> Pasos:
> 1. Comprueba el estado real de cada worktree (u11 a u18) con `git log` y `git status`, y de los agentes de OpenCode por sus logs en `C:\kw\logs\oc-*.log`. Los agentes Claude de la sesión anterior (LV-C en u11, CAT-A en u15 y G en u16) ya no existen. Si dejaron trabajo sin commitear, relánzalo con un modelo gratuito Flash, que continúe desde `git diff` con el brief de `docs/screens-v2/briefs/` y el bloque `free_common.md`.
> 2. Revisa cada entrega terminada: informe, diff, renders y resultado de pruebas.
> 3. Integra en u11 por este orden: u13 (con `RecipeContractInventoryTest` en verde), u12, u14, u17, u18, u15 y u16. Resuelve los conflictos con criterio y apúntalos.
> 4. Sigue con los pendientes de `RELEVO.md` §2, con un agente por paquete.
> 5. Después: regresión, QA en el emulador o el teléfono y APK. Pregunta al usuario antes de master o del push.

## 5. Decisiones del orquestador en esta sesión

- **Esquema congelado:** acordado con la sesión de técnica por vídeo.
- **Etiqueta de las series %TM sin TM:** las series en %TM sin TM se marcan como pendientes (`CALIBRADOR_V1` §5b.6).
- **Bloque APRE:** manda la tabla de Mann y la serie 3 es AMRAP (§4).
- **Fusiones hechas:** MOT y PROG en u11 sin conflictos. En u13, el conflicto de `AugeFatigueEngine` se resolvió con la versión de MOT, porque `plannedSetWeightKg` ya respeta el TM.
- **Pruebas de voz:** el fallo de `VoiceFirstUseDeviceTest` es intermitente por carga, no viene de CAL3.
