# QA en el Flip5 · variante `com.example.kpkn.mfp`

Protocolo en `README.md` (D12). Aquí, el procedimiento paso a paso y qué se comprueba por paquete. Lo ejecuta el orquestador (Codex no toca el teléfono).

## 1. Compilar e instalar
1. Compilar (una sola compilación a la vez, por la cola global):
   `bash /c/kw/tools/gradle_solo.sh /c/kw/mfp/android-native -I C:/kw/tools/mfp-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/mfp-apk-<n>.log 2>&1`
   APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk` (applicationId `com.example.kpkn.mfp`).
2. Estado del teléfono, sin tocar nada: `python C:\kw\tools\mfp_phone.py --check` → debe decir APTO.
3. Instalar y abrir la primera actividad en una sola sesión corta con `mfp_phone.py --apk … --actions "launch:…"` (comprueba con aapt2 que el paquete es `.mfp`).

## 2. Sembrar datos de la variante (base propia y vacía)
1. Persona QA y activación sin dedo: `launch:WizardHarnessActivity|--es|scope|full|--es|persona|qa|--ei|autonext|600|--es|until|REVIEW_ACTIVATE|--ei|activate|1500|--ez|pro|true` (esperar ~2 min; el arnés recorre el alta y pulsa «Activar»).
2. Datos de demostración (8 entrenos en 14 días, comidas del día, check-in): `launch:DemoSeedActivity|--ez|pro|true`; abre `MainActivity`.
3. Para nutrición larga (check-in): `NutritionHistorySeedActivity` (N2b).

## 3. Qué se mira por paquete
| Paquete | En el teléfono |
|---|---|
| T2 | `kpkn://program/<id>` → pestaña **Volumen**: «Real» en series/sem frente a la barra, ambos con la misma unidad; «N registros sin fecha no se cuentan» solo si procede; desglose por ejercicio y por cabeza que suma lo mostrado; selector Programa/Semana; letra al 130 % sin recortes (el arnés no cambia la letra del teléfono: se comprueba en la hoja de render). |
| T1 | `EquipmentHarnessActivity` (estados `empty`, `commercial`, `error`) y la ruta real desde Ajustes → «Mi material»: guardar, descartar, validaciones, resumen vivo. |
| N2a | Línea de estado del día en Nutrición; hoja con las opciones; sugerencia de día parcial. |
| N2b | `NutritionHistorySeedActivity` + tarjeta de check-in → flujo por módulos → aceptar / declinar / posponer; pausa por falta de registros; objetivo nuevo desde mañana. |
| T6 | Reemplazar un ejercicio en la sesión en vivo: «Sugeridos» arriba y la lista completa intacta debajo. |
| T3/T4 | Sesión en vivo: sugerencias de carga coherentes con RIR; propuesta con kg y repeticiones sobre el material declarado. |
| T5a/T5b | Panel por músculo (lista y cuerpo), progreso por ejercicio y récords. |
| N3 | `BarcodeSelfTestActivity` (ML Kit sin cámara): `SELFTEST OK n/n` en logcat; escáner real: no verificable sin un producto delante (lo prueba la persona). |
| N4 | Copiar a varias fechas, repetir ayer, plantillas, entrada rápida. |

## 4. Reglas de la sesión
- Solo `.mfp`; nada de `.dbg`, `.vtqa` ni producción; no se usa el candado del banco; sin `logcat -c`, sin `kill-server`.
- Sesiones cortas y agrupadas; capturas en `C:\kw\shots\mfp\phone\`; las que muestren pantallas privadas de la persona se borran sin describirlas.
- Al terminar la ola: `python C:\kw\tools\mfp_phone.py --uninstall`, borrar APK y capturas pesadas.
