# B2b · Símbolos de cuatro disciplinas: Powerbuilding, Culturismo, Strongman y Powerlifting (punto 7)

Worktree indicado en tu encargo, rama `v3/b2b`. Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 7».

## Contexto del código
- `screens/onboarding/design/entreno/plan/GoalProfileArtDisciplines.kt` (las disciplinas) y `GoalProfileArt.kt` (`GoalArt`, lienzo 64 × 64,
  grosores ya escalados `fine`/`mid`/`bold`, `dotR`; el mismo dibujo sirve en la lista ≈ 56 dp y en la portada ≈ 200 dp: usa SIEMPRE esos
  grosores). El cuadro de reposo pinta `drawDynamic(restT)`.
- Figuras: de frente, `FrontPose` + `frontFigure` (`PlaceScenes.kt:248-268`); de perfil, `FigGeo`/`FigPose.solve`/`FigPen.figure`
  (`FigureKit.kt`) y las poses de levantamientos `LiftPoses.solve(LiftMark, u, pose)` (`LiftPoses.kt`: lienzo 80 × 76, suelo en y = 70). Para
  usar una pose de `LiftPoses` en 64 × 64: escala s = 0,78 y traslado (0,8; 5,4) (el suelo queda en y ≈ 60). Mira cómo dibuja esas figuras
  `LiftMarksPicker.kt` y reutilízalo. **No modifiques `LiftPoses.kt`** (la banca la corrige otro paquete; tú la llamas tal cual).
- Mantén el acento que cada disciplina ya tiene. Hoja de render: `src/test/java/com/example/kpkn/render/SymbolRenderSheet.kt` (de B1).

## Qué hay que hacer (coordenadas orientativas en unidades del lienzo; ajusta lo necesario para que se lea, sin cambiar la idea)
1. **Powerbuilding = primero dos brazos haciendo doble bíceps, después una figura haciendo peso muerto.**
   - Escena A (primer plano de frente): línea de hombros (20, 44)–(44, 44) `bold`; cuello y cabeza (círculo de radio 4,2 en (32, 32)); brazos
     de los hombros a los codos (9, 37) y (55, 37) y de ahí a los puños (13, 22) y (51, 22), en `bold`; puños como puntos; **bíceps** como
     elipses rellenas de acento sobre cada brazo (radios 4,6 × 3, orientadas como el brazo) que «laten» (escala 1→1,18→1) dos veces.
   - Escena B: peso muerto de perfil con `LiftPoses.solve(DEADLIFT, u, …)` y el disco de la barra.
   - Línea de tiempo (período 4,8 s): A 0–1,8 (latidos 0,3–0,7 y 1,0–1,4) → fundido a B 1,8–2,1 → repetición 2,2–3,4 (subida), arriba
     3,4–3,8, bajada 3,8–4,3 → fundido a A 4,4–4,8. `restT` = 0,5.
2. **Culturismo = figura posando de frente.**
   - Torso en V: hombros (22, 20)–(42, 20), costados de (22, 20) a (27, 36) y de (42, 20) a (37, 36); dos arcos de pectoral y tres trazos cortos
     de abdomen; cabeza de radio 3,6 en (32, 12); piernas de la cadera (28, 37)/(36, 37) a rodillas (25, 48)/(39, 48) y pies (24, 59)/(40, 59)
     con un arco de cuádriceps en cada muslo.
   - Pose 1, doble bíceps: brazos de los hombros a codos (14, 18)/(50, 18) y manos (16, 8)/(48, 8), con los bíceps rellenos de acento.
   - Pose 2, dorsales abiertos: manos en la cintura (27, 34)/(37, 34), codos afuera (17, 27)/(47, 27), y el contorno del torso se abre a
     (19, 26)/(45, 26) bajo las axilas con relleno de acento tenue en los dorsales.
   - Línea de tiempo (período 3,6 s): pose 1 de 0 a 1,2 (los bíceps laten), transición 1,2–1,6, pose 2 de 1,6 a 2,8 (los dorsales se abren un
     poco más), vuelta 2,8–3,2 y reposo. `restT` = 0,6.
