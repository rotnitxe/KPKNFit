Eres el agente que implementa el paquete **VT9a** de la ola VT («técnica por vídeo y conteo automático de repeticiones») de **KPKN Fit**, una app Android en Kotlin con Jetpack Compose.
- Tú escribes el código y haces los commits.
- Otro agente, el orquestador, revisará tu rama y la integrará. No fusiones nada.

**Tu paquete:** dos pantallas nuevas.
- **Tendencias por ejercicio:** velocidad por carga con 1RM orientativo, pérdida de velocidad, ROM y profundidad, TUT y la lista de vídeos.
- **Comparar dos vídeos:** dos reproductores con un deslizador compartido, trayectorias superpuestas y una tabla de diferencias.
- Antes, un **paso 0** que verifica la base integrada.

## Dónde trabajas
- **Worktree:** `C:\kw\vt-a`, rama `vt/vt9a`, ya creada y en checkout desde `feat/video-technique`. Trabaja SOLO ahí y no cambies de rama.
- **Fuera de `C:\kw\vt-a` solo** escribes logs en `C:\kw\logs\` y capturas en `C:\kw\shots\vt\9a\`.
  - Hay otros worktrees de otras sesiones en marcha: no los toques. Son `C:\kw\vt-b`, `C:\kw\u*`, `C:\kw\v3c`, `C:\kw\sb`, `C:\kw\r4i`, `C:\kw\msi` y `C:\Users\valen\Documents\KPKNFit`.
- **Los comandos de los briefs son de Git Bash.** Si tu terminal es PowerShell, ejecútalos con `bash -c '…'`.

## Lee primero, enteros y en este orden (rutas dentro de `C:\kw\vt-a`)
1. `CLAUDE.md`: las reglas del proyecto. Dice v28, pero la base de datos ya es v29.
2. `docs/video-technique/briefs/VT_COMUN.md`: reglas comunes, cómo compilar, Git, la sección «Pantallas» y el informe.
3. `docs/screens-v1/briefs/S_COMUN.md` y `docs/design/KPKN_UI_V2.md`: el lenguaje visual (piezas KPKN v2, nada de Material genérico).
4. `docs/android/SKILL_COMPOSE_CANVAS.md`: las gráficas con Canvas.
5. `docs/video-technique/briefs/VT9a.md`, **tu brief**. Manda sobre todo lo demás. Lee con calma su sección «Heredado»: lleva los nombres reales del repositorio, del motor y del reproductor.
6. `docs/video-technique/VIDEO_TECHNIQUE_V1.md`: solo las secciones que cita tu brief.
7. El código que vas a usar, antes de tocarlo:
   - `data/repository/VideoAnalysisRepository.kt` y `data/models/VideoAnalysisSummary.kt`;
   - `domain/biomechanics/vbt/LoadVelocityProfile.kt` y `domain/biomechanics/profiles/AnalysisProfileResolver.kt`;
   - `screens/technique/player/` (VT7a) y `screens/technique/analysis/` (VT7b), sobre todo cómo su ViewModel lee los sidecars;
   - `navigation/Navigation.kt` y el NavHost de `MainActivity.kt`;
   - las piezas de `ui/design/`;
   - `src/test/.../screens/technique/TechniqueSamples.kt`.

## Reglas que no se rompen
- **Gradle:** SOLO por la cola compartida, en segundo plano y con la salida a un log:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/vt-a/android-native <tareas> > /c/kw/logs/vt-9a-<n>.log 2>&1`
  - Nunca `./gradlew` ni `gradle` directos: la RAM y las ranuras se comparten con otra sesión.
  - Cada corrida tarda de 5 a 15 min, más la cola. Si tu herramienta corta los comandos largos, lánzala en segundo plano y vigila el log.
  - Gradle no imprime el total de pruebas cuando todo pasa: cuéntalo en los XML de `android-native/app/build/test-results/testBaseDebugUnitTest/`.
