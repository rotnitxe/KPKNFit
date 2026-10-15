# LR1a2 · Dos remates de la serie refinada, vistos en el teléfono (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, con LR1a integrado). Lee
`docs/screens-v1/briefs/LR1_COMUN.md` y `docs/screens-v1/briefs/LR1a.md`.

LR1a puso la piel nueva a la serie y al descanso. En la segunda prueba en el teléfono (360 × 880 dp, letra al 100 %) el descanso y casi
toda la serie quedaron bien. Mira con Read:
- `C:\kw\shots\screens\phone-lr1a2\s5_serie.png` (la serie de hoy);
- `C:\kw\shots\screens\phone-lr0\s5_serie.png` (la original).

Quedan dos defectos. Solo tocas `screens/workout/WorkoutHeaderBar.kt` y `screens/workout/components/SetExecutionCard.kt`, y solo
para esto.

## 1. El chip «Sin etiquetas» sale cortado con un borde recto («Sin et»)
- **Causa:** en `WorkoutHeaderBar.kt` (~L748-764) la máscara de alfa va DESPUÉS de `.horizontalScroll(…)`. Así se aplica al contenido
  desplazable entero, cuyo ancho llega fuera de la pantalla, y el fundido cae donde no se ve. El borde visible es un recorte duro.
- **Arreglo:**
  - pon `.graphicsLayer { compositingStrategy = Offscreen }` y el `drawWithContent` con `DstIn` ANTES de `.horizontalScroll(…)`,
    para que se aplique a la ventana visible;
  - además, la fila de chips vive dentro de la columna del título, que estrechan los botones de icono. Esos botones van ARRIBA, no al
    lado de los chips. Saca SOLO la fila de chips a todo el ancho de la cabecera, en el mismo sitio vertical (debajo del subtítulo),
    para que los tres chips quepan enteros a 360 dp. El fundido se queda por si un día no caben.
- **Comprueba:** a 360 dp, «59:46 / 1:00:00», «82 %» y «Sin etiquetas» se ven enteros.

## 2. «Ver ejercicio/Fotos» parte en TRES líneas («Ver / ejercicio/ / Fotos»)
- **Por qué pasa:**
  - la tarjeta queda casi pegada al cajón de abajo;
  - el ✓ flotante, anclado a la pantalla, pisa el final de esa píldora;
  - culpa de los 28 dp de relleno al final que le puso LR1a para esquivar el ✓.
- **Arreglo** en `SetSecondaryPill` y su fila (~L686 y ~L2381):
  - texto a 13 sp con interlineado de 16, relleno vertical de 8 dp y horizontal de 10 dp; alto mínimo de 48 dp;
  - anchos desiguales: «Opciones avanzadas» ~42 % y «Ver ejercicio/Fotos» ~58 % del ancho. Las dos salen en DOS líneas
    («Opciones / avanzadas» y «Ver ejercicio/ / Fotos») y siguen midiendo lo mismo de alto (`IntrinsicSize.Min`). Mismo orden y mismo
    sitio;
  - el relleno final de la píldora de la derecha = lo que el ✓ pisa de ella + 8 dp. Calcúlalo con la posición real del ✓
    (`WorkoutRecordFabPlacement.kt`) y no a ojo: el texto tiene que terminar a la izquierda del círculo.
- **Comprueba**, con números en el informe, a 360 × 880 dp:
  - el texto de la derecha termina antes del borde izquierdo del ✓;
  - el borde inferior de la tarjeta queda al menos 12 dp por encima del cajón plegado.

## Pruebas
- `com.example.kpkn.screens.workout.*` y la hoja `*LiveSessionRefine*` (la de LR1a) siguen en verde.
- Actualiza la hoja de LR1a si su encuadre lo necesita.
- La compilación de `androidTest`.

## Comando
`KPKN_SLOT_PRIORITY=1 KPKN_RENDER_DIR=C:/kw/shots/screens/lr1a2 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "*LiveSessionRefine*" > /c/kw/logs/screens-lr1a2-<n>.log 2>&1`

`.dbg`, PRIMERO, en cuanto compile:
- `KPKN_SLOT_PRIORITY=1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-lr1a2-dbg-<n>.log 2>&1`;
- cópialo a `C:/kw/shots/lr1a2/app-lr1a2-dbg.apk` y avísame enseguida con SendMessage a "main";
- después, la corrida de pruebas.

## Commit
- `style(workout): los chips de la cabecera caben enteros y las dos píldoras de la serie en dos líneas sin pisar el ✓ (LR1a2)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambiaste, con las medidas pedidas.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
