# Compatibilidad de runtimes de Firebase en CI

El job Firebase usaba Node 22 para todo, mientras la dependencia fijada de pruebas de reglas exige Node 24.12+. El workflow conserva el ejecutable Node 22 para Functions/CLI y llama al cliente de pruebas mediante su ejecutable Node 24. Las instalaciones npm usan Node 24; el servidor conserva su runtime declarado Node 22.

El YAML se parseó, Bash pasó su validación y un capturador local confirmó las comillas del comando con una ruta que contiene espacios. Pasaron 12 contratos en Node 22 y 22 escenarios contra Auth/Firestore/Storage/Functions locales, con Functions/CLI 22.23.3 y cliente 24.19.0. Los registros y hashes están en [DELIVERY.json](DELIVERY.json) y [validation.json](validation.json).

Sólo cambia `.github/workflows/android-stability.yml`. El APK y las fuentes Android siguen iguales a 25a20f1f0. No se ejecutó el job remoto, no hubo push/deploy ni compras reales; el plan de lanzamiento A–J sigue abierto.
