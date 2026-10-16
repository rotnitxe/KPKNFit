# LV · Común de la implementación de los prototipos (2026-10-16)

El usuario aprobó TODOS los prototipos de `docs/screens-v1/proposals/sesion-en-vivo/` y pidió: «Implementa todos los artefactos a
la APP, DE FORMA FIABLE Y RESPETANDO LA LÓGICA de la app». Este documento se suma a `LV_COMUN.md` (entorno, Gradle, git, informe):
léelos los dos.

## Cómo se usan los prototipos
- Cada `.dc.html` es HTML y CSS legible. Lo que está dentro de `<x-dc>` es la pantalla (390 × 844 px de referencia; 1 px del
  prototipo ≈ 1 dp, y la pantalla real es 360 dp: ajusta proporcionalmente los anchos, nunca la letra). Lo que está en
  `<script type="text/x-dc">` es la lógica de la demo (estados, qué cambia al tocar). `{{x}}` son valores que calcula esa lógica.
- `README.md` de esa carpeta: qué es cada prototipo, las leyes y la lista de opciones inventadas que NO se añaden.
- **Fieles al prototipo en el ASPECTO** (jerarquía, medidas, colores, tipografía, animaciones, textos de la interfaz) y **fieles a
  la app en el CONTENIDO** (sus opciones, sus datos, su lógica). Si el prototipo muestra una opción o un dato que la app no tiene,
  NO se inventa: se usa lo de la app y se anota en el informe. Si la app tiene una opción que el prototipo no muestra, se conserva.
- Los números de ejemplo del prototipo (95 kg, 89 %, «Pierna A»…) son ejemplos: en la app salen del estado real.

## Las leyes del usuario (mandan sobre todo)
1. **La tarjeta de la serie NUNCA cambia de tamaño** (anverso, «Avanzado», «Modo visual», teclado, drops). «Modo visual» cabe en
   la tarjeta y tiene «Expandir»: con una animación, pasa a casi toda la pantalla ENCIMA de todo y al plegar vuelve.
2. **La intensidad (RIR/RPE) empieza en el MÍNIMO** de su escala; el objetivo del plan es una marca tenue sobre la pista; los
   valores de la escala son los de la app.
3. **Las acciones del relator son parte de la frase** (sin caja, brillo sobre toda la frase, animaciones al pulsar).
4. **El resumen final nunca deja hueco** entre el contenido y los FAB: la hoja tiene una altura y cada sección la llena.
5. **No inventar opciones; no quitar ninguna** («Refinar, no quitar»).

## Decisiones del 16-oct
- **RINGS luminosos = diseño DEFINITIVO de los anillos en toda la app** (ver «Anillos luminosos» abajo).
- **RIR/RPE OBLIGATORIO** para registrar una serie (antes era opcional). Desaparece «Qué tan intenso fue» del descanso.
- **Calidad técnica** con slider interactivo en el descanso de la última serie.
- **Molestias**: buscador amable, chips destacados (reportes anteriores y las más probables), ficha por molestia y un MOTOR que
  detecta posibles causas en la sesión y en la semana y aconseja, sin diagnosticar (si persiste, «consulta a un profesional»).
- **Descanso**: sin círculo; con los RINGS (Torre, Pista, Espiral), un personaje sobre la forma y un minijuego. **La TARJETA de
  descanso recibe exactamente las mismas mejoras** (el usuario lo recalcó el 16-oct).
- **Ajuste manual de anillos**: G5 (Energía y Columna) y G6 (Músculos) son el ajuste OFICIAL en toda la app.
- **La ficha del ejercicio es UNA** para toda la app y completa (G4/G4b).

## Anillos luminosos (pieza común)
Referencia: `G1-EstadoPrevio.dc.html` e `I1-HomeRings.dc.html`.
- Torre de tres elipses: abajo Columna (`#8FB2FF`), en medio Músculos (`#F49A6E`), arriba Energía (`#F7CF73`). Proporciones del
  prototipo (viewBox 300 × 230): abajo cx 150, cy 178, rx 138, ry 40, trazo 18; medio cy 112, rx 104, ry 30, trazo 16; arriba
  cy 54, rx 70, ry 21, trazo 14.
- Cada anillo: pista `#151515`; arco de progreso con degradado horizontal (Columna `#5E7EC4 → #B9CFFF`, Músculos
  `#C7704A → #FFC2A3`, Energía `#C9A04A → #FFE7A8`), extremos redondeados; un halo del color del anillo al 22 % con desenfoque
  (≈ 5 dp) detrás del arco; y un destello: un trazo blanco al 55 % corto (≈ 40/900 del perímetro) que recorre el anillo cada 6 s
  con desfase de 1,2 s entre anillos.
- El arco empieza en el extremo derecho de la elipse y avanza en sentido horario, sin costuras ni cortes rectos: el halo y el arco
  comparten inicio y fin.
- Con «reducir movimiento», sin destello. Ningún bucle fuera de pantalla.

## Movimiento
- Animaciones lentas y prácticas. Con «reducir movimiento», el estado final estático.
- Ningún bucle corre fuera de pantalla, con la app en segundo plano ni con la pantalla apagada.
- Los personajes son de trazo fino (1,8–2,6 dp), tinta `#F2EEE6` y acentos de `SymbolPalette`; se dibujan en `Canvas`, sin
  librerías nuevas.

## Pruebas y verificación
- Cada paquete añade escenas a una hoja de render (`KPKN_RENDER_DIR`) al 100 % y al 130 % a 360 dp, y MIRA sus PNG antes de cada
  corrida nueva.
- Cada cambio de lógica lleva su prueba (Robolectric o pura) que FALLA sin el cambio.
- Nada de pruebas instrumentadas, teléfono ni emulador: los usa el orquestador al integrar.
