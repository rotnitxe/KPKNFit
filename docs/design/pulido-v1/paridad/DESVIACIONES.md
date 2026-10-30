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
- Aprobada por: pendiente (se mide en el Flip5, puerta 4)

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
- Filas: ed-S09, ed-G03, ed-G04, ed-A18
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
- Qué cambia: la tarjeta abierta se despliega con el `AnimatedVisibility` por defecto (fundido + `expandVertically` con resorte), no con `grid-template-rows` en 0,3 s `--ed-ease`. Pendiente de una pasada corta de movimiento (una línea: `expandVertically(tween(280, Estandar))`).
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
