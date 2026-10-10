# Evidencia nativa de GPS, descanso y Free

Pasaron siete casos Android API 36 con el APK de producto `f765a31a…`, sin cambiar producción: tres carreras de GPS/descanso, un ciclo de servicio GPS y tres casos Free/caducidad. Las primeras tres carreras usaron AndroidTest `cbe22ba2…`; los cuatro casos posteriores usaron `3a346501…` con la fixture nueva. La suite JVM final de 8.092 casos y lint/R8 pertenecen a las mismas fuentes de producto; no se volvieron a ejecutar por cambios exclusivos de AndroidTest/runner.

El ciclo GPS usa **el proveedor GPS de prueba de Android** y el cliente Fused real, con posiciones explícitamente sintéticas. Acredita dos puntos al iniciar, pausa sin aceptar puntos nuevos, tres puntos después de reanudar, parada del FGS y conservación de tres puntos/11,119 m al vaciar memoria y leer el archivo. Es la misma PID, no un reinicio. El test llama APIs de servicio: la captura posterior está en la puerta de elegibilidad de edad, sin confirmarla. No acredita la activación desde la UI pública de entrenamiento, GNSS, ruta física ni permisos aproximados/revocados.

Los tres casos Free guardan la quinta sesión, bloquean la sexta y conservan programa, log, comida y foto después de una caducidad sintética. El último reabre Room dentro del mismo proceso y verifica exportación/borrado. No es compra Play real, reinicio de dispositivo ni regla agregada entre varias rutinas.

Los intentos anteriores se conservan. El AVD original tenía `hw.gps=no` y GMS deshabilitado; se restauró GMS tras su ensayo temporal. El AVD GPS nuevo se creó en una carpeta propia, sin tocar el AVD original ni el teléfono. Allí hubo un ANR de inicio de instrumentación y un diálogo de SystemUI. El Perfetto local registra 7,493 s de CPU en Main, 5,980 s listo para CPU sin ejecutarse, apertura DEX de 2,337 s y un frame de 5,547 s. No son duraciones sumables. Apps de fábrica consumían CPU simultáneamente; el análisis no demuestra causa única ni arreglo de rendimiento. La captura de fluidez se descarta por el diálogo superpuesto.

Cambiar preparación de ActivityScenario/Compose mantuvo todas las aserciones. El canal `emu geo fix` siguió sin entregar posiciones en el AVD nuevo; el PASS sólo llegó usando el proveedor de prueba de Android. Se conservaron el timeout original y sus logs. Después del PASS se retiraron el proveedor, el app-op y los permisos añadidos, y se reactivaron las cuatro apps de fábrica suspendidas durante el ensayo.

El runner requiere un AVD propio con GPS/GMS disponibles y el opt-in `--mock-provider`:

```powershell
python scripts/stability/native_gps_qa.py --out gps-check --serial emulator-5558 --avd Kpkn_Gps_QA --avd-config artifacts/stability-service-followup/qa-avd/avds/Kpkn_Gps_QA.avd/config.ini --mock-provider
```

El plan A–J y la aceptación de lanzamiento siguen abiertos. [DELIVERY-final.json](DELIVERY-final.json), [GPS](native-stable-gps-17/result.json), [Free](native-free-19/result.json), [restauración del entorno](environment-restored-18.json) y [análisis Perfetto](startup-profile-14/query-analysis.json) contienen el detalle y hashes. El recibo de integración posterior es la única prueba de master; este documento no la anticipa.
