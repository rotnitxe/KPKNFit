# Matriz de paridad — Refinamiento 02 · sesión en vivo Android

Rehecha desde cero el 2026-10-27, en `master` a partir de `afb6dc752` con WIP sin commitear. Sustituye a la matriz anterior, cuyas referencias de captura estaban desalineadas (p. ej. citaba la 18 como «viewport» cuando es «corregir encuadre»).

## Regla de lectura

- **PASS**: captura nativa del Z Flip que coincide con la referencia en composición, jerarquía, tamaños, tipografía, color y comportamiento. Ni un test verde ni un render de Robolectric bastan.
- **RENDER OK**: el render Robolectric (`GraphicsMode.NATIVE`) de las piezas reales coincide con la referencia; vale como evidencia para 393 × 852 y 412 × 1007 (decisión 7). En 360 sigue haciendo falta la captura del Flip.
- **PARCIAL**: hay implementación nativa, pero no coincide todavía. Se indica qué falta.
- **FALTA**: no hay implementación coincidente ni evidencia nativa.

**Estado del dispositivo (2026-10-27):** el Flip no aparece en `adb` (depuración inalámbrica apagada o emparejamiento perdido). No hay capturas nativas de esta ronda, así que **ninguna fila es PASS**.

Referencias: `C:/Users/valen/Desktop/KPKN-sesion-iteracion-02/capturas-v2/NN-*.png` (01–23) y escenas de `prototipos.html`; para Cardio, Modo Voz y Juegos, la iteración 03 aprobada (`docs/design/live-session-iteration-03/prototipos.html`). La captura 01 está tomada a **393 × 852** (marco de 345 × 470).

## 1. Tarjetas · header + tarjeta + roadmap (área en curso)

Render: `parity/render-tarjetas-3/` (7 escenas: 360×740, 393×852 y 412×1007, al 100 y al 130 %, y Flip 360×880). Test: `LiveTarjetasParityRenderTest`.

| Elemento | Estado | Referencia | Archivo nativo | Nota |
|---|---|---|---|---|
| Cápsula de la sesión | REHECHA (2.ª vuelta) | 01 | `WorkoutHeaderBar.kt` | El usuario señaló que no respetaba el artefacto. Se rehízo con `.phone > .island`: 60 dp, radio 30, relleno 15/8, degradado 135° `#3A3A3AB0 → #202020A8`, borde `#FFFFFF21`, reflejo y sombra; título 13/600 y subtítulo 10 en gris tenue. `.timer-pill`: 40 dp, radio 19, blanco al 3 %, cronómetro de 15 dp y el tiempo transcurrido en tinta, sin objetivo ni anillo. Pendiente de captura del Flip. |
| Contexto del ejercicio | RENDER OK | 01 | `WorkoutHeaderBar.kt` (`WorkoutHeaderContextLine`) | Nombre en Syne 18 debajo de la cápsula; línea «Barra · Alta · Competición ⌄»; micrófono sin relleno (con acento tenue al activarse). Las etiquetas dejan de ser píldoras sueltas. |
| Tarjeta de serie (REPS/AMRAP) | RENDER OK | 01 | `components/SetExecutionCard.kt` (`LegacyLoadHero`, kicker, `LegacyDetailRow`), `LiveModeSetContent.kt`, `EffortFireSlider.kt`, `EffortScale.kt` | «SERIE 3 / 4»; «CARGA» + «Externa ⌄»; cifra grande con «kg» pegado y −/+ en cuadro de 36 dp; «100 × 8 · RIR 2» + historial; «REPETICIONES»; «ESFUERZO · Sin marcar ⌄» (abre la escala completa); leyenda «RIR 10 · RIR 2 · plan · Fallo» en gris; «Parciales, ayuda y ROM»; acciones sin borde tras un filete. Densidad compacta en el marco de 394. |
| Roadmap | RENDER OK | 01, 02 | `LiveSessionLine.kt`, `WorkoutScreen.kt` | Vidrio gris flotante: «SERIES · 2 DE 9 ⌄» (abre la ruta; pulsación larga = opciones), «Sentadilla 3 / 4», pista MOV · APR · SER · REST, ‹ «Registrar serie» ›; durante el descanso, «Terminar descanso». Deshacer flota sobre el roadmap. |
| Marco fijo por viewport | RENDER OK | plan | `LiveSessionCardFrame.kt` | 312×394 / 345×470 / 364×488 medidos; 360×880: 312×394 con el hueco entre tarjeta y roadmap (decisión 1). |
| Escena `strength` en el Flip | FALTA | 01 | — | Pendiente de captura nativa. |

Pendiente dentro de Tarjetas: el resto de modos de la tarjeta (tiempo, distancia, RM, solo RPE, calibrador, unilateral, peso corporal/lastre/asistida, técnicas) usa `LiveModeSetContent` y no se ha auditado contra el prototipo; abajo figuran como PARCIAL.

## 2. Escenas del selector Estado (62)

