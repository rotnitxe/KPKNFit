# Continuacion de la ola MFP en Codex Cloud - 22-oct-2026

## Instruccion vigente del usuario

> Si, prepara el traslado y te autorizo a subir las ramas MFP con los cambios pendientes para continuar en Codex Cloud. No fusiones master ni vuelvas a ejecutar Gradle en mi equipo.

Esta instruccion posterior reemplaza la prohibicion de push SOLO para el traslado inicial de las cinco ramas siguientes. Se conservan las demas restricciones. No se ha fusionado ni modificado master durante la preparacion. No volver a ejecutar Gradle/Java de tests en Windows, WSL del PC, por SSH al PC ni mediante sus herramientas locales. La implementacion continua directamente por Codex; el usuario delego la eleccion del metodo mas efectivo sin despilfarrar tokens. No hace falta lanzar subagentes.

## Punto de partida verificable

Repositorio: `https://github.com/rotnitxe/KPKNFit`. Iniciar la tarea sobre `feat/macrofactor-parity`, nunca sobre la rama por defecto. Esta tabla fija las puntas de codigo antes del commit de documentacion y herramientas de transporte (la punta publicada de la base incluira tambien dicho commit).

| Rama | Commit de codigo | Contenido |
|---|---|---|
| `feat/macrofactor-parity` | `216bcb16ee475cdc3349603b8d1fce9695e5fc69` | T4 y base de la ola |
| `mfp/n` | `00bde8b504f0a37a059bcdf337199149bcde7687` | N3 y N4 |
| `mfp/d` | `c0e8f77ab76fb7e50dd0ab2c1528cd4e9464bea2` | D1 y D1-b |
| `mfp/t5a` | `7b373aa224d6ef11049e7971fd33e96ebd83c2e4` | Panel de volumen |
| `mfp/h` | `0fe99e5489ca2c429b73025187a45dfc69b7259e` | Habitos |

Las ocho modificaciones pendientes quedaron conservadas en dos commits: T4 `216bcb16e` (cuatro archivos de codigo/tests) y N4 `00bde8b50` (dos tests); los otros dos archivos pendientes eran README y cambios/T4, incluidos en el commit de traslado. D1, T5a y habitos no recibieron cambios de producto en esta preparacion. El manifiesto `cloud/manifest.json` conserva los hashes y la evidencia XML anterior; no contiene credenciales, bases personales, APK ni caches.

## Lecturas y prioridad

1. Este archivo y `CLOUD_PROMPT.txt` (instruccion actual, mas reciente que el relevo anterior).
2. `RELEVO.md` como historia; su estado de colas/cuotas y su asignacion a agentes Claude estan obsoletos.
3. `README.md`, `PLAN_COMPLETO.md`, `briefs/COMUN.md`, `briefs/KIT_AGENTE.md` y el brief del paquete.
4. `ROJOS_BASE.txt`, `data/DRI_ADULTOS.csv` y `data/DRI_ADULTOS_LEEME.md`.
5. `artifacts/macrofactor-reverse-engineering-20261020/INFORME_FINAL.md` seccion 6; no leer coeficientes ni evidencia prohibida para reimplementarlos.
6. `tools/macrofactor-cloud/README.md` para ejecutar remotamente. La cola `C:/kw/tools` y sus guias solo describen el PC; en Cloud se usan los wrappers portables incluidos. No intentar recrear esos caminos en Windows.

## Estado de validacion al salir del PC

- T4: `mfp-T4-1.log` termino con desaparicion de Gradle. Una corrida posterior T4-2 ejecuto 100 pruebas con 1 fallo nuevo: `NativeWorkoutProgressionRuntimeTest.finalizeWorkoutPersistsNativeProposalAlongsideTheRealCompletedLogs` (esperaba una propuesta y recibia cero). La correccion explicita el inventario de la prueba; conserva su asercion de persistencia. La revision encontro ademas que el texto proponia repeticiones nuevas pero aceptar solo guardaba el peso: ahora `NativeProgressionProposal.targetReps: Int? = null` conserva el par en JSON y lo aplica en series futuras manteniendo el rango del plan. Incluye compatibilidad con JSON antiguo, rechazo, idempotencia y preservacion de sesiones realizadas. Este ultimo cambio NO se ha compilado ni probado: es la primera prioridad cloud.
- N3/N4: N4-1 fallo al compilar tests; N4-2 quedo en cola sin ejecutar Android. N4-3 ejecuto 358 pruebas con 2 fallos nuevos: el fallo Room simulado cerrando una base no provocaba el error esperado y el test de la etiqueta rapida buscaba solo en el arbol semantico agrupado. Se corrigieron con un trigger real de fallo de insercion y comprobacion de semantica/contenido accesible. Commit `00bde8b50`, todavia sin ejecucion. N3/N4 deben volver a validar tambien permiso de camara al reanudar y micronutrientes de alimento propio.
- Las corridas N4-4 y JVM T4 del 22-oct fueron canceladas por orden del usuario para liberar recursos. Se detuvieron sus lanzadores y se retiraron sus propios turnos/candados; no hay resultado nuevo que reutilizar. No relanzarlas en el PC.
- D1/D1-b, T5a y habitos conservan la evidencia del README. Falta la regresion amplia de T5a, la validacion integrada y toda QA pendiente de dispositivo. No imputar a las nuevas puntas los XML antiguos.

