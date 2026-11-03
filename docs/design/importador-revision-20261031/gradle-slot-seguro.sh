#!/usr/bin/env bash
# Uso: gradle_slot.sh <carpeta android-native | arnés *-jvm> [argumentos de gradle...]
# Ejecuta ./gradlew con las banderas estándar de KPKN, pero SOLO cuando hay una ranura libre en su carril y RAM suficiente.
# Una ranura sin latido durante 150 s se considera huérfana y se libera sola. Estado de la cola: bash /c/kw/tools/gradle_cola.sh
#
# v10 (2026-10-21 noche, orden del usuario «coordina tú la cola»; 14 corridas esperaban detrás de una de 50 min):
#   - Dos carriles. ANDROID (`<raíz>/`): 1 ranura (KPKN_GRADLE_SLOTS=2 permite otra, solo con ≥10 GB libres). JVM (`<raíz>/jvm/`): KPKN_JVM_SLOTS=2
#     ranuras (antes 1), la 2.ª con ≥8 GB libres, Gradle con -Xmx2g. El carril JVM se elige con KPKN_SLOT_LANE=jvm o solo
#     porque la carpeta se llama *-jvm / *-jvm-<x> / kpkn-jvm*.
#   - Orden por COSTE, no solo por llegada: clase 1 = sin tests (compilar/ensamblar); 2 = tests solo de clases concretas;
#     3 = tests con 1–2 comodines (`paquete.*`); 4 = ≥3 comodines o sin filtro. Por cada KPKN_SLOT_PROMOTE_S (1.200 s) de
#     espera el turno sube una clase, así nadie se queda sin turno. A igual clase, orden de llegada. KPKN_SLOT_PRIORITY=1
#     sigue siendo clase 0. Los turnos v9 que ya estaban en cola se tratan como clase 1 y conservan su antigüedad.
#   - REUTILIZACIÓN: si la última corrida de la MISMA carpeta con las MISMAS tareas y el MISMO árbol (HEAD + cambios sin
#     commitear, por mtime y tamaño) terminó con éxito hace menos de 2 h, no se repite: se avisa y se sale con 0.
#     KPKN_SLOT_FORCE=1 obliga a repetir. En el carril JVM el árbol es el de -PappRoot.
#   - Quita :app:compileBaseDebugAndroidTestKotlin y :app:assembleBaseDebugAndroidTest (no hacen falta para tests unitarios
#     ni para compilar; nadie lanza instrumentadas desde la cola). KPKN_SLOT_KEEP_ANDROIDTEST=1 los conserva.
#   - Al tomar ranura baja a BelowNormal la prioridad de ffmpeg (clips de la ola VT); el Gradle sigue en Normal.
#   La v9 queda en gradle_slot.v9.sh.
# v9: marca -Dkpkn.slot=<testigo> en la JVM; cierra su árbol al terminar o si lo cortan; barre huérfanos; 2 ranuras Android
#     (la 2.ª con ≥ KPKN_SECOND_SLOT_MIN_GB=10 GB libres).
# v8: tope GLOBAL de ranuras vivas. v7: carril jvm. v6: KPKN_SLOT_PRIORITY. v5: cola por orden de llegada con latidos.
# v4: UN solo Gradle por carpeta. v3: testigo en owner. v2: RAM con punto decimal y reclamo atómico de huérfanas.
base=${KPKN_SLOTS_DIR:-/c/kw/slots}
stale=${KPKN_SLOT_STALE:-150}
promote=${KPKN_SLOT_PROMOTE_S:-1200}
dir="$1"; shift
lane=${KPKN_SLOT_LANE:-}
case "$dir" in *-jvm|*-jvm/|*-jvm-*|*/kpkn-jvm*) lane=jvm ;; esac
if [ "$lane" = "jvm" ]; then
  N=${KPKN_JVM_SLOTS:-2}; root=$base/jvm; minfree=${KPKN_MIN_FREE_GB:-4.0}; xmx=2g
  second_min=${KPKN_SECOND_SLOT_MIN_GB:-8}
