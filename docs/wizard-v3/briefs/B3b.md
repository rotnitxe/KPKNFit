# B3b · Músculos: encuadre común por familia, contexto del cuerpo y contracción al elegir (punto 9, diseño)

Worktree indicado en tu encargo, rama `v3/b3b` (sale de `feat/wizard-v3` con B3a integrado: ya son 11 músculos, sin «Trapecio»). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 9» y la fila «Músculos» de la crítica de diseño (§2).

## Contexto (verificado)
- `screens/onboarding/design/entreno/MuscleArt.kt` (153 líneas): cada músculo es una REGIÓN (path SVG) sobre una silueta de cuerpo entero de frente
  o de espalda en un «espacio del cuerpo» (eje x = 0, alto 400), recortada por un encuadre propio (`MuscleWindow(cx, cy, size)`). Datos en
  `MuscleArtPaths.kt` (446 líneas: `*_WINDOW`, `*_REGION`, `*_DETAIL`, `*_CONTEXT`); la cuadrícula y el pintado en `MuscleSymbols.kt` (453).
- Problema que ve el usuario («mejor diseño»): cada músculo tiene un encuadre distinto (unos de muy cerca, otros de lejos), Hombros es casi solo
  contorno y faltan referencias del cuerpo, así que la cuadrícula se lee como formas sueltas.

## Diseño
1. **Encuadre por familia** (mismo `MuscleWindow` para todos los de la familia, elegido para que quepa el más grande con 8 % de aire):
   - Torso de frente: Pecho, Hombros, Abdomen.
   - Torso de espalda: Espalda.
   - Brazo (de frente, brazo derecho de quien mira, ligeramente separado del cuerpo): Bíceps, Tríceps (vista de espalda del mismo brazo), Antebrazos.
   - Pierna de frente: Cuádriceps.
   - Pierna de espalda: Glúteos, Isquios, Pantorrillas.
   Ajusta las regiones si al cambiar el encuadre algo queda cortado.
2. **Silueta de contexto** dentro del encuadre en `WizardColors.textFaint` (trazo `SymbolStroke.FINE`), con dos o tres marcas de referencia de la
   familia (clavícula y ombligo; columna y omóplatos; codo; rótula; pliegue del glúteo) para que se entienda qué parte del cuerpo es.
3. **Estados**:
   - Sin elegir: contorno de la región en tinta al 60 % y relleno al 8 %; fibras (`detail`) al 25 %.
   - Elegido: relleno con el acento `SymbolPalette.musculo` al 85 %, contorno en tinta y **contracción** en bucle (2,4 s, reposo reconocible): la región
     se acorta un 6 % en la dirección de sus fibras alrededor de su origen y se ensancha un 5 % en perpendicular (escala con un `Matrix` sobre el
     `Path` ya creado, sin crear `Path` por cuadro), y sus fibras se juntan igual. La dirección de las fibras va en el spec (`fiberAngleDeg` y punto
     de origen nuevos en `MuscleSpec`).
   - Movimiento reducido: el cuadro final (contraído) estático.
4. Todos con el mismo tamaño en la cuadrícula; la etiqueta como hoy.

## Archivos permitidos
`MuscleArt.kt`, `MuscleArtPaths.kt`, `MuscleSymbols.kt` (solo el pintado y el bucle), y sus pruebas.

## Pruebas
- `MuscleArtTest` (amplía): los 11 tienen encuadre de su familia; cada región cabe en su encuadre con aire; la contracción conserva el área ±12 %.
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/b3b`, con `SymbolRenderSheet`): la cuadrícula de 11 sin elegir, con todos elegidos y la contracción
  de Bíceps, Cuádriceps y Espalda en 4 fotogramas. Revisa a 60 dp que se reconozca cada músculo sin leer la etiqueta.
- Filtro: `*MuscleArt*`, `*MuscleSymbol*`, `*MuscleGrid*`. Antes, `:app:compileDebugKotlin`.
