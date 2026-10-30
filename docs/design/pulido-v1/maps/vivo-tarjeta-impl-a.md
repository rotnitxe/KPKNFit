# Informe de implementación · TARJETA EN VIVO (`lv`) · fases 1 y 2 (31-oct-2026)

Worktree `C:\kw\pu-lv`, rama `pulido/lv` (sin commit; árbol = `feat/pulido-v1` 74566268e + cambios de este paquete).

## Hecho
- Carrusel de repeticiones (`RepsCarousel`): un lienzo de 46 dp, cifras Inter lining+tabulares, bloque crema 56×41, vecinos 64 %/48 %, imán, háptico por cifra, riel de rango/fijo/AMRAP, rótulo «Plan 4–6 ✓ / Plan 5 · −1 / Mínimo 8 / Serie fallida»; `Ref02RepButtons` y `WorkoutHorizontalWheelPicker` borrados.
- Esfuerzo (`LiveEffortRange` + `LiveEffortFx`): valor con su palabra, plan bajo su marca, relleno degradado con resplandor, pulgar que se calienta, brasas, reflejo, lengua, destello de llegada y halo; estático con movimiento reducido o con `effortFireSecondsOverride`.
- ROM: `RomBand` (36 dp), `RomSlider`, `RomSwitch`, hoja `LiveSetDetailSheet` (interruptor «Registrar ROM» → `Exercise.trackRom`, campo 10–100, zonas, «Guardar también en el programa»), editor («Registrar ROM» interruptor), modo Lista con el mismo origen, `CompletedSet.rom` solo con `trackRom` (`LiveRom.recordable`), `RecordedSetPayload.romPercent`, ROM en historial, descanso y referencia anterior.
- Movimiento y profundidad con los cimientos (`kpknShadow/kpknRing/kpknInset/drawKpknShadow`, `kpknAnimate`, `animateKpknFloat/ColorAsState`, gradiente `relleno-horizontal-crema-2`).
- Tarjeta: compacta solo por debajo de 404 dp o 350 dp de ancho (el Flip usa la tarjeta amplia, como el lienzo), márgenes de 15 dp, sin aire propio sobre el carrusel.

## Decisiones
1. Las cifras del carrusel se dibujan en un lienzo y se exponen como nodos de accesibilidad (texto + tocar) para no componer 51 `Text`.
2. Con ROM apagado la fila conserva el resumen «+N parciales · N con ayuda» (dato real); el lienzo solo muestra el texto fijo.
3. «Guardar también en el programa» escribe `trackRom` en la sesión de la semana en curso (`persistExerciseTrackRom`); `persistToProgram` del flujo en vivo era una bandera sin efecto.
4. Imán del ROM: las marcas 50/80/100 son múltiplos de 5, así que el redondeo a 5 ya las encuentra (igual que el lienzo).
5. «Escala de calor» (`lv-esf-escala`) es un catálogo del lienzo: en la app es el mismo slider; se renderiza con 6 instancias, sin comparación numérica.
6. Historial: el ROM va en la línea de la sesión solo si < 95 %, y siempre en las series desplegadas.

## Verificación (cola)
JVM: `LiveCardLogicTest` + `EffortHeatTest`, 22 pruebas en verde. Android (última corrida, 211 pruebas): 2 fallos que NO son de este paquete: `LiveSessionRefineRenderSheetTest.serieCheckGeometria360` (base de la tarjeta 645 dp > 640; ya fallaba antes del merge del roadmap) y `SetExecutionCardCompactTest.pasosV1ConservanNavegacionYObjetivosTactiles` (`LiveSessionLine`: botón de 48 dp frente a 44 esperados: roadmap).
Comparación `compare.cjs` (tarjeta 312×430 recortada, % píxeles / error medio): reps-rango 8,3/10,6 · reps-fijo 7,9/9,5 · reps-amrap 9,2/11,6 · reps-fallo 9,2/12,0 · esf-vivo 8,3/10,6 · esf-fallo 9,3/12,1 · esf-reducido 9,1/11,9 · rom-tarjeta 8,7/11,1 · rom-corporal 7,5/9,2 · rom-tiempo 6,7/8,0 · rom-descanso 8,1/14,1. Hojas (recorte inferior): hoja 9,9/13,4 · editor 12,0/16,4 · historial 18,2/20,6 · hoja-on 20,1/22,4 (marco `LiveSheetKit`: DESV-601). Lo que queda en las tarjetas es rasterizado de texto (Chrome 1× frente a Skia 3×) y 1–2 dp de aire; no hay fotogramas de movimiento renderizados (puerta 3 pendiente).

## Filas
Cerradas `[x]`: S02–S06, S08, S09, S17–S20, S22, S23, G01, A03, A05, A11, T01, T02, T07–T11, X01–X06, X10–X13, J01–J15. DESV: A04 (600), S21 (601), G02/G03/S31 (602). Sin cerrar y no mías: S07 (chip del editor), S10 (tarjetas fantasma), S11 (isla).

## Riesgos
Gestos del carrusel dentro del paginador y fuego en el Flip sin probar en dispositivo; `blurRect` cae a trazo nítido antes de la API 28 (DESV-102); `tightCard` ahora distingue 404 dp: revisar las caras Avanzado/Modo visual a 430 dp.
