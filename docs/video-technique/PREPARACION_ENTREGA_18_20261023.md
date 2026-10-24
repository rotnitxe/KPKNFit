# Preparación de entrega Pre Release 18 — 2026-10-23

## Orden vigente · 24-oct-2026

El usuario pospone expresamente otro APK final enG: hasta pedirlo. No firmar, recompilar por entrega, copiar artefactos ni hacer FF de master ahora. El APK52 existente no se modificó. La comparación de fuentes públicas (evidence/RELEASE18_FINAL_SOURCE_EQUIVALENCE_20261023.json) acredita main/release idénticos al oficial firmado5ffdff2559; los cambios nuevos son exclusivos de debug/KPKNDev. QA funcional original y privacidad acotada: véase el estado vigente del informeQA. La preparación histórica siguiente queda conservada para una futura petición, no autoriza su ejecución automática.

**Estado vigente · 23-oct noche Santiago:** el APK conjunto oficial Pre Release18/code52/Room30/motor10 ya está en G:/Mi unidad, SHA1e058061be132316f850f032b66a9507f3c8ff77612e3e550fe783ecd055ea12, firma oficial verificada. El usuario ordenó continuar los pendientes originales antes de anexos y mantener una única KPKN Dev aparte de la personal. Nuevo QA R8 code52/packagecom.example.kpkn.dbg/etiquetaKPKNDev fue construido, atestado, instalado y arrancado sincrash; SHA d68ce1f36133ddd7df6859249e183742db73964475096c2e38c39af33b4650ea. El incidente histórico30→29 no describe el entorno actual: la unión usaRoom30, sin borrar DB personal. El cierre físico conjunto está en curso (véase QA_FASE5_RESULTADOS_20261023.md). No declarar F5/F6 cerradas por build. La comparación de fuentes demuestra que desde el APK oficial52 sólo cambió la identidad/firma debug y sus recursos exclusivos; main/release permanecen idénticos. Conservar ese APK oficial firmado evita otra compilación sin cambios de producto (recibo RELEASE18_FINAL_SOURCE_EQUIVALENCE_20261023.json). Una revisión con futuros cambios de producto sí superará el código52; nombrePreRelease18/packagecom.example.kpkn/Room30/motor10 y certificado oficial9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1. Sólo firmar por C:/kw/tools/signed_release.py con opción pública deheap8g ya revisada, cola compartida/worker1. Copiar APK+LEEME+SHA directamente aG:/Mi unidad del Explorador; no complementoDrive. Preservar el APK52 anterior ya respaldado y todo WIP de master. No más mensajes a otros chats sin nueva autorización específica. ≈m/s y1RMbeta continúan sin referencia física.

Las comprobaciones siguientes conservan la preparación histórica del checkpoint51/Room29; sus cifras no sustituyen el estado vigente anterior.
## Resultado de compatibilidad

La búsqueda acotada a nombres de APK KPKN/base en la raíz documentada `G:\Mi unidad\` encontró el APK firmado del 20-oct y Pre Release 16. No se abrieron LEEME ni otros archivos y no se recorrieron subcarpetas. Las comprobaciones públicas con `aapt2 dump badging` y `apksigner verify --print-certs` dieron:

| Artefacto | SHA-256 del APK | Paquete / versión | Certificado SHA-256 | Verificación |
|---|---|---|---|---|
| `KPKNFit-release-FIRMADO-20261020-c0ca7d72.apk` | `3dfb1087b42460d2a616c614f4fe23958ecb8b44b4671cfa5f415104be22d3fb` | `com.example.kpkn`, código 50, `KPKN Pre Release 17` | `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1` | `apksigner` exit 0; firma V3 |
| `KPKN Pre Release 16.apk` | `52ceba7fcee04c4166cc8211d226300d2251dfa71899aee6542b959491804ac7` | `com.example.kpkn`, código 49, `KPKN Pre Release 16` | `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1` | `apksigner` exit 0; firma V3 |

El base del 20-oct es el APK previo identificado más reciente en esa raíz. Sus metadatos confirman que usa el mismo paquete y el código 50; ambos APK históricos coinciden con el pin de certificado público consignado en la nota de firma. El historial no mostró un archivo cuyo nombre diga Pre Release 17, pero el APK del 20-oct sí lleva `versionName` 17. Los APK QA/debug `.dbg` citados en `EJECUCION_RELEVO_20261022.md` siguen siendo artefactos distintos y no se usan como referencia de firma.

La fuente Android actual declara `applicationId = "com.example.kpkn"`, `versionCode = 51` y `versionName = "KPKN Pre Release 17"` en `android-native/app/build.gradle.kts`. El paquete coincide y el código de checkpoint 51 supera el 50 del base firmado del 20-oct. Room declara v29 en `KpknDatabase.kt` y existe el esquema exportado `app/schemas/com.example.kpkn.data.db.KpknDatabase/29.json`. El código/nombre observados son el checkpoint fijado mientras termina la extracción; no se modifican aquí. La versión final se confirmará después de los gates originales.

Los metadatos históricos reducen la incertidumbre de actualización: el base de 20-oct es `com.example.kpkn`, código 50, con certificado público SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`. Para cerrar la comprobación del APK final todavía hay que confirmar `versionCode > 50` y que `apksigner --print-certs` muestre ese mismo certificado. La fuente actual usa código 51, pero no se afirma la compatibilidad de un APK futuro hasta verificar su artefacto firmado. No se debe inferirla a partir de los APK `.dbg`.

