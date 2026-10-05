# Estado de ejecución — wizard de bienvenida

## Estado vigente — 2026-10-05

**Fase 1 abierta.** El índice F0.3 y la consolidación de evidencia se entregan en este commit documental. Código entregado: recetas y metadatos, 32 rutas en `f56cd6e76`; A39 `54668bcb8`; Q-07 `ede1123d4`; tests editoriales `6fcd47e08`; consumidor TM `7c0c27e5b`. Catálogo `8eb3432a2`/`844c3d1d9`, SHA `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`.

**Consumidor TM entregado y probado.** Cuatro rutas comparten la resolución de RM+PERCENT_TM, conservan los kg y las repeticiones prescritas y dejan pendiente un TM ausente, sin inferirlo de PR o historial. El filtro excepcional de la sesión 73134 pasó exit 0/BUILD SUCCESSFUL en 2 min 42 s: nueve suites, 143 tests sin fallos, errores ni omitidos, incluidos los 13 nuevos. C verificó los hashes de los XML archivados y las cuatro fuentes. La autorización humana `call_FPTcKi9Z0wPLyDWEltPGNO3K` de una ejecución adicional quedó consumida. Se preserva el intento 3 fallido por dos oráculos literales Double; la reparación comparó exactamente el porcentaje original con el posterior, sin cambiar tolerancias, kg ni producción. El caso publicado de 174/178,35 kg pasó antes y después de esa reparación.

| Evidencia actual | Resultado | Alcance |
|---|---|---|
| Contrato FULL final | Exit 0/BUILD SUCCESSFUL, 14 min 49 s; 2 tests sin fallos, errores ni omitidos; 25.650 filas, 13.498 Ready y 12.152 rechazos honestos, todos reparados; cero violaciones de las siete clases | Generación C1–C5, `native-cycle-2`, seis ratchets en cero, Temurin C2 normal y 4 hilos. No sustituye la suite Base global ni los tests del consumidor |
| Editorial actual | Library 19/0 y PlanInfo 52/0, total 71/0; BUILD SUCCESSFUL, 52 s; exit estructurado NO_OBSERVABLE | XML, log y fuentes archivados; dos rutas en `6fcd47e08`; no acredita QA visual |
| Diagnóstico E16 | Nutrition 5/0; p95 mediana 26,2 ms frente a 50 ms; BUILD SUCCESSFUL, 79 s; exit estructurado NO_OBSERVABLE | C2 normal; no se extrapola a la suite Base completa |
| Base global | Full1 rechazada; full2 OOM e incompleta; full3 interrumpida por C2; full4 C1 con timeout, exit 124 a los 3.600 s | Sin suite Base actual aceptada; otra ejecución con JBR21 sigue sin autorización |
| Consumidor TM actual | Filtro excepcional 73134: exit 0/BUILD SUCCESSFUL, 2 min 42 s; nueve suites y 143 tests sin fallos, errores ni omitidos; 13 nuevos/0 | Commit `7c0c27e5b`; fuentes y XML archivados. No se extrapola a Base completa ni UI |
| APK nuevo e instalación | Assemble-only 51687: exit 0/BUILD SUCCESSFUL, 44 s; SHA `09ae806279ad93688cab49ff7f720fa9868a0191cdb1e99135f0fe9e52e6add8`, 553.916.698 bytes, UTC 03:37:07.4030450. Instalación Success a las 03:52:17 UTC | Arranque adicional con Status ok, COLD de 4.883 ms, MainActivity al frente y Welcome observado en XML/PNG a las 03:52:49, sin modal del sistema |
| QA pública | El FULL temporal llegó a Experiencia de entrenamiento y quedó aparcado antes de configurar o activar un plan | Los 28 casos públicos siguen NOT_RUN; no se aceptó una fixture FULL. El arranque PASS se registra por separado |
| Conservación | Restore ORIGINAL posterior: exit 0/PASS a las 04:05:22.5294762 UTC; 207 archivos y WAL con bytes equivalentes, app detenida | Backup original privado `35f20af…3bdf` y parcial de QA, 259 miembros/SHA `4fe4e207…3f2e9`, preservados. C no leyó tar, DB ni preferencias |

