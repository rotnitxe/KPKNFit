# Integración del rediseño de Inicio y sesión — 25 de octubre de 2026

Origen: `post-rings-v3` (`abb4db8ea`), dos commits exclusivos desde `e4c12044d`.
Base de integración: `ed6cee4d6`, conservando las correcciones de integridad de sesión.
Worktree: `.worktrees/redesign-integration`, rama `codex/redesign-integration-20261025`.

## Alcance

Se integran las métricas y avisos de Inicio, volumen del asistente del editor,
preparación, cierre en tres pestañas y composición nativa de historias.
No se modifica Room ni se incorporan los cambios locales de nutrición/Room36.

La revisión encontró y corrigió:

- Conflicto en el cierre: conservar las pestañas y el contador lógico vigente.
- Contabilizar una sola serie lógica para registros de ambos lados, compartiendo
  el contador entre Inicio, volumen muscular y cierre; conservar las ejecuciones.
- Calcular tonelaje con la función vigente, incluidos drops/rest-pause y modos de carga.
- Mostrar sRPE solamente cuando fue declarado; no sustituirlo con RIR medio.
  El esfuerzo temporal utiliza segundos originales cuando están disponibles.
- La preparación conserva ausencia de respuestas y campos capturados, aguarda
  persistencia, muestra errores y mantiene el acceso a notas y disciplina.
- Conservar el modo de voz elegido al pedir permiso y activar ese modo.
- Consultar el bienestar original del día; las reintentos conservan la primera
  observación por fecha. El relator no interpreta defaults no respondidos.
- Conectar las acciones de los avisos con navegación real; excluir registros
  futuros del aviso de proximidad al fallo, distinguir RIR1 de menos de RIR1
  y romper la racha de sRPE alto ante una sesión sin sRPE.
- Hitos en cierre e historia derivados de hitos `pr_e1rm` registrados.
- Ajustar la vista previa 9:16 al espacio disponible de la hoja.
- Abrir el cierre expandido: la prueba de 740 dp detectó que el estado parcial
  ocultaba el pie. Mantener visible «Compartir al guardar» en las tres pestañas.
- Bloquear el arrastre de la preparación mientras espera la escritura.

## Validación completada el 26 de octubre de 2026

- `assembleBaseDebug`: correcto con heap de 6 GB y un worker, por la cola compartida.
- 72 pruebas en 10 clases: cero fallos, errores u omitidas en el consolidado final.
  Se conserva el último XML de cada clase: ronda2 y repetición final de las tres
  clases de interfaz en ronda3. No es una única suite monolítica. El primer fallo
  de interfaz se conserva en la evidencia y se corrigió sin relajar la aserción.
- Preparación: continuar sin respuestas no inventa una observación; sólo quedan
  capturados los campos respondidos. Persistencia de bienestar idempotente y
  reconexión de Room verificadas.
- Cierre: recorrido Resumen/Progreso/Anotaciones, notas y sRPE conservados al
  guardar; ausencia de PR inventado. Historia visible a 740 dp con texto grande.
- Regresiones focalizadas de cancelación, commit durable, reintentos y conservación
  de ejecuciones. Las 3.627 fuentes/configuraciones/esquemas del candidato final
  permanecieron idénticas durante la última validación.

APK final: `c544ea8bd4252841a10432b57b81266b51950c1373a8771b873951d19b20887f`.
Identidad `com.example.kpkn.dbg`, nombre **KPKN Dev**, versionCode52 y certificado
SHA256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.

Emulador temporal `KPKN_PostRings_QA`, puerto5570, AVD abierto con `-read-only` y
sin guardar snapshots. Actualización con `-r`, mismo certificado y migración
Room34→35. Los 42 historiales originales conservan todas sus columnas y el hash
`b1de4c23d4abe57d4f0f445c8376ce5719b84b3b7c1b368f69162aa14abd2b2a`.
Arranque en frío final `Status: ok`, MainActivity, sin entradas en el buffer de
crashes. Inicio muestra Series/Tonelaje/Esfuerzo, cambia unidades y abre el panel
de volumen. La preparación y el cierre fueron recorridos por pruebas Compose;
no se atribuye a esta ronda un recorrido físico de voz, vídeo o GPS.

El primer intento de compilación se interrumpió de forma controlada por falta de
heap (4 GB). Los reintentos esperaron su ranura; no se detuvieron compiladores
ajenos. El primer arranque después de instalar quedó esperando permisos; tras
omitir los avisos opcionales, el siguiente arranque en frío fue correcto.

Evidencia local en el worktree: `artifacts/redesign-integration-20261025/`:
`validation-summary.json`, `validation-2.log`, `validation-3.log`, XML de cada ronda,
`emulator-conservation.json`, `cold-launch-final.txt`, `crash-final.log`, capturas y
huellas de fuentes y WIP. Los binarios, copias de datos y logs no se incorporan al commit.

La validación compilada corresponde al commit aislado con Room35. Los cambios
locales ajenos de nutrición/Room36 no se incluyen en este resultado ni se declaran
validados por esta tarea. Sin push, sin modificación de la app personal y sin
publicar una APK a G:.
