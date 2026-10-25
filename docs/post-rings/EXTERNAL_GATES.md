# Gates que necesitan otro host o equipo

Estado vigente: [QA Android local completado](ANDROID_CLOSURE_20261024.md). Los pasos macOS/iOS siguen pendientes por instrucción del usuario. La sección Z Flip se conserva como protocolo de reproducción del gate ya ejecutado.

Los pasos macOS no se ejecutan desde este host Windows.
La entrega privada conserva código, fixtures, APK debug y evidencia cloud.

## macOS / iOS

1. Restaurar el bundle privado sobre la base indicada en su manifiesto y comprobar
   la rama/HEAD y el árbol. No importar el bundle anterior Rings v4.
2. Desde la raíz, ejecutar `bash tools/training-evidence/verify-swift.sh`.
   Usa `docs/research/training-evidence/fixtures/insights-v1.json`; compara IDs,
   unidades, contexto, reloj, deduplicación y mediana. No sustituye Xcode.
3. Ejecutar `xcodebuild -list -project ios-native/KPKNFit/KPKNFit.xcodeproj` y
   `xcrun simctl list devices available`. Elegir un simulador disponible y ejecutar
   `xcodebuild test -project ios-native/KPKNFit/KPKNFit.xcodeproj -scheme KPKNFit -destination 'platform=iOS Simulator,id=UUID_DISPONIBLE'`.
   Sustituir el UUID por uno real. Conservar salida, exit code y xcresult; no
   interpretar el análisis sintáctico Linux como aprobación de tipos/enlaces.
4. En un contenedor de pruebas sintético, verificar migración 19→23: sueño,
   bienestar, molestias, notas y sus identidades/fechas/escalas sobreviven; tablas,
   claves y propuestas fisiológicas se purgan. Restaurar dos veces una copia
   antigua, provocar fallo/cancelación y comprobar rollback/conexión DAO activa.
   No usar el contenedor personal del usuario.
5. Ejecutar los XCTest de entrenamiento registrado y los contratos existentes de
   catálogo/transporte/nutrición. Probar Home, diálogo opcional, cierre e historial
   sin datos y con registros de fuerza, resistencia, funcional, combate y saltos.
   Verificar cero explícito, ausencia, unidad desconocida, documentos futuros o
   incompatibles, offline, texto grande y reinicio. Guardar capturas sintéticas.

## QA físico Z Flip

1. Disponer del helper local autorizado `phone_run` y su `phone.lock`; no están
   incluidos en este checkout. Consultar su ayuda/protocolo vigente y adquirir el
   único lock respetando guardas de salud, plegado, foco y prioridad del usuario.
   Si faltan, detener este gate; no sustituirlos por ADB directo.
2. Verificar el SHA del APK contra `PRIVATE_RESULTS.json`. Inspeccionar APK con
   `aapt dump badging` y `apksigner verify --print-certs`: paquete
   `com.example.kpkn.dbg`, aplicación y launcher «KPKN Dev». La firma cloud debug
   puede diferir de la instalada: comparar con KPKN Dev mediante el helper. Ante
   incompatibilidad, registrar bloqueo y conservar datos; no crear otro paquete,
   desinstalar, borrar datos ni tocar `com.example.kpkn`.
3. Comprobar versión Room y respaldo recuperable de KPKN Dev. La migración final
   Android termina en 34; usar fixtures sintéticos para validar primero cualquier
   trayectoria pendiente. Solo después, actualizar con `-r` a través del helper.
4. Con datos de prueba, reanudar un borrador en cada etapa retirada y los modos
   parciales. Conservar Perfil/Entrenamiento/Nutrición, identidad, commit y recibo;
   activar una sola vez sin preview ni etapa RINGS. La bienvenida dice «Ejemplo»,
   muestra 5 km/30 min/29 min y no presenta esos valores como datos del usuario.
5. Abrir sesión, continuar sin contestar, declarar opcionalmente disciplina/notas,
   registrar series por UI y voz, confirmar APRE y registrar inmediatamente S4.
   Verificar cancelación, cierre único, notas/medios y ausencia de cambios de carga
   sin confirmación. Reabrir una sesión en curso tras cerrar el proceso.
6. En Home/historial usar ventanas 1/4/12 semanas (defecto4), filtrar disciplina,
   abrir un punto y verificar fuente, unidad, método/protocolo y contexto.
   No deben agregarse unidades/contextos incompatibles ni aparecer lecturas futuras.
   Probar familias de fuerza/resistencia/funcional/combate-equipo/sprint-salto y
   fallback descriptivo en el resto de disciplinas sin sensores inventados.
7. Repetir a 130 % de texto, TalkBack, offline, rotación y plegado/desplegado.
   Comprobar acciones visibles/accesibles, notas tras reinicio y vuelta a Home.
   Conservar capturas y logs sin datos personales, resultado por caso y bloqueos;
   liberar el lock según el helper. No afirmar este gate aprobado antes de hacerlo.

## Fuentes científicas

PubMed/PMC/Crossref/Europe PMC devolvieron denegación de proxy en este host. Revisar
las fuentes primarias del dossier desde un acceso autorizado, comprobar DOI/PMID,
población, tamaño, efectos, incertidumbre y limitaciones. Hasta entonces las
referencias aportadas siguen rotuladas como no verificadas independientemente.
No requieren ni autorizan un predictor nuevo ni promesas de recuperación/lesión.
