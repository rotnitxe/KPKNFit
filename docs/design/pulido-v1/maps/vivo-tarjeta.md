# Mapa · VIVO-TARJETA (`lv` · pestaña «En vivo · tarjeta») · puntos 13, 14, 15

Todo en `app/src/main/java/com/example/kpkn/`. Diagnóstico de lo que hay hoy (leído en el código, no supuesto):

## 1. Piezas reales y su diagnóstico
- `screens/workout/components/SetExecutionCard.kt`: **`legacyModeLayout = true` (l. 985) es una constante**: la tarjeta que ves es la rama legacy (`LegacyLoadHero`, `ValueStepperBlock`, `EffortBlock`, `LegacyDetailRow`, `LiveSetCardActions`). Todo lo «no legacy» (incluido `RomBlock`) es código muerto.
- Repeticiones: `Ref02RepButtons` (l. ~4099): 5 botones fijos `current-2..current+2`, Syne 19/24 sp. No hay carrusel: `WorkoutHorizontalWheelPicker` (LazyRow + `rememberSnapFlingBehavior`) solo se usa en la rama muerta.
- Esfuerzo: `LiveEffortRange.kt` (la que se ve: pista 9 dp, pulgar melocotón desde el 80 %, leyenda 3 columnas). El fuego de verdad vive en `EffortFireSlider.kt` + `EffortScale.kt` (`effortFireLevel`, `effortParticleCount`, `effortParticlePeriodSeconds`, `effortWord`) pero tampoco se pinta.
- Roadmap: `LiveSessionLine.kt` (`LiveSessionLineHeight = 112.dp`; ojo: DECISIONES 23 dice 138, el código y tu captura miden 112), `LiveSessionRail.kt` (`LiveRailNode`, `railWindow`, `RailDot`, `RestPill`), `LiveSessionLineSteps.kt`, uso en `WorkoutScreen.kt` l. 1059. Destino del toque en el nombre: `LiveRouteSheet.kt`.
- ROM: `Exercise.trackRom` (`data/models/Session.kt:311`), «Medir ROM» en `ExerciseOptionsBar.kt:125`, «· ROM» en el resumen (`SessionEditorUiUtils.kt:99`), `RomReportSlider` (`SetExecutionCard.kt` ~4003, 10–100 %), `LiveSetDetailSheet.kt` + `SetDetailSheetHost` (l. 2119), modo Lista (`LiveSessionListView.kt:1173/1496`), `CompletedSet.rom` (`WorkoutLog.kt:186`), `SetEntryV2.rom` (`WorkoutV2Models.kt:312`), voz (`WorkoutVoiceCommandHandler.kt:395/1542`) y vídeo (`WorkoutVideoAnalysisController.kt:240`), historial (`WorkoutHistorySheetContent.kt:191`, `LiveExerciseHistorySheet.kt` `historySetValue`), descanso (`RestRef02.kt` `restRef02LastSetText`).

**Por qué el ROM «no está bien conectado»** (4 fallos, todos confirmados):
1. `RomBlock()` solo se llama en la rama no-legacy → con `trackRom` activo la tarjeta NO pinta el deslizador.
2. El interruptor de la hoja (`onRomToggle`) solo pone o quita `romValue` de esa serie; **nunca toca `exercise.trackRom`**. Editor, voz y vídeo leen `trackRom`; la hoja no. Además `romValue` se guarda en `CompletedSet.rom` aunque el ejercicio no mida ROM (voz sí lo anula con `if (trackRom)`).
3. El modo Lista tiene su propio `rom` sin relación con `trackRom`.
4. Nada lo consume: `SetEntryV2.rom` entra a `evaluateSetEntryV3` pero ningún código de `domain/` lee `rom`. Se guarda y se muestra en ningún sitio.

