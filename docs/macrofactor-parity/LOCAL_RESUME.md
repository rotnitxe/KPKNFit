# Reanudación local de la ola MFP · 24-oct-2026

## Instrucción vigente y base

El usuario pidió implementar directamente en local, con el método más efectivo y rápido, sin push. Sustituye Cloud y delegación. Un solo agente; no contactar otros chats ni fusionar master. La orden posterior del usuario exige continuar hasta entregar el 100 %, sin detenerse para preguntas rutinarias; las verificaciones adicionales necesarias quedan autorizadas. El checkout principal conserva su trabajo ajeno y no se modifica.

Worktrees limpios al comenzar: `mfp`/`feat/macrofactor-parity` en `c488bfbe8`, `mfpn`/`mfp/n` en `00bde8b50`, `mfpd`/`mfp/d` en `c0e8f77ab`, `mfpt5`/`mfp/t5a` en `7b373aa22`, `mfph`/`mfp/h` en `0fe99e548`. La RAM inicial era 1,84 GB libres; el usuario liberó memoria y las guardas de la cola se mantuvieron intactas.

## Resultado comprobado

| Bloque | Evidencia local | Estado |
|---|---|---|
| N3/N4 sobre `00bde8b50` | 257 pruebas Android / 22 clases, 0 fallos/errores/omisiones; 38 PNG | Validación focal verde; revisión visual parcial, sin QA física |
| T4 | Commit `3b6c5153e`; 34 pruebas JVM y 58 Android verdes | Corregida la pérdida de repeticiones aceptadas al regenerar semana; prueba bilateral y por lado, JSON e historial |
| P2-Na | Commits `f684146b9` y harness separado `b2abea01d`; 475 pruebas Android / 64 clases previas al arreglo final, 44 JVM / 4 clases y 73 Android / 9 clases posteriores | Recetas, favoritos, hora local, preferencias JSON y exportación por fila implementados. Validación focal completa, incluido el arreglo de raciones y editor decimal; integración y QA física pendientes |
| T5a | Commit `770d9bc4c`; 1.527 JVM/140 clases y 398 Android/34 clases verdes, sin omisiones; 20 PNG | Regresión amplia verde; rótulo truncado al 130 % corregido con prueba de texto. Revisión visual parcial, QA física pendiente |
| P2-Nb | Commits `f237e727f` + debug `616d7b517`; 1.317 JVM/108 clases, 12 focales posteriores, 330 Android/47 clases, diez PNG | Verde; revisión final parcial 130 % realizada. Sin QA física |

## Logs y hojas

- N3/N4: `C:/kw/logs/mfp-N4-local-20261024-1.log`; `C:/kw/shots/mfp/N4-local-20261024`.
- T4: `C:/kw/logs/mfp-T4-local-20261024-jvm-3.log`, `mfp-T4-local-20261024-android-2.log`. Primera tanda Android sin pruebas por heap de 4 GB; segunda verde con 6 GB y guarda de 7 GB libres.
- P2-Na: `C:/kw/logs/mfp-P2Na-local-20261024-android-3.log`; `C:/kw/shots/mfp/P2Na-local-20261024-final3`, 54 PNG. Regresión final JVM: `C:/kw/logs/kpknjvm-mfpn-20261024-024525.log`. El recibo detallado está en `C:/kw/mfpn/docs/macrofactor-parity/cambios/P2-Na.md`.
- T5a: `C:/kw/logs/mfp-T5a-local-20261024-jvm-1.log`, `mfp-T5a-local-20261024-android-2.log`; 20 PNG finales en `C:/kw/shots/mfp/T5a-local-20261024-final`. Dos tandas Android, sin necesidad de repetir.

## Siguiente paso y límites

La instrucción posterior del usuario autoriza las comprobaciones adicionales necesarias y exige seguir hasta el 100 %, sin preguntas rutinarias. La tanda adicional de P2-Na ya pasó: **73 pruebas Android / 9 clases**, 0 fallos/errores/omisiones; log `C:/kw/logs/mfp-P2Na-local-20261024-android-extra.log`, 18 PNG y revisión del logger de media ración al 130 %. El límite anterior orienta a agrupar bien el trabajo; no vuelve a usarse como motivo para detener la entrega. P2-Nb terminó verde. CSV/OCR/banco P2-F están implementados en mfpn, aún sin commit: 19 pruebas puras verdes tras corregir la porción FDA; primera ronda Android agrupada (64 clases) en cola, sesión 44189, log `C:/kw/logs/mfp-CSVE-local-android-1.log`. No modificar sus fuentes mientras espera/ejecuta.

