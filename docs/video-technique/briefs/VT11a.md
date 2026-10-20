# VT11a · Herramientas del banco completo: manifiesto congelado, conductor de la extracción y evaluador

- **Rama:** `vt/vt11a` en `C:\kw\vt-c`, que sale de `feat/vt-ui`.
- **Fuera del repo:** `C:\kw\vt-bench\full-20261020\` (nuevo).
- **Logs:** `C:\kw\logs\vt-11a-<n>.log`.
- **Antes de empezar, lee:**
  - `docs/video-technique/BANCO_COMPLETO.md` (el plan; manda sobre todo lo demás), `INFORME_CONFIANZA_20261020.md`, `GOLDEN_REPORT.md`,
    `GOLDEN_TIMING_HOLDOUT.md`, `ESTADO.md` y `DECISIONES.md` (D29–D34);
  - el código del banco: `android-native/app/src/test/java/com/example/kpkn/domain/biomechanics/golden/` (`GoldenCorpusTest`,
    `GoldenReport` y lo que usen);
  - los conductores anteriores, en `C:\Users\valen\Documents\KPKNFit\artifacts\video-technique-continuation-20261016\`:
    `run_vt10.py` y `run_external_vt10.py`, con sus logs `vt10-external-*.log`;
  - `C:\kw\tools\emu_run.py` (solo leerlo: cómo elige el dispositivo, cómo sube y baja archivos);
  - el modo `analyze_file` de `android-native/app/src/debug/.../TechniqueBenchActivity.kt`: sus extras, sus salidas y cómo nombra los
    sidecars;
  - el arnés JVM `C:\kw\vt-jvm` (`settings.gradle.kts` y `build.gradle.kts`).

## 1. Manifiesto congelado
- **Archivo:** `C:\kw\vt-bench\full-20261020\manifest.json`, con los 1.076 clips de `C:\kw\vt-bench\labels\`. Cada fila lleva:
  - `id`;
  - la ruta absoluta del vídeo: el `file` de la etiqueta; si es relativa (`dl/...`), sale de `C:\kw\vt-bench`;
  - el SHA-256 del vídeo, comprobado ahora, y el de la etiqueta;
  - `source`;
  - `groupKey`:
    - en Kaggle, el vídeo original del que salió el trozo. Búscalo en `02_catalogo\clasificacion.csv` del banco
      (`original_filename`, `archive_member`, `source_url`…) y en `corpus` de la etiqueta. Si no hay forma fiable de saberlo, agrupa
      por ejercicio de Kaggle y explícalo;
    - en UCF101, `gXX`;
    - en el resto, el `id`;
  - `category`:
    - `G1` si `usable`;
    - `G3` si `suggestedProfile == "UNSUPPORTED"`;
    - `G2` el resto;
  - la especificación de ejercicio que necesita el banco de depuración, igual que la construían los conductores anteriores;
  - `fold` (0–4) y `exam` (`true`/`false`).
- **El examen:** el 20 % de los GRUPOS, estratificado por fuente × categoría × perfil sugerido, al azar con una semilla fija.
  - Ningún grupo queda partido entre examen y desarrollo: compruébalo.
  - Los `fold` del desarrollo también van por grupos.
- **Registro:** escribe en la sección «Manifiesto» de `docs/video-technique/BANCO_COMPLETO.md` el SHA-256 del manifiesto, la semilla
  y los recuentos por grupo, fuente y examen o desarrollo. Créala al final del documento.
- **Una vez escrito, el manifiesto no cambia.** Si hace falta corregirlo, se hace una versión nueva con otro nombre y se explica.

## 2. Conductor de la extracción
- **Archivo:** `C:\kw\vt-bench\full-20261020\run_full_bench.py`, adaptado de `run_external_vt10.py`.
- **Qué hace:**
  - lee el manifiesto;
  - lotes de 12, configurable;
  - se reanuda: salta los clips cuyos sidecars ya están completos;
  - en cada lote sube los vídeos, lanza `TechniqueBenchActivity` en `analyze_file` por `emu_run.py` (como antes), baja las salidas a
    `full-20261020\traces\` y BORRA del dispositivo los vídeos y las salidas;
  - deja una línea por lote en `full-20261020\extraction.log`, con la hora, los clips, la duración y los fallos;
  - guarda el estado en `full-20261020\extraction-state.json`, con el hash del manifiesto.
- **Parámetros:** `--serial`, para elegir el emulador; `--only G1,G2,G3` o `--ids`; `--limit`; `--dry-run`.
- **Comprobaciones:**
  - al empezar, que el APK `.dbg` instalado lleva M1. Busca una marca fiable: la versión del motor en el `analysis.json`, el
    `poseStride` a 30 fps o lo que exista. Si no la hay, explícalo;
  - al terminar, imprime el tamaño de la carpeta `tmpAdbCmds` del AVD.
- **AHORA NO SE EJECUTA contra ningún emulador.** Pruébalo con `--dry-run`: debe mostrar los comandos de 2 lotes sin llamar a adb ni
  a `emu_run.py`.

## 3. Evaluador del banco completo
- **Dónde:** `test/.../domain/biomechanics/golden/`. Una prueba nueva, `FullBankReportTest`, o una extensión de `GoldenCorpusTest`
  que reutilice su repetición del motor sobre las trazas.
  - Lee `KPKN_FULL_BANK_DIR` (manifiesto, trazas y etiquetas) y escribe en `KPKN_FULL_BANK_OUT`.
  - Sin esas variables, se salta: las corridas normales no necesitan el banco.
- **Qué mide, por grupo (G1/G2/G3), por perfil, vista, fuente y banda de fps, y en el subconjunto «encuadre bueno»** (vista admitida
  por el perfil, cuerpo entero y una persona):
  - serie exacta, dentro del rango y el error absoluto medio;
  - el reparto ALTA/MEDIA/BAJA/sin salida;
  - la precisión de cada nivel;
  - el «número ofrecido equivocado»: ALTA o MEDIA fuera del rango;
  - en G3, la tasa de número ofrecido;
  - en la zancada caminando, que no se cuenten reps;
  - intervalos de Wilson al 95 % para cada proporción.
- **Por clip**, en `KPKN_FULL_BANK_OUT` (FUERA del repo), un JSON con:
  - la salida y el nivel;
  - los componentes de la confianza: `c_track`, `c_snr`, `c_amp`, `c_rhy`, `c_view` y `n_amb`;
  - `fs`, `LOW_FPS`, las personas y las banderas.
- **Desarrollo y examen:**
  - por defecto SOLO desarrollo (`exam=false`);
  - el examen exige `KPKN_FULL_BANK_EXAM=1` y NO se ejecuta en este paquete.
- **Informe agregado** en Markdown (sin trazas ni datos por clip): `KPKN_FULL_BANK_OUT/BANCO_COMPLETO_RESULTADOS.md`.

## Validación con las trazas viejas
Antes de la extracción nueva, el evaluador se prueba con las 513 trazas que ya hay:
- `continuation-20261016\traces-compatible`;
- `continuation-20261016\external\traces`.

Para eso, arma un manifiesto de PRUEBA aparte, que no es el congelado, apuntando a esas trazas. Debe reproducir:
- en los 59 de ajuste de VT10 con el motor actual: 57 salidas, 5 ALTA, 11 MEDIA y 41 BAJA. Con la máscara de caminar actual,
  40/57 exactas y 43/57 en rango (`INFORME_CONFIANZA_20261020.md`);
- en el externo, las cifras de `continuation-20261016\external\reports\GOLDEN_EXTERNAL.md` que sean comparables.

Las 37 de la reserva vieja ya están gastadas: no las uses para nada.

## Corridas (arnés JVM, carril ligero)
- `& 'C:\Program Files\Git\bin\bash.exe' -c "KPKN_SLOT_LANE=jvm KPKN_FULL_BANK_DIR=... KPKN_FULL_BANK_OUT=... bash /c/kw/tools/gradle_slot.sh /c/kw/vt-jvm test --offline -PappRoot=C:/kw/vt-c/android-native/app --tests 'com.example.kpkn.domain.biomechanics.golden.*' > /c/kw/logs/vt-11a-<n>.log 2>&1"`
- Antes de la primera, comprueba en `gradle_slot.sh` que el carril `jvm` acepta `/c/kw/vt-jvm` como directorio. Si no lo acepta,
  explícalo y no lo fuerces.

## Archivos permitidos
- **Repo:** `android-native/app/src/test/java/com/example/kpkn/domain/biomechanics/golden/*` y la sección «Manifiesto» de
  `docs/video-technique/BANCO_COMPLETO.md`.
- **Fuera:** `C:\kw\vt-bench\full-20261020\*`.
- **Solo lectura:** todo lo demás, sobre todo las etiquetas, los vídeos, los conductores anteriores y `emu_run.py`.

## Commits
- `test(technique): evaluador del banco completo (1.076 clips, grupos G1–G3, desarrollo y examen)`
- `docs(technique): manifiesto congelado del banco completo`