## Invocación prevista

`C:\kw\tools\signed_release.py` documenta dos argumentos posicionales: directorio `android-native` en ruta POSIX y archivo de log. Detecta `productFlavors` y el flavor `base`, por lo que escoge `:app:assembleBaseRelease` y delega la ejecución a `C:\kw\tools\gradle_slot.sh`. No acepta `--help`. No copia ni renombra el APK ni escribe el LEEME.

Tras completar los gates y congelar la versión de entrega, la forma exacta prevista para este worktree es:

```powershell
python C:\kw\tools\signed_release.py /c/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native C:\kw\logs\signed-prerelease18-20261023.log
```

La salida Android esperada es `android-native/app/build/outputs/apk/base/release/app-base-release.apk`. La copia a `G:\Mi unidad\`, el archivo `KPKN Pre Release 18 LEEME.txt` y el SHA-256 del APK se hacen después del build, según `PLAN_RELEVO.md`. El script toma la configuración de firma del checkout principal; no se leyeron propiedades, credenciales, keystores ni claves.

## Orden vigente y límites

`PLAN_RELEVO.md` y `EJECUCION_RELEVO_20261022.md` prevalecen sobre el orden cronológico de la lista pendiente de `RELEVO.md`. La aceptación competitiva es posentrega y no bloquea Pre Release 18; los gates originales de Fases 2–6 siguen vigentes. MM-Fit y la integración M5 ya terminaron; consultar en EJECUCION el estado actual de Android/R8, QA original y privacidad dinámica. No procede firmar antes de terminar esos gates.

Esta preparación no cambió versión ni archivos de la app, y no tocó el `RELEVO.md` untracked. No se ejecutó Gradle, teléfono ni firma; `aapt2` y `apksigner` solo leyeron metadatos públicos de los dos APK históricos señalados. La compatibilidad del APK final queda pendiente hasta contrastar su artefacto firmado con el base del 20-oct.



## BORRADOR — LEEME de entrega Pre Release 18

### Texto propuesto (pendiente del cierre de QA y del artefacto)

Esta versión integra el motor de análisis de técnica 10 sin cambiar el esquema de Room 29. Las métricas de
velocidad en m/s se muestran como aproximadas (≈); el 1RM estimado por velocidad sigue en beta.

En el control MediaPipe de G1 hubo salida en 88/89 casos; la cobertura se mantuvo con la guarda elegida. Hubo 67/88 FULL
exactas (frente a 60/88), 50/58 exactas ALTA, 11/12 exactas MEDIA y 70/89 ofertas ALTA/MEDIA. En el control histórico,
las FULL exactas se mantuvieron en 50/63. Al combinar de forma descriptiva las 405 filas tune y 89 del control ciego MM-Fit,
hubo 337/489 FULL exactas, 252/293 ALTA exactas, 53/67 MEDIA exactas y 360/494 ofertas ALTA/MEDIA. Este agregado no es
un ensayo independiente de 494 casos.

Desglose descriptivo por ejercicio, tune más control ciego G1:

| Perfil | FULL exactas / salidas |
|---|---:|
| CURL | 38/59 |
| LUNGE | 49/62 |
| OHP | 57/60 |
| PUSH_UP | 44/65 |
| RAISE | 43/56 |
| ROW | 48/63 |
| SQUAT | 56/62 |
| TRICEPS | 2/62 |

Las metas internas 98/90 siguen sin alcanzarse, especialmente en tríceps. Estos resultados no acreditan paridad con otras
aplicaciones. En el baseline de referencia MediaPipe GPU/Full del motor 9 (616 clips), el tiempo superó el objetivo de tiempo real: ratio análisis/vídeo mediano
1,5265 y p95 3,2389.

El análisis se procesa en el teléfono. La exportación ZIP de medios es manual y se inicia desde la app; incluye el árbol completo de medios y sus sidecars. La exportación individual desde el visor es una acción distinta.

### Pendiente antes de aprobar o publicar este texto

Preparación Git revisada, todavía sin aplicar: copia byte-exacta de los39WIP tracked en
`C:/kw/vt-mmfit/master-wip-backup-preflight-20261023T1515Z`, manifiesto SHA-256
`aa855fe510eccd87b44f7a52244d361834209a6e89c71568e77f07dcea82b40c`. A las15:15 el índice principal estaba limpio,
master seguía en8ac57c9d42 y había un único solape, `WorkoutFinishController.kt`, con propuesta3-vías externa ya revisada;
cero colisiones de untracked con el destino. Revalidar todos los pins justo antes del FF, después de los gates y del
commit final de versión: si cambió cualquier WIP, solape o destino, detenerse y rehacer la propuesta. El procedimiento
revisado conserva los38WIP no solapados, coloca temporalmente solo el solape en su blob base, ejecuta FF al SHA exacto y
reaplica allí la unión revisada con el hunk de calendario. No se usa stash/reset/restore ni se stagea WIP ajeno. Verificar
HEAD/índice/hashes después y conservar el backup original; el avance de master no se revierte con `--ff-only`.

- El QA físico en el Z Flip sigue parcial y no se declara aprobado. El APK QA R8 de motor 10/FPS,
  SHA-256 `0afd5a5af80d9249936a5c8222a626b85e14ee8a76d858381d760c4a36b21fc4`, se instaló sin limpiar los datos.
  Al abrir análisis previos se observaron dos `RECOMPUTE` desde sidecars, sin decodificar ni volver a procesar pose;
  reabrir uno no creó duplicados. Grabaciones con preferencia 60 ON y OFF dieron ~30,0101 fps efectivos. Con ON,
  Camera3 ya mostraba `[30 30]` antes de REC y los rangos AE normales anunciados llegaban a 30; esto respalda fallback en esta
  configuración, no 60 fps. El cierre automático al minuto y los ajustes 45/40/35/Otro=42 cm con restauración a 45 cm
  sí se observaron. El tercer TUNE CURL mostró +1 parcial estable y barra tramada; la confianza propia ALTA se infiere
  del formato frente a MEDIA global. No apareció zona ni porcentaje de recorrido y se revisa la causa. La selección
  manual desde Photo Picker del hub fue confirmada por el usuario; falta observar su resultado y completar el QA delta
  cuando la guarda natural permita usar el teléfono. BAJA y MEDIA propias tienen pruebas y renders previos, pero no
  una nueva observación física; no se consideran aprobadas por esta toma. Los demás flujos pueden avanzar por rutas permitidas. Sin referencia externa se conserva
  `≈` y `1RM beta`.
- La ventana de privacidad dinámica del APK QA instalado encontró sano el acumulador UID antes y después de una pasada FULL y sin clave para la app. Esto permite inferir cero bytes registrados en ese acumulador durante esa ventana, con límite OEM; no es un contador explícito ni una garantía universal. `trafficcontroller --short` solo devolvió el encabezado y no mostró estado de programa. La lectura no corresponde al artefacto firmado final.
- El artefacto final debe confirmar versionName Pre Release 18, versionCode mayor que 50 y el certificado público esperado
  9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1. Nombre definitivo, SHA-256 del APK y fecha de
  archivo quedan PENDIENTES del artefacto firmado. Ejecutar signed_release.py solo al final de los gates; no hay una
  promesa de hora de entrega.
- El anexo competitivo, la voz y la revisión de Metric son posentrega.

### Memoria de la firma · propuesta revisada, aún sin aplicar ni firmar

La cola añade Xmx4g cuando el firmador no pasa jvmargs, aunque gradle.properties pida8g. Para evitar repetir el OOM ya
conocido, se preparó fuera del repo una opción pública y opcional de memoria para el mismo signed_release.py; no cambia
su manejo de claves ni su invocación posicional. Base SHA54f63fedc3f0bd15cedfa3b76f57772b0fe43a9c5d4a0bd3af25e62fdb481179.
Parche revisado C:/kw/vt-mmfit/signed-release-heap-20261023/signed_release_heap_option_v2.patch,
SHA5112e597ce7ae77912f5898b421725dc3e43b9c65d4343957317985c4df6c5a6; candidata signed_release_v2.py.proposed,
SHAe26c782f2ba88cd5403ddb92a7ab34a60888443ed74244f3621cbd7058a26737. Validación AST aislada aprobada, sin ejecutar main:
opción ausente conserva argv exacto anterior; valores1..8 aceptados y entradas inválidas rechazadas antes de cualquier
lectura de propiedades; worker1 y parallelfalse son argumentos separados. El helper real sigue intacto.

Después de los gates y justo antes de la firma, revalidar base/candidata y ausencia de otra firma activa, preservar el
script base y aplicar solo este cambio opcional revisado. Para esta entrega usar KPKN_SIGNED_RELEASE_HEAP_GB=8 en el
proceso del firmador, manteniendo cola compartida, una Gradle propia, worker1 y máximo8g. No cambiar prioridad/slots ni
leer credenciales. La firma continúa exclusivamente por C:/kw/tools/signed_release.py; no ejecutar la candidata externa.
