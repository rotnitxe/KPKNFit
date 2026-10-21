# K3a · Portadas con identidad propia: motor de estilos, 5 estilos y los 30 programas a medida (2026-10-12)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/wizard-v3/briefs/K_COMUN.md` (punto 3), `docs/wizard-v3/briefs/D3b.md` (la ranura de foto) y `docs/programas/PROMPTS_PORTADAS.md`
(la escena de cada programa: úsala como inspiración del motivo). Tareas Gradle `...Base...`.

## De dónde sale
Punto 3 del usuario: las portadas de «Elige tu programa» siguen siendo genéricas. Cada una debe tener identidad propia y verse
hermosa, y no hace falta que todas sigan la misma lógica visual.
- **Por qué hoy son genéricas** (`design/entreno/plan/PlanCover.kt:112-223`, `PlanArt.kt`, `GoalProfileArt*.kt`):
  - el acento y la ilustración dependen SOLO del perfil (`goalAccent`, `goalCoverArt`), y la semilla solo mueve algo de geometría;
  - en un mismo PLAN, todas las tarjetas comparten dibujo y color.
- **Sin fotos:** no hay ninguna (0 de 77 en `assets/program_covers/`).

## Diseño: un motor de portadas por estilos («CoverStudio»)
### Identidad de cada programa (tabla en código, `design/entreno/plan/cover/CoverIdentities.kt`)
Por id de programa: `CoverIdentity(style, palette, primary, secondary, motif, seed)`.
- **`style`:** uno de los estilos del motor. K3a hace 5 y K3b añade otros 5.
- **`palette`:**
  - `NOCHE`: fondo `#0A0A0B` con un velo del acento principal al 8 %;
  - `TINTA`: fondo = el acento principal, a tinta plena;
  - `PAPEL`: fondo `#F2EEE6`.
  - En TINTA y PAPEL, el grafismo va en negro `#0A0A0B` más el otro acento.
- **`primary` / `secondary`:** dos acentos de `SymbolPalette` (músculo, columna, energía, mente, ok) o la tinta.
- **`motif`:** el dato que el estilo necesita (p. ej. el numeral «12», el número de semanas o la pose).
- **`seed`:** estable por id.

### Composición común
- El arte es UN FONDO A SANGRE de toda la portada, no una ilustración en el hueco del medio.
- El texto que ya existe (kicker, insignia, título y datos) se queda donde está.
- **Legibilidad:** el tercio de abajo lleva SIEMPRE un velo negro (degradado de transparente a `#0A0A0B` al 92 %, del 52 % al 100 % del
  alto) para leer el título en tinta. Con fondo TINTA o PAPEL, el kicker y la insignia de arriba van sobre una franja negra al 70 % de 28 dp.
- **Banda ancha** (detalle del plan): el mismo arte, recortado al centro de su motivo. Cada estilo dice dónde está su foco
  (`focus: Offset` en 0-1).
- **Foto propia:** si existe (`rememberProgramCover`), manda como hoy.
- **Movimiento:** con `animate`, cada estilo tiene un movimiento LENTO y sutil (bucle de 8 a 12 s). Sin `animate` o con movimiento reducido,
  cuadro fijo.
- **Paralaje:** el de hoy (`parallax`) desplaza el arte ±3 %.
- **Rendimiento:** sin crear objetos al pintar (`Path` y `Paint` reutilizados, como `SymbolPen`).

### Los 5 estilos de K3a (unidades relativas al rectángulo w × h de la portada; trazos en dp × min(w,h)/300)
1. **NUMERAL** (cartel tipográfico):
   - **Numeral:** el de `motif` («12», «6», «1»), en Syne ExtraBold a 0,78 h. Arranca en x = 0,16 w y se sale por la derecha y por abajo
     (línea base a 0,90 h).
   - **Colores:** relleno con `primary` (NOCHE) o con negro (TINTA y PAPEL); una copia desplazada (+0,02 w, −0,02 h) solo en contorno de
     1,5 dp, con `secondary` al 60 %.
   - **Arriba:** 5 líneas finas horizontales (0,5 dp, tinta o negro al 15 %) en el 40 % superior y, arriba a la derecha bajo la zona del
     kicker, una etiqueta de 10 sp en versalitas espaciadas: «SEMANAS» o «SEMANA».
   - **Movimiento:** la copia de contorno se desliza ±0,01 w.
