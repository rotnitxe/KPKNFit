# Validación privada: entrenamiento y respuesta

Base pública verificada: edc237f66d8474c3a9922296b61940e7030b7f8a.
Paquete POST-RINGS: 481310 bytes; SHA-256 c7baf4d85e097ded24e8c0f45fd676d48a3173bcffd564bf17c5990363f44161.
El bundle antiguo Rings v4 no se importa. Las ramas anteriores permanecen recuperables.

## Evidencia y límites actuales

- Room 33: 20 pruebas, cero fallos/errores/omitidas, Gradle exit 0.
  XML privados en `/workspace/private-results/post-rings/evidence/room33-checkpoint/`.
- Room 34, primera regresión integrada ejecutada: 235 pruebas, 217 pasan y 18
  fallan. No representa aprobación final. Se identificaron expectativas de calorías
  de fuerza retiradas, un recorrido sin selección de plan, una diferencia de texto
  y memoria insuficiente en Compose. XML en `evidence/room34-v3/`.
- Room 34 incorpora purga de propuestas fisiológicas pendientes sin editar
  sesiones/cursor/cargas; preserva propuestas de protocolos y originales.
  iOS avanza 22 → 23 con la misma frontera de datos.
- Gate semántico incluye main/debug, iOS, backend y recursos, controles positivos
  y anillas físicas negativas. Excepciones: importadores raw aislados, rutas 410 y
  cuatro líneas de saneamiento SQLite/encode de iOS identificadas por SHA-256.
  Un gate de patrones complementa la revisión; no la sustituye.
- Catálogo: compilador reproducible `--check`, 228 definiciones, 553 configuraciones,
  hash canónico ce1ed8f4ec0c7476e66a353e567717b86cc896fda797d33ee99bb5c788584ce7.
- Swift: revisión de código y análisis sintáctico; no hay macOS/Xcode. Las nuevas
  pruebas XCTest y la restauración SQLite requieren ejecución real en macOS.
- Dossier: acceso a fuentes científicas bloqueado por 403; las cifras aportadas
  quedan identificadas como no verificadas independientemente y no autorizan
  afirmaciones fisiológicas de producto.

## Ejecución cloud

Activar `/workspace/kpkn-env/activate.sh` (JDK 21, SDK Android). El JDK 17 permanece
para arneses portables independientes; no se reanuda el arnés de Rings retirado.
Gradle desde android-native: `--no-daemon --console=plain --max-workers=2`.
Las suites Robolectric se ejecutan con init script privado
`/workspace/kpkn-env/test-memory.gradle`: un fork concurrente, heap 3 GiB y rotación
cada cuatro clases para aislar cachés de Room/Compose. No cambia compuertas.
No editar fuentes de un checkout mientras compile; se usa un checkout privado de
edición y cherry-picks de commits propios para validación estable.

Pendiente de registrar al cerrar: última ejecución de regresión, XML/recuentos,
assembleBaseDebug sin firma de distribución y manifiesto de artefactos actualizado.

## Gates externos

macOS: abrir el proyecto Xcode, compilar y ejecutar XCTest de preservación,
comparabilidad y proyección; verificar gráfico, texto grande, offline y reinicio.
La sintaxis Swift por sí sola no prueba tipos, enlaces ni comportamiento.

Z Flip: no conectado desde cloud; ninguna instalación ni lectura de datos personales.
Solo `com.example.kpkn.dbg` KPKN Dev, bajo `phone_run` y `phone.lock`, un dueño.
Verificar certificado, compatibilidad Room y respaldo recuperable antes de `-r`.
No borrar datos ni desinstalar; no tocar `com.example.kpkn`. Comprobar migración,
wizard reanudado sin calibración, sesión/voz/cierre, historial multideporte, 130 %
de texto, offline, rotación/reinicio y vuelta a Home. Guardar capturas/logs sin datos
personales. No afirmar QA físico aprobado antes de ejecutarlo.

## Segundo checkpoint integrado

La ejecución `post-rings-final-regression-v2.log` termina con 505 pruebas, 503
aprobadas, 2 fallos y cero errores/omitidas (50 XML). Incluye todos los tests WIP
multideporte/PerformanceLab seleccionados, migraciones, restauración, APRE/TM,
voz/cancelación y gráfico con restauración de estado. Los dos fallos corresponden
a inactividad de Compose del nuevo diálogo en el modo gráfico legacy de Robolectric;
se alinea su configuración al modo NATIVE y densidad real usado por las suites UI.
Los XML completos se conservan en `evidence/final-regression-v2/`.

Backend final: 19 pruebas y 32 subpruebas aprobadas; endpoints retirados 410 sin cálculo.
No hay publicación en GitHub ni acceso al dispositivo.

Cambios siguientes requieren ejecución nueva: confirmación de propuestas AUTO
heredadas, rechazo de identidades `unknown@version`, cierre/voz/contexto originales
en iOS y fixture poblada de propuestas mixtas. No se atribuye el checkpoint anterior
a esos cambios posteriores.