**Recuperación acotada.** El dueño respondió «Autorizar recuperación y un intento adicional» a `call_gCvy5XfLG2s1qLUaYCUHz3Pw`. La recuperación de `emulator-5554` usó RAM temporal de 4.096 MB, frente a los 6.144 MB previos, y mantuvo CPU6, los flags `-no-snapshot` y la configuración persistente. El backing `ram.img` cambió entre boots: 6.442.516.480→4.295.032.832 bytes, SHA D12DE041…6424FA→BBA36E59…263607. La igualdad de bytes y metadata se verificó dentro de cada stop, no entre los dos boots; no se prueba el estado del disco completo ni Quick Boot, y no hay copia del backing RAM anterior disponible para restaurarlo. El boot pasó en menos de 45 s; `5556` quedó intacto. El único arranque adicional mostró Welcome sin ANR. Root aparcó el flujo temporal, conservó su backup parcial y restauró ORIGINAL. El QEMU nuevo, PID47564, se cerró con exit 0; la configuración y el backing RAM conservaron bytes y metadata equivalentes únicamente dentro de ese cierre, entre las 04:09:18 y 04:09:27 UTC. Puertos y candado quedaron libres; Gradle/ADB5554 se cedieron a alimentos a las 04:10:10 UTC. Las excepciones de arranque y de prueba quedaron consumidas; no autorizan lanzamientos o tests automáticos adicionales, otra suite Base ni JBR21, y no constituyen una pausa pedida por el dueño.

**Pendientes de aceptación.** Los 28 casos públicos de QA, incluida la observación del consumidor, y la decisión sobre otra suite Base completa. QEMU47564 ya está cerrado; la configuración permanece igual y el backing RAM es equivalente solo antes/después del último stop y alimentos recibió la prioridad de Gradle/ADB5554. Madcow/material y programas activos/custom/legacy mantienen sus reservas específicas; C10 permanece SOFT y la paridad iOS/backend sigue diferida a sus fases. Se conservan el cardio del Atleta, glúteos hasta 17,5 solo en planes propios, PHUL con progresión KPKN/PHAT sin ella y el oráculo Madcow de 174/178,35 kg.

La matriz y los recibos viven en [el índice de curaduría](audits/2026-10-programs/README.md). Room actual es v28; X-full-2 (4.270 tests) es baseline histórico, no corrida actual. La autorización de subagentes Codex (`DEC-EXECUTOR-20261004`) y los oráculos Madcow (`DEC-MADCOW-20261004`) conservan su registro en DEVIATIONS; la copia del plan solo cambia dos líneas. Programas guardados mantienen sourceRecipe, sin migración automática.

## Registro histórico del 2026-09-20

Lo que sigue describe aquella implementación y sus pruebas. Sus rutas, hash, Room v27 y validación de Health son historia; no se usan como estado actual ni como gate pendiente de esta curaduría.

## Fuente y baseline

- Plan aprobado íntegro: `docs/PLAN_WIZARD_BIENVENIDA_APROBADO.md`.
- Checkout actual: `2d0cae9e2517ffce4e686669ba4ddc563cd613ca` (`feat: ship integrated setup wizard and plan catalog`).
- Worktree: `C:\Users\valen\.codex\worktrees\446c\KPKNFit`.
- Room autoritativo en código: v27. El mapa generado y algunos documentos históricos indican v25/v26; esa discrepancia queda registrada y no modifica el esquema.
- Árbol inicial: limpio al comenzar esta tarea.

## Estado por fase

| Fase | Estado | Evidencia / pendiente |
|---|---|---|
| 1. Congelar contratos | Completa | Contratos, persistencia existente y límites Room v27 conservados. |
| 2. Fundamentos | Completa | Gate raíz anterior a Home, carrusel B1–B4, draft reanudable y permisos/deep links retenidos. |
| 3. Volumen | Completa | `VolumeCalibrationEngine`, respuestas obligatorias y perfil global persistido incluso sin programa. |
| 4. Programas | Completa | Rutas personalizable/protocolo/después, prioridades, menor énfasis, split de 7 posiciones, catálogo y `sourceRecipe`. |
| 5. Nutrición | Completa | Elección explícita; al omitirla se ocultan contenido, pestaña y avisos/reminders alimentarios. |
| 6. Rings | Completa | Actividad separada de intensidad, alcance muscular, axial, molestias, reajuste manual y evidencia de origen/campos. |
| 7. Diseño y activos | Completa para el alcance aprobado | P1–P4 generados con `imagegen`, inspeccionados y registrados; el logo y textos permanecen nativos. |
| 8. Integración y QA | Completa para el flujo principal; límites registrados | Tests dirigidos, builds base/Health, instalación conservando datos, flujo real hasta Home y reinicio con blob >2 MB. |

