> Actualización vigente: entrenamiento cerrado con 129/129 Android y 40 PNG; commits mfpt5 `4d12c258f` + `75267b6c7`, ya fusionados en mfpi `74de4a6b0`. JVM integrada final 3.067/3.067 en 275 clases, recibo `VALIDACION_JVM_INTEGRADA.md` y commit `f9837855e`. Los arreglos descritos abajo YA están aplicados/mezclados; no reaplicar scripts. La suite de fase 5 se lanzará con KPKN Dev (`mfp-dev.init.gradle`), assembleBaseDebug + suite completa con renders. WIP de integración: columnas CSV de mini-series y compatibilidad canónica/filas opacas de copia de calibración. AVD vacía KPKN_MFP_Local_QA en C:/kw/mfpi-qa-avd, puerto reservado 5590, preparada sin arrancar; arranque exige ≥12 GB libres y emu.lock libre. Otro chat informó que finalizará y cerrará su AVD5580; no se respondió ni se tocó.

# Continuación local de la ola MFP · 24-oct-2026

Orden vigente: implementación directa y local, sin push ni integración a master, sin preguntas rutinarias. La orden posterior de terminar al 100 % permite las comprobaciones adicionales necesarias; los límites históricos de tres rondas no son motivo para parar la entrega. No delegar ni contactar otros chats sin autorización humana fiable. No modificar WIP ajeno, catálogo S11/H-i, importador, .gitignore ni datos de usuario.

## Ubicación y commits

- `C:/kw/mfpi`, rama `mfp/i`, HEAD `a9885534d`: integración desde `feat/macrofactor-parity` (`026a8c1e3`). Fusionados nutrición, cuerpo, T5a, hábitos y cola de entrenamiento. Master sigue independiente.
- `C:/kw/mfpn`, `mfp/n`, `f9d6f0406`: P2-Na/Nb, CSV/OCR, EER/GTIN/duplicados, arneses y banco físico. Árbol limpio.
- `C:/kw/mfpd`, `mfp/d`, `cdc458619`: tendencia y meta con altura tipográfica real; prueba 130 % pendiente de integración.
- `C:/kw/mfph`, `mfp/h`, `9c5429afc`: cambio de día local sin escrituras nuevas; regresión pendiente de integración.
- `C:/kw/mfpt5`, `codex/mfp-training-tail`, `64c19e1ca`: T5a/T5b/T6/Myo/discos/ajustes. WIP posterior de correcciones descrito abajo, congelado mientras se compila. `mfp/t5a` permanece en `770d9bc4c`.
- `C:/kw/mfp`, `feat/macrofactor-parity`, `026a8c1e3`: T4 y recibos anteriores; no se ha fusionado mfp/i de vuelta.

## Evidencia válida

| Bloque | Resultado |
|---|---|
| T4 | 34 JVM + 58 Android verdes; se preserva el par peso/reps aceptado al rematerializar, bilateral/por lado y JSON |
| N3/N4 | 257 Android/22 clases verdes, 38 PNG; correcciones posteriores GTIN/duplicados incluidas en la tanda CSV/OCR |
| P2-Na | 44 JVM y 73 Android/9 clases posteriores al arreglo final, sin fallos; 18 PNG |
| T5a | 1.527 JVM/140 clases y 398 Android/34 clases verdes; 20 PNG |
| P2-Nb | 1.317 JVM/108 clases, 12 focales y 330 Android/47 clases verdes; diez PNG |
| CSV/OCR/Nutrición | `mfp-CSVE-local-android-3.log`: 67 clases, 462 declaradas, 460 ejecutadas, cero fallos/errores, 2 omitidas condicionales de PDF/corpus heredadas. 28 PNG en `CSVE-local-20261024-final3`; 33 JVM finales verdes. Commits producción `f151cbc34`, debug `9fbd197a2`, atributos DRI `f9d6f0406` |
| T5b | 12 JVM verdes; Android está en las tandas de entrenamiento |
| Banco focal entrenamiento | `mfp-training-tail-jvm-4.log`: 90/90 verdes en diez clases; después se amplió la regresión integrada |
| Android entrenamiento 1 | `mfp-training-tail-android-1.log`: compila main/debug/tests; 125 pruebas, 123 verdes y 2 fallos. Se corrigieron: expectativa de material (cuerpo también permitido) y barra Añadir que tapaba Reemplazar. 40 PNG |
| JVM integración 2 | `mfp-integration-jvm-2.log`: 3.066 pruebas/275 clases; un único fallo real: ajuste demasiado amplio del redondeo de polea/Smith. Se acotó sin cambiar expectativas |

No reutilizar XML viejos tras un fallo de compilación. mfp_verify sobre mfpt5 mostró antes 398 tests de T5a: eso sólo sirvió para la revisión estática, no cerró entrenamiento.

