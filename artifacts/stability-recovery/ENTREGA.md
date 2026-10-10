# Recuperación y cierre del cliente Play

Este parche integra las correcciones recomendadas sobre la base `11aff1778`, conservando Health Connect retirado, GPS y las implementaciones paralelas anteriores.

El diario de restauración ahora se lee mediante `AtomicFile`, limita su estado a 32 bytes y conserva la copia de recuperación ante un estado no reconocido. Al cerrar el perfil se retira el cliente Play, se termina la conexión y se impide que callbacks tardíos resuelvan derechos o publiquen ofertas para otro propietario.

- **34 pruebas JVM / 10 suites aprobadas**, cero fallos y omisiones. Los ensayos de diez fallos IO por fase se ejecutaron en API26/API34.
- **17 fases nativas aprobadas en API36**, con 21 terminaciones reales y lectura durable desde otro PID. Se comprobaron base, avatar, preferencias, reapertura y ZIP de entrada intacto. Las terminaciones intencionales no cuentan como tests aprobados.
- **SQLITE_FULL real aprobado**: cuota de 162 páginas y cero páginas libres; original conservado después del fallo. El filesystem no se llenó físicamente.
- **Lint: 0 errores,614 advertencias,39 avisos menores; R8 aprobado**. No se generó ni instaló una release firmada.
- **120 búsquedas Room/FTS aprobadas**: p95 92,5362 ms, presupuesto 150 ms. El intento anterior de p95 322,4865 ms se conserva; la carga concurrente hace que atribuir la diferencia sólo al parche o sólo al host sea una inferencia. La repetición empezó tras nuestros builds, con otro build u1 registrado.
- APK instalado y `MainActivity` visible; captura, árbol UI y logs conservados.

`DELIVERY.json` enlaza las fuentes, hashes, reproducciones y resultados. Los errores de preparación del fixture permanecen identificados y no se atribuyen a producción. La prueba de cuota final modifica sólo ese método del fixture; los hashes de test APK anteriores se conservan por escenario.

La integración local de este parche no cierra el plan A-J ni acredita publicación en Google. Siguen pendientes la suite JVM amplia, la matriz completa de productores/dispositivos, SAF y los servicios reales Firebase/Play. No hubo push ni despliegue.