## Decisiones de implementación

- Se reutilizan `SetupWizard*`, `SetupPersistence`, blobs JSON y receipts existentes.
- No se crea un segundo onboarding ni una tabla de Room para campos que caben en JSON.
- La calibración de volumen no depende de crear programa.
- La nutrición se controla con un estado explícito separado de `augeEnableNutritionTracking`.
- Los rings son el último capítulo; su preview llama la misma política que Home.
- No se fabrican cifras, capturas de KPKN o anatomía funcional con generación de imágenes.

## Evidencia acumulada

- Baseline y esquema inspeccionados; `KpknDatabase.kt` declara v27.
- `SetupEntryScreen` es ahora la ruta inicial; una instalación nueva no compone Home antes de terminar el wizard.
- El recorrido real ejecutado cubrió bienvenida → perfil → volumen → personalización → semana/split → nutrición omitida → Rings → revisión → commit → Home.
- El flujo real usó 3 días, 60 minutos, mancuernas + gimnasio, split personalizado, prioridad de glúteos, menor énfasis de pectorales, sensibilidad por músculo y ajuste manual.
- La reinicialización del proceso descubrió y corrigió una regresión real: programas JSON de hasta 2,67 MB ya no se cargan en un único `CursorWindow`; `ProgramDao` reconstruye sus blobs por chunks SQL.
- Evidencia visual principal: `.codex_tmp_qa_20260920/restart-chunked.png`, `review.png`, `volume-filled2.png`, `training.png`, `week-ready.png`, `rings-complete.png` y sus UI dumps.

## Registro de pruebas

| Fecha | Comando / flujo | Resultado |
|---|---|---|
| 2026-09-20 | Tests dirigidos de volumen, validación, Rings, persistencia y catálogo personalizado | `BUILD SUCCESSFUL`; 33 tareas, tests seleccionados ejecutados. |
| 2026-09-20 | `:app:compileBaseDebugKotlin` + `:app:assembleBaseDebug` | `BUILD SUCCESSFUL`; APK en `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`. |
| 2026-09-20 | `:app:compileHealthDebugKotlin` | `BUILD SUCCESSFUL`. |
| 2026-09-20 | Instalación conservando datos en `emulator-5554` | `Success`; `com.example.kpkn/.MainActivity` visible. |
| 2026-09-20 | Integridad del APK instalado | SHA-256 local/instalado: `93b6ae7c3b8d2fcea48d318b8231d07a5c6e33148c71a87704cd22a704050b51`. |
| 2026-09-20 | Flujo end-to-end principal | Home alcanzada; receta y Rings visibles; no se creó historial ficticio; nutrición omitida no aparece en Home ni en la barra inferior. |
| 2026-09-20 | Reinicio de proceso tras instalar el APK | Home recuperada; sin `ProgramRepository`/`AndroidRuntime` error después de cargar blobs grandes. |
| 2026-09-20 | `git diff --check` | Sin errores de whitespace; Git solo reporta avisos de normalización LF/CRLF. |

## Límites de validación

- La matriz completa de rutas alternativas (protocolo avanzado, solo nutrición, recuperación de proceso en cada capítulo y sesión posterior) no se ejecutó en este turno; se validó el flujo principal de personalización con nutrición omitida.
- No se validó en dispositivo físico, iOS ni backend; los cambios de dominio compartido tienen tests Android y la compilación Health, pero no constituyen una prueba de paridad externa.
- No se realizó una captura S1–S5 nueva para incorporarla como recurso; no se fabricaron capturas falsas de KPKN. Las imágenes P1–P4 sí están documentadas en `docs/assets/onboarding-generated/`.
- No se afirma cierre de 360 dp, texto al 200 % ni movimiento reducido; requieren una pasada QA específica posterior.
