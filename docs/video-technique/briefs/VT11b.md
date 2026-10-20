# VT11b · Conductor del banco completo para el Z Flip5 (pose por GPU, dispositivo real)

Decisión del usuario (20-oct, «Ambos»):
- el banco completo corre en el emulador, que mide la pose por CPU;
- Y en el Z Flip5 del usuario, con su GPU, cuando él lo deje enchufado, por depuración inalámbrica y 1–2 h sin usarlo;
- **el examen final se decide con las cifras del Flip.**

## Dónde
- **Fuera del repo:** `C:\kw\vt-bench\full-20261020-flip\`. No hay worktree ni Gradle.
- **Lee antes:**
  - `C:\kw\vt-bench\full-20261020\README.md`, `run_full_bench.py` y sus pruebas (`self-tests\`, `validate_tools.py`): el conductor
    del emulador, que es tu base;
  - `C:\kw\tools\phone_run.py`, ENTERO, solo para leerlo: las reglas del teléfono del usuario. Mandan sobre todo lo demás;
  - el modo `analyze_file` de `android-native/app/src/debug/.../TechniqueBenchActivity.kt` (y `AnalyzeFileBench.kt`), en
    `C:\Users\valen\Documents\KPKNFit\.claude\worktrees\video-technique-rep-counter-ee5b0d`.

## Qué haces
1. **`run_full_bench_phone.py`**, derivado de `run_full_bench.py`. Importa `phone_run.py` como módulo de solo lectura, igual que el
   conductor del emulador importa `emu_run.py`, y usa SUS ayudas:
   - `find_serial`;
   - el candado del teléfono: `acquire`, `release` y `touch_lock`;
   - `free_to_use`, `focus_is_ours`, `awake`, `device_closed` y `media_playing`;
   - su `adb`.
   Nunca llama a adb por su cuenta ni cambia ninguna regla de `phone_run.py`.
2. **Reglas del teléfono, en cada lote:**
   - **Antes:** `free_to_use()` debe dar luz verde (regla de 20 min, sin audio ni vídeo, sin plegar). Si no, espera 5 min y vuelve a
     mirar. Nunca fuerza.
   - **Durante:** si la persona lo coge (pantalla encendida y el foco no es nuestro), corta el lote enseguida, sin enviar Inicio ni
     apagar la pantalla. Espera a que vuelva a estar libre y repite ese lote desde el principio.
   - **Batería** (`dumpsys battery`): con menos del 30 % y sin cargar, pausa hasta que cargue o pase del 40 %. Anota el nivel en cada
     lote.
   - **Temperatura** (`dumpsys thermalservice`): anota el estado en cada lote. Con un estado SEVERE (3) o peor, pausa 10 min.
   - **Al acabar** cada sesión: deja la pantalla como la encontró, como hace `phone_run.py`.
3. **Límites duros:**
   - solo el paquete `com.example.kpkn.dbg`;
   - solo rutas bajo `/sdcard/Android/data/com.example.kpkn.dbg/files/`;
   - NUNCA `pm clear`, `uninstall`, ajustes del sistema, nada de `com.example.kpkn` (la app con los datos reales del usuario) ni
     reiniciar el servidor de adb.
4. **APK:** se exige el instalado con el SHA-256 de `C:\kw\vt-bench\full-20261020\m1-apk.sha256`, comprobado con `pm path` y
   `sha256sum`, como en el emulador.
   - El APK lleva arm64-v8a.
   - Si no está instalado, el conductor lo instala con la ruta de instalación de `phone_run.py` (que comprueba el applicationId con
     aapt2) y vuelve a comprobar el SHA.
5. **¿Funciona con la pantalla bloqueada?** Mira si `TechniqueBenchActivity` pide `setShowWhenLocked(true)` y `setTurnScreenOn(true)`,
   y si el análisis sigue cuando la actividad pasa a segundo plano: `lifecycleScope` o `repeatOnLifecycle`, servicio o hilo.
   - Si NO funcionaría bloqueado, NO cambies el APK: descríbelo en el informe con el cambio mínimo que haría falta.
   - Mientras tanto, deja preparado el modo `--supervised` de `phone_run.py`, que solo vale cuando el usuario cede el teléfono
     desbloqueado.
6. **Salidas** en `C:\kw\vt-bench\full-20261020-flip\`:
   - una COPIA del manifiesto congelado: comprueba su SHA-256, `8168ab09…fb71`, y no la modifiques;
   - `traces\`, `extraction-state.json` y `extraction.log`, con una línea por lote: hora, clips, duración, batería, estado térmico,
     pausas y fallos;
   - **`timing.json`**: por clip, la duración del vídeo, el tiempo de análisis y la razón entre ambos, más los ms por fotograma si el
     banco los da.
   - Con esa estructura, el evaluador `FullBankReportTest` sirve sin cambios, con `KPKN_FULL_BANK_DIR` apuntando a esta carpeta.
     Compruébalo leyendo su código.
7. **Pruebas sin teléfono:**
   - `--dry-run`, como en el emulador;
   - autopruebas en Python con un `adb` simulado:
     - no libre → espera;
     - foco perdido a mitad → corta sin Inicio y repite el lote;
     - batería baja → pausa;
     - SEVERE → pausa;
     - SHA distinto → se niega;
     - una ruta fuera del directorio `.dbg` → se niega.
   - Ahora NO hay teléfono conectado, y no debes conectarte a ninguno.

## Prohibido
- Conectarte a cualquier dispositivo o emulador, ejecutar `adb` real y emparejar.
- Modificar `phone_run.py`, `emu_run.py`, el conductor del emulador, el manifiesto o las etiquetas.
- Escribir fuera de `C:\kw\vt-bench\full-20261020-flip\`.
- Descargas y librerías nuevas.

## Informe final (≤ 350 palabras)
1. Qué hace el conductor y cómo aplica cada regla.
2. Lo que muestran el `--dry-run` y las autopruebas.
3. El punto 5: si funciona bloqueado o qué cambio mínimo haría falta.
4. El comando exacto para lanzarlo cuando el teléfono esté conectado.