2. **TOPO** (topografía de una cumbre):
   - **Curvas:** de 14 a 18 curvas de nivel cerradas alrededor de una cumbre en (0,62 w, 0,32 h). Cada curva es un círculo deformado con
     ruido de baja frecuencia (semilla) y radio creciente.
   - **Trazo:** 1 dp en tinta o negro al 22 % en las exteriores; las 3 interiores en `primary`, de 1,6 dp.
   - **Cumbre:** un triángulo relleno de 8 dp.
   - **Ruta:** una línea discontinua en `secondary` que sube desde abajo a la izquierda hasta la cumbre.
   - **Movimiento:** la ruta se dibuja y se borra, avanzando lento.
3. **PLATES** (discos de una barra, de perfil):
   - **Manga:** horizontal a 0,42 h, de −0,1 w a 1,1 w.
   - **Discos:** de 4 a 6 desde el centro hacia fuera, como rectángulos de esquinas redondeadas de alto decreciente (0,62 h; 0,52 h;
     0,44 h; 0,36 h…) y ancho de 0,05 a 0,08 w, rellenos planos alternando `primary`, `secondary` y un tono de tinta.
   - **Detalle:** collarín, una línea de suelo y el reflejo de los discos bajo el suelo al 14 %, sin desenfoque ni brillo.
   - **Movimiento:** la barra baja y sube 0,01 h, como si respirara.
4. **GRID** (calendario de bloques):
   - **Rejilla:** W filas (semanas, del `motif`) × 7 columnas de cuadrados redondeados (celda 0,085 w, hueco 0,018 w), centrada en el 70 %
     superior.
   - **Días encendidos:** los del programa (días por semana del `motif`); el resto, contorno tenue.
   - **Intensidad por semana:** la opacidad de los encendidos sube con la acumulación y la intensificación; la descarga va tenue; la fila del
     pico, en `primary` pleno.
   - **Movimiento:** una fila se enciende de izquierda a derecha y recorre las semanas.
5. **WAVES** (ondas ondulantes):
   - **Ondas:** de 9 a 12 senoidales apiladas a todo el ancho, con la amplitud que crece y luego baja (ondulación) y la fase desplazada
     en cada una.
   - **Trazo:** de 1 a 2,5 dp, con un degradado de color de arriba abajo de `secondary` a `primary`.
   - **Punto:** un punto de 6 dp que navega la cresta de la 5.ª onda.
   - **Movimiento:** las fases avanzan despacio.

### Asignación de los 30 programas a medida (`generated:<perfil>`, `:blocks` y `:periodized`)
- **Regla:** en cada perfil, sus tres variantes van con TRES estilos y TRES paletas distintos.
  - **Primario:** el acento del perfil (`goalAccent`).
  - **Secundario:** otro acento, elegido con gusto (no el mismo para todos).
- **Propuesta** (ajústala si un render lo pide y explícalo):

  | Perfil | Simple (1 sem) | Bloques (6 sem) | Periodizado (12 sem) |
  |---|---|---|---|
  | Fuerza y masa muscular | PLATES · NOCHE | GRID · TINTA | NUMERAL «12» · PAPEL |
  | Fuerza y cardio | WAVES · NOCHE | GRID · PAPEL | TOPO · TINTA |
  | Funcional y saludable | GRID · NOCHE | WAVES · TINTA | TOPO · PAPEL |
  | Powerbuilding | PLATES · TINTA | WAVES · NOCHE | NUMERAL «12» · PAPEL |
  | Calistenia | NUMERAL «1» · NOCHE | GRID · TINTA | TOPO · PAPEL |
  | Culturismo | WAVES · TINTA | GRID · NOCHE | NUMERAL «12» · PAPEL |
  | Halterofilia | WAVES · NOCHE | GRID · PAPEL | TOPO · TINTA |
  | Armwrestling | NUMERAL «1» · TINTA | GRID · NOCHE | TOPO · PAPEL |
  | Strongman | PLATES · NOCHE | WAVES · PAPEL | TOPO · TINTA |
  | Powerlifting | PLATES · PAPEL | WAVES · TINTA | TOPO · NOCHE |

