# Preparación de entrega Pre Release 18 — 2026-10-23

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
aplicaciones. La duración de análisis GPU sigue por encima del objetivo de tiempo real: ratio análisis/vídeo mediano
1,5265 y p95 3,2389.

### Pendiente antes de aprobar o publicar este texto

- El QA de ejecución en el Z Flip y el QA R8 del motor 10 siguen pendientes. Una grabación oscura con preferencia60 activada produjo30fps reales; el fallo del respaldo sigue en depuración. No afirmar60fps ni UI aprobada antes de
  completar esas verificaciones.
- El artefacto final debe confirmar versionName Pre Release 18, versionCode mayor que 50 y el certificado público esperado
  9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1. Nombre definitivo, SHA-256 del APK y fecha de
  archivo quedan PENDIENTES del artefacto firmado. Ejecutar signed_release.py solo al final de los gates; no hay una
  promesa de hora de entrega.
- El anexo competitivo, la voz y la revisión de Metric son posentrega.
