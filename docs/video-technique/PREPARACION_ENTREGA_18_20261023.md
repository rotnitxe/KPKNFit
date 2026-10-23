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

`PLAN_RELEVO.md` y `EJECUCION_RELEVO_20261022.md` prevalecen sobre el orden cronológico de la lista pendiente de `RELEVO.md`. La aceptación competitiva es posentrega y no bloquea Pre Release 18; los gates originales de Fases 2–6 siguen vigentes. Consultar el contador y los gates pendientes en el Estado vigente de EJECUCION; el snapshot de esta preparación aún tiene MM-Fit activo, QA original, privacidad dinámica y cierre de cifras pendientes. No procede firmar antes de terminar esos gates.

Esta preparación no cambió versión ni archivos de la app, y no tocó el `RELEVO.md` untracked. No se ejecutó Gradle, teléfono ni firma; `aapt2` y `apksigner` solo leyeron metadatos públicos de los dos APK históricos señalados. La compatibilidad del APK final queda pendiente hasta contrastar su artefacto firmado con el base del 20-oct.
