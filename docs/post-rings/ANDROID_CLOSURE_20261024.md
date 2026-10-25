# Cierre Android de POST RINGS — 24 de octubre de 2026

Estado: **Android cerrado en el checkout aislado Room 34**. iOS permanece pendiente por instrucción del usuario. Build final exit 0 y QA físico de APRE corregido aprobados; sin push.

## Alcance y aislamiento

El cierre queda guardado en `codex/post-rings-android-close`, sobre el checkpoint privado `3661e40fba8344b32d90309af8d4fbd404efebe0`. Conserva la curación local del catálogo: 228 definiciones, 552 configuraciones, hash canónico `c9d234c1a384fc7da59f58308ff3f86f27b3018e6158eaa2b302e362f2ecc73d`. Android usa Room 34.

El checkout aislado reúne los cambios POST RINGS ya aplicados y sus correcciones posteriores: plantillas y generador compatibles con el catálogo vigente, reconexión del DAO de observaciones, eliminación del escalado fisiológico de volumen, permisos protegidos frente a recreación y lectura agrupada del detalle con TalkBack. No incorpora el trabajo concurrente de nutrición/Room 35 del árbol principal. El resultado queda privado y local, sin push.

## Defecto encontrado durante el cierre

En el APK `655f2eb4…`, S3 registró 150 kg × 12 y propuso 155 kg para S4. Antes de confirmar, S4 conservó 150 kg correctamente. Después de pulsar «Aplicar a S4», el indicador de la ruta mostraba 155 kg, pero la tarjeta y el registro inmediato seguían usando 150 kg. La base extraída confirmó esa discrepancia.

La tarjeta identificaba una calibración comparando el texto de su explicación. El controlador ya mostraba otra etiqueta. La corrección transmite `isCalibrationAssignment` en los dos modelos de presentación y lo consume en la tarjeta; la etiqueta queda libre para mostrar texto sin gobernar el registro. No requiere migración ni cambia los valores prescritos del programa.

Las nuevas regresiones cubren el adaptador APRE real, registrar 155 kg sin tocar la carga, conservar 150 kg ante una sugerencia sin confirmar, actualizar una tarjeta ya compuesta al confirmar/revertir y respetar una carga manual fijada. Las pruebas existentes conservan las protecciones de identidad, lateralidad y series ya hechas.

## QA físico completado antes de repetir APRE

Se utilizó exclusivamente KPKN Dev (`com.example.kpkn.dbg`) en el Z Flip, mediante `phone_run` y `phone.lock`. La sesión fue supervisada y autorizada por el usuario. Todos los datos temporales se prepararon sobre copias recuperables; los respaldos, bases y capturas permanecen fuera de Git.

- **18 etapas retiradas:** cada borrador antiguo llegó a «TODO LISTO». Las respuestas útiles, identidad y commit se conservaron. El cursor antiguo almacenado no se reescribe por el simple hecho de leerlo: lo que se verificó aquí fue su normalización y recorrido nativo. La activación creó un solo programa y recibo para el commit probado y consumió ese borrador.
- **Modos parciales:** entrenamiento abre Nuevo programa, nutrición abre Nuevo plan y el acceso retirado vuelve a Home. El recorrido FULL/RESUME no muestra una etapa RINGS.
- **Bienvenida:** la tercera vista muestra «Ejemplo», Carrera · 5 km, sesión anterior 30 min y reciente 29 min, con «Resultados ilustrativos, no tus registros». Su descripción accesible también declara que no usa datos del usuario.
- **Sesión sin respuestas:** Continuar permitió entrar; Abandonar sin guardar dejó cero sesiones en curso y no añadió un registro de entrenamiento.
- **Notas y disciplina:** se introdujeron desde la UI y sobrevivieron pausa, cierre del proceso, reapertura y guardado. Se mantuvieron las cargas del plan antes de una confirmación de protocolo.
- **UI y voz:** S1 quedó como 75 kg × 12 por UI. El usuario confirmó el reconocimiento de «Listo» y dictó S2; el resultado persistido fue 100 kg × 10, RIR 2. Después se apagó la escucha.
- **Pausa/reinicio/plegado:** se recuperaron las dos series, la nota y Fuerza. El usuario plegó y desplegó el teléfono; S3 mantuvo 150 kg y el progreso 2/4.
- **Cierre:** la hoja se expandió por su controlador, permitió guardar sin responder al esfuerzo global y produjo un único log que siguió siendo único tras abrir Home en frío. Este primer log sintético conserva el fallo APRE y no se considera aprobación de S4.
- **Multideporte:** siete recorridos físicos de filtro, punto, fuente, protocolo/contexto y cierre: fuerza, carrera, CrossFit, boxeo, deporte de equipo, sprint y salto. La matriz reutiliza 42 registros sintéticos. El detalle largo permite desplazarse hasta método/protocolo y contexto ausentes explícitos.

## Repetición final con el APK corregido

Build `testBaseDebugUnitTest assembleBaseDebug`: **32 min 29 s, exit 0**. APK de 484.377.056 bytes, SHA-256 `ba2bfbc95b66cd084c68c191e51861b182ff7a74fccb1e213e5ee5ed095eda85`, versión 52, KPKN Dev, certificado `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`, Room 34 e identidad `5af27d74f402ec169ea8037559264738`. Los 23 DEX no contienen los cinco identificadores retirados auditados; sí contienen el motor de hechos y la asignación tipada. Es un control estático acotado, separado del recorrido runtime.

