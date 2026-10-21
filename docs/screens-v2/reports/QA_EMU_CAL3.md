# QA en emulador · CAL3 (calibrador en vivo, tarjeta V7)

- **Fecha:** 21-oct-2026.
- **Dispositivo:** AVD `ZFlip5_QA` (API 36, 1080 × 2640 a 480 dpi = 360 × 880 dp), `-memory 3072`, `emulator-5580`.
- **APK:** el de CAL3, `a913d3742`: `KPKN Pre Release 17`, versionCode 50, `com.example.kpkn.dbg`. Copia en `C:\kw\apk\cal3\`. Se instaló y se precompiló con `cmd package compile -m speed -f`.

## Pruebas instrumentadas

Comando:
```
am instrument -w -r -e package com.example.kpkn.screens.workout com.example.kpkn.dbg.test/androidx.test.runner.AndroidJUnitRunner
```

Resultado: **197 pruebas, 3 fallos y 7 omitidas por suposición** (revisiones congeladas 14 y 15, y usuario de QA 10, igual que antes). Log en `C:\kw\logs\qa-cal3-inst.log`.

| Prueba | Resultado | Diagnóstico |
|---|---|---|
| `Cal3V7DeviceTest` (880 y 740) | ✅ 2/2 | Registra, aplica, revierte, edita y no sale del marco real. |
| `SessionRevisionFlowDeviceTest#realActivityReducedHeightKeepsCardAndLineComplete` | ❌ | Ya conocido: la voz queda por debajo de 48 dp a 760 dp. Es el punto 0 de LV-C. |
| `VoiceFirstUseDeviceTest` (2 de 4) | ❌ intermitente | `awaitChoicesLoaded` espera 5 s a que se cargue en IO. Al repetir la clase sola falló 1 de 4. La prueba aísla el diálogo, que CAL3 no toca. Es de tiempos con el equipo cargado (Gradle de otros agentes). Se pasó a LV-C subir la espera a 15 s. |

## Medidas en el dispositivo (`cal3-v7-renders/*-medidas.txt`)

| Alto | Marco de la tarjeta | Resultado | Botones | Línea de la sesión |
|---|---|---|---|---|
| 880 | 312 × 425 en (24, 299) | 280 × 405 | Aplicar y Conservar de 280 × 44, dentro | 328 × 112, 12 dp por debajo |
| 740 | 312 × 425 en (24, 159) | 280 × 405 | Revertir de 280 × 44, dentro | 328 × 112, 12 dp por debajo |

Las capturas están en `C:\kw\shots\cal3\cal3-v7-renders\`. Todo queda dentro de la tarjeta. Las fichas S2 y S3 muestran «62,5 kg · calibrada» después de aplicar, y la tarjeta dice «Cargas aplicadas · calibrada».

## Hallazgo

- **Texto inexacto**, en `CalibrationEngine.kt` ≈ :121-122. Las decisiones RAISE y LOWER siempre dicen «limitada por la política», aunque el factor no tenga tope. Con referencia 88 y capacidad 92 el ratio es 1,045 y no hay tope, pero la tarjeta lo dice igual. Además, «la política» es jerga. Lo corrige LV-C con textos según el tope real, 5 % o 8 %.

## Qué no se hizo a mano en el emulador

Los pasos 4, 6, 7, 8, 9 y 10 del informe CAL3 no se recorrieron a mano. Son la carga fijada, matar y reabrir la app, el alcance «Próximas sesiones» y la cancelación, los estados no aplicables, la vista previa OFF/PROPONER/AUTO y el resumen final.

Los cubren las pruebas JVM de CAL3, en verde: `WorkoutCalibrationRecorderRoomTest` (17), `Cal3ViewModelRoomTest` (7), `Cal3ProgramPreviewTest` (5), `Cal3CardUiTest` (20) y `Cal3SummaryUiTest` (2). Quedan para la QA de cierre de ola en el teléfono, con datos reales de una sesión.

## Limpieza

El emulador se cerró con `emu kill`. Se borraron sus `*.qcow2`, `snapshots/` y `tmpAdbCmds/`, y el AVD quedó en 34 MB.