else
  # v10: UNA ranura Android por defecto. Con dos a la vez (v9) los logs del 21-oct muestran RAM libre de 0,4–0,8 GB (swap) y
  # corridas de más de una hora. KPKN_GRADLE_SLOTS=2 vuelve a dos, y aun así la 2.ª exige ≥10 GB libres.
  # v11b (28-oct): 4g no alcanza para compilar el main en frío (worktree nuevo): la cola compila Kotlin dentro de Gradle
  # y revienta con OutOfMemoryError en compileBaseDebugKotlin (ExerciseEditorCard). 8g = el tope de la guía; con una sola
  # ranura Android cabe. KPKN_ANDROID_XMX lo cambia.
  N=${KPKN_GRADLE_SLOTS:-1}; root=$base; minfree=${KPKN_MIN_FREE_GB:-3.0}; xmx=${KPKN_ANDROID_XMX:-8g}
  second_min=${KPKN_SECOND_SLOT_MIN_GB:-10}
fi
# Cada latido, un quinto de la caducidad (30 s con 150 s), para que nada vivo caduque entre dos latidos.
period=$((stale / 5)); [ "$period" -gt 30 ] && period=30; [ "$period" -lt 2 ] && period=2
mkdir -p "$root/cola" "$base/ultimo"
me="$$.$RANDOM"
parent=$$
dkey=$(printf '%s' "$dir" | tr -c 'A-Za-z0-9' '_')
dl=$base/dir.$dkey
last=$base/ultimo/$dkey
ticket=""
s=""
gpid=""
free_gb() { powershell -NoProfile -Command "[math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB,1)" 2>/dev/null | tr -d '\r' | tr ',' '.'; }
# Segundos desde el último latido (o desde la creación si aún no hay latido).
age() { local f="$1" now t; now=$(date +%s); t=$(stat -c %Y "$f/beat" 2>/dev/null || stat -c %Y "$f" 2>/dev/null || echo "$now"); echo $((now - t)); }
# Cuántas ranuras hay VIVAS ahora en este carril (con latido de menos de `stale` s). El tope N es global al carril.
alive_slots() { local n=0 d; for d in "$root"/[0-9]; do [ -d "$d" ] || continue; [ "$(age "$d")" -le "$stale" ] && n=$((n + 1)); done; echo "$n"; }
# Latido: toca $1 cada `period` s mientras viva este script y $2 siga diciendo que es nuestro.
beat() { ( while kill -0 "$parent" 2>/dev/null && grep -q "$me" "$2" 2>/dev/null; do touch "$1" 2>/dev/null; sleep "$period"; done ) & }
# Cierra (con su árbol) los procesos java marcados con kpkn.slot cuyo padre ya no existe. Solo toca JVM lanzadas por este
# script (llevan la marca), nunca el Gradle de Android Studio ni de otras herramientas.
sweep_orphans() {
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'kpkn\\.slot=' -and -not (Get-Process -Id \$_.ParentProcessId -ErrorAction SilentlyContinue) } | ForEach-Object { Write-Output ('[gradle_slot] cierro Gradle huerfano ' + \$_.ProcessId); taskkill /T /F /PID \$_.ProcessId | Out-Null }" 2>/dev/null | tr -d '\r'
}
# Cierra el árbol de NUESTRO Gradle (los java con nuestra marca) si el script termina antes que él.
kill_ours() {
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'kpkn\\.slot=$me' } | ForEach-Object { taskkill /T /F /PID \$_.ProcessId | Out-Null }" 2>/dev/null
}
# v10: ffmpeg (clips de vídeo) a prioridad baja mientras compilamos. Reversible; no toca nada más.
calm_competitors() {
  powershell -NoProfile -Command "Get-Process ffmpeg -ErrorAction SilentlyContinue | ForEach-Object { try { \$_.PriorityClass = 'BelowNormal' } catch {} }" >/dev/null 2>&1
}
release() {
  if [ -n "$gpid" ] && kill -0 "$gpid" 2>/dev/null; then kill "$gpid" 2>/dev/null; kill_ours; fi
  [ -n "$s" ] && grep -q "$me" "$s/owner" 2>/dev/null && rm -rf "$s"
  grep -q "$me" "$dl/owner" 2>/dev/null && rm -rf "$dl"
  [ -n "$ticket" ] && rm -f "$ticket"
  kill $beater $dbeater $tbeater 2>/dev/null
}
trap release EXIT INT TERM

# Antes de nada, barre huérfanos de corridas cortadas (de cualquier sesión que use este script).
# No se barren procesos ajenos.

