#!/bin/bash
# Ejecuta el test TC066 (detección de pantallas negras y errores)
# Requisitos: Python 3, playwright (pip install playwright && playwright install chromium)
# La app debe estar sirviendo en http://localhost:5500 (npm run serve)

set -e
cd "$(dirname "$0")/.."

echo "=== TC066: Black screen and error detection ==="
echo "Asegúrate de que la app está en http://localhost:5500 (npm run serve)"
echo ""

python testsprite_tests/TC066_Black_screen_and_error_detection_smoke_test.py

echo ""
echo "=== TC066 completado ==="