## 2. Inventario escena → pieza de la app
| Escena | Pieza |
|---|---|
| `lv-reps-rango`, `-fijo`, `-amrap`, `-fallo` | `ValueStepperBlock` → sustituye `Ref02RepButtons`; rango = `plannedRepRange`/`RepRange` (`evaluateRepRange`); AMRAP = `amrapMinimumReps`; fallo = `isFailedSet` |
| `lv-esf-vivo`, `-escala`, `-fallo`, `-reducido` | `LiveEffortRange` + canvas de fuego portado de `EffortFireSlider`; `wizardReducedMotion()` |
| `lv-road-a-*` (reposo, registrada, ultima, ultimo, descanso, prep, 10series, 14ej), `lv-road-b-*` (las mismas) | `LiveSessionLine` rehecha (2 bandas; B añade tira). Desaparecen `LiveSessionRail`/`RailDot`/`RestPill`/`railWindow`; se conservan `isUpdateMode`, `recordLabel`, `restActive`, `pendingActions`, `onNavigateAdjacentExercise` |
| `lv-road-ruta`, `-ruta-14` | `LiveRouteSheetContent` reordenada (avance arriba, «Aquí»/«Ir ›», superserie, cardio, Reordenar, Añadir, Resumen); nada se quita |
| `lv-road-medidas` | antes/ahora y medidas en dp |
| `lv-rom-editor` | `ExerciseOptionsBar` («⋯» → «Opciones del ejercicio») y `exerciseSummary` |
| `lv-rom-tarjeta`, `-corporal`, `-tiempo` | hueco de `LegacyDetailRow` → nuevo `RomBand` (reutiliza la lógica de `RomReportSlider`) |
| `lv-rom-hoja`, `-hoja-on` | `LiveSetDetailSheet` |
| `lv-rom-descanso`, `-historial` | `restRef02LastSetText`, píldora hecha del roadmap, `historySetValue` |

## 3. Propuestas y decisiones (con su razón)
**Repeticiones (13).** Carrusel horizontal de cifras Inter (lining + tabulares; Syne queda para la carga): bloque central crema 56×41 con la cifra a 32 sp; vecinos a 64 % y 46 % de tamaño y 62 %/28 % de opacidad; bordes con máscara de alfa; imán al centro (`rememberSnapFlingBehavior`); tocar un vecino lo centra; háptico `TextHandleMove` por cifra cruzada (el mismo del raíl) y rebote de 240 ms al encajar. Rango del plan = riel crema de 3 dp bajo las cifras (las de dentro en crema, las de fuera al 62 %); un plan fijo es una marca corta bajo ese valor; AMRAP = riel desde el mínimo hacia la derecha. Rótulo derecho: «Plan 4–6 ✓», «Plan 5 · −1» o «Mínimo 8» (el de AMRAP ya existe). Por debajo del plan, trazo melocotón bajo el bloque (acento de sesión, como `carouselGlowBelowPlan`). Misma altura que hoy (21 + 4 + 46). Con letra al 130 % las cifras se capan a 1,15× (el bloque no crece).
**Esfuerzo (13).** Mismos niveles del motor (0 hasta RPE 6, 2 = RPE 8 «resplandor», 4 = RPE 10, 5 = Fallo). Nivel ≥ 2: halo suave y 2 brasas; 3: reflejo que viaja por el relleno y degradado crema → melocotón; 4: lengua de luz sobre el pulgar; 5: destello único al llegar (anillo + chispas, 460 ms) y halo que respira. Todo dentro de la banda táctil de 44 dp. Con movimiento reducido: degradado y halo fijos. Se conserva «Sin marcar», la marca del plan, la hoja de escala y la leyenda; la etiqueta del plan se coloca bajo su marca y cede ante «Fallo» (no se descarta). Se añade la palabra de `effortWord` junto al valor.
**Roadmap (14, 2.ª propuesta).** A «Calma» 112 dp: banda 1 de 32 dp («Serie 2 de 3 · nombre» + «4/18» con anillo; toda ella abre la «Ruta»; pulsación larga = opciones del ejercicio) y banda 2 de 48 dp (‹ [Registrar serie] ›); margen 12, hueco 8. B 132 dp (solo alto ≥ 820; si no, A): banda 1 de 30, tira de 14 (un segmento por ejercicio, ancho = series, tocable a 44 dp) y banda 2 de 48. Descanso = fondo melocotón en la banda 1. «Ruta» (hoja común) conserva todo el despliegue de hoy. En B la tira admite pulsar y deslizar (segmento provisional de 18 dp, pulso de 80 ms y vibración de 10 ms por segmento; al soltar salta, fuera de la tira vuelve al actual): sustituye al scrub del riel. En A no hay tira: el recorrido es con ‹ › y con la «Ruta» (decisión consciente: en pantallas bajas se pierde el arrastre). El riel de puntos desaparece; las opciones de serie, a los chips de la «Ruta» y al ⚙ de la tarjeta. Cada escena lleva su ficha de sombras y animaciones (dp, ms, curvas) para implementar sin adivinar.
**ROM (15).** Una sola verdad: `Exercise.trackRom`. Editor: «Medir ROM» pasa a **interruptor «Registrar ROM»** en la misma hoja (no cierra el menú; el nombre coincide con el de la hoja en vivo). Tarjeta: banda de 36 dp en el hueco de «Parciales, ayuda y ROM›» (ROM + valor | deslizador 10–100 % de 5 en 5 con marcas imantadas 50/80/100 y nombre de zona al arrastrar | «Detalle ›» con punto si hay parciales/ayuda). Sin ROM la fila queda como hoy (es el camino para descubrirlo). Hoja: «Registrar ROM · En todas las series de este ejercicio» (enciende `trackRom`, la tarjeta de detrás cambia al instante), deslizador grande, tres botones de zona (Parcial 50 · Casi completo 80 · Completo 100), campo exacto en % y «Guardar también en el programa». Por defecto 100 % visible (nunca oculto). Zonas: < 65 Parcial, 65–94 Casi completo, ≥ 95 Completo.
**Cómo cuenta dentro de la serie.** Se guarda `CompletedSet.rom` (Int 10–100) solo si `trackRom`; `SetEntryV2.rom` → `RecordedSetPayload.romPercent` (nuevo). Lo usan: (a) historial, descanso y resumen: «120 × 4 · ROM 80 %»; (b) comparabilidad: la progresión, las sugerencias de carga y los PR comparan solo series con ROM ≥ 90 % o dentro de ±10 puntos del ROM de referencia del ejercicio (mediana de las últimas 5 con ROM); (c) volumen efectivo de AUGE: las repeticiones con ROM < 100 pesan `rom/100` (como las parciales); (d) con vídeo, `romPercent` sugerido (ya existe) se ofrece como «Usar».

