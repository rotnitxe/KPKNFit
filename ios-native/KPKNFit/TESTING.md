# Pruebas de iOS (XCTest)

Las pruebas unitarias reales de la app iOS son las **15 funciones `test...` de `KPKNFitTests/KPKNFitTests.swift`**. `KPKNFitUITests` solo trae las 3 pruebas de plantilla de Xcode (`testExample`, `testLaunchPerformance`, `testLaunch`) y no forman parte de esta corrida.

> Estado: **no ejecutadas**. No se han corrido en esta sesión y el PC de desarrollo no tiene macOS ni Xcode. Hay 13 entradas de `ios-native` sin commit (10 archivos Swift de la app, el catálogo `exercise_catalog_v2.json`, la propia prueba y este documento) que, hasta donde se sabe, nunca se han compilado. Si no tienes Xcode a mano, anota estas pruebas como `NOT_RUN`: no se afirma que compilen ni que pasen.

## Qué necesitas

* Un Mac con Xcode. El proyecto se creó con Xcode 15.2 y apunta a iOS 17.2 (`IPHONEOS_DEPLOYMENT_TARGET = 17.2`, Swift 5.0). Si el iMac sigue en macOS 13.7.8, probablemente llega como máximo a Xcode 15.2, que es justo con el que se creó: debería servir.
* Un simulador de iPhone con iOS 17.2 o superior.
* Si hay varios Xcode instalados, elige uno: `export DEVELOPER_DIR=/ruta/a/Xcode.app/Contents/Developer` (el script `ios-native/watch_and_deploy.sh` usa `/Users/imacmantra/Downloads/Xcode.app/Contents/Developer`).

## Pasos exactos (desde la raíz del repositorio)

1. Comprueba la versión y elige un simulador:

   ```sh
   xcodebuild -version
   xcrun simctl list devices available
   ```

   Copia el UDID de un iPhone con iOS 17.2 o superior (el script de despliegue usa un «iPhone 15 Pro»; si ese simulador ya no existe, usa otro).

2. Corre las 15 pruebas del paquete `KPKNFitTests` y guarda la salida:

   ```sh
   xcodebuild test \
     -project ios-native/KPKNFit/KPKNFit.xcodeproj \
     -scheme KPKNFit \
     -destination 'platform=iOS Simulator,id=<SIMULATOR_UDID>' \
     -only-testing:KPKNFitTests \
     2>&1 | tee ios-run.log
   ```

3. Lee el final de `ios-run.log`:

   * `** TEST SUCCEEDED **` y una línea `Executed 15 tests, with 0 failures` → las 15 pasan.
   * `** TEST FAILED **` → mira qué `Test Case ... failed` aparece (hay una línea `error:` por aserción).
   * `** BUILD FAILED **` → el objetivo de pruebas o la app no compilan: guarda todas las líneas con `error:` (`grep -n "error:" ios-run.log`).

4. Una sola prueba (por ejemplo, la del transporte del plan de Android):

   ```sh
   xcodebuild test \
     -project ios-native/KPKNFit/KPKNFit.xcodeproj \
     -scheme KPKNFit \
     -destination 'platform=iOS Simulator,id=<SIMULATOR_UDID>' \
     -only-testing:KPKNFitTests/KPKNFitTests/testProgramDecodeEditEncodePreservesAndroidPlanTransport
   ```

## Qué comprueban las 15 pruebas

* **Catálogo v2** (2): carga con identidades únicas (definiciones y configuraciones sin repetir, todas elegibles para automatización, con configuración por defecto) y resolución exacta con guarda de revisión. Revisión esperada del catálogo: `v2-approved-2026-09-29-a`.
* **Transporte del plan de Android** (2): decodificar, editar una sesión, volver a codificar sin perder receta, procedencia, campos heredados, sobrescrituras manuales, series ni cardio; y reemplazo de sesión con el índice de mesociclo contando todos los bloques.
* **Recuperación nutricional y metas** (9): sin comidas o sin metas no se infiere déficit; el déficit sigue calculándose cuando hay metas; el plan activo manda sobre los ajustes (incluso con 0); ausencia ≠ 0 al decodificar; una meta de 0 kcal nunca se usa como divisor.
* **Baterías manuales de AUGE** (2): ventana activa de 18 h y ancla por defecto en la fecha del registro, no en «ahora».

## Si algo no funciona

* **«KPKNFitTests no pertenece al esquema»** o 0 pruebas ejecutadas: el repositorio no trae un esquema compartido (`xcshareddata/xcschemes/`). Abre el proyecto en Xcode, entra en *Product → Scheme → Manage Schemes…*, marca *Shared* en `KPKNFit` y comprueba en *Edit Scheme → Test* que `KPKNFitTests` está activo. Como alternativa, prueba con `-scheme KPKNFitTests`.
* **Error de compilación en los primeros archivos Swift**: no intentes arreglarlo a ciegas; envía las líneas `error:`. Se corrigió a ojo en `KPKNFitTests.swift` (líneas 13-14) un `Set(x.map(\.id).count)` que no podía compilar (debía ser `Set(x.map(\.id)).count`); esa línea llevaba así desde el commit `10e19735b` (2026-08-02), por lo que el objetivo de pruebas pudo no compilar desde entonces. Es una corrección hecha a ojo, sin compilar: no se afirma que arregle todo. El resto de `KPKNFitTests.swift` se revisó por lectura contra las firmas de los tipos que usa (catálogo, `Program`, motor de recuperación nutricional, AUGE) y no se encontraron más errores de ese tipo.
* **Simulador no disponible**: `xcrun simctl list runtimes` muestra los runtimes instalados; hace falta uno de iOS 17.2 o superior.

## Dónde registrar el resultado

Anota PASS o FAIL, la fecha, la versión de Xcode y el simulador en `artifacts/consolidation-20261001/closeout/` (por ejemplo, junto a `IOS_RUN_GUIDE.md`) y guarda `ios-run.log`. Si no hay Xcode disponible, el estado correcto es `NOT_RUN`.
