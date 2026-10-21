Eres el agente que implementa el paquete **VT8a** de la ola VT («técnica por vídeo y conteo automático de repeticiones») de **KPKN Fit**, una app Android en Kotlin con Jetpack Compose.
- Tú escribes el código y haces los commits.
- Otro agente, el orquestador, revisará tu rama y la integrará. No fusiones nada.

**Tu paquete:** el grabador de técnica a pantalla completa.
- Se abre desde la tarjeta de la serie.
- Tiene vista previa 16:9, comprobación de encuadre, 60 fps cuando se pueda, botón REC con cronómetro y límite de duración.
- Guarda los metadatos de captura (gravedad, focal y fps) y arregla la captura actual: lado normalizado, cifras al finalizar y miniaturas por serie.
- Antes de eso, un **paso 0** con dos arreglos pequeños de la capa de datos.

## Dónde trabajas
- **Worktree:** `C:\kw\vt-a`, en la rama `vt/vt8a`, que ya está creada y en checkout. Trabaja SOLO ahí y no cambies de rama.
- **No toques nada fuera de `C:\kw\vt-a`**, salvo escribir logs en `C:\kw\logs\` y capturas en `C:\kw\shots\vt\8a\`. Hay otros worktrees de otras sesiones en marcha: `C:\kw\vt-b`, `C:\kw\u*`, `C:\kw\v3c`, `C:\kw\sb`, `C:\kw\r4i` y `C:\kw\msi`, además de `C:\Users\valen\Documents\KPKNFit`.
- **Los comandos de los briefs son de Git Bash.** Si tu terminal es PowerShell, ejecútalos con `bash -c '…'`.

## Lee primero, enteros y en este orden (rutas dentro de `C:\kw\vt-a`)
1. `CLAUDE.md`: reglas del proyecto. Dice v28, pero la base de datos ya es v29.
2. `docs/video-technique/briefs/VT_COMUN.md`: reglas comunes de la ola, cómo compilar, el emulador, Git y el informe.
3. `docs/screens-v1/briefs/S_COMUN.md` y `docs/design/KPKN_UI_V2.md`: el lenguaje visual (piezas KPKN v2, nada de Material genérico).
4. `docs/video-technique/briefs/VT8a.md`: **TU brief.** Manda sobre todo lo demás: archivos permitidos, paso 0, qué haces, pruebas, corridas y commits.
5. `docs/video-technique/VIDEO_TECHNIQUE_V1.md`: solo las secciones que cita tu brief.
6. El código que cita tu brief, antes de tocarlo.

## Reglas que no se rompen
- **Gradle:** SOLO con la cola compartida, y siempre con la salida a un log:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/vt-a/android-native <tareas> > /c/kw/logs/vt-8a-<n>.log 2>&1`
  - Nunca `./gradlew` ni `gradle` directos: la RAM y las ranuras se comparten con otra sesión.
  - Cada corrida tarda de 5 a 15 min, más la espera en la cola. Si tu herramienta corta los comandos largos, lánzala en segundo plano y vigila el log.
  - Las pruebas filtradas, con `--tests "<filtro>"`, como dice el brief.
- **Emulador:** SOLO con `python C:\kw\tools\emu_run.py`; mira su `--help`. Si no está arriba, `python C:\kw\tools\emu_start.py`.
  - Nunca `adb` suelto, nunca reiniciar el servidor de adb y nunca `pm clear`.
  - No uses ningún teléfono físico.
- **Git:**
  - `git add` solo por rutas explícitas;
  - prohibidos `git add -A`, `stash`, `reset --hard`, `rebase`, `merge`, `push` y cambiar de rama;
  - commits pequeños, en español y con los mensajes del brief.
  - La línea `Co-Authored-By` de VT_COMUN es la de Claude Sonnet: si eres otro agente, pon la tuya o ninguna.
- **Archivos:** solo los de la lista del brief. Si necesitas cambiar otro, NO lo hagas: descríbelo en el informe (qué, dónde y por qué).
- **Nada de descargas ni de librerías nuevas.** La versión de CameraX es la 1.5.3 y no se toca.
- **Disco:** no borres nada fuera de tu worktree. Si el disco C: baja de 8 GB libres, para y dilo.
- **Tope de intentos:** si algo sigue fallando tras 3 intentos, para y descríbelo en el informe con el log. No des vueltas.

## Orden de trabajo
1. **Paso 0 del brief:** los dos arreglos heredados de VT4c, con su commit y su comprobación.
   - Es la primera compilación de esta rama. Si falla por código que no es tuyo, para e informa.
2. **Los pasos 1 a 7 del brief**, en orden, con sus pruebas JVM.
3. **La hoja de render:**
   - PNG en `C:/kw/shots/vt/8a`, a 360 dp, al 100 % y al 130 %;
   - mira cada PNG y critícalo con el listón de VT_COMUN, con un máximo de 3 rondas;
   - si no puedes ver imágenes, dilo en el informe.
4. **Ensamblado y prueba en el emulador:**
   - `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`;
   - después, el modo `recorder_preview` del banco de depuración, con las capturas `idle`, `rec` y `stop`;
   - tras la grabación, comprueba que el vídeo y su `capture.json` existen y qué fps se concedieron.

**Terminado =** todas las corridas del brief en verde, los PNG y las capturas del emulador hechos, los commits en `vt/vt8a` y `git status` limpio.

## Informe final
Termina tu respuesta con el informe de VT_COMUN, en español y en un máximo de 400 palabras, sin pegar código. El usuario se lo pasará al orquestador. Incluye:
1. Los commits, con su hash y su mensaje.
2. Los archivos tocados y las firmas públicas nuevas o cambiadas.
3. Cada corrida: el comando, su log en `C:\kw\logs\vt-8a-<n>.log` y la línea de resultado (`X tests completed, Y failed` o BUILD SUCCESSFUL).
4. Las rutas de los PNG y de las capturas del emulador, y qué nivel de fps se concedió.
5. Los cambios que necesitas fuera de tu lista.
6. Riesgos, dudas y lo que no pudiste verificar.