## Trabajo en el entorno remoto

Preparar JDK 17, SDK Android 36, Python 3, Git, Bash y acceso a Gradle/Maven/Google. La version del wrapper es 9.4.1; AGP 9.2.1 y Kotlin 2.2.10 salen del repositorio. No importar keystores, tokens MCP ni archivos .env del PC. Usar debug sin credenciales privadas. Verificar los requisitos de herramientas adicionales declaradas en el proyecto antes de compilar.

Hacer fetch de las cinco ramas publicadas con refspecs explicitos y comprobar sus hashes. Crear worktrees SOLO dentro del workspace cloud. Las herramientas de la base aceptan como argumento otra ruta remota: sirven tambien para `mfp/n` y `mfp/t5a`. Mantener una sola compilacion activa para no desperdiciar memoria, sin tocar procesos ajenos. Antes de Android, probar el dominio con el arnes JVM; en Android pedir clases concretas. Los stubs/exclusiones del arnes estan publicados y no sustituyen las pruebas nativas. Todas las hojas con doble guarda y `KPKN_RENDER_DIR` definido.

Orden: cerrar T4 y N3/N4; regresion amplia T5a (dominio training en JVM y programdetail/profile/progress en Android); actualizar docs; P2-Na; P2-Nb (240 filas DRI, UL solo de ingesta total, sodio 2300 como techo); P2-D CSV y documentacion de huerfanos; P2-E galeria/OCR experimental con confirmacion; P2-F solo cuando pueda medirse en el dispositivo; T5b; T6; P2-Ta; P2-Tb. Seguir el resto de fases y criterios de PLAN_COMPLETO.md. P2-F y QA de telefono se registran como pendientes verificables, no se inventan datos cloud para cerrarlos.

El usuario delego y se eligio continuar T6/P2-Ta/P2-Tb con cambios minimos y aditivos sobre MFP actual. En la comprobacion del 22-oct `feat/screens-v2` aun no era ancestro de master local; revalidar en remoto antes de esos paquetes. No fusionar master ni implementar una integracion con otra ola sin revisar el estado actual. La integracion entre ramas MFP puede prepararse en una rama propia `codex/mfp-cloud-integration`, dentro de Cloud, respetando los conflictos conocidos. La fusion final a master la decide el usuario.

Room congelado: 30 calibracion, 31 nutricion, siguiente libre 32 solo con permiso. No cambiar entidades/migraciones/esquemas para los paquetes pendientes. Al integrar tras screens-v2: 30->31 nutricion, KSP genera 31.json y actualizar los tres tests de cadena indicados en README. Avisar antes a la sesion VT si no se mantiene el orden; no enviar mensajes a otras sesiones sin autorizacion del humano. Nunca editar a mano schemas. No tocar .gitignore raiz, catalogo S11/H-i, algoritmos AUGE ni borrar datos o worktrees ajenos. Clean-room y maximo 3 iteraciones por problema siguen vigentes.

## Limite de lo realizable en Cloud

No hay acceso automatico al Flip5, su camara, archivos personales ni a las capturas y logs locales fuera de Git. No usar conexiones al PC para eludir esa ausencia. Generar nuevas hojas de render en Cloud y adjuntar pruebas. La aceptacion a dos fotogramas, linterna/vibracion, migraciones sobre datos del usuario, QA de Decline/Postpone, busqueda p95 real y APK instalada en el telefono requieren una sesion local posterior autorizada. No afirmar que la ola esta al 100 por ciento mientras falten esos criterios.

Al terminar: entregar commits, comandos remotos, XML contados, fallos heredados/nuevos comparados con las 51 entradas de ROJOS_BASE, PNG revisados a 100/130, cambios visibles y pendientes reales. No fusionar master, no publicar releases y no desinstalar ni limpiar el PC desde Cloud.
