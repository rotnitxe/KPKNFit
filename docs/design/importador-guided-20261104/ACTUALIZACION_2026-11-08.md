# Cierre del alcance y validación focal · 8 de noviembre

## Alcance vigente

La instrucción más reciente del usuario reemplaza la expectativa anterior de reproducir el prototipo como una secuencia de muchas páginas y hojas. El importador debe confirmar que los ejercicios y su distribución semanal son correctos en una sola revisión conjunta; al guardar, abre el `ProgramDetail` existente para que el usuario ajuste las semanas ahí. No se añade un editor paralelo de sesiones al recorrido de importación.

El flujo actual en `ImportProgramGuidedScreen` presenta `ImportProgramMatchesPage` y `ImportWeeklyPlanner` dentro de la revisión conjunta. El callback de guardado de `MainActivity` navega al `ProgramDetail` con el ID devuelto y quita la ruta de importación de la pila. `ImportSessionEditorPortal` sigue teniendo un uso separado en onboarding, pero no es un paso del importador.

## Validación

- La corrida focal de siete clases del importador terminó con **79 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**: lector de archivos, calendario semanal, parser OCR, sesiones, unión de páginas OCR, constructor del programa y arnés OCR.
- `ProgramUrlReaderTest` terminó con **14 aprobadas, 0 fallos, 0 errores y 1 omitida**. La omisión es la comprobación optativa de HTTPS público, que requiere configurar `KPKN_IMPORT_LIVE_URL`; las pruebas de transporte con respuestas controladas sí se ejecutaron. No se configuró una URL pública arbitraria.
- La regresión OCR sintética conserva como avisos las celdas truncadas `Sautps` del día 9 y `25 Push` de los días 22 y 27. Verifica que no se inventen esos ejercicios y que los ejercicios vecinos permanezcan en sus días. El corpus Repport sigue siendo una fuente de baja confianza: 99 ejercicios detectados, solo 11 de 26 nombres distintos con coincidencia confiable, y filas incompletas/omitidas que requieren revisión. No se afirma soporte general de escritura manuscrita ni que se hayan recuperado los datos faltantes.
- `git diff --check` no reportó errores en los dos archivos de pruebas ajustados.

## Z Flip · recorrido físico completado

- Las primeras invocaciones del helper dentro del sandbox no pudieron iniciar el daemon ADB local (`could not read ok from ADB Server`). Eso no demostraba que el Z Flip estuviera desconectado. Con el permiso necesario para iniciar el daemon, `phone_run.py` encontró el teléfono y mantuvo el acceso dentro de `phone.lock`. La pantalla estaba encendida y el usuario autorizó expresamente esta sesión supervisada.
- Se cerró el emulador que se había arrancado durante el fallback inicial antes de continuar con el Z Flip. El cierre terminó el árbol del proceso del AVD; la prueba física posterior no usó el emulador ni un navegador.
- APK instalado en el mismo paquete KPKN Dev con reemplazo conservando datos: `com.example.kpkn.dbg`, versionCode 55, SHA-256 `DBE213B79BE3A672FCB336AEC02A0EA3CC7B7BC524DE09981CCCB0C2BC45874A`, certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`, Room 37. `phone_run.py` informó `Performing Streamed Install` y `Success`; no se limpió ni desinstaló ninguna app.
- Desde Programas → Nuevo → Importar → Pegar texto se leyó una rutina de dos sesiones. La revisión conjunta mostró 2/2 coincidencias altas, cero pendientes y cero avisos. El catálogo resolvió «Barbell Bench Press» a «Press de Banca Plano» y «Sentadilla Libre con Barra Olímpica» a «Sentadilla Trasera con Barra Alta».
- La distribución inicial mostró lunes y jueves. Se movió la segunda sesión a martes y la misma pantalla mostró lunes y martes, 2 sesiones y 5 descansos. «Guardar y abrir programa» llegó directamente al `ProgramDetail` existente; la pestaña Estructura mostró la semana base, la edición de distribución y la acción para añadir una semana.
- El programa temporal `QA ZFlip 2026-11-08` no se activó. Se eliminó desde su propia tarjeta tras confirmar el nombre; luego se verificó que ya no aparecía y que los programas anteriores seguían en la lista. El teléfono volvió a Inicio.
- Capturas de evidencia del flujo, sin la captura preliminar de notificaciones ni las listas con datos previos: `output/import-physical-20261108/zflip/read-text_review.png`, `review-scroll_weekly.png`, `move-session_moved.png`, `save-import_handoff.png` y `program-structure_structure.png`.

La verificación física del recorrido y del destino en `ProgramDetail` quedó completada. La prueba de HTTPS público sigue siendo optativa y requiere configurar `KPKN_IMPORT_LIVE_URL`; no se ejecutó una suite completa ni hubo commit o push.