En Z Flip con fuente **1,3**, se reanudó el caso nativo con S3 150 kg × 12 y propuesta pendiente. Tras «Aplicar a S4» y «Continuar», la tarjeta mostró **155 kg**. Se marcó esfuerzo y se registró inmediatamente, sin tocar la carga: `weight`, `originalLoad` y `recordedPayloadV3.externalLoad` guardaron **155 kg**, con 10 repeticiones. Las tres series previas permanecieron idénticas y la decisión aplicada apunta exclusivamente a S4.

La hoja de cierre se expandió y desplazó al 130 %, mantuvo Guardar sesión accesible y guardó sin esfuerzo global obligatorio. Tras el arranque en frío hubo un solo log de QA con cargas 75/100/150/155 kg, nota y disciplina intactas, y ningún entrenamiento en curso. Después se retiró por completo la fixture y se restauró el estado preservado.


## Evidencia previa que sigue siendo válida

[QA Android ampliado](ANDROID_QA_20261024.md) documenta historial vacío, offline, 29 filtros de disciplina, texto 130/200 %, teclado y rotación/restauración del detalle en un AVD sintético. [Permisos y TalkBack](PERMISSIONS_TALKBACK_20261024.md) documenta el recorrido físico del lector confirmado por el usuario, la corrección de lectura al abrir y las rotaciones con solicitud pendiente, denegación única y ausencia de solicitudes repetidas.

Los cinco archivos de permisos, actividad y gráfico del último manifiesto físico coinciden exactamente por SHA-256 con este checkout. Es evidencia previa sobre esos mismos archivos, no una nueva ejecución física de TalkBack en cada APK posterior. Las cifras de las tandas anteriores se solapan y no se suman a las pruebas finales.

## Preservación y restauración

La base original Room 34 se respaldó antes de las fixtures; también se conservó el APK instalado `655f2eb4111f12266c03e9f45ab8c48169660d4cbbce230f8e5d63d808d60cc7`. La primera restauración ya compara **las 47 tablas completas, sin diferencias**, con integridad SQLite `ok`: un programa, un log, ningún entrenamiento en curso, ningún borrador, un recibo y seis registros de medios. Los 12 archivos referenciados de medios/miniaturas están presentes.

Micrófono y Bluetooth volvieron exactamente a sus concesiones y flags iniciales. TalkBack sigue apagado, AnyDesk conserva su servicio, exploración táctil 0, fuente 1.0 y orientación inicial. Se respeta la cancelación intencional de la sesión original confirmada por el usuario; no se recupera sobre esa decisión.

La restauración final conserva la copia íntegra más reciente tomada antes de repetir APRE. Respecto de la primera copia, diferían solamente dos preferencias de ajustes: objetivo calórico y recorrido visto de Nutrición. Ambas se conservaron; la copia inicial también permanece disponible. Las **47 tablas finales coinciden exactamente con ese estado más reciente**, integridad `ok`, cero fixtures o sesiones de QA, y los **12 archivos de medios conservan sus hashes**. Permisos de voz y ajustes del sistema coinciden exactamente con los originales. Se retiró el lector temporal de UI y se dejó el teléfono en Inicio.

## Validación y reproducción

| Gate | Resultado de esta continuación |
|---|---|
| Herramientas del catálogo | 218 pruebas, cero fallos/errores, exit 0 |
| Backend | 19 pruebas y 32 subpruebas, exit 0; una advertencia de TestClient |
| Compilador del catálogo `--check` | Aprobado, hash exacto conservado |
| Gate de retiro | Cero infracciones activas; controles positivos y anillas físicas aprobados |
| Android, selección ampliada | **481 pruebas en 44 clases; cero fallos, errores u omisiones; 44 XML nuevos; exit 0** |
| APK corregido e instalación | Base Debug aprobado; certificado/identidad/versión/Room comprobados; actualización `-r`, Success y arranque en frío |
| APRE físico corregido y restauración final | 155 kg × 10 inmediatos al 130 %; cierre único y 47 tablas restauradas, aprobados |

Desde `android-native`, con JDK 21, usar el wrapper con candado:

```powershell
powershell -NoProfile -File ../.opencode/scripts/run-gradle-locked.ps1 -Owner POST-RINGS-QA -Tasks "testBaseDebugUnitTest assembleBaseDebug --init-script ../tools/training-evidence/android-post-rings-regression.init.gradle --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx6g" -TimeoutSec 3600
```

Desde la raíz: `python -m unittest discover -s scripts/tests`, `python scripts/compile_exercise_catalog_v2.py --check` y `python scripts/verify_post_rings_retirement.py`. Para backend, añadir raíz y `backend` a `PYTHONPATH` y ejecutar `python -m pytest backend/tests` en el entorno con sus dependencias.

La evidencia privada de esta continuación está bajo `post-rings/qa-integral` en los artefactos de este chat. Incluye matriz/identidades de borradores, recibo de activación, fixtures, XML y capturas, respaldo/APK anterior, manifiesto de fuentes, resultados multideporte, restauración de permisos, comparación de tablas y medios. Los intentos fallidos e interrumpidos se conservan separados y no cuentan como aprobación.

## Pendiente expresamente: iOS

Compilación/XCTest en macOS, paridad runtime y QA de migraciones/restauraciones y accesibilidad iOS permanecen pendientes. No se ejecutaron ni se declaran aprobados. Los pasos siguen en [EXTERNAL_GATES.md](EXTERNAL_GATES.md).
