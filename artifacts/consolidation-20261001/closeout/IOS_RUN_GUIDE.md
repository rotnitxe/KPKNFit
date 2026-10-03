# Guía para correr las pruebas de iOS en el iMac (20-40 min tuyos)

Qué se quiere saber: si las 15 pruebas de `KPKNFitTests` compilan y pasan. Nunca se han ejecutado; este PC no tiene Xcode, así que **no se afirma que pasen**.

1. Lleva al iMac la carpeta `ios-native` **actual** de este PC (o haz el commit y bájalo con git). El arreglo de `KPKNFitTests.swift` (líneas 13-14) y el documento `ios-native/KPKNFit/TESTING.md` solo existen en este PC.
2. En el iMac abre Terminal, entra en la raíz del repositorio y ejecuta `xcodebuild -version` (debería decir Xcode 15.2 o parecido).
3. Ejecuta `xcrun simctl list devices available` y copia el código (UDID) de un iPhone con iOS 17.2 o superior (por ejemplo el «iPhone 15 Pro»).
4. Ejecuta, cambiando `<UDID>`: `xcodebuild test -project ios-native/KPKNFit/KPKNFit.xcodeproj -scheme KPKNFit -destination 'platform=iOS Simulator,id=<UDID>' -only-testing:KPKNFitTests 2>&1 | tee ios-run.log`
5. La primera compilación puede tardar varios minutos. No cierres Terminal hasta que vuelva el prompt.
6. Mira las últimas líneas: `** TEST SUCCEEDED **` con `Executed 15 tests, with 0 failures` significa que pasan las 15; `** TEST FAILED **` significa que alguna falla; `** BUILD FAILED **` significa que algo no compila.
7. Si dice que `KPKNFitTests` no pertenece al esquema o ejecuta 0 pruebas, prueba el mismo comando con `-scheme KPKNFitTests` (hay más pasos en la sección «Si algo no funciona» de `TESTING.md`).
8. Si algo falla o no compila, **no lo arregles tú ni a ciegas**: solo guarda el resultado (el arreglo, si hace falta, son 1-3 h de trabajo después).
9. Envíame de vuelta: el archivo `ios-run.log` completo (o, si es enorme, sus últimas 80 líneas más todas las líneas que contengan `error:`), la salida de `xcodebuild -version` y el nombre del simulador usado.
10. Con eso se registra PASS o FAIL en `artifacts/consolidation-20261001/closeout/`. Si no puedes usar el iMac ahora, el estado correcto es «NO EJECUTADO» y la deuda sigue abierta (¿para cuándo quieres publicar iOS?).
