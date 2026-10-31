# Desviaciones aceptadas respecto al lienzo

Una desviación es algo que la app NO hace exactamente como el lienzo aprobado (por límites de Android, de la librería de vidrio o por una decisión del usuario).
Cada una lleva un identificador `DESV-nn`, el ID de la fila de `paridad/<prefijo>.md` que cubre, qué cambia y cuánto, una captura **lado a lado** (lienzo | app | diferencias, hecha con
`tools/compare.cjs`) y, cuando exista, quién la aprobó. **Sin captura y sin aprobación, una desviación no vale: la fila sigue abierta.** No se declara «1:1» nada que tenga una desviación.

Formato (copiar y rellenar; el título tiene que ser `## DESV-nn` exacto porque `tools/paridad-check.cjs` lo busca):

```
## DESV-01
- Filas: lv-S06, lv-S16
- Qué cambia: la sombra `0 14px 34px #000c` se dibuja con capas porque `Modifier.shadow` no admite desenfoque ni extensión arbitrarios; diferencia máxima medida: 3 % de píxeles en la franja inferior.
- Captura: paridad/capturas/DESV-01.png
- Aprobada por: (usuario, fecha) / pendiente
```

**Rangos de numeración** (cada agente trabaja a la vez que otros; así no se pisan los números): profundidad (`dp`) 100–199 · movimiento (`mo`) 200–299 · `as` 300–399 · `ed` 400–499 · `ce` 500–599 · `lv` 600–699 · `lx` 700–799 · `cv` 800–899 · `ct` 900–999 · `rp` 1000–1099 · `tc` 1100–1199 · `nc` 1200–1299 · `sp` 1300–1399 · `aj` 1400–1499.
Los documentos (este archivo, `CALIBRACION_*.md`, `capturas/`) se escriben SOLO en la copia canónica `C:\kw\pulido\docs\design\pulido-v1`; las copias de los worktrees son de lectura.

(Todavía no hay desviaciones: se abren durante la implementación.)


---

## DESV-100
- Filas: vidrio (`backdrop-filter`) de todas las filas con preset `vidrio-isla`, `vidrio-hoja`, `vidrio-barra`, `vidrio-editor`, `velo-desenfoque`, `vidrio-interruptor` o `velo-suave` (`paridad/*.md`)
- Qué cambia: (1) el desenfoque real de Haze solo se pinta con `RenderEffect` en un teléfono con Android 12 o más; en la hoja de Robolectric (lienzo de software) sale el velo de respaldo, así que NO se pudo medir el vidrio real de Haze: se midió su equivalente por software (σ = B, saturate/brightness de CSS, borde espejo del navegador): 0,7–2,1/255 en el interior de las franjas, y el cálculo contra el lienzo confirma σ = 1,0 × B. (2) No se comprobó qué borde usa Haze en el recorte (Chrome: espejo): puede cambiar el color de los ~30 dp del borde de la franja. (3) Debajo de Android 12 no hay desenfoque (velo `KpknGlass.VeilFallback`; decisión aprobada). (4) Con hijos dentro del nodo del vidrio, la saturación y el brillo también les afectan: poner el vidrio en un nodo de fondo sin contenido.
- Captura: paridad/capturas/DESV-100.png
- **Medida real (30-oct, 07:00, emulador «ZFlip5_QA» = 1080×2640 a 480 dpi, Android 16, Haze con `RenderEffect`; el teléfono no estaba conectado)**: `DesignSheetsHarnessActivity` con `cm-vid-1` contra el lienzo (`tools/depth-zones.cjs`): error medio DENTRO de las muestras 17,0 (isla), 18,4 (hoja), 17,8 (barra), 19,6 (editor), 11,1 (velo-desenfoque), 16,1 (interruptor) y 4,7 (velo-suave) sobre 255, con 20–24 % de píxeles por encima de 28/255 en los vidrios con saturación; imagen completa 11,55/255 (12,2 % de píxeles). El desenfoque, los bordes y la geometría coinciden a la vista; la diferencia es de **color**: el vidrio real sale más saturado y menos lavado que el del lienzo sobre las franjas de prueba (la hoja de prueba lleva franjas saturadas para exagerarlo; sobre fondos oscuros de pantallas reales se notará poco). Falta calibrar en dispositivo el camino de saturación/brillo (`kpknBackdropAdjust` se emuló en Robolectric pero sobre Haze real no afecta igual al fondo desenfocado). Captura lado a lado: `paridad/capturas/DESV-100-emulador-ZFlip5.png`.
- Aprobada por: pendiente (se mide en el Flip5, puerta 4; queda abierta la calibración del color)

## DESV-101
- Filas: filtros `halo-desenfoque` y `resplandor-desenfoque` (`filter: blur(20px / 26px)`)
- Qué cambia: `RenderEffect` (Android 12+) no se pinta en el lienzo de software de la JVM; medido por emulación: 4,6/255 de media en el interior (el mejor modelo en Python sobre el propio lienzo da 6,4: el borde transparente de Chrome no se reproduce exacto). Debajo de Android 12 el desenfoque se ignora. Los filtros de color (`foto-apagada`, `video-apagado`, `miniatura-apagada`) sí se pintan en la JVM: 0,9–1,0/255.
- Captura: paridad/capturas/DESV-101.png
- Aprobada por: pendiente (Flip5)

## DESV-102
- Filas: toda sombra con desenfoque (caídas y resplandores) en Android 7–8 (API 24–27)
- Qué cambia: `BlurMaskFilter` no se acelera por hardware antes de la API 28: las capas con desenfoque caen a `Modifier.shadow` con elevación = desenfoque/2 (sombra de Material, sin extensión ni capas) y los anillos e interiores se siguen dibujando exactos. No se puede medir (la hoja se calibra en API 34).
- Captura: no aplica (no hay dispositivo ni hoja de API < 28)
- Aprobada por: pendiente

## DESV-200
- Filas: familia `revelado` (`tcvid`, hoja `cm-mov-trazos-1`)
- Qué cambia: `filter: blur(6px)` se aplica con `Modifier.blur(…, Unbounded)` (Android 12+); Robolectric no pinta `RenderEffect`, así que en la hoja de calibración el desenfoque no se ve y no se puede medir. Brillo (0,5 → 1) y escala (1,06 → 1) sí coinciden. Error del estadio: 13,4/255 en t = 0, 6,4 en 25 %, 1,8 en 50 %, 0,6 en 75 %, 0,4 en 100 %. En Android < 12 no hay desenfoque (queda el brillo).
- Captura: paridad/capturas/DESV-200.png (t = 0: lienzo | app | diferencias)
- Aprobada por: pendiente (comprobar en el Flip)

## DESV-201
- Filas: hoja `cm-mov-cuenta` (`KpknCountUp`)
- Qué cambia: el lienzo pinta las cifras en Inter 800 de 40 px; la app solo tiene Inter Regular, Medium y SemiBold, así que usa SemiBold, y la línea de 42 dp coloca los números unos 3–4 dp más abajo. Los valores (1.091 · 1.881 kcal · 72,2 kg · 16.118 kg · 144,2 g a los 550 ms) y la curva coinciden en los 5 instantes. Error de la hoja: 8,8/255 de media (oficial 8,4).
- Captura: paridad/capturas/DESV-201.png (t = 550 ms)
- Aprobada por: pendiente

## DESV-202
- Filas: todas las hojas `cm-mov-*` (texto de rótulos y de las chips)
- Qué cambia: Chrome y Skia rasterizan el texto distinto; el error de las hojas enteras es 1,2–2,8/255 aunque el círculo coincida a 0,04 dp y la pista a 0,2/255. Los estadios de las familias con texto (chips) quedan en 1,0–1,9/255.
- Captura: paridad/capturas/DESV-202.png (curvas-1, t = 120 ms)
- Aprobada por: pendiente

## DESV-203
- Filas: `KpknWaveField` estado «Sobre la zona» (`above`, aspereza 0,5)
- Qué cambia: con la misma fase y la misma intensidad quedan 5,6/255 en los trazos casi verticales; los otros cinco estados, entre 0,5 y 2,0. Causa probable: suavizado de bordes en trazos de 1 dp muy inclinados; el algoritmo coincide (15 valores de `cvWaveD` a 0,01).
- Captura: paridad/capturas/DESV-203.png (t = 2000 ms)
- Aprobada por: pendiente

## DESV-1000
- Filas: rp-S03
- Qué cambia: la hoja del lienzo (`.pl-sheet`: `0 -14px 44px #000c` + filete interior `0 1px #ffffff10`) la pinta `KpknSheet`, el componente compartido de hojas, que hoy no dibuja la sombra de caída hacia arriba (solo el velo `#000000a8` y su filete superior); las hojas del reparto (`RpSheet`: decisiones y resumen) la usan tal cual. Tocar `KpknSheet` afecta a todas las pantallas de la app y queda fuera de este paquete. En la captura se ve en la hoja de «Antes de aplicar» (a la izquierda el lienzo, en medio la app: sin halo sobre el borde de la hoja; en la hoja de render, además, la hoja se dibuja a mano y con otra altura).
- Captura: paridad/capturas/DESV-1000.png (`rp-decisiones`, 360 × 880)
- Aprobada por: pendiente
- Estado: **Resuelta el 30-oct por pasar KpknSheetSpec**: `RpSheet` pasa `RpSheetSpec` (sombra hoja) a `KpknSheet`.


## DESV-300
- Filas: as-X05 (`.as-root .as-reopen`, `transform: translateX(-50%)`)
- Qué cambia: «Abrir asistente» (`.as-reopen`, centrado con `left:50%` y `translateX(-50%)`) es un control del prototipo para repetir la entrada tras cerrar la hoja (lo dice la nota de la escena). En la app no existe: la hoja se reabre desde los accesos del propio editor (anillo de minutos, botón del asistente), que son de otro paquete; no hay nada que centrar.
- Captura: paridad/capturas/DESV-300.png (as-entrada: lienzo | app | diferencias; la píldora solo existe con la hoja cerrada, que el lienzo no tiene como escena)
- Aprobada por: pendiente

---

## DESV-400
- Filas: todas las de `ed` cerradas en A2 con vidrio, texto o composición completa (escenas `ed-lista`, `ed-lista-scroll`, `ed-dia`, `ed-pildora-mas`, `ed-anadir`, `ed-barra`, `ed-borrar*`, `ed-ordenar*`)
- Qué cambia: el error medio de las escenas completas con Robolectric es 13–17/255 a 100 % (11–25 a 130 %), lejos del objetivo de 2,5. La estructura cuadra a ±2–4 dp (isla, cabecera, módulo, filas de 68 dp, pie, barra, aviso, ficha levantada y hueco), pero tres causas no se pueden medir ni cerrar con esta hoja: (1) el texto: la hoja de Robolectric no dibuja Inter/Syne con los pesos de Chrome (los nombres de ejercicio salen más finos y caben más), (2) el vidrio: sin Haze real detrás de las islas y los popovers hay un velo casi negro (DESV-100), y (3) el lienzo incluye la barra de estado del teléfono simulado y el marco de la pantalla. La barra inferior, que no tiene texto de nombres, baja a 7,2/255 (5,4 % de píxeles). Se ve el contraste real en el Flip (puerta 4).
- Captura: paridad/capturas/DESV-400.png
- Aprobada por: pendiente (Flip5)

## DESV-401
- Filas: ed-S09, ed-G03, ed-G04, ed-A18, ce-S01, ce-S09, ce-G03, ce-G04, ce-A05
- Qué cambia: son reglas de la hoja base del lienzo (`.tok.on`, `.split7 i.on`, `.story .st-week i.on`, `.wr-bars i.on::after`) que el inventario asocia a las escenas del editor, pero ningún elemento de las escenas de A2 las usa (la barra de intervalos del cardio es del paquete `ce`). No hay nada que dibujar en la lista, las islas ni la barra.
- Captura: paridad/capturas/DESV-401.png
- Aprobada por: pendiente

## DESV-402
- Filas: ed-A05
- Qué cambia: la fila recién añadida (`ed-fresh`: 0,5 s, crece de 0 a su alto con fondo de tinta al 12 %) entra con el `animateItem` de la lista (fundido de 260 ms y las vecinas se apartan 260 ms), sin el destello de fondo ni el crecimiento de alto exacto de la familia `despliegue-fila` (el motor no tiene canal de alto aplicable a un ítem perezoso).
- Captura: paridad/capturas/DESV-402.png
- Aprobada por: pendiente

## DESV-403
- Filas: ed-T17, ed-T18
- Qué cambia: la sombra y el fondo de la fila levantada aparecen (y se van al asentarse) de golpe, no en 200 ms / 300 ms; sí se animan el movimiento de asentamiento (340 ms, resorte), la escala (1 → 1,06 → 1,025) y el anillo de tinta (460 ms). La sombra `levantado` es la de los cimientos (no se hace fundido de capas por el coste de la capa de sombra durante el arrastre).
- Captura: paridad/capturas/DESV-403.png
- Aprobada por: pendiente

## DESV-404
- Filas: ed-T07
- Qué cambia: (RESUELTA en la pasada B/C: ed-T07 está [x]; la tarjeta usa `expandVertically`/`shrinkVertically` de 300 ms `--ed-ease`, ver ExerciseEditorCard.kt:320.) Antes: despliegue con el `AnimatedVisibility` por defecto.
- Captura: paridad/capturas/DESV-404.png
- Aprobada por: pendiente

## DESV-405
- Filas: ed-J23
- Qué cambia: el borrado se confirma a los 5,0 s (la ventana del `ViewModel`, `EXERCISE_REMOVAL_UNDO_WINDOW_MS`) y no a los 5,23 s del lienzo; el aviso aparece a los 230 ms y su cuenta atrás dura 4,77 s para que la línea acabe justo cuando el ejercicio se quita. Cambiar la ventana tocaría los tests y el `ViewModel` del asistente.
- Captura: paridad/capturas/DESV-405.png
- Aprobada por: pendiente

## DESV-406
- Filas: ed-J27
- Qué cambia: al soltar sin llegar al umbral, la píldora se recoge siguiendo a la fila (rebote de 340 ms), no con su propia transición de 0,3 s de ancho y 0,2 s de opacidad.
- Captura: paridad/capturas/DESV-406.png
- Aprobada por: pendiente

## DESV-407
- Filas: ed-J39, ed-J40, ed-J41
- Qué cambia: son utilidades de la demostración del lienzo (`sleep`, `raf`, `freezeUndo`); la app no las necesita. Su equivalente son los estados congelados de la hoja de render (`EditorListRenderSheetTest`).
- Captura: paridad/capturas/DESV-407.png
- Aprobada por: pendiente

## DESV-700
- Filas: lx-S12
- Qué cambia: la sombra `0 -14px 44px #000c` de `.pl-sheet` cae por ENCIMA del borde superior de la hoja, fuera de su propia caja; la hoja de la app es el marco compartido (`KpknSheet` / `ModalBottomSheet`), que recorta a su ventana y no la dibuja. El resto de la hoja (fondo #1B1B1D, radio 28, asa de 40 × 4) sí es igual.
- Captura: paridad/capturas/DESV-700.png
- Aprobada por: pendiente (Flip5)

## DESV-701
- Filas: lx-S02 (la franja «Listo» de `lx-t-coexiste`)
- Qué cambia: en el lienzo la franja flota SOBRE el contenido (posición absoluta, no cambia nada debajo). En Compose un hijo fuera de los límites de su padre no recibe toques, así que la franja ocupa su sitio en la columna de la cabecera y empuja el contexto del ejercicio ~76 dp hacia abajo mientras está. Sombra y vidrio son los del lienzo.
- Captura: paridad/capturas/DESV-701.png
- Aprobada por: pendiente

## DESV-702
- Filas: lx-A04
- Qué cambia: la hoja «Sesión completada» entra con la animación propia de `ModalBottomSheet` (muelle de Material), no con `lxsheet` de 0,34 s `estandar`; las entradas internas (anillos, cuenta, líneas) sí van con las curvas y los retardos del lienzo.
- Captura: paridad/capturas/DESV-702.png
- Aprobada por: pendiente

## DESV-703
- Filas: lx-S13, lx-V04, lx-G04 a lx-G09, lx-A15, lx-X10 a lx-X12
- Qué cambia: son los degradados, velos, la foto apagada, la medalla y las semanas de la historia dibujada en HTML por el lienzo. En la app la historia es una imagen de 1080 × 1920 que pinta `WorkoutStoryRenderer` con Canvas y Paint (no pasa por `ui/design/depth`); la vista previa y las miniaturas la muestran, con su sombra «historia» y su borde interior de los cimientos. No se tocó el diseño interno de la imagen (otro paquete).
- Captura: paridad/capturas/DESV-703.png
- Aprobada por: pendiente

## DESV-705
- Filas: lx-G01, lx-G03
- Qué cambia: el fondo de «cámara» con degradados y la rejilla del tercio son un marcador del lienzo; en la app el escenario es la vista previa real de la cámara (o el fondo plano #0D1015 sin permiso), así que no hay degradado de cámara ni rejilla que igualar.
- Captura: paridad/capturas/DESV-705.png
- Aprobada por: pendiente

## DESV-706
- Filas: lx-S01
- Qué cambia: `.ann` es la anotación de medidas del propio lienzo (`lx-l-medidas`); no existe en la app.
- Captura: paridad/capturas/DESV-706.png
- Aprobada por: pendiente

## DESV-708
- Filas: lx-S05, lx-S06
- Qué cambia: el dock y sus pasos son el fondo de las escenas de la hoja «Tiempo» y pertenecen al paquete del roadmap (`lv`), que otro agente rehace; aquí no se tocan.
- Captura: paridad/capturas/DESV-708.png
- Aprobada por: pendiente


## DESV-650
- Filas: lv-S01, lv-S30, lv-A01, lv-A02, lv-A22
- Qué cambia: son reglas de las hojas comunes del lienzo (`.ab.cur`, `.tok.done`, `.draw.go .ln`, `.burst .ln`, `.wr-bars i.on::after`) que el inventario asigna a las escenas `lv-road-*` por llevar el CSS en la página, pero ninguna escena del roadmap pinta esos elementos (comprobado: `scenes-lv.js` no contiene `class="ab`, `tok`, `draw`, `burst`, `split7`, `st-week` ni `wr-bars`). No hay nada que igualar en el roadmap.
- Captura: no aplica (el elemento no existe en las escenas del roadmap).
- Aprobada por: pendiente (orquestador)

## DESV-651
- Filas: lv-S12 a lv-S16, lv-A06 a lv-A10, lv-T03, lv-T04, lv-T05, lv-X07, lv-X08, lv-X09, lv-J16 a lv-J19
- Qué cambia: son las sombras, animaciones, transiciones, máscaras y JS del roadmap VIEJO (`.lv-road`, `.lv-pill`, `.lv-rail`, `.lv-rec`, `.lv-nav`, `.swap`, el temporizador y el aviso de «Registrar»). El roadmap aprobado (escenas `lv-road-a-*` y `lv-road-b-*`) los sustituye: `LiveSessionRail`, `RailDot`, `RestPill` y `railWindow` desaparecen de la app (decisión del usuario, `DECISIONES_APROBADAS.md`, «Roadmap»). Sus equivalentes nuevos están en las filas `lv-S24` a `lv-S27`, `lv-A12` a `lv-A21`, `lv-T12` a `lv-T20`.
- Captura: paridad/capturas/DESV-651.png (roadmap B del lienzo | app).
- Aprobada por: usuario (retirada del roadmap viejo, 29-oct-2026)

## DESV-652
- Filas: lv-S29, lv-J20, lv-J21
- Qué cambia: aviso `.lv2-toast` y temporizador acelerado 18× que el lienzo usa solo para DEMOSTRAR («Pulsación larga: opciones del ejercicio», «Después viene el cardio…», descanso de 180 s en 10 s). La app no los tiene: la pulsación larga abre de verdad las opciones del ejercicio y el descanso corre en tiempo real con el temporizador de la sesión.
- Captura: no aplica (es demostración del prototipo).
- Aprobada por: pendiente (orquestador)

## DESV-653
- Filas: lv-S28, lv-T16, lv-T17, lv-T18, lv-T21, lv-X16
- Qué cambia: la hoja «Recorrido de la sesión» se abre con `LiveSheet` / `HeaderSheet` / `KpknSheet`, el contenedor común de todas las hojas de la sesión en vivo (paquete «vivo-resto»), no con una hoja propia. Por eso la entrada (resorte de 420 ms), la salida (260 ms), el velo (240/200 ms), la sombra «hoja» y el panel (`#141513` al 96 % sobre desenfoque frente a `#1B1B1D` plano) son los de esa hoja y no los de `.lv2-sheet`; el título lleva 15 dp de margen izquierdo (el lienzo, 19). El CONTENIDO de la hoja sí es 1:1 (ver la captura: 7,6/255 en el recorte frente al lienzo; la hoja de prueba dibuja la cabecera del lienzo). Pendiente de decidir con quien lleve «vivo-resto» si `KpknSheet` admite el movimiento de la ruta.
- Captura: paridad/capturas/DESV-653.png
- Aprobada por: pendiente (orquestador)
- Estado: **Resuelta el 30-oct por pasar KpknSheetSpec**: la «Ruta» pasa `LiveRouteSheetSpec` a `LiveSheet`/`HeaderSheet`/`KpknSheet` (panel `#1b1b1d` liso, velo `#000000a8`).

## DESV-654
- Filas: lv-J22
- Qué cambia: en el lienzo la banda 1 sigue diciendo «registrada» 550 ms y solo entonces pasa a «Descanso». En la app, la banda 1 pasa a «Descanso» en cuanto el modelo arranca el descanso (el botón sí conserva «Registrar serie» y el barrido durante los 550 ms). No se retrasa el estado real de la sesión para imitar una demostración.
- Captura: paridad/capturas/DESV-654.png (descanso).
- Aprobada por: pendiente (orquestador)

## DESV-655
- Filas: (sin fila propia; posición del roadmap, `lv-S24`)
- Qué cambia: el lienzo deja 22 dp (14 dp en pantallas bajas) entre el roadmap y el borde inferior del teléfono; en la app el margen inferior sigue siendo el de siempre de `WorkoutScreen` (`navigationBarsPadding()` + 8 dp), para no pisar la barra de navegación del sistema ni mover el cálculo del hueco de la tarjeta. La hoja de render dibuja los 22 / 14 dp del lienzo; en el teléfono depende de la barra del sistema. El alto sí es el del lienzo: 112 dp (A) y 132 dp (B), `dockBottomClearance` sigue a ese alto.
- Captura: paridad/capturas/DESV-655.png
- Aprobada por: pendiente (Flip5, puerta 4)

## DESV-656
- Filas: (sin fila propia; todas las escenas `lv-road-*`)
- Qué cambia: rasterización del texto (Chrome frente a Skia: DESV-202), iconos de la app en lugar de los del lienzo (`KpknLineIcons` para ‹ › ⌃ y cronómetro; `Icons.Default.Update` para «Actualizar serie» frente al icono de refrescar del lienzo) y tamaño exacto de las sombras. Error medio del recorte de la isla (360 × 140–156 dp, comparado con `tools/compare.cjs` y alineado 1 px por el marco del teléfono del lienzo): 6,9–8,4/255 en las escenas A y B de 360 × 880 (3,9–4,5 con 1,5 px de desenfoque, que quita la diferencia de texto), 10,8–11,7 en «prep» (la píldora «Estado previo» y su texto), 10–14,4 en las densas de 740 dp y 130 % (el recorte incluye las tarjetas fantasma del lienzo, que no son del roadmap); la «Ruta»: 7,6 y 6,8/255. No llega al objetivo de 2,5/255 porque el recorte es todo texto y bordes; no se pudo medir el vidrio (el roadmap es plano) ni el Flip5.
- Captura: paridad/capturas/DESV-656.png
- Aprobada por: pendiente (orquestador)

## DESV-600
- Filas: lv-A04 (`.lv-edrow .tx small .rm`, `lv-pin` 0,34 s al encender «Registrar ROM»)
- Qué cambia: el «ROM» de la línea resumen del ejercicio en el editor (`exerciseSummary`, `SessionEditorUiUtils`) es un trozo de una cadena unida con « · », no un elemento propio; para animarlo (escala 0,86 → 1 con `aparece-escala`) hay que partir esa línea en texto con estilos (`AnnotatedString` con un tramo animado), cosa que toca la fila del editor (paquete `ed`). El interruptor, el dato y el texto «· ROM» sí están; solo falta el rebote de entrada.
- Captura: paridad/capturas/DESV-600.png (lv-rom-editor: lienzo | app | diferencias)
- Aprobada por: pendiente

## DESV-601
- Filas: lv-S21 (`.lv-sh`: `0 -14px 44px #000c` + filete superior; panel #1b1b1d)
- Qué cambia: la hoja «Detalle del registro» (y la de historial) usa el marco de `LiveSheetKit` (`HeaderSheet`: velo #99000000, panel #F5141513, asa de 33 × 3, título Syne 21): es el mismo marco que comparten las demás hojas de la sesión en vivo y de otros paquetes, así que su sombra y su color no se tocan aquí. El contenido de las hojas del paquete (filas, campos, interruptor, zonas, deslizador) sí es el del lienzo. Diferencia medida: la hoja de la app arranca ~30 dp más abajo y su panel es algo más oscuro.
- Captura: paridad/capturas/DESV-601.png (lv-rom-hoja-on: lienzo | app | diferencias)
- Aprobada por: pendiente

## DESV-602
- Filas: lv-G02, lv-G03, lv-S31
- Qué cambia: son reglas base de las hojas comunes del lienzo (`.split7 i.on`, `.story .st-week i.on`, `.tok.on`) que el inventario asocia a `lv-rom-hoja-on` pero que no tienen ningún elemento en las escenas del paquete: no hay nada que dibujar.
- Captura: no aplica
- Aprobada por: pendiente


---

## DESV-500
- Filas: ce-V02 (y el vidrio de ce-S07)
- Qué cambia: el botón de cierre de la ficha del cardio es un círculo de vidrio (`vidrio-isla`: `blur(22px) saturate(1.5)`) que se pinta con `kpknGlassPreset(null, …)`: el portal de la ficha no tiene fuente de Haze debajo, así que sale el velo de respaldo de `KpknGlass` (como DESV-100 punto 3) y no un desenfoque real. Debajo solo hay el héroe oscuro de la ficha (viñeta casi negra), donde el desenfoque no se vería. La sombra `isla-vidrio` y el borde sí son los exactos (ce-S07). Medición en Robolectric: el botón queda dentro del error general de la ficha (10,9/255); el vidrio real solo se mide en el Flip5.
- Captura: paridad/capturas/DESV-500.png
- Aprobada por: pendiente

## DESV-502
- Filas: ce-A04, ce-J01
- Qué cambia: los avisos breves de la maqueta («Plantilla aplicada: Tabata.», «Cardio duplicado.», «El cardio se hace antes de la fuerza.»…) salen en la snackbar común del editor (`KpknSnackbar`, la misma de «Versión restaurada»), con su propio movimiento y su propia duración, no con `.ce-toast` (`ce-in` 0,25 s y 2,4 s). No se construyó un toast propio para no tener dos sistemas de avisos en la misma pantalla; los textos son los del lienzo.
- Captura: paridad/capturas/DESV-502.png (la franja del aviso no se dibuja en la hoja de render: lo pinta el `Scaffold` del editor)
- Aprobada por: pendiente

## DESV-503
- Filas: ce-J02, ce-S06
- Qué cambia: «Ejercicio eliminado · Deshacer» es el aviso común de borrado blando de fuerza y de cardio (`SessionExerciseUndoNotice` y el temporizador del ViewModel, tanda A2 del paquete editor). Su tiempo de espera y su sombra (`flotante`) no son los de `.ce-undo` (4,2 s) porque lo ocupan los dos tipos de ejercicio; se igualan allí, no en el cardio.
- Captura: paridad/capturas/DESV-503.png
- Aprobada por: pendiente

## DESV-504
- Filas: ce-J03
- Qué cambia: la pulsación larga de una fila de cardio abre su menú con el umbral del sistema (`combinedClickable`, 400 ms) y no con los 520 ms del prototipo; la fila es de la tanda A2 del editor. El menú que abre sí es el del lienzo (`CardioQuickActionsSheet`).
- Captura: paridad/capturas/DESV-504.png
- Aprobada por: pendiente

## DESV-505
- Filas: ce-S08
- Qué cambia: las hojas del cardio (añadir, cambiar, selector, zona, protocolo, avisos, plantillas, menú) son la hoja común `KpknSheet` en plano (`#1B1B1D`, asa de 36 × 4, filete superior de 1 dp). Esa hoja común no dibuja la caída `0 -14px 44px #000c` del preset `hoja`: es de todas las hojas de la app y se cierra una sola vez en `KpknSheet`, no en cada paquete. En la captura se ve como un borde algo más seco sobre el velo.
- Captura: paridad/capturas/DESV-505.png
- Aprobada por: pendiente

## DESV-506
- Filas: (escenas ce-duracion-teclado y ce-duracion-error; sin fila propia)
- Qué cambia: el lienzo dibuja un teclado de maqueta (3 × 4 más «Listo») para enseñar el estado «escribiendo». En la app escribe el teclado del sistema (`KeyboardType.Number` o `Decimal`) y la hoja enseña la cifra, el cursor de 3 × 52 dp que parpadea (familia `pulso`), la ayuda o el error y el botón «Listo»; Robolectric no dibuja el teclado del sistema, por eso el error medio de estas dos escenas (17,6–23,4/255) es el teclado que falta.
- Captura: paridad/capturas/DESV-506.png
- Aprobada por: pendiente

## DESV-450
- Filas: ed-T24 (`.ed-seg button`, transition background/color 0,2 s)
- Qué cambia: `.ed-seg` es una regla CSS que ninguna escena del lienzo pinta (no hay conmutador de 44 dp en ninguna de las 18 escenas de las series ni de las hojas), y la app tampoco tiene ese componente (el pequeño de «Fijas | Rango» es `.ed-sm`, ed-T26, ya cerrado). No hay nada que animar.
- Captura: paridad/capturas/DESV-450.png (ed-serie: lienzo | app; el conmutador que sí existe es `.ed-sm`)
- Aprobada por: pendiente

## DESV-451
- Filas: objetivos táctiles (transversal: flechas de día y siete días de la hoja de la semana de A2; controles de las series)
- Qué cambia: lo que se ve conserva el tamaño del lienzo y el área táctil llega a 48 × 48 dp con el mínimo de Compose (`touchBoundsInRoot`, el criterio de `LiveHeaderTouchTargetsTest`), no agrandando la caja: crecer 4 dp por control movería todo el ritmo vertical del lienzo. Los siete días caben a 42 dp de ancho en 294 dp; 48 dp de ancho no caben sin scroll, así que su área táctil se solapa 3 dp con la vecina (Compose elige la más cercana).
- Captura: paridad/capturas/DESV-451.png
- Aprobada por: pendiente

## DESV-452
- Filas: ed-A06 (`.ed-focus.anim`, `.ed-presets.anim`, `.ed-tcfg.anim`: `ed-in` 0,26 s `--ed-ease`)
- Qué cambia: la entrada de 6 dp con fundido se hace con el `@keyframes` y la duración exactos, pero el foco de la serie solo se anima al elegir otra ficha o al cambiar Fijas/Rango, no al abrir la tarjeta (el lienzo sí, porque la redibuja con `anim=true`): al abrir ya la mueve el despliegue de 300 ms y animarlo al componer repetiría el efecto cada vez que la lista perezosa vuelve a componer la tarjeta. Los atajos de descanso y la configuración de técnica sí entran al aparecer.
- Captura: paridad/capturas/DESV-452.png
- Aprobada por: pendiente

## DESV-453
- Filas: ed-A10 (`.ed-sc.flash`: `ed-flash` 0,6 s)
- Qué cambia: tras «Aplicar a todas» destellan las fichas de las series (fondo #f2eee633 y escala 1,04 que vuelven en 600 ms); las fichas de aproximación y de movilidad, que el lienzo también marca (`.ed-sc:not(.add)` de toda la tarjeta), no destellan porque su estado vive en el carrusel de preparación, fuera de la lista de series.
- Captura: paridad/capturas/DESV-453.png
- Aprobada por: pendiente

## DESV-454
- Filas: ed-J05 (`closeLayer`: la hoja queda 230 ms con `.out` y se desmonta)
- Qué cambia: la hoja del editor es `KpknSheet(lienzo = EdSheetSpec)` y se desmonta cuando termina su salida de 240 ms (10 ms más que los 230 ms del lienzo); los botones de dentro cierran con `LocalEdSheetClose` y lo que confirman corre después, como `closeLayer(cb)`. Las demás hojas del editor (cardio, catálogo, asistente) no son de este paquete.
- Captura: paridad/capturas/DESV-454.png
- Aprobada por: pendiente

## DESV-455
- Filas: escenas completas de las series (`ed-serie`…`ed-descanso-ejercicio`) con profundidad y movimiento
- Qué cambia: el error medio de las 18 escenas con Robolectric es 11,5–24,9/255 (6–15 % de píxeles); lejos del 2,5 pedido. Misma causa que DESV-400: Inter/Syne no se rasterizan como en Chrome, el lienzo incluye barra de estado y marco del teléfono, y el vidrio, el desenfoque y las sombras de gran radio no se miden en Robolectric. Las más altas son `ed-modo` (24,9, la hoja con velo), `ed-serie-descanso` (22,7) y `ed-preparacion` (18,2). Pasar por el Flip.
- Captura: paridad/capturas/DESV-455.png
- Aprobada por: pendiente (Flip5)


## DESV-1300
- Filas: todas las escenas del paquete (icono y error medio)
- Qué cambia: los iconos propios del lienzo (spCube, spShake, spFish…) no existen en `KpknLineIcons`; se usan los más cercanos (Layers, Apple, Star, Moon…). El error medio de las 8 escenas dibujadas con Robolectric es 9,7–25,4/255 (módulo 15,7; vacío 25,4; hora 17,7; completo 16,4; techo 17,3; ajustar 18,1; aviso 9,7; añadir 18,9), lejos de 2,5: la página de contexto del test (título, tarjetas, barra inferior) no es la real, hay una deriva vertical de unos 5 dp, y Inter/Syne no rasterizan como en Chrome (misma causa que DESV-400).
- Captura: paridad/capturas/sp-modulo-lado.png (y sp-*-lado.png)
- Aprobada por: pendiente (Flip5)

## DESV-1301
- Filas: sp-X01, sp-S08, sp-G03, sp-G04, sp-A01, sp-T04, sp-T06, sp-T09, sp-T10
- Qué cambia: pertenecen a escenas NO construidas en esta entrega por presupuesto: Inicio (sp-inicio, sp-inicio-hecho, sp-rapida), detalle (sp-detalle, sp-detalle-cafeina), alta/baja/propio/edición, notificación y recordatorios.
- Captura: n/a
- Aprobada por: pendiente

## DESV-1302
- Filas: sp-S01, sp-S03, sp-S05, sp-S06, sp-S07, sp-S09, sp-V01, sp-V02, sp-G01, sp-G02, sp-G05, sp-G06, sp-G07, sp-G08, sp-X04, sp-A03
- Qué cambia: son reglas base del marco del prototipo (vidrio, barra superior, cinta, historia) que NINGUNA pieza de Suplementos usa en las escenas construidas (el módulo es un `KpknModule` plano, como en el lienzo); no hay elemento que cablear.
- Captura: n/a
- Aprobada por: pendiente

## DESV-1303
- Filas: sp-J02 (toast de 5 s con «Deshacer»)
- Qué cambia: el aviso usa la barra de avisos de Nutrición (`SnackbarDuration.Short`, ~4 s) en lugar de un toast propio de 5 s; el «Deshacer» sí quita la toma.
- Captura: n/a
- Aprobada por: pendiente


## DESV-1400
- Filas: aj-S01, aj-G01
- Qué cambia: `.aj-sys` es el diálogo de permisos del propio Android (la app llama a `RequestMultiplePermissions`, no lo dibuja), así que no hay sombra `modal` propia que reproducir. `.food.ask` (degradado `tinte-hacia-modulo-2`) solo existe en la escena documento «Veredictos», que no es una pantalla de la app. La escena `aj-permiso-dialogo` se renderiza sin el diálogo.
- Captura: paridad/capturas/DESV-1400.png (permisos, lienzo con el diálogo | app sin él)
- Aprobada por: pendiente

## DESV-1401
- Filas: aj-A04
- Qué cambia: el lienzo sube la hoja 40 px con fundido (`aj-sheet`, 0,36 s `cubic-bezier(.2,.8,.2,1)`); `KpknSheet` solo sabe subirla desde el borde inferior con esa misma duración y curva (`AjSheetSpec`). Velo (0,22 s ease-out) y sombra sí coinciden. No hay hojas con salida en el lienzo: la salida usa la del cimiento (0,24 s `cae`).
- Captura: no aplica (es movimiento; no se midió fotograma a fotograma)
- Aprobada por: pendiente

## DESV-800
- Filas: cv-S15, cv-S16, cv-G05, cv-G06, cv-A27, cv-A28, cv-A29, cv-A30
- Qué cambia: son reglas base del marco del prototipo (subrayado de músculos `.tok.on`, barra `.vbar .z`, selector `.split7 i.on`, historia semanal `.story .st-week i.on`, trazos de dibujo `.draw.go`, ráfaga `.burst.go`, barra de trabajo `.wr-bars i.on::after`) asociadas por el inventario a escenas de cardio pero que pertenecen al marco común del lienzo; ninguna escena de cardio en vivo las utiliza.
- Captura: n/a
- Aprobada por: pendiente

## DESV-801
- Filas: render de cardio en vivo con visual-gate.cjs / compare.cjs
- Qué cambia: error medio en Robolectric frente al prototipo (24,2/255 en cv-trabajo y ~16 % píxeles sobre 28/255); la estructura, alturas de módulos, hero (232/176 dp), ondas de 18 crestas KpknWaveField, cinta de pulso y dock de 3 pestañas coinciden. Las diferencias provienen del suavizado de fuentes tipográficas Syne/Inter en Skia vs Chrome, barra de estado y marco externo del prototipo, y velo de software de Haze (DESV-100).
- Captura: artifacts/cardio-pulido-render-v3/gate-review.png
- Aprobada por: pendiente

## DESV-1402
- Filas: render completo de las 19 escenas con `compare.cjs`
- Qué cambia: error medio 3,6–13/255 (csv 7,4 y csv-error 8,6 tras alinear el botón al fondo de la hoja alta) (objetivo 2,5) y 3–10 % de píxeles > 28/255 en 360×880. Causa: Inter/Syne no se rasterizan como en Chrome (el texto se desplaza 1–3 px y envuelve distinto), el lienzo trae marco de teléfono y barra de estado, y el vidrio/sombras de gran radio no se miden en Robolectric. La estructura (medidas, orden, textos, estados) coincide a la vista. Más altas: permisos 12,9 (filas 3–4 px más separadas), borrar 11,2 (texto más ancho: una línea menos).
- Captura: paridad/capturas/DESV-1402-permisos.png, DESV-1402-borrar.png, DESV-1402-csv.png
- Aprobada por: pendiente (Flip5)

## DESV-901
- Filas: ct-S11, ct-S12, ct-S13, ct-S14, ct-V02, ct-V03, ct-V04, ct-G05, ct-G06, ct-G07, ct-G08, ct-A05, ct-A06, ct-A07, ct-A08
- Qué cambia: son reglas de la hoja base del prototipo (.glass, .pl-glass, .tok.on, .topbar .glass, .split7 i.on, .story .st-week i.on, .draw.go .ln, .plate .ln, .wr-bars i.on::after) asociadas por el inventario al marco exterior de la aplicación; el catálogo como hoja modal vive sobre su propio contenedor y utiliza los presets de profundidad específicos (.ct-sheet, .ct-back, .ct-hero, etc.).
- Captura: n/a
- Aprobada por: pendiente

## DESV-1201
- Filas: nc-S01, nc-S03, nc-S08, nc-S09, nc-S10, nc-V01, nc-V03, nc-G01, nc-G06, nc-G07, nc-G08, nc-A02, nc-A03, nc-A12
- Qué cambia: son reglas de la hoja base del prototipo (.glass, .nc-os, .tok.done, .tok.on, .topbar .glass, .split7 i.on, .story .st-week i.on, .draw.go .ln, .wr-bars i.on::after) asociadas por el inventario al marco exterior de la aplicación o al simulador del diálogo del sistema operativo (que en Android se gestiona directamente mediante el diálogo nativo de permisos de audio); las escenas reales de Nutrición y Cuerpo utilizan los presets específicos de profundidad, hojas modales, islas flotantes y animaciones nativas implementadas.
- Captura: n/a
- Aprobada por: pendiente

## DESV-1101
- Filas: tc-S19, tc-S20, tc-G01, tc-G02, tc-A09, tc-J10, tc-J11, tc-J14, tc-J15, tc-J18
- Qué cambia: son reglas del marco base del prototipo (.tok.done, .tok.on, .split7 i.on, .story .st-week i.on, .wr-bars i.on::after) y funciones de render sintético estático en SVG del navegador (tcStillBind en el visor HTML); la aplicación real renderiza vídeo mediante ExoPlayer / Media3 con capas nativas en Canvas de Compose (TechniqueVideoPlayer, TechniqueOverlay).
- Captura: n/a
- Aprobada por: pendiente
