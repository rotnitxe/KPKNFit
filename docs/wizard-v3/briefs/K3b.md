# K3b · Portadas: cinco estilos más y la identidad de los programas del catálogo (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`, ya con K3a integrado). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/K_COMUN.md` (punto 3) y `docs/wizard-v3/briefs/K3a.md`: el motor
`CoverStudio`, la composición común, las paletas, `CoverStudioArt` y `coverIdentityFor`. Lee también el CÓDIGO que dejó K3a
en `design/entreno/plan/cover/`: manda sobre este brief si algo no cuadra. Para el motivo de cada programa, mira
`docs/programas/PROMPTS_PORTADAS.md`. Tareas Gradle `...Base...`.

## De dónde sale
- **Lo que pidió el usuario (punto 3):** que cada portada tenga identidad propia y se vea hermosa.
- **Lo que hizo K3a:** el motor, 5 estilos (NUMERAL, TOPO, PLATES, GRID y WAVES) y los 30 programas a medida.
- **Lo que falta:**
  - los programas del catálogo LISTADOS (los de `PlanEditorialTable` con `listed = true`, unos 47) siguen con el arte viejo;
  - con solo 5 estilos, la biblioteca se repite.

## Los 5 estilos nuevos
Mismas unidades que K3a (rectángulo w × h; trazos en dp × min(w,h)/300). Cada uno declara su `focus`, tiene un movimiento LENTO
(bucle de 8 a 12 s) con cuadro fijo sin `animate` o con movimiento reducido, y no crea objetos al pintar.

6. **HALFTONE** (trama de cartel):
   - **Trama:** de puntos a 0,034 w de paso, con el radio que varía según un degradado radial centrado en (0,66 w; 0,36 h). El
     radio máximo es 0,45 × paso en el centro y 0 a 0,62 w de distancia.
   - **Colores:** puntos en `primary` (NOCHE) o en negro (TINTA y PAPEL).
   - **Segundo foco:** un grupo pequeño en `secondary`, abajo a la izquierda (0,18 w; 0,72 h), con radio máximo de 0,25 × paso.
   - **Corte:** una línea diagonal de 2 dp en tinta o negro al 40 % cruza de (0, 0,58 h) a (w, 0,30 h).
   - **Movimiento:** el centro del degradado deriva ±0,02 w.
7. **TRACK** (pista):
   - **Carriles:** 7 carriles de un estadio (rectángulo con extremos semicirculares) visto en perspectiva. La elipse exterior mide
     1,3 w × 0,75 h y está centrada en (0,55 w; 0,42 h); cada carril entra 0,05 w.
   - **Trazo:** líneas de 1 dp en tinta o negro al 22 %. El carril 3 va en `primary`, de 2,2 dp.
   - **Números:** del 1 al 7, pequeños (10 sp), en la recta de abajo y en tinta al 40 %.
   - **Corredor:** un punto de 7 dp en `secondary` que recorre el carril 3.
   - **Movimiento:** el punto avanza.
8. **TRAIL** (ruta de hitos):
   - **Camino:** en zigzag suave de abajo a la izquierda (0,08 w; 0,92 h) a arriba a la derecha (0,82 w; 0,18 h), con 3 o 4 tramos,
     en línea discontinua de 2 dp en tinta o negro al 45 %.
   - **Hitos:** círculos de 9 dp a lo largo del camino, tantos como bloques o fases tenga el programa (`motif`; si no, 4).
     - los ya «alcanzados» en la composición (el primer tercio) van rellenos de `primary`;
     - el resto, en contorno.
   - **Arriba:** un banderín triangular en `secondary`.
   - **Movimiento:** un pulso recorre el camino de hito en hito.
9. **ENGRAVING** (grabado):
   - **Líneas:** horizontales cada 0,018 h a todo el ancho.
   - **Silueta:** el grosor de cada línea se modula para dibujar una forma, sin contorno:
     - 2,4 dp dentro de la forma y 0,6 dp fuera;
     - la transición dura 0,02 w.
   - **Forma:** la elige el `motif`:
     - disco (círculo de 0,62 w en (0,62 w; 0,40 h));
     - piedra (elipse de 0,58 × 0,5 w);
     - pesa rusa (círculo + asa);
     - mancuerna (dos discos + barra).
   - **Colores:** líneas en tinta (NOCHE) o en negro (TINTA y PAPEL). Un círculo pequeño en `secondary` rellena el «brillo» de la
     forma.
   - **Movimiento:** una banda de grosor extra recorre la forma de arriba abajo.
