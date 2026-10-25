# Aplicación local del plan — 24-oct-2026

Seguimiento vigente: [cierre Android aislado y evidencia final](ANDROID_CLOSURE_20261024.md). Este documento conserva el checkpoint anterior con su alcance y binario propios.

> Continuación de QA: [QA Android ampliado](ANDROID_QA_20261024.md) registra el APK
> posterior `b277df6e…`, 15 pruebas focalizadas aprobadas, 29 filtros deportivos
> recorridos, casos vacío/offline/rotación y accesibilidad a 130/200 %. El recorrido
> completo de TalkBack y el giro durante permisos iniciales quedaron para la tanda siguiente.
> El APK `c6149797…` y las 382 pruebas descritos debajo son el checkpoint previo.

> [Seguimiento físico final](PERMISSIONS_TALKBACK_20261024.md): APK `655f2eb4…`
> instalado en Z Flip, seis regresiones finales aprobadas y QA de permisos al rotar
> y lectura inicial completa con TalkBack confirmado. Room30→34 con respaldo
> recuperable y originales conservados; accesibilidad, notificaciones y orientación
> restauradas. Sin commit ni push. El resto de este documento conserva el checkpoint anterior.

El plan se aplicó a `C:\Users\valen\Documents\KPKNFit`, sobre la base `edc237f66d8474c3a9922296b61940e7030b7f8a`, recuperando el trabajo privado `3661e40fba8344b32d90309af8d4fbd404efebe0`. La ejecución vigente es local. No se reanudó desarrollo en cloud ni se publicó en GitHub.

## Aplicación y preservación

Se respaldaron los archivos locales afectados antes de escribir o retirar código. Se resolvieron seis conflictos del catálogo conservando la curación local y usando el compilador documentado; permanecen 228 definiciones y **552 configuraciones**, incluido el retiro local previo de `conventional_row__cable`. El hash canónico local es `c9d234c1a384fc7da59f58308ff3f86f27b3018e6158eaa2b302e362f2ecc73d`. Las tres copias Android/JVM/iOS se generaron juntas y las aserciones conservan un hash exacto.

El producto retira motores, pantallas y etapa RINGS, preserva originales mediante Room 34 y reutiliza los registros en Home, sesión e historial. Las propuestas de protocolos requieren confirmación. Los importadores aislados y esquemas históricos permanecen para recuperar originales, sin reactivar los cálculos retirados.

La auditoría local detectó y eliminó otro ajuste de volumen de −15 %/+10 % derivado de molestias y capacidad declarada. Se quitaron sus consumidores Android/iOS y un detector iOS huérfano de sobreentrenamiento. La región anatómica se denomina `JointRegion`, conservando los mismos valores serializados. Se amplió el gate de retiro para detectar estas reintroducciones.

El repositorio de observaciones vuelve a obtener el DAO vigente después de reconectar Room. Las nuevas regresiones comprueban que se conservan las declaraciones y que no cambian las referencias explícitas del programa por esas respuestas.

La continuación en este chat terminó la corrección del fallo de arranque causado por plantillas que todavía pedían `conventional_row__cable`. Usan la configuración vigente `gironda_row__medium`, sin reintroducir la variante retirada ni cambiar el catálogo curado. También se corrigieron las reservas del generador y el mapa de polea alta/baja: una ausencia explícita de ese aparato debe negar el remo vigente. Las pruebas leen el catálogo real y verifican las recetas, plantillas, reservas y resolución de equipo. El alias de recuperación de registros antiguos permanece aislado.

El control de retiro lee explícitamente UTF-8 (admitiendo BOM), para ejecutarse en Windows sin depender de la codificación regional de la consola.

## Validación local

| Comprobación | Resultado |
|---|---|
| Catálogo, `unittest discover -s scripts/tests` | 218 pruebas aprobadas; exit 0 |
| Backend, `pytest backend/tests` | 19 pruebas aprobadas; exit 0; una advertencia de deprecación de TestClient |
| Compilador de catálogo `--check` | Aprobado |
| Gate de retiro ampliado | Cero infracciones activas; controles positivos/anillas físicas aprobados |
| Android, `compileBaseDebugUnitTestKotlin assembleBaseDebug` | Aprobado con el código final; exit 0 |
| Regresión Android focalizada final | **382 pruebas en 33 clases; cero fallos, errores u omisiones; 33 XML nuevos, ninguna clase seleccionada ausente; exit 0** |
| Emulador, actualización y migración | APK final instalado con `-r`; Room 34; originales comparados con la copia Room 30; cero diferencias inesperadas; arranque a MainActivity confirmado |

