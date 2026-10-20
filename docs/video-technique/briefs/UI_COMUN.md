# UI · Común de la ronda de interfaz de «Técnica en vídeo» (20-oct)

Plan y decisiones: `docs/video-technique/UI_PLAN.md`. Cada paquete UI-n tiene su brief en esta carpeta. Lee también, enteros:
- `docs/screens-v1/briefs/LV_COMUN.md` y `docs/screens-v1/briefs/LV_COMUN2.md`: las leyes de la sesión en vivo, el entorno y git.
  Mandan sobre todo lo demás;
- `docs/screens-v1/proposals/sesion-en-vivo/README.md`, con `A3-ModoVisual.dc.html` y `G3-ModoVisualExpandido.dc.html`: el aspecto
  aprobado del Modo visual.

## Entorno
- **Worktree:** `C:\kw\vt-a`, en la rama del brief, que ya está creada y en checkout. Trabaja SOLO ahí y no cambies de rama.
- **Gradle:** SOLO por la cola compartida y con la salida a un log:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/vt-a/android-native <tareas> > /c/kw/logs/vt-<paquete>-<n>.log 2>&1`
  - Nunca `./gradlew` directo, nunca `--stop` y nunca matar procesos java.
  - Cada corrida tarda de 5 a 20 min más la cola: espera a que termine leyendo el log.
  - Cuando todo pasa, Gradle no imprime el total de pruebas: cuéntalo en
    `C:\kw\vt-a\android-native\app\build\test-results\testBaseDebugUnitTest\*.xml`.
- **Prohibido:**
  - el teléfono, adb y el emulador (los prueba el orquestador);
  - push, `stash`, `reset --hard`, `rebase`, `merge` y `git add -A`;
  - otros worktrees y el checkout principal `C:\Users\valen\Documents\KPKNFit`;
  - descargas y librerías nuevas.
- **Commits:** por rutas (`git add <ruta>`), en español y con los mensajes del brief, terminados con
  `Co-Authored-By: Antigravity (Gemini 3.8 Flash) <noreply@google.com>`.

## Reglas de esta ronda
- **Ley 1:** la tarjeta de la serie NUNCA cambia de tamaño: ni el anverso, ni «Avanzado», ni «Modo visual». Lo que no quepa va en un
  menú anclado o en la vista expandida, encima de todo.
- **Ley 3:** la acción va dentro de la frase. Usa `LinkAnnotation.Clickable` con `SpanStyle(fontWeight = FontWeight.SemiBold)`, al
  mismo tamaño y en la misma línea, como `WorkoutLiveRelatorBar`. Su objetivo táctil mide al menos 48 dp de alto.
- **Piezas y colores:**
  - solo las piezas de `ui/design/` (`KpknModule`, `KpknTextAction`, `KpknIconButton`, `KpknType`, `KpknSurface`, `KpknShape`…);
  - los colores de `WizardColors` y `SymbolPalette`;
  - nada de Material genérico (`TextButton`, `Card` o `Button` de Material3).
- **Honestidad:**
  - toda estimación lleva «≈»;
  - la confianza BAJA es «no concluyente», nunca un número como si fuera seguro;
  - nada se aplica solo: siempre hay una acción explícita.
- **Textos:**
  - español neutro, sin voseo («Puedes», no «Podés»);
  - sin signos de exclamación;
  - coma decimal y espacio inseparable antes de «%», «kg» y «s»;
  - «…» de un solo carácter.
- **Accesibilidad:**
  - todo lo tocable mide ≥ 48 dp y lleva `Role` y descripción;
  - el estado del análisis se lee entero en TalkBack;
  - con «reducir movimiento» (`wizardReducedMotion()`), sin animaciones.
- **Hojas de render:**
  - las existentes, con escenas nuevas: `render/TechniqueRecorderRenderSheetTest` y la hoja del Modo visual de LV8 (búscala con
    `grep -rl MediaFaceTag src/test`);
  - 360 dp, al 100 % y al 130 %, con `onlyWhenAsked()`;
  - PNG en `C:/kw/shots/vt/ui/<paquete>`;
  - MIRA cada PNG antes de la corrida siguiente;
  - como mucho 3 rondas de crítica.
- **Lógica:** cada cambio de lógica lleva una prueba JVM (pura o Robolectric) que FALLA sin el cambio.
- **Archivos:** solo los de la lista del brief. Si hace falta otro, NO lo toques: descríbelo en el informe.
- **Tope:** si algo sigue fallando tras 3 intentos, para y descríbelo con el log.

## Informe final (en español, ≤ 400 palabras, sin pegar código)
1. Los commits, con su hash y su mensaje.
2. Por punto del brief: qué cambió y en qué archivo.
3. Cada corrida: el comando, el log y el resultado, con el número de pruebas.
4. Las rutas de los PNG y lo que cambiaste en cada ronda de crítica.
5. Lo que no pudiste hacer y por qué, y los cambios que harían falta fuera de tu lista.