# v10: normaliza los argumentos, calcula la clase de coste y la firma de las tareas.
orig=("$@")
args=(); have_jvm=""; has_test=0; nfilters=0; nwild=0; expect_filter=0; dropped=(); app_root_arg=""
note_filter() { nfilters=$((nfilters + 1)); case "$1" in *\**) nwild=$((nwild + 1)) ;; esac; }
for a in "${orig[@]}"; do
  if [ "$expect_filter" = 1 ]; then expect_filter=0; note_filter "$a"; args+=("$a"); continue; fi
  case "$a" in
    --tests) expect_filter=1; args+=("$a") ;;
    --tests=*) note_filter "${a#--tests=}"; args+=("$a") ;;
    -PappRoot=*) app_root_arg=${a#-PappRoot=}; args+=("$a") ;;
    -Dorg.gradle.jvmargs=*) args+=("$a -Dkpkn.slot=$me"); have_jvm=1 ;;
    :app:compileBaseDebugAndroidTestKotlin|compileBaseDebugAndroidTestKotlin|:app:assembleBaseDebugAndroidTest|assembleBaseDebugAndroidTest)
      if [ -n "${KPKN_SLOT_KEEP_ANDROIDTEST:-}" ]; then args+=("$a"); else dropped+=("$a"); fi ;;
    *) case "$a" in :app:test*|test|:app:connected*|connected*) has_test=1 ;; esac; args+=("$a") ;;
  esac
done
[ -z "$have_jvm" ] && args=("-Dorg.gradle.jvmargs=-Xmx$xmx -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -Dkpkn.slot=$me" "${args[@]}")
if [ "$has_test" = 0 ]; then cls=1
elif [ "$nfilters" = 0 ]; then cls=4
elif [ "$nwild" = 0 ]; then cls=2
elif [ "$nwild" -le 2 ]; then cls=3
else cls=4; fi
[ -n "${KPKN_SLOT_PRIORITY:-}" ] && cls=0
argsig=$(printf '%s\n' "${orig[@]}" | md5sum | cut -c1-32)
[ "${#dropped[@]}" -gt 0 ] && echo "[gradle_slot] quito ${dropped[*]} (no hace falta para tests unitarios; KPKN_SLOT_KEEP_ANDROIDTEST=1 lo conserva)"
# Firma del árbol: HEAD + cada cambio sin commitear con su mtime y tamaño. En el carril JVM, el árbol de -PappRoot.
sigdir="$dir"; [ -n "$app_root_arg" ] && sigdir=$app_root_arg
tree_sig() {
  local top
  top=$(git -C "$sigdir" rev-parse --show-toplevel 2>/dev/null) || { echo "nogit.$$.$RANDOM"; return; }
  {
    git -C "$top" rev-parse HEAD 2>/dev/null
    git -C "$top" status --porcelain --untracked-files=all 2>/dev/null
    # v11: un solo stat por lotes. En v10 era un stat por archivo: con 60.000 sin seguimiento, 40 min con la ranura tomada.
    ( cd "$top" && { git diff --name-only -z HEAD 2>/dev/null; git ls-files -z -o --exclude-standard 2>/dev/null; } \
      | xargs -0 -r stat -c '%n %Y %s' 2>/dev/null )
  } | md5sum | cut -c1-32
}

# 0) Un solo Gradle por carpeta: espera el candado de la carpeta.
dwait=0
while true; do
   : # No se retiran candados ajenos.
  if mkdir "$dl" 2>/dev/null; then touch "$dl/beat"; echo "$dir $(date +%H:%M:%S) $me" > "$dl/owner"; break; fi
  sleep 10; dwait=$((dwait + 10))
  if [ $((dwait % 300)) -eq 0 ]; then echo "[gradle_slot] espero a que termine otro Gradle de $dir (${dwait}s)"; fi
done
beat "$dl/beat" "$dl/owner"; dbeater=$!

# 0b) v10: ¿ya se hizo exactamente esto sobre exactamente este árbol? Entonces no se repite.
if [ -z "${KPKN_SLOT_FORCE:-}" ] && [ -f "$last" ]; then
  read -r l_arg l_tree l_end l_rc _ < "$last"
  now=$(date +%s)
  if [ "$l_arg" = "$argsig" ] && [ "${l_rc:-1}" = 0 ] && [ $((now - ${l_end:-0})) -le 7200 ]; then
    cur=$(tree_sig)
    if [ "$cur" = "$l_tree" ]; then
      echo "[gradle_slot] REUTILIZO la corrida de las $(date -d "@$l_end" +%H:%M 2>/dev/null) de $dir: mismas tareas y mismo árbol (HEAD y cambios sin commitear), terminó con éxito. No la repito."
      echo "[gradle_slot] resultados: $dir/app/build/test-results/ (carril Android) o $dir/build/test-results/test/ (carril JVM). KPKN_SLOT_FORCE=1 obliga a repetir."
      exit 0
    fi
  fi
