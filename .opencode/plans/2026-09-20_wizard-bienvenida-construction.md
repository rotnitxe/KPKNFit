---
flags: [room, auge, nutrition]
status: construction
source_commit: 2d0cae9e2517ffce4e686669ba4ddc563cd613ca
---

# Construcción del wizard de bienvenida integrado

Este plan de construcción ejecuta `docs/PLAN_WIZARD_BIENVENIDA_APROBADO.md` sobre el checkout actual. El documento aprobado es la fuente de alcance; el código y el esquema exportado son la autoridad cuando exista divergencia histórica.

## Rutas

- `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/`
- `android-native/app/src/main/java/com/example/kpkn/data/onboarding/`
- `android-native/app/src/main/java/com/example/kpkn/data/models/`
- `android-native/app/src/main/java/com/example/kpkn/domain/training/`
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/`
- `android-native/app/src/main/java/com/example/kpkn/screens/home/`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/`
- `android-native/app/src/main/java/com/example/kpkn/data/db/`
- `android-native/app/src/test/`
- `ios-native/` y `backend/` solo para contrato/fixtures de paridad cuando el comportamiento compartido cambie; no se declarará build iOS validado desde Windows.

## Impacto

- Entrada raíz anterior a Home, con reanudación, deep-link retenido y error recuperable.
- Wizard existente convertido en recorrido condicionado: perfil compartido, volumen obligatorio, elección de programa, nutrición opcional y rings al cierre.
- Persistencia JSON existente ampliada sin duplicar tablas ni subir Room por campos blob.
- Generación personalizable y protocolos con contratos de catálogo, compatibilidad y receta preservados.
- Home/nutrición condicionados por elección explícita del módulo.
- Evidencia inicial de recuperación extendida por músculo y reajuste explícito, con preview/Home compartiendo política.
- Activos visuales originales y capturas reales sintéticas, sin usar referencias como recursos de producción.

## Pruebas

- Tests dirigidos de onboarding, persistencia, volumen, generación, protocolos, nutrición y recuperación.
- `git diff --check` después de cada bloque de cambios.
- `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest"` con filtros dirigidos primero.
- `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "assembleBaseDebug"` y compilación Health si el código compartido queda afectado.
- Instalación con respaldo previo del perfil poblado; QA en perfil sintético aislado con `com.example.kpkn/.MainActivity` visible, screenshot, UI dump y logcat.
- Evidencia separada para código, tests, build, instalación, QA Android y contrato/paridad pendiente.

## Riesgos

- El árbol compartido contiene sistemas sensibles de AUGE, nutrición, catálogo y persistencia; no se cambiarán fórmulas sin vector de regresión.
- `docs/WIZARD_REDESIGN_SUMMARY.md` contiene referencias históricas de una app TypeScript y no es autoridad para Android.
- El mapa generado habla de Room v25, pero el código actual declara v27; no se hará migración por campos JSON.
- El emulador de uso habitual puede contener datos; no se borrará ni se reemplazará sin respaldo verificable.
- La paridad iOS/backend puede quedar pendiente de ejecución en sus entornos; se reportará sin sobredeclarar validación.

