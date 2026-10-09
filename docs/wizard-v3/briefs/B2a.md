# B2a · Símbolos de los tres objetivos generales (punto 6)

Worktree indicado en tu encargo, rama `v3/b2a`. Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 6».

## Contexto del código
- `screens/onboarding/design/entreno/plan/GoalProfileArt.kt`: `GoalArt(accent, period, restT, lk)` (lienzo 64 × 64) con `drawStatic`/`drawDynamic(t)`;
  grosores **ya escalados** `fine`, `mid`, `bold` y `dotR(base)` (el mismo dibujo sirve en la lista ≈ 56 dp y en la portada ≈ 200 dp con
  `COVER_LINE_SCALE`: usa SIEMPRE estos grosores, nunca `SymbolStroke` directo). El cuadro de reposo pinta `drawDynamic(restT)`.
- Primitivas de `SymbolPen` y curvas (`riseHoldFall`, `seg`, `smooth`, `lerpF`) en `EntrenoSymbolKit.kt`; figura de frente
  `FrontPose` + `SymbolPen.frontFigure(p, headR, color, w)` en `PlaceScenes.kt:248-268`. Los tres generales usan el acento `SymbolPalette.ok`.
- Hoja de render: `src/test/java/com/example/kpkn/render/SymbolRenderSheet.kt` (la creó B1; si no está en tu rama, avisa en el informe y
  crea una tira mínima con el mismo patrón).

## Qué hay que hacer (coordenadas en unidades del lienzo; ajusta ±2 u si al verlo hace falta, sin cambiar la idea)
1. **«Fuerza y masa muscular» (`StrengthMuscleArt`) = mancuerna + gráfico que sube con «KG».**
   - Mancuerna a la izquierda centrada en (15, 42): mango de 16 u, un disco por lado (cajas de 3 × 12 con radio) en tinta con detalle de acento.
   - Gráfico a la derecha: línea base (30, 52)–(60, 52) en `soft`; tres barras x 32–37, 41–46 y 50–55 con alturas finales 10, 17 y 26
     (contorno `accent` `mid` y relleno `accent` al 22 %).
   - Flecha ascendente en tinta de (33, 44) a (44, 36) a (56, 22) con punta en (56, 22).
   - Rótulo **«KG»** trazado con líneas (no texto): K con el palo en x = 50 (y 8–16) y dos diagonales a (54, 8) y (54, 16); G como arco de radio 4
     centrado en (59, 12) de 45° a 315° con un trazo corto hacia dentro.
   - Animación (período 2,8 s): las barras crecen de una en una (0,2–0,55 / 0,55–0,9 / 0,9–1,25 s, frenado suave); la flecha se dibuja de
     1,1 a 1,5 s; «KG» aparece con un pequeño salto de escala (0,6→1) de 1,45 a 1,65 s; la mancuerna se inclina hacia arriba (0° → −25°)
     mientras crecen las barras y se mantiene; de 2,5 a 2,8 s todo vuelve suave al inicio. `restT` = 2,0 (todo visible).
2. **«Fuerza y cardio» (`StrengthCardioArt`) = mancuerna + corazón.**
   - Mancuerna a la izquierda en (16, 38) (como la anterior).
   - Corazón a la derecha: trazo `accent` `bold` con relleno `accent` al 18 %; `Path`: `moveTo(44, 42)`, `cubicTo(30, 32, 34, 20, 44, 26)`,
     `cubicTo(54, 20, 58, 32, 44, 42)`.
   - Línea de pulso en tinta `fine` bajo el corazón: (28, 52) (38, 52) (41, 46) (44, 58) (47, 50) (50, 52) (62, 52), con un tramo resaltado en
     acento que la recorre de izquierda a derecha en cada latido.
   - Animación (período 2,0 s): dos latidos por ciclo (escala del corazón 1→1,12→1 en 0,25–0,45 y 0,65–0,85 del período, alrededor de su
     centro); la mancuerna sube 3 u en cada latido. `restT` = 0.
3. **«Funcional y saludable» (`FunctionalArt`) = persona saltando** (salto de tijera de frente, con `frontFigure`):
   - Pose A, de pie: cabeza (32, 13) radio 3,4; hombros (27,5; 20) y (36,5; 20); codos (26, 28) y (38, 28); manos (26,5; 35) y (37,5; 35);
     cadera (32, 36); rodillas (30, 47) y (34, 47); pies (29,5; 58) y (34,5; 58).
   - Pose B, en el aire: todo 3 u más arriba; brazos en V con codos (24, 13) y (40, 13) y manos (21, 6) y (43, 6); piernas abiertas con
     rodillas (27, 45) y (37, 45) y pies (23, 55) y (41, 55).
   - Pose C, aterrizaje: como A con la cadera 2 u más baja y las rodillas algo flexionadas.
   - Animación (período 1,8 s): C (0–0,3) → B (0,3–0,75, frenado suave) → C (0,75–1,2) → A (1,2–1,8), interpolando articulaciones con
     `smooth`. Sombra elíptica tenue bajo los pies que encoge cuando sube. Dos trazos cortos de movimiento en acento junto a las manos en el
     punto alto. `restT` = 0,55 (pose B).

## Archivos permitidos
`plan/GoalProfileArt.kt` (solo las tres clases generales y piezas comunes nuevas que necesites) y pruebas.

## Pruebas
- Hoja de render en `KPKN_RENDER_DIR=C:/kw/shots/v3/b2a`: los tres símbolos en 8 cuadros del período, a tamaño de lista y de portada
  (`goalArt` y `goalCoverArt`). Revisa con Read: ¿se lee a 56 dp? ¿«KG» legible? Hasta 3 rondas.
- Filtro: `*GoalProfileArt*` (incluye `GoalProfileArtDrawTest`) y `*GoalProfileList*`. Antes, `:app:compileBaseDebugKotlin`.