| Escena | Estado | Ref. | Archivo nativo | Nota |
|---|---|---|---|---|
| strength | RENDER OK | 01 | SetExecutionCard, header, LiveSessionLine | Falta captura del Flip. |
| unilateral | PARCIAL | escena | SetExecutionCard (selector de lado) | El kicker trae «Izquierda/Derecha» como TextButton; falta auditar contra el prototipo. |
| superset | PARCIAL | 06 | SetExecutionCard, LiveSessionListView | Kicker «RONDA 2 · A1» no implementado en Tarjetas. |
| amrap | PARCIAL | escena | SetExecutionCard (layout de reps) | Misma composición que strength; aviso de mínimo por auditar. |
| calibrator | PARCIAL | escena | LiveModeSetContent | Sin auditar. |
| calibration-result | PARCIAL | escena | LiveCalibrationResult | Sin auditar. |
| rm | PARCIAL | escena | LiveModeSetContent (`LiveRmFocus`) | Sin auditar. |
| solo-rpe | PARCIAL | escena | LiveModeSetContent | Sin auditar. |
| time | PARCIAL | escena | LiveModeSetContent (`LiveSetClockHero`) | Sin auditar. |
| distance | PARCIAL | escena | LiveModeSetContent | Sin auditar. |
| custom | PARCIAL | escena | LiveModeSetContent | Sin auditar. |
| bodyweight | PARCIAL | escena | LiveModeSetContent | Sin auditar. |
| assisted | PARCIAL | escena | LiveModeSetContent | Sin auditar. |
| partials | PARCIAL | escena | LegacyDetailRow (popup) | El prototipo usa una superficie externa; el nativo, un popup. |
| rom | PARCIAL | escena | RomReportSlider | Sin auditar. |
| drops / restpause / myo | PARCIAL | escena | SetCardTechniqueBack, GuidedTechniquePanel | Cara «Avanzado» sin auditar. |
| guided | PARCIAL | escena | GuidedTechniqueFlow | Sin auditar. |
| failed | PARCIAL | escena | SetExecutionCard (`isFailedSet`) | Sin auditar. |
| completed | PARCIAL | escena | SetExecutionCard (`isUpdateMode`) | «Actualizar serie» en el roadmap: hecho; tarjeta sin auditar. |
| empty | PARCIAL | escena | LegacyLoadHero | «Sin historial anterior» y «—»: hecho; falta captura. |
| mobility / mobility-move / mobility-global | PARCIAL | 07 | WarmupMobilityLiveCards, LiveSessionListView | Lista compacta existe; sin verificar en dispositivo. |
| warmup / warmup-rest | PARCIAL | 08 | WarmupMobilityLiveCards, LiveSessionListView | Igual. |
| cardio / intervals | FALTA | iteración 03 | CardioLiveCard (actual) | Sustituido por el espacio de cardio aprobado; el código v2 está en `C:/kw/u5` (decisión 15c). |
| rest | PARCIAL | 03 | RestExperience, WorkoutRestOverlay | Sin auditar en esta ronda. |
| overrun | PARCIAL | escena | RestOverrunLayer | Sin auditar. |
| feedback | PARCIAL | 04 | RestFeedbackCard | Sin auditar. |
| visual / visual-expanded | PARCIAL | escena | SetCardExerciseMediaBack, ExerciseAlbumSheet | Sin auditar. |
| analysis-working / analysis-low | PARCIAL | escena | TechniqueAnalysisScreen | Sin auditar. |
| readiness / finish | — (manda lo nativo) | escena | WorkoutReadinessSheet, RecordedSessionFinishSheet | Decisión 4: solo verificar que `b3a057e13` sigue intacto. |
| rest-card | PARCIAL | 02 | RestLiveCard | El roadmap ya ofrece «Terminar descanso»; la tarjeta conserva su propio botón de saltar (se revisa en el área de Descanso). |
| rest-suggested | PARCIAL | escena | RestLiveCard | Sin auditar. |
| rest-feedback / rest-feedback-full | PARCIAL | 04 | RestFeedbackCard | Sin auditar. |
| games / game-runner / game-flappy / game-shooter | FALTA | iteración 03 | RestArcade, RestGameEngine | Se implementa la propuesta propia aprobada (iteración 03). |
| tags / tag-setup | RENDER OK (hoja) | 09 | WorkoutExerciseSheets (`WorkoutTagListOverlay`, `TagCreateForm`), HeaderSheet | Hoja «Etiquetas del ejercicio» con filas de radio y referencia, más «Nueva etiqueta» con los campos del prototipo. El gestor de la etiqueta usa el marco nuevo. |
| set-config | IMPLEMENTADO | 10 | `components/LiveSetConfigSheet.kt` | Pestañas Objetivo / Carga / Detalle, «Aplicar a» y aviso de cambios sin guardar. El editor completo del ejercicio queda en «Todas las series». |
| replacement / replacement-preview | PARCIAL | 11 | WorkoutReplacePicker, WorkoutReplacementRules | Sin auditar. |
| alert-effort | PARCIAL | escena | EffortFireSlider (`hint`) | Aviso junto al campo: existe; falta captura. |
| alert-discomfort / alert-save / alert-conflict | IMPLEMENTADO | 12 | `components/LiveAlert.kt` | `.v2-alert` en todas las alertas de la sesión (molestia, sesión en curso, actualizar serie, quitar, cambio de grupo, cambios sin guardar, técnica, cardio). |
| video-detail | PARCIAL | 13, 14, 19 | TechniqueAnalysisScreen | Sin auditar. |
| voice-picker | PARCIAL | 15 | VoiceCaptureModeDialog | Sin auditar. |
| voice-conversation / voice-review | FALTA | iteración 03 (voz) | VoiceConversationSurface | Se rehace como cápsula sobre la sesión (decisión 14). |
| framing / framing-warning | PARCIAL | 17, 18, 20 | FramingCheckController, TechniqueRecorderStage, FramingGuideOverlay | Sin auditar en esta ronda. |
| relator-context | PARCIAL | escena | WorkoutLiveRelatorAssist | Franja retirada (D3); asistencia contextual sin auditar. |