- **Sin emulador en este paquete:** las pantallas aún no tienen entrada (las cablea VT9b). La comprobación visual es la hoja de render.
- **Git:**
  - `git add` solo por rutas explícitas;
  - prohibidos `git add -A`, `stash`, `reset --hard`, `rebase`, `merge`, `push` y cambiar de rama;
  - commits en español, con los mensajes del brief.
  - La línea `Co-Authored-By` de VT_COMUN es la de Claude Sonnet: si eres otro agente, pon la tuya o ninguna.
- **Archivos:** solo los de la lista del brief. Si necesitas cambiar otro, NO lo hagas: descríbelo en el informe (qué, dónde y por qué).
- **ViewModels:** `StateFlow` de solo lectura (`asStateFlow()`). La lectura de sidecars y de Room va en `Dispatchers.IO`.
- **Nada de descargas ni de librerías nuevas.**
- **Disco:** no borres nada fuera de tu worktree. Si el disco C: baja de 8 GB libres, para y dilo.
- **Tope de intentos:** si algo sigue fallando tras 3 intentos, para y descríbelo en el informe con el log. No des vueltas.

## Orden de trabajo
1. **Paso 0, ANTES de editar nada.** Verifica la base, que junta por primera vez la pantalla de análisis (VT7b) y el grabador (VT8a):
   `:app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.technique.*" --tests "com.example.kpkn.screens.workout.technique.*" --tests "com.example.kpkn.data.media.*" --tests "com.example.kpkn.data.work.*"`
   - Mientras espera en la cola, lee y planifica.
   - Si falla algo que no es tuyo, NO lo arregles: anota el error exacto en el informe y sigue si tu parte compila.
2. **Tendencias:**
   - pantalla, ViewModel y estado;
   - las cuatro gráficas en Canvas, cada una con su descripción para TalkBack;
   - la lista de vídeos con «Comparar», que selecciona 2.
3. **Comparar:**
   - pantalla y ViewModel;
   - dos reproductores con `onTap = null` y el deslizador compartido;
   - `OverlaidPathsChart` y la tabla de diferencias.
4. **Las dos rutas** en `Navigation.kt` y su registro en el NavHost de `MainActivity.kt`, solo con bloques nuevos.
5. **Pruebas JVM** de los ViewModels (Robolectric con BD en memoria):
   - los puntos de carga-velocidad salen de las filas;
   - la llamada a `estimate` con la MVT del perfil resuelto;
   - cada código `NotEnough` con su texto;
   - una pérdida de velocidad `null` no entra en la gráfica.
6. **Hoja de render:**
   - PNG en `C:/kw/shots/vt/9a`, a 360 dp, al 100 % y al 130 %;
   - tendencias con datos y con «faltan datos», comparar con dos trazas y cada gráfica suelta;
   - mira cada PNG y critícalo con el listón de VT_COMUN («¿se entiende de un vistazo?», «¿cada cifra con su unidad?», «¿«≈» donde toca?», «¿se corta algo al 130 %?», «¿parece Material genérico?»);
   - como mucho 3 rondas;
   - si no puedes ver imágenes, dilo en el informe.

**Corridas del brief:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.technique.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/vt/9a` con `--tests "com.example.kpkn.render.TechniqueTrendsRenderSheetTest"`.

La 2 y la 3 pueden ir juntas.

**Terminado =** el paso 0 y las corridas en verde, los PNG revisados, los commits en `vt/vt9a` y `git status` limpio.

## Informe final
Termina tu respuesta con el informe, en español y en un máximo de 400 palabras, sin pegar código. El usuario se lo pasará al orquestador. Incluye:
1. El resultado del paso 0: log y línea de resultado, más cualquier fallo ajeno con su error.
2. Los commits, con su hash y su mensaje.
3. Los archivos tocados y las firmas públicas nuevas: rutas, `create(...)` y composables de entrada.
4. Cada corrida: el comando, su log en `C:\kw\logs\vt-9a-<n>.log` y la línea de resultado, con el número de pruebas sacado de los XML.
5. Las rutas de los PNG y lo que cambiaste en cada ronda de crítica.
6. Los cambios que necesitas fuera de tu lista.
7. Riesgos, dudas y lo que no pudiste verificar.
