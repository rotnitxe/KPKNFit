# Reanudación local de la ola MFP · 24-oct-2026

## Instrucción vigente y base

El usuario pidió implementar directamente en local, con el método más efectivo y rápido, sin push. Sustituye Cloud y delegación. Un solo agente; no contactar otros chats ni fusionar master. El checkout principal conserva su trabajo ajeno y no se modifica.

Worktrees limpios al comenzar: `mfp`/`feat/macrofactor-parity` en `c488bfbe8`, `mfpn`/`mfp/n` en `00bde8b50`, `mfpd`/`mfp/d` en `c0e8f77ab`, `mfpt5`/`mfp/t5a` en `7b373aa22`, `mfph`/`mfp/h` en `0fe99e548`. La RAM inicial era 1,84 GB libres; el usuario liberó memoria y las guardas de la cola se mantuvieron intactas.

## Resultado comprobado

| Bloque | Evidencia local | Estado |
|---|---|---|
| N3/N4 sobre `00bde8b50` | 257 pruebas Android / 22 clases, 0 fallos/errores/omisiones; 38 PNG | Validación focal verde; revisión visual parcial, sin QA física |
| T4 | Commit `3b6c5153e`; 34 pruebas JVM y 58 Android verdes | Corregida la pérdida de repeticiones aceptadas al regenerar semana; prueba bilateral y por lado, JSON e historial |
| P2-Na | Commits `f684146b9` y harness separado `b2abea01d`; 475 pruebas Android / 64 clases previas al arreglo final y 44 JVM / 4 clases posteriores | Recetas, favoritos, hora local, preferencias JSON y exportación por fila implementados. **Abierto:** el arreglo posterior de raciones y editor decimal aún requiere comprobación Android |
| T5a | Commit `770d9bc4c`; 1.527 JVM/140 clases y 398 Android/34 clases verdes, sin omisiones; 20 PNG | Regresión amplia verde; rótulo truncado al 130 % corregido con prueba de texto. Revisión visual parcial, QA física pendiente |
| P2-Nb | `P2-Nb_FUENTES.md` documenta fuentes, alcance de UL y límites de la revisión | Preparación, sin implementación |

## Logs y hojas

- N3/N4: `C:/kw/logs/mfp-N4-local-20261024-1.log`; `C:/kw/shots/mfp/N4-local-20261024`.
- T4: `C:/kw/logs/mfp-T4-local-20261024-jvm-3.log`, `mfp-T4-local-20261024-android-2.log`. Primera tanda Android sin pruebas por heap de 4 GB; segunda verde con 6 GB y guarda de 7 GB libres.
- P2-Na: `C:/kw/logs/mfp-P2Na-local-20261024-android-3.log`; `C:/kw/shots/mfp/P2Na-local-20261024-final3`, 54 PNG. Regresión final JVM: `C:/kw/logs/kpknjvm-mfpn-20261024-024525.log`. El recibo detallado está en `C:/kw/mfpn/docs/macrofactor-parity/cambios/P2-Na.md`.
- T5a: `C:/kw/logs/mfp-T5a-local-20261024-jvm-1.log`, `mfp-T5a-local-20261024-android-2.log`; 20 PNG finales en `C:/kw/shots/mfp/T5a-local-20261024-final`. Dos tandas Android, sin necesidad de repetir.

## Siguiente paso y límites

Se pidió autorización para una cuarta tanda Android focalizada de P2-Na: el encargo limita a tres por paquete y ya se consumieron. Está preparada en `C:/kw/logs/mfp-P2Na-local-20261024-extra.sh`, **sin ejecutar**. No eludir este límite etiquetando la comprobación como otro paquete. La nueva regresión de recetas pasa por la tarjeta real, guarda/reabre y conserva raciones fraccionarias, gramos, nutrientes e incertidumbre; la prueba de interacción y el render del editor decimal siguen pendientes de Android.

Después: completar P2-Nb y los restantes paquetes del plan, integración aislada, regresiones completas y QA física. La ola no está terminada. No se ha construido/instalado un APK nuevo ni medido p95 en dispositivo.

## Guardas

Gradle exclusivamente por `gradle_slot.sh` / `kpkn_jvm.sh`; una compilación propia a la vez. Room congelado, esquema intacto y sin constantes prohibidas según verificación local. Sin tocar importador, catálogo S11/H-i, `.gitignore` ni datos del usuario.

En el Z Flip rige la orden AGENTS posterior del 23-oct: única **KPKN Dev `com.example.kpkn.dbg`**, `baseDebug`, acceso por `phone_run` y `phone.lock`, comprobar firma y compatibilidad de datos antes de actualizar con `-r`. Sustituye la antigua `.mfp`. No se han usado adb, ejecutores de teléfono, instalaciones ni limpieza de datos durante esta reanudación.