## 2b. Hojas y menús de la sesión (decisión 17)

| Hoja | Estado | Archivo |
|---|---|---|
| Tu sesión (menú), Vista, Cronómetro, Notas, Hitos, Calorías | RENDER OK | `LiveSessionSheets.kt` |
| Recorrido de la sesión | IMPLEMENTADO | `components/LiveRouteSheet.kt` |
| Detalle del registro (parciales, ayuda, ROM, nota) | RENDER OK | `components/LiveSetDetailSheet.kt` |
| Modo de carga + cargas rápidas | RENDER OK | `components/LiveModeSetContent.kt` |
| Esfuerzo realizado | RENDER OK | `EffortFireSlider.kt` |
| Opciones del ejercicio / de la serie | RENDER OK | `ExerciseOptionsSheet.kt`, `SeriesTypeSheet.kt` |
| Historial del ejercicio | RENDER OK | `LiveExerciseHistorySheet.kt` |
| Tiempo de sesión | RENDER OK | `SessionTimeSheet.kt` |
| Configurar serie, AMRAP, nota del ejercicio, superserie, acortar, salir, alcance de cambios | IMPLEMENTADO | `LiveSetConfigSheet.kt`, `SetExecutionCard.kt`, `WorkoutStructureSheetsHost.kt`, `WorkoutV2Body.kt`, `WorkoutSessionOverlaysHost.kt` |
| Cajones y hojas de cabecera restantes | MARCO NUEVO | `WorkoutDrawerHost.kt`, `HeaderSheet.kt` (asa, Syne, panel, velo, pie) |
| Orden de la sesión | MARCO NUEVO | `LiveReorderSheet.kt` |
| Catálogo (añadir, reemplazar) | SIN CAMBIO | Selector de catálogo a pantalla completa, fuera del alcance del prototipo de sesión. |

## 3. Iteración 03 (aprobada)

| Área | Estados | Estado | Nota |
|---|---|---|---|
| Cardio · transiciones y espacio propio | 16 | FALTA | Implementación pendiente; antes, decidir qué se porta de `C:/kw/u5`. |
| Modo Voz como capa | 10 | FALTA | Implementación pendiente. |
| Juegos (propuesta propia) | 6 | FALTA | Implementación pendiente; «Seguir jugando» como tiempo extra sigue abierto. |

## 4. Accesibilidad y viewports

| Área | Estado | Evidencia |
|---|---|---|
| 393 × 852 y 412 × 1007 | RENDER OK (Tarjetas) | `render-tarjetas-3/` |
| Texto al 130 % | RENDER OK (Tarjetas) | `render-tarjetas-3/*-130.png`: sin solapes y con el marco intacto. |
| TalkBack, áreas táctiles y movimiento reducido en el Flip | FALTA | Pendiente del teléfono (decisión 8: por adb, restaurando los ajustes). |

## 5. Orden de implementación y riesgos

1. **Tarjetas** (en curso): cerrar la captura del Flip y auditar los demás modos de la tarjeta.
2. **Lista** (filas, acordeón, superserie, preparación) contra 06–08.
3. **Descanso, feedback y juegos** (02–05 y la iteración 03).
4. **Etiquetas, configuración, reemplazo y avisos** (09–12).
5. **Analizador** (13, 14, 19).
6. **Encuadre y voz** (15–18, 20 y la capa de voz de la iteración 03).
7. **Cardio** (iteración 03), tras decidir qué se porta de `C:/kw/u5`.
8. **Readiness y cierre** (solo verificación) y accesibilidad en el dispositivo.

Riesgos:
- Sin el Flip no se puede declarar ningún PASS.
- La cola de Gradle es lenta: cada test con Robolectric tarda de 4 a 8 min.
- Cardio depende de un trabajo grande sin integrar.
- Hay WIP ajeno en el árbol: los commits por área se preparan solo con rutas propias.
