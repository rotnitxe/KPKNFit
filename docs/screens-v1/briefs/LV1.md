# LV1 · Cabecera del ejercicio y hoja de estado previo (puntos 1, 12, 12.1, 12.2 y 13)

Rama y worktree: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `f6da01f52`). Lee antes `LV_COMUN.md`.

## 1. El nombre del ejercicio, en carrusel
- **Lo que pasa:** en la cabecera (`screens/workout/WorkoutHeaderBar.kt`), el nombre se corta por los botones de historial y de voz.
- **Qué haces:**
  - Si el nombre no cabe, se desplaza solo en una línea, como un carrusel lento: `Modifier.basicMarquee` con unos 30 dp/s,
    1,5 s quieto antes de empezar y 2,5 s quieto entre vueltas.
  - Un fundido de 16 dp en el borde derecho, y en el izquierdo mientras se mueve, para que no corte en seco.
  - Si cabe, quieto. Con «reducir movimiento», quieto con «…».
  - Los botones no cambian de sitio ni de tamaño.

## 13. La portada del programa, de fondo en la cabecera
- **Dónde:** detrás de la zona del título, los botones de historial y voz, y los chips de tiempo y etiquetas.
- **Qué dibujas:** la portada del programa activo, la misma fuente que usa el héroe de Program Detail (búscala desde
  `screens/programdetail/components/HeroCoverKind.kt` y su héroe: foto, portada empaquetada o arte procedural `PlanCover`).
- **Cómo se ve:**
  - tenue, con una opacidad de 0,16–0,22;
  - un degradado vertical hacia el fondo de la página, que termina antes del relator;
  - la tarjeta de la serie y el relator quedan sobre negro, como hoy.
- **Contraste:** el texto debe seguir con al menos 4,5:1. Si con la portada no llega, oscurece más.
- **Sin portada:** como hoy.
- **Datos:** si la cabecera no recibe la portada, pásala desde el estado que ya tiene el programa. Toca lo mínimo en `WorkoutScreen` o
  `WorkoutV2Body` (solo el parámetro).

## 12. La hoja «¿Cómo llegas a entrenar hoy?», más compacta
En `screens/workout/components/WorkoutReadinessSheet.kt`:
- **12.** Fuera el texto de arriba «Reporta tu estado antes de entrenar» (~L222). La pregunta se entiende sola.
- **12.1.** Fuera las tres líneas que explican cada anillo («Músculos: Normal · Volumen normal y buena ejecución.», «Energía: …» y
  «Columna: …»), porque ya se explicaron en el alta. La ayuda «Si no te representan los rings, presiona cada uno para ajustarlo» pasa
  a una línea corta: «Toca un anillo para ajustarlo».
- Lo demás (los anillos, sus cifras, el resto de la hoja y el ✓) no cambia.
- **12.2. Anillos con más vida:** hoy se ven planos.
  - **Componente:** el de la torre de RINGS que usa esta hoja; búscalo desde la hoja. Añádele un estilo opcional (por ejemplo
    `TowerRingsStyle.Luminous`). Lo usan esta hoja y, más adelante, el resumen final; el resto de pantallas sigue como está.
  - **La pista:** un poco más oscura, con profundidad, como una sombra interior suave.
  - **El arco:** un degradado a lo largo, más claro en la cabeza.
  - **El brillo:** un resplandor muy suave detrás del arco (alfa ≤ 0,25), sin bordes brillantes.
  - **El destello:** recorre cada arco al aparecer y después cada ~6 s, desfasado por anillo (lento y sutil).
  - **Con «reducir movimiento»:** sin destello.
  - Las cifras y los colores de marca no cambian: Músculos #F49A6E, Energía #F7CF73 y Columna #8FB2FF.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*`.
- **Nuevas:**
  - el nombre largo usa el carrusel y el corto no;
  - con «reducir movimiento», no hay carrusel;
  - la hoja de estado previo ya no tiene el texto de arriba ni las tres explicaciones;
  - sin portada no se dibuja fondo.
- **Hoja de render**, al 100 % y al 130 %:
  - la cabecera con un nombre largo (dos fotogramas del carrusel) y con portada;
  - la hoja de estado previo en dos fotogramas del destello.

## Permitido y prohibido
- **Permitido:**
  - `WorkoutHeaderBar.kt`;
  - `components/WorkoutReadinessSheet.kt`;
  - el componente de la torre de RINGS (solo añadir el estilo opcional);
  - el paso del parámetro de portada;
  - archivos nuevos y pruebas.
- **Prohibido** (otros paquetes trabajan a la vez):
  - el relator (`WorkoutLiveRelator*`);
  - la tarjeta de la serie (`SetExecutionCard.kt`, `WorkoutSetPager.kt`);
  - la barra inferior y el cockpit;
  - las hojas de estructura;
  - el resumen final;
  - `WorkoutViewModel` (salvo leer).

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "*LiveSessionRefineRenderSheet*" --tests "*LiveSessionPrepRenderSheet*" > /c/kw/logs/lv1-<n>.log 2>&1`

Tope: 4 corridas.

## Commits
- `feat(workout): el nombre del ejercicio en carrusel lento y la portada del programa de fondo en la cabecera (LV1)`.
- `style(workout): la hoja de estado previo sin textos sobrantes y con anillos con profundidad y destello (LV1)`.