10. **BAUHAUS** (formas planas):
    - **Retícula:** de 3 × 4 celdas.
    - **Formas:** de 4 a 6 primitivas planas, colocadas por la semilla:
      - un círculo grande de 1,2 celdas en `primary`;
      - un cuarto de círculo en `secondary`;
      - una barra de 3 celdas × 0,35 celda en tinta (NOCHE) o en negro;
      - un triángulo y uno o dos puntos.
    - **Sin trazos**, solo rellenos.
    - **Movimiento:** cada 10 s una pieza gira 90° con una curva suave.

## Asignación
### Programas del catálogo (tabla en `CoverIdentities.kt`)
- **Todos los LISTADOS** tienen identidad. Los no listados caen en la de respaldo de `coverIdentityFor`.
- **Criterio por carácter del programa**, no al azar (ajústalo si un render lo pide y explícalo):
  - **Métodos con número firma:** NUMERAL con su número (5/3/1 → «531»; Smolov Jr. → «3», por sus semanas; nSuns → «5»).
  - **Escuelas soviéticas, Sheiko y métodos clásicos:** ENGRAVING (disco).
  - **Westside, conjugado y métodos de autor muy reconocibles:** BAUHAUS.
  - **Por semanas y oleadas** (Candito, Cube, Calgary, TSA): GRID o WAVES.
  - **Culturismo** (PPL, PHAT, PHUL y estilo RP): HALFTONE o WAVES.
  - **Strongman:** ENGRAVING (piedra o pesa rusa) o PLATES.
  - **Calistenia:** TRAIL o TOPO.
  - **Híbridos, cardio y funcional:** TRACK o WAVES.
  - **Principiantes:** TRAIL o GRID.
- **Reglas:**
  - En una misma disciplina, dos programas seguidos de la lista no comparten estilo NI paleta.
  - PAPEL en 30 % o menos de todas las portadas (catálogo + a medida): la marca es negra.
  - Cada uno de los 10 estilos aparece al menos 3 veces.
  - Los acentos van de `SymbolPalette`, con el `primary` del programa = el acento de su perfil (`goalAccent`). El `secondary`, con
    gusto.

### Programas a medida (los 30 de K3a)
Reasigna algunas filas para que los estilos nuevos también estén:
- Calistenia simple: NUMERAL «1» → TRAIL.
- Fuerza y cardio simple: WAVES → TRACK.
- Strongman en bloques: WAVES → ENGRAVING (pesa rusa).
- Culturismo periodizado: NUMERAL «12» → HALFTONE.
- Powerbuilding en bloques: WAVES → BAUHAUS.

Se mantiene la regla de K3a: en cada perfil, sus tres variantes tienen estilos y paletas distintos.

## Pruebas
- **Unitarias:**
  - cada programa listado tiene identidad;
  - en cada disciplina, dos seguidos no comparten estilo ni paleta;
  - PAPEL ≤ 30 %;
  - cada estilo aparece al menos 3 veces;
  - se mantienen las pruebas de K3a.
- **Robolectric:** cada estilo nuevo pinta sin errores en póster y en banda, con `animate` y sin él.
- **Hoja de render (lo principal), en `C:/kw/shots/v3/k3b`:**
  - una hoja de contacto por disciplina (todas sus portadas, en 3:4 a 200 dp, con su título real);
  - una por estilo nuevo, grande (300 dp), en sus tres paletas.
  - **Crítica:** mira CADA PNG con Read y critícalo como un director de arte. ¿Cada portada se distingue de sus vecinas?, ¿se lee el
    título?, ¿es bonita?, ¿es de la marca? Hasta 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/v3/k3b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*PlanCover*" --tests "*CoverIdentit*" --tests "*CoverStudio*" --tests "*CoverRenderSheet*" > /c/kw/logs/v3-k3b-<n>.log 2>&1`

## Commit
- Por rutas: `feat(wizard): cinco estilos de portada más y la identidad de cada programa del catálogo (K3b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El texto de la portada.
- La ranura de foto y los assets.
- `GoalProfileArt*`.
- `SetupWizardHost.kt` y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, archivo por archivo, con la tabla final (programa → estilo, paleta y acentos).
- Pruebas: número y resultado.
- Rutas de los PNG y tu crítica por estilo.
- Lo que no se pudo hacer y por qué.