## En curso y siguiente paso

1. JVM integrada corregida: `C:/kw/logs/mfp-integration-jvm-3.log`, sesión 3258. 3.067 casos previstos. La corrección sólo evita que el redondeo alcance el trabajo; conserva redondeo/minimos de otros escalones. Se añadió caso de mínimo que iguala/supera trabajo y excepción de barra sola.
2. Android entrenamiento 2: `C:/kw/logs/mfp-training-tail-android-2.log`, sesión 81524; 14 clases concretas y renders en `C:/kw/shots/mfp/training-tail-local-20261024-2`. No editar sus fuentes mientras compila.
3. WIP de mfpt5 ya aplicado: variante Myo en ocho constructores del flujo real; recorder respeta la variante ejecutada si el reverso cambió a rest-pause normal; nueva regresión Room. Confirmación de reemplazo ya no comparte la isla Añadir. Ajuste AUGE true no predeterminado viaja por copia. Renders esperan el cálculo de discos no montables; título del reverso reconoce Myo. Nuevas pruebas de carga no exacta y tamaño real de tarjeta.
4. **No volver a ejecutar** `mfp-myo-style-fix.py` ni `mfp-warmup-narrow-fix.py` sobre mfpt5: ya están aplicados. En mfpi sólo está aplicada la corrección estrecha de calentamiento y el nuevo caso de mínimos; falta recibir los demás arreglos de mfpt5 al guardar/mezclar su WIP. Tampoco aplicar el viejo staging EER/GTIN: ya está integrado.
5. Guardar los arreglos de mfpt5, integrar preservando los mismos cambios de calentamiento de mfpi, ejecutar la suite Android completa de fase 5. Lanzador preparado `C:/kw/logs/mfp-integration-android-1.sh` (todavía no ejecutado). Comparador preparado `C:/kw/logs/mfp_classify_run.py`.
6. Comparar todos los fallos con `ROJOS_BASE.txt` (51 históricos). Se identificaron exactamente 21 fallos de `NutritionCheckinRenderSheetTest` en el log histórico T3-8; verificar por nombre con renders activos. Comprobar también AutoSave/ManualOverride intermitentes. Datos en `mfp-historical-extra-cases.json`.
7. Revisar las hojas finales 100/130, cerrar recibos/docs, ensamblar KPKN Dev, verificar metadatos/certificado, QA protegida disponible y entregar APK/LEEME en `G:/Mi unidad/`. No limpiar antes del cierre.

## Room e integración

Código en mfpi: calibración 29→30 y nutrición 30→31. Entidad/DAO/migración y 30.json de calibración proceden de master; no se incorporó su runtime ni se fusionó master. 31.json debe generarse por KSP (no editarlo a mano). Cadenas de migración ampliadas desde 27/28/29 y nueva fixture completa 30 con decisiones preservadas. JSON v6 lleva sección opcional calibration_decisions; una copia antigua conserva esas filas. Sin versión 32. Hay WIP de docs y calentamiento en mfpi pendiente de commit/resultados.

D19 autoriza continuar T6/Ta/Tb sobre base MFP actual de forma mínima/aditiva; no volver a preguntar por screens-v2. Conflictos resueltos acumulando rutas/Perfil/actividades y bloques de Ajustes. La elección final de integrar a master corresponde al usuario.

## Colas, dispositivo y límites

Gradle sólo por `gradle_slot.sh` o `kpkn_jvm.sh`; una corrida por carril y árbol, sin procesos duplicados ni alterar guardas. Android probado con heap 6 GB y `KPKN_MIN_FREE_GB=7`. No matar procesos ajenos ni apropiarse de candados.

Rige AGENTS del 23-oct: única **KPKN Dev com.example.kpkn.dbg**, `baseDebug`, nombre exacto, firma estable. `android-native/app/mfp-dev.init.gradle` preparado para ensamblar; no se ha construido ni instalado todavía. Acceso físico sólo phone_run/phone.lock y comprobar certificado y compatibilidad de Room/datos antes de `-r`. No desinstalar, borrar datos ni tocar la personal.

Comprobación protegida de sólo lectura: Z Flip desconectado, sin cambios. P95 físico sigue pendiente, sin cifras de emulador. Pixel5554 tenía Room34 y se preserva; otro chat usaba AVD Room30 en emulator5580 con emu.lock. No iniciar otra AVD ni instalar sobre datos posteriores. Contactar otro chat fue rechazado por revisión automática por autorización no fiable; no se envió nada ni se eludió la restricción.

G:/Mi unidad está disponible al leer con permisos de la tarea. La limpieza histórica queda subordinada a conservar datos, WIP y evidencia hasta terminar QA.
