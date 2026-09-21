# Estado de ejecución — wizard de bienvenida

Última actualización: 2026-09-20

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