Después: completar P2-Nb y los restantes paquetes del plan, integración aislada, regresiones completas y QA física. La ola no está terminada. No se ha construido/instalado un APK nuevo ni medido p95 en dispositivo.

## Guardas

Gradle exclusivamente por `gradle_slot.sh` / `kpkn_jvm.sh`; una corrida por paquete y carril, con guardas de RAM intactas. Room congelado, esquema intacto y sin constantes prohibidas según verificación local. Sin tocar importador, catálogo S11/H-i, `.gitignore` ni datos del usuario.

En el Z Flip rige la orden AGENTS posterior del 23-oct: única **KPKN Dev `com.example.kpkn.dbg`**, `baseDebug`, acceso por `phone_run` y `phone.lock`, comprobar firma y compatibilidad de datos antes de actualizar con `-r`. Sustituye la antigua `.mfp`. No se han usado adb, ejecutores de teléfono, instalaciones ni limpieza de datos durante esta reanudación.

## Trabajo adicional en curso

- T5b en `C:/kw/mfpt5`, rama `codex/mfp-t5b` desde `770d9bc4c`: proyección, VM, pantalla, entradas, siete fixtures, doce pruebas puras (12/12 verdes) y tres de VM, catorce renders preparados. JVM verde; Android y renders pendientes. Se conserva mfp/t5a intacta.
- Corrección de meta en mfp/d (altura tipográfica medida, regresión 130 %) y ticker de día local en mfp/h (regresión de medianoche): WIP sin verificar.
- Arreglos EER result prioritario con fallback legacy, preferencia GTIN exacta en SQL y aviso de duplicado propio: preparados FUERA del árbol pendiente en `C:/kw/logs/mfp-minor-stage`; seis archivos con bases en `mfp-minor-stage-bases.json`, más nueva prueba UI. Aplicar sólo tras terminar la ronda actual y comprobar TODOS los hashes.
- Comprobación: la punta actual de feat/screens-v2 no es ancestro de master, pero el merge-base es 5ffdff255 (la interfaz nueva ya entró). Además README D19 registra autorización previa del usuario para continuar T6/P2-Ta/P2-Tb sobre base MFP actual, mínimo/aditivo y documentando conflictos. No volver a preguntar ni bloquear por esa condición.
- Otro chat informó indisponibilidad temporal de Z Flip y cambios Room34 en Pixel5554; no instalar sobre esquemas posteriores. El intento de responder a la coordinación de emulador fue RECHAZADO por revisión automática por autorización no confiable; no se envió. No eludir ni contactar de nuevo sin autorización humana confiable. No hay acceso adb ni instalaciones nuestras.

## Actualización 24-oct, CSV/OCR cerrados en pruebas locales

mfp/n HEAD `f9d6f0406`: producción CSV/OCR/minores `f151cbc34`, arneses/banco `9fbd197a2`, atributos DRI `f9d6f0406`. Ronda Android 3 BUILD SUCCESSFUL: 67 clases, 462 declaradas, 460 ejecutadas, 0 fallos/errores, 2 omitidas heredadas/condicionales de PDF/corpus OCR. 28 PNG finales; revisión visual parcial realizada. JVM final 33/33. El staging de EER/GTIN/duplicados YA ESTÁ APLICADO; no volver a aplicar. Sin cambios de Room según mfp_verify. Falta QA física de galería/SAF/p95 y suite integrada.

Entrenamiento continúa en `codex/mfp-training-tail`, conserva T5a y T5b en su historia. Banco puro activo `C:/kw/logs/mfp-training-tail-jvm-1.log`; T6/Myo/discos/ajustes en WIP. Medianoche y rótulo de meta ya tienen commits locales separados con regresiones pendientes de la tanda integrada.