- K3b añadirá HALFTONE, TRACK, TRAIL, ENGRAVING y BAUHAUS, y reasignará algunas filas para más variedad.

### Para Home (la usarán los paquetes I2 e I5 del rediseño de Home)
Expón dos piezas reutilizables, `internal` en el módulo y en `design/entreno/plan/cover/`:
- **`CoverStudioArt(identity, modifier, animate = false, reduced, parallax = { 0f })`:** pinta SOLO el arte a sangre (fondo de la
  paleta y estilo), sin texto, sin velo y sin franja. `PlanCover` lo usa por dentro; Home lo usará de fondo en su sesión del día y en
  sus pósteres.
- **`coverIdentityFor(programId: String, templateId: String?): CoverIdentity`:**
  - Primero busca en la tabla por `templateId` (el id del catálogo o del programa a medida con el que se creó el programa) y luego
    por `programId`.
  - Si no hay entrada (un programa propio o importado), devuelve una identidad DETERMINISTA a partir del hash de `programId`, con
    uno de los estilos que existan, una paleta, dos acentos distintos de `SymbolPalette` y la semilla.
  - El mismo id da siempre la misma identidad.
- **Pruebas unitarias:** la identidad de respaldo es estable para el mismo id y válida (acentos distintos), y un id de la tabla
  devuelve su fila.
- **Programas del catálogo:** K3a no los asigna. Siguen con el arte de hoy hasta K3b, así que el arte actual tiene que seguir funcionando
  para ellos.

## Pruebas
- **Unitarias:**
  - cada id a medida tiene identidad;
  - no hay dos identidades iguales (estilo + paleta + primario) entre los 30;
  - en cada perfil, sus tres estilos y paletas son distintos.
- **Robolectric:**
  - `PlanCover` con identidad pinta sin errores en cartel (3:4) y banda;
  - con foto propia, la foto manda.
- **Siguen en verde:** `PlanCoverTest`, `GoalProfileArtDrawTest`, `ProgramCoverRepositoryTest`, `HeroCoverKindTest`, `EntrenoPlanStepTest`;
  y las hojas `GoalArtRenderSheetTest` y `DisciplineArtRenderSheetTest`, si dependen del arte de hoy.
- **Hoja de render (lo principal):**
  - **Contacto:** una hoja con las 30 portadas a medida en 3:4 y 260 dp de ancho, con su título real.
  - **Detalle:** una por estilo en tamaño grande (300 dp), y las tres de un perfil juntas como se ven en PLAN.
  - PNG en `C:/kw/shots/v3/k3a`.
  - **Crítica:** mira CADA PNG con Read y critícalo como un director de arte: ¿se distingue cada portada?, ¿se lee el título?, ¿es bonita? Hasta 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/v3/k3a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*PlanCover*" --tests "*CoverIdentit*" --tests "*CoverStudio*" --tests "*GoalProfileArt*" --tests "*ProgramCoverRepository*" --tests "*HeroCoverKind*" --tests "*EntrenoPlanStep*" --tests "*CoverRenderSheet*" > /c/kw/logs/v3-k3a-<n>.log 2>&1`

## Commit
- Por rutas: `feat(wizard): portadas con identidad propia, motor de estilos y programas a medida (K3a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El texto de la portada (kicker, insignia, título y datos): solo se añaden el velo y la franja.
- La ranura de foto (`ProgramCoverRepository`) y los assets.
- Las ilustraciones de objetivo (`GoalProfileArt*`): se siguen usando en los símbolos del paso de objetivo.
- `SetupWizardHost.kt`, `SetupReviewStep.kt` (K9) y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, archivo por archivo, con la tabla final de asignación.
- Pruebas: número y resultado.
- Rutas de los PNG y tu crítica por estilo.
- Lo que no se pudo hacer y por qué.
