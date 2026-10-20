# VT11c · El banco en segundo plano: servicio en primer plano en la `.dbg`, y su conductor

- **Decisión del usuario (20-oct): «Sí, en segundo plano».** El banco puede correr en su Z Flip5 MIENTRAS él usa otras apps. Es una
  excepción a la regla de 20 min, SOLO para el banco:
  - el PC sube y baja archivos de la carpeta de `.dbg` y arranca o para su servicio;
  - nunca abre pantallas, ni toca otras apps, los datos de `com.example.kpkn` o los ajustes.
- **Rama:** `vt/vt11c` en `C:\kw\vt-b`, que ya lleva el commit de `setShowWhenLocked`.
- **Logs:** `C:\kw\logs\vt-11c-<n>.log`.
- **Lee antes:**
  - `docs/video-technique/briefs/VT11b.md` y `C:\kw\vt-bench\full-20261020-flip\README.md`, con su conductor
    `run_full_bench_phone.py` y sus autopruebas;
  - `android-native/app/src/debug/`: `TechniqueBenchActivity.kt`, `AnalyzeFileBench.kt`, `PoseBench.kt` (`BenchLayout`) y
    `AndroidManifest.xml`;
  - el manifiesto principal: los permisos de servicio en primer plano que ya declara WorkManager;
  - `C:\kw\tools\phone_run.py` (solo leerlo).

## Qué haces
1. **`TechniqueBenchService`** en `src/debug/.../debug/`: un servicio en primer plano.
   - **Tipo:** `mediaProcessing` en API 35+ y `dataSync` en API 29–34.
   - **Arranque:** `adb shell am start-foreground-service -n com.example.kpkn.dbg/com.example.kpkn.debug.TechniqueBenchService`, con
     LOS MISMOS extras que la actividad (`mode=analyze_file`, `videos` y `exercises`).
   - **Ejecuta EXACTAMENTE el mismo camino que `AnalyzeFileBench`:** mismas entradas y salidas (`vt-bench-in`, `vt-bench-out`,
     `analyze.json` con temporal y renombrado, sidecars y `progress.txt`) y el mismo JSON.
     - Si hace falta, saca el ejecutor a una función compartida por la actividad y el servicio, sin cambiar lo que hace la
       actividad.
     - Los conductores y el evaluador NO deben cambiar por esto.
   - **Notificación:** en un canal «Banco de técnica» de importancia baja, fija y sin sonido. Dice «Banco de técnica · vídeo 5 de 12»
     y se actualiza con cada vídeo.
   - **Bloqueo de suspensión parcial** mientras procesa: se suelta siempre al terminar, y luego `stopSelf()`. Un segundo arranque
     con uno en marcha escribe un `analyze.json` de error («ocupado»), sin pisar el trabajo en curso.
   - **En el `analyze.json`, por vídeo:** el tiempo de análisis (si ya existe, el que haya) y el estado térmico
     (`PowerManager.currentThermalStatus`) al empezar y al acabar.
   - **Manifiesto de debug:**
     - el servicio exportado, solo en debug, como la actividad del banco;
     - los permisos que falten: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC`,
       `WAKE_LOCK` y `POST_NOTIFICATIONS`, solo si no están ya en el principal.
2. **El conductor del teléfono** (`C:\kw\vt-bench\full-20261020-flip\run_full_bench_phone.py`) gana un modo `--background`, que pasa a
   ser el recomendado. En ese modo:
   - **Sin la puerta de 20 min ni la del foco:** es la excepción del usuario. Siguen la batería (con < 30 % y sin cargar, pausa) y la
     temperatura (con SEVERE, pausa de 10 min).
   - **Nunca** abre actividades, ni envía teclas (Inicio, encendido, despertar), ni toca la pantalla.
   - **En cada lote:** sube los vídeos, arranca el servicio, mira `analyze.json` cada 10 s (hasta un máximo, configurable), baja las
     salidas y borra del teléfono los vídeos y las salidas del lote.
   - **Una sola vez:** concede `POST_NOTIFICATIONS` a la `.dbg` (`pm grant com.example.kpkn.dbg …`), solo a ese paquete, para que la
     notificación se vea.
   - **El candado del teléfono** (`C:\kw\phone.lock`) va por lote y se suelta entre lotes, para no bloquear a otros agentes.
   - El SHA del APK se pasa por parámetro, como hoy.
   - **Autopruebas nuevas con `adb` simulado:**
     - arranca el servicio y nunca la actividad;
     - ninguna `input keyevent` ni `am start` de actividades;
     - pausas por batería y por temperatura;
     - se niega a rutas o paquetes fuera de `.dbg`;
     - un servicio «ocupado» → espera y reintenta.
   - Las autopruebas anteriores (38) siguen pasando.
3. **Compilación:** compila y genera el APK de depuración (corrida 2). Escribe su SHA-256 en
   `C:\kw\vt-bench\full-20261020\bench-apk.sha256` y también en la carpeta del Flip.

## Gradle (como siempre)
- `& 'C:\Program Files\Git\bin\bash.exe' -c "bash /c/kw/tools/gradle_solo.sh /c/kw/vt-b/android-native <tareas> > /c/kw/logs/vt-11c-<n>.log 2>&1"`
  - Primer plano y timeout ≥ 7 200 000 ms: espera a que no haya otras compilaciones.
  - Ya va con 6 GB de heap. Ni homes paralelos ni otros trucos.
- **Corrida 1:** `:app:compileBaseDebugKotlin`.
- **Corrida 2:** `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`.

## Prohibido
- Tocar `src/main` o el código de la app: solo `src/debug/**`.
- Teléfono, adb real, emuladores y descargas.
- Modificar `phone_run.py`, `emu_run.py`, el manifiesto congelado y las etiquetas.
- push, `stash`, `reset`, `rebase`, `merge` y `git add -A`.

## Commit
`feat(debug): el banco de técnica también corre como servicio en primer plano (segundo plano en el teléfono)`, con
`Co-Authored-By: Codex <noreply@openai.com>`.

## Informe final (≤ 350 palabras)
1. El commit.
2. El servicio y el manifiesto: qué declaraste y por qué.
3. El modo `--background` y las autopruebas: cuántas hay y cuántas pasan.
4. Las corridas y el SHA del APK.
5. El comando exacto para lanzar el banco en el Flip.