3. **Strongman = piedra atlas cargada sobre una plataforma** (de perfil, mirando a la derecha; la roca de 8 vértices actual se sustituye).
   - Plataforma: caja x 42–60, y 42–60 en tinta `mid` con el canto superior en acento `fine`. Piedra: círculo de radio 8,5 en tinta `bold` con
     un arco de brillo en acento (de 200° a 260°). Suelo en y = 60.
   - Figura con `Pose`/`ik` (muslo y espinilla 10, brazo y antebrazo 7). Fotogramas clave (centro de la piedra · cadera · hombro · cabeza ·
     manos):
     - K0 (t = 0): piedra (26; 51,5) · cadera (13, 44) · hombro (24, 36) · cabeza (28, 31) · manos (21, 49) y (30, 47); pies (16, 60) y (19, 60).
     - K1 (t = 1,0, regazo): piedra (23, 44) · cadera (14, 48) · hombro (19, 32) · cabeza (21, 26) · manos (18, 46) y (27, 42).
     - K2 (t = 1,8, al pecho): piedra (27, 33) · cadera (18, 38) · hombro (20, 22) · cabeza (22, 16) · manos (24, 36) y (30, 34).
     - K3 (t = 2,6, sobre la plataforma): piedra (49; 33,5) · cadera (22, 38) · hombro (31, 24) · cabeza (35, 19) · manos (41, 30) y (44, 28).
     - Después la figura se yergue (hasta 3,0), la piedra se queda arriba y de 3,3 a 3,6 vuelve con un fundido a la posición inicial.
   - Período 3,6 s; interpola con `smooth`. `restT` = 2,7 (piedra sobre la plataforma).
4. **Powerlifting = figura con singlet oficial que alterna los tres básicos.**
   - Usa `LiftPoses.solve` para sentadilla, banca y peso muerto (escala y traslado de arriba). En la banca dibuja también el banco como lo
     dibuja `LiftMarksPicker`.
   - **Singlet**: el tronco (hombro → cadera) y el primer 30 % de cada muslo se pintan como una banda gruesa de acento (≈ 2,2 × `bold`) con un
     tirante sobre el hombro; brazos, piernas y cabeza en tinta encima. La barra en tinta con el disco en tinta y anillo interior.
   - **Tres luces de jueces**: tres puntos de radio 2,2 en (22, 6), (32, 6) y (42, 6) que se encienden en blanco (`ink`, con un pequeño salto)
     al terminar cada repetición y se apagan al cambiar de levantamiento.
   - Línea de tiempo (período 6,0 s): sentadilla 0–1,8 → fundido 1,8–2,0 → banca 2,0–3,8 → fundido → peso muerto 4,0–5,8 → fundido de
     vuelta 5,8–6,0. Las luces se encienden hacia 1,4 / 3,4 / 5,4. `restT` = 1,5 (sentadilla arriba con las luces encendidas).

Calistenia, Halterofilia y Armwrestling no se tocan.

## Archivos permitidos
`plan/GoalProfileArtDisciplines.kt` (las cuatro clases), un archivo nuevo `plan/DisciplinePoses.kt` para poses y utilidades propias, y pruebas.

## Pruebas
- Hoja de render en `KPKN_RENDER_DIR=C:/kw/shots/v3/b2b`: cada símbolo en 10 cuadros de su período, a tamaño de lista y de portada. Revisa con
  Read: ¿se reconoce cada disciplina a 56 dp sin leer el nombre? Hasta 3 rondas; en el informe di cuál sigue costando.
- Nueva `DisciplinePosesTest` (JVM): en 40 cuadros del período de cada símbolo los huesos mantienen su largo (± 0,5 u), los pies no se
  hunden bajo el suelo y ningún punto sale de 0..64.
- Filtro: `*GoalProfileArt*`, `*DisciplinePoses*`, `*GoalProfileList*`. Antes, `:app:compileBaseDebugKotlin`.