APK local final: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`; 489.482.000 bytes; SHA-256 `c6149797c5a29816d17230e39af328250f859ad407e0ba2667b4cce08ff66a4e`. Identidad `com.example.kpkn.dbg`, nombre KPKN Dev, certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`, coincidente con el del emulador y comprobado antes de actualizar.

La inspección de 23 DEX no encuentra los prefijos de motores/pantallas/repositorio retirados ni los identificadores `calculateVolumeAdjustment` y `ArticularBattery`; sí encuentra `TrainingInsightsEngine`. Es una comprobación estática acotada, separada del QA runtime.

Para evitar acumular memoria se separó compilación y ejecución de pruebas: un trabajador Gradle, heap de compilación 6 GiB, luego heap Gradle 2 GiB con un solo fork de pruebas de 3 GiB y rotación cada cinco clases. Una primera compilación con 4 GiB se detuvo únicamente por su árbol de procesos al mostrar presión de heap; no es un resultado aprobado. No se detuvieron JVM ajenas.

El build final `compileBaseDebugUnitTestKotlin assembleBaseDebug` terminó con exit 0 en 12 min 56 s. La regresión posterior terminó con exit 0 en 10 min 25 s. Se ejecutó sin excluir las tareas de compilación, que estaban `UP-TO-DATE`: un intento previo con `-x` falló por un proveedor de AGP antes de lanzar pruebas y se conserva como fallo de infraestructura, sin contarlo como resultado de tests. La selección de 33 clases amplía el checkpoint previo de 220 pruebas; los recuentos se solapan y no se suman. No equivale a ejecutar toda la suite del repositorio.

## Datos del emulador y límites externos

Antes de actualizar `emulator-5554` se guardaron APK y base de KPKN Dev: Room 30, 11 sesiones, tres programas y un recibo de activación. No se borraron datos ni se instaló otra identidad. La instalación exige comprobar de nuevo identidad Room y certificado antes de `-r`.

La lectura final después de instalar conserva las 11 sesiones, tres programas, un recibo y un registro de medio, con los mismos IDs y campos originales comparados. Las 246 diferencias JSON son exclusivamente datos derivados retirados; no hay diferencias inesperadas en cantidades, notas o fechas ni tablas AUGE activas. La comparación acredita los registros y referencias de medios, no un checksum de sus archivos binarios. El harness dejó un borrador sintético, sin activar otro programa ni crear un recibo.

QA real en el AVD `ZFlip5_QA`, serial `emulator-5554`, fuente 1,3 y pantalla efectiva 1080×2220 a 480 dpi: arranque en frío, Home con datos, gráfico de duración en minutos, periodos 1/4/12 semanas (6/11/11 registros), apertura del punto, fuente/fecha/unidad, método y contexto ausentes explícitos, desplazamiento y cierre del detalle. El historial no convierte la falta de contexto en equivalencia o descanso. La revisión del wizard se abrió con su harness sin pulsar Activar. El APK final volvió a Home tras la extracción y `topResumedActivity` confirmó `com.example.kpkn.dbg/com.example.kpkn.MainActivity`, con proceso vivo y sin excepción fatal en la lectura acotada del log. No se acredita aquí QA físico, TalkBack, rotación, funcionamiento offline ni todos los deportes en un dispositivo.

La evidencia de esta continuación está en `C:\Users\valen\.codex\visualizations\2026\10\24\01a16ee1-907f-7643-b0a2-45f779d8f023\post-rings`: selección y logs exactos, `ANDROID_RESULTS.json`, los 33 XML nuevos, `APK_VERIFICATION.json`, capturas/árboles de UI, identidad/firma de instalación y `PRESERVATION_COMPARISON.json`. `SOURCE_CHECKPOINT.json` guarda hashes de los archivos seleccionados de la continuación; no pretende capturar todo el WIP concurrente. No se creó commit ni se hizo push; HEAD sigue en `edc237f66` y los cambios quedan locales.

En ese checkpoint se consultó disponibilidad del Z Flip mediante `phone_run` y `phone.lock`, con guardas intactas. La KPKN Dev instalada no era depurable y `run-as` impedía extraer la base. Se conservó su APK, **no se intentó actualizarla**, no se borraron datos y la app personal no se tocó. Este bloqueo se superó después mediante exportación completa desde Ajustes; el seguimiento físico enlazado arriba conserva el respaldo y los resultados posteriores.

Compilación/XCTest iOS siguen pendientes de un host macOS. La [verificación científica local](../research/training-evidence/VERIFICACION_LOCAL_20261024.md) sustituye el bloqueo bibliográfico del checkpoint cloud, distingue acceso real y corrige sus cifras problemáticas. No acredita validación clínica ni una revisión sistemática exhaustiva.