## 4. Cambios de motor o datos
- UI: nuevo `RepsCarousel` (puede nacer de `WorkoutHorizontalWheelPicker`), `LiveEffortRange` con canvas de fuego (portar de `EffortFireSlider`), `LiveSessionLine` y `RailDot` rehechos, `RomBand`; borrar `Ref02RepButtons` y las ramas muertas de `legacyModeLayout` que ya no se usan.
- Conectar: `onRomToggle` → `updateExercise { copy(trackRom = it) }` (con el alcance de las acciones de estructura: solo esta vez / semana / bloque); el modo Lista usa el mismo origen; `romValue` solo se guarda con `trackRom`.
- Room **no cambia** (v30/31 congeladas): `rom` y `trackRom` ya viven en JSON de `Exercise`/`CompletedSet`; `romPercent` en el payload es un campo opcional con valor por defecto.
- Motor: leer `rom` en `evaluateSetEntryV3` (comparabilidad y volumen efectivo) y en el selector de series comparables; pruebas de dominio nuevas.

## 5. Dudas que necesito que decidas
1. **Alcance del ROM al activarlo en vivo**: propuesto «solo esta sesión» con «Guardar también en el programa». ¿O siempre al programa?
2. **¿Excluir de PR y de la progresión las series con ROM < 90 %?** (es lo que hace que «cuente»; si no, solo se anota).
3. Rango del deslizador: 10–100 % como hoy (propongo igualar el campo exacto, hoy 0–100) y zonas 65/95. ¿Te sirven?
4. ¿Entrada directa de repeticiones tocando el bloque central? Hoy solo existe en el Detalle; no la añadí.
5. El destello al llegar a Fallo: ¿también en la hoja de escala completa y en el modo Lista, o solo en la tarjeta?
