# Receta de REA para los análisis de APK (orden del usuario: la ingeniería inversa se hace con REA)

**REA** = `rea-agents` 6.5.0 (CLI + servidor MCP de ingeniería inversa), ya instalado en el equipo con Ghidra 12.1.4 y JADX headless. Úsalo para TODO lo que sea inspeccionar el APK (manifiesto, clases, métodos decompilados, referencias, cadenas de Dart, JavaScript, nativo). Tus scripts de Python siguen valiendo como complemento (extraer cadenas, parsear Hermes, etc.), pero los hallazgos del APK deben apoyarse en salidas de REA y citar el comando usado.

## Cómo se llama
Siempre por el envoltorio, que pone Ghidra/JDK/JADX y deja correr UNA sola ejecución de REA a la vez en toda la máquina (la RAM va justa y hay otros agentes):
`bash /c/kw/research/tools/rea_run.sh <comando> <ruta> [opciones] --token-limit 20000 > <tu-carpeta-de-tools>/salida.txt` (formato por defecto, legible y compacto; probado: `inspect-android-package` sobre el APK de Athlytic tarda ~25 s y devuelve manifiesto, 7 822 clases y recursos). **`--token-limit/--token-offset` NO se pueden combinar con `--format json`**: si quieres JSON usa `--format json --filter-output <claves>` o la paginación propia del comando.
- Redirige SIEMPRE la salida a un archivo en `C:\kw\research\tools\<tu-id>\` y léela por partes (`--token-limit`, `--token-offset`, `--filter-output <claves>`); las salidas completas son enormes.
- Para comandos largos (JADX sobre un DEX de 10 MB, Ghidra sobre una `.so`) lánzalos en segundo plano (`run_in_background`) con log y espera a que terminen; el primer uso de JADX sobre un APK grande puede tardar varios minutos. Si el envoltorio imprime «espero turno de REA», es normal.
- Ayuda de cualquier comando: `bash /c/kw/research/tools/rea_run.sh <comando> --help` (y `--schema`). Lista completa: `bash /c/kw/research/tools/rea_run.sh --help`.
- Los comandos que tocan dispositivos (`*-adb-*`), instalan, ejecutan, depuran o capturan procesos (`capture-*`, `debug-*`, `observe-*`, `install-*`, `start-*`, Frida) **están prohibidos**: solo análisis estático.
- Apktool NO está instalado (`decode-android-resources` no funciona); para recursos usa `aapt2.exe dump` del SDK (ver el brief común) o `inspect-android-package`.

## Comandos útiles por tipo de app
**APK en general (Java/Kotlin, DEX)** — la ruta es un APK suelto (`.apk` dentro del `.xapk` ya extraído):
1. `inspect-android-package <apk>`: declaraciones, manifiesto decodificado, componentes, permisos.
2. `search-android-classes <apk> <consulta>`: encontrar clases por nombre/paquete (busca `kubios`, `hrv`, `rmssd`, `readiness`, `recovery`, `strain`, nombre del desarrollador…).
3. `inspect-android-class <apk> <clase>`: inventario de miembros; `inspect-android-method <apk> <clase> <método> [sobrecarga]`: método decompilado; `trace-android-references <apk> …`: quién llama a qué; `project-android-application-graph`: grafo de la aplicación.
**Flutter (EVOLVE)**: `identify-flutter-build <apk>` (hash de la instantánea Dart, motor) y `inspect-dart-aot <config.arm64_v8a.apk> --abi arm64-v8a`: secciones, cabeceras y **el grupo de cadenas** (identificadores, claves JSON, textos, plantillas). Vuelca el grupo de cadenas a un archivo y analízalo con tus scripts (palabras clave, vecindad de cadenas). `search-bytes`/`read-bytes` sobre `libapp.so` si necesitas offsets.
**JavaScript / web híbrida (Athlytic)**: `analyze-javascript-application <carpeta assets\public>` (reconstrucción estática: módulos, grafo, semántica), `recover-javascript-sources`, `trace-javascript-semantics`; para búsquedas puntuales en el bundle minificado usa también tus scripts.
**Nativo (`.so`, Kubios u otros)**: `inspect-artifact <so>`, `inspect-binary-layout`, `search`/`xrefs`/`decompile` (Ghidra; pesado: úsalo solo en bibliotecas que no sean runtime genérico —descarta `libhermesvm`, `libreactnative`, `librnskia`, `libc++_shared`, reanimated, mmkv, rnscreens, rnsvg— y, si hay una propia de la app, primero identifícala por nombre/cadenas).
**Hermes (`index.android.bundle`)**: REA no trae un decompilador de Hermes; usa `search-bytes`/`read-bytes` y tu parser de la tabla de cadenas; si necesitas más, dilo en el informe (qué herramienta y por qué) para que el orquestador pida permiso al usuario.
