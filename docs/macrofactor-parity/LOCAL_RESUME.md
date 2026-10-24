# Reanudación local de la ola MFP · 24-oct-2026

## Instrucción vigente

El usuario pidió: «Implementa tú directamente, en local, con el método más efectivo y rápido y sin despilfarrar tokens. Continúa la ola MFP sin hacer push». Sustituye las instrucciones anteriores de Cloud y de delegación. No lanzar subagentes ni contactar otros chats; no fusionar master.

## Base comprobada

Los cinco worktrees estaban limpios al comenzar: `mfp`/`feat/macrofactor-parity` en `c488bfbe8`, `mfpn`/`mfp/n` en `00bde8b50`, `mfpd`/`mfp/d` en `c0e8f77ab`, `mfpt5`/`mfp/t5a` en `7b373aa22`, `mfph`/`mfp/h` en `0fe99e548`. El checkout principal está en master con trabajo ajeno y no se modifica.

La cola local estaba vacía. Inicialmente había 1,84 GB de RAM libre; se dejó la validación esperando con todas las guardas y el usuario liberó memoria. La corrida N3/N4 obtuvo la ranura Android con más de 6 GB libres.

## Evidencia y trabajo en curso

- Corrida Android N3/N4, 22 clases concretas: `C:/kw/logs/mfp-N4-local-20261024-1.log`, render en `C:/kw/shots/mfp/N4-local-20261024`. Resultado pendiente. Una única corrida, sin alterar el árbol de `mfpn` mientras corre.
- `mfp_verify.py` sobre las puntas iniciales: Room intacto y sin constantes prohibidas. Sus XML son anteriores a las correcciones: T4 100 tests/1 fallo; N3/N4 358/2 fallos. No son evidencia de las puntas actuales.
- Revisión T4: corregida la pérdida de repeticiones aceptadas al regenerar una semana. Cambio aditivo en JSON (`PlanLoadReference.capturedTargetReps`), materializador y referencia guardada por el runtime. Nuevas regresiones bilateral y por lado, con JSON, historial y rango. Pendiente validar por el arnés JVM y Android antes de cerrar el paquete.
- Las fases restantes del plan siguen pendientes; no hay aceptación de dispositivo nueva ni medición p95.

## Guardas

Gradle local solo mediante `gradle_slot.sh`/`kpkn_jvm.sh`, una compilación propia a la vez, máximo de iteraciones del plan intacto. Room congelado; no tocar `.gitignore`, datos del usuario ni catálogo S11/H-i. No push, instalación, limpieza de datos ni eliminación de worktrees durante esta reanudación sin cumplir sus condiciones de cierre.

## Resultados confirmados

- N3/N4 Android sobre `mfp/n@00bde8b50`: `mfp-N4-local-20261024-1.log`, **257 pruebas en 22 clases, 0 fallos, 0 errores y 0 omitidos**, 38 PNG al 100/130 %. Antes de comenzar P2-Na el worktree seguía limpio. Revisión visual parcial, no aceptación física.
- T4: commit `3b6c5153e`, **34 pruebas JVM** y **58 Android**, todas verdes y sin omisiones. Logs `mfp-T4-local-20261024-jvm-3.log` y `mfp-T4-local-20261024-android-2.log`. La primera vuelta Android falló por heap de 4 GB sin ejecutar pruebas; la segunda pasó con 6 GB y guarda de 7 GB libres. El verificador confirma Room intacto y ausencia de constantes prohibidas.
- P2-Na se implementa en `mfp/n`, desde `00bde8b50`: exportación por fila, recetas con instantáneas/rendimiento/versionado, favoritos, hora local y preferencias en copia aditiva v6. Validación de dominio en curso (`mfp-P2Na-local-20261024-jvm-1.log`); no marcar el paquete como terminado hasta Android/render y revisión.