fi

# 1) Turno en la cola: nombre = [0]<ns de llegada>.<testigo>; dentro, la clase de coste.
ticket="$root/cola/${KPKN_SLOT_PRIORITY:+0}$(date +%s%N).$me"
write_ticket() { { echo "$dir $(date +%H:%M:%S) $me"; echo "class=$cls"; echo "args=${orig[*]:0:400}"; } > "$ticket"; }
write_ticket
beat "$ticket" "$ticket"; tbeater=$!

# v10: el turno que va primero = menor (clase efectiva, llegada). Clase efectiva = clase - minutos de espera / promote.
first_ticket() {
  local now t n c ts eff key best="" bestkey=""
  now=$(date +%s)
  for t in "$root"/cola/*; do
    [ -f "$t" ] || continue
    n=${t##*/}
    c=$(sed -n 's/^class=//p' "$t" 2>/dev/null | head -1); [ -z "$c" ] && c=1
    case "$n" in 0*) c=0; ts=${n:1:10} ;; *) ts=${n:0:10} ;; esac
    case "$ts" in ''|*[!0-9]*) ts=$now ;; esac
    eff=$((c - (now - ts) / promote)); [ "$eff" -lt 0 ] && eff=0
    key="$eff $n"
    if [ -z "$bestkey" ] || [[ "$key" < "$bestkey" ]]; then bestkey=$key; best=$n; fi
  done
  echo "$best"
}

# 2) Solo el turno que va primero busca ranura en su carril y RAM libre.
waited=0
while true; do
  # Si alguien borró nuestro turno por caducado (p. ej., el equipo se suspendió), vuelve con el mismo nombre: conserva su puesto.
  if [ ! -f "$ticket" ]; then
    kill $tbeater 2>/dev/null
    write_ticket
    beat "$ticket" "$ticket"; tbeater=$!
  fi
  for t in "$root"/cola/*; do
    [ -f "$t" ] || continue
    : # No se retiran tickets ajenos.
  done
  if [ "$(first_ticket)" = "${ticket##*/}" ] && [ "$(alive_slots)" -lt "$N" ]; then
    for i in $(seq 1 "$N"); do
      s=$root/$i
      : # No se retiran ranuras ajenas.
      if mkdir "$s" 2>/dev/null; then
        touch "$s/beat"
        f=$(free_gb)
        # Con otra ranura viva, la segunda exige más RAM libre.
        need=$minfree
        if [ "$(alive_slots)" -gt 1 ]; then need=$(awk -v a="$minfree" -v b="$second_min" 'BEGIN{print (a > b ? a : b)}'); fi
        if [ -n "$f" ] && awk -v f="$f" -v m="$need" 'BEGIN{exit !(f < m)}'; then
          rm -rf "$s"; s=""; break
        fi
        echo "$dir $(date +%H:%M:%S) $me" > "$s/owner"
        beat "$s/beat" "$s/owner"; beater=$!
        rm -f "$ticket"; ticket=""
        [ "$((waited + dwait))" -gt 0 ] && echo "[gradle_slot] ranura $i del carril ${lane:-android} tras esperar $((waited + dwait))s (clase $cls)"
        # No se modifica la prioridad de otros procesos.
        pre_sig=$(tree_sig)
        cd "$dir" || exit 1
        ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process "${args[@]}" &
        gpid=$!
        wait "$gpid"; rc=$?
        gpid=""
        # v10: recuerda el resultado solo si el árbol no cambió durante la corrida.
        post_sig=$(tree_sig)
        if [ "$pre_sig" = "$post_sig" ]; then echo "$argsig $pre_sig $(date +%s) $rc $(date +%H:%M)" > "$last"; fi
        exit $rc
      fi
    done
    s=""
  fi
  sleep 15; waited=$((waited + 15))
  if [ $((waited % 300)) -eq 0 ]; then
    pos=$(ls "$root/cola" 2>/dev/null | sort | grep -n -F "${ticket##*/}" | cut -d: -f1)
    echo "[gradle_slot] sigo esperando turno (${waited}s; clase $cls; ${pos:-?} de $(ls "$root/cola" 2>/dev/null | wc -l) en la cola por llegada; RAM libre $(free_gb) GB; ranuras: $(cat "$root"/[0-9]*/owner 2>/dev/null | awk '{print $1}' | tr '\n' ' '))"
  fi
done
