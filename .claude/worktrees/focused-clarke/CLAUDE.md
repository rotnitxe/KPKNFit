# KPKN Fit — Contexto para Claude

## Leer siempre al inicio de sesión
- `memory/PWA-structure.md` — mapa completo de la PWA
- `memory/KOTLIN-structure.md` — estado del proyecto Kotlin nativo
- `memory/CONVENTIONS.md` — naming, patrones, equivalencias PWA→Kotlin

## Estructura del repo

```
kpkn-fit-(beta-test)/
├── [root]              ← PWA principal (React + TypeScript + Capacitor)
├── packages/           ← shared-types, shared-domain, design-tokens
├── android/            ← Capacitor Android wrapper (NO es la migración)
├── android-native/     ← ★ KOTLIN NATIVO (Android Studio aquí)
├── backend/            ← Python FastAPI
├── memory/             ← Knowledge base persistente para Claude
└── CLAUDE.md           ← Este archivo
```

## Rutas absolutas

- PWA root: `C:/Users/valen/Downloads/kpkn-fit-(beta-test)/`
- Kotlin: `C:/Users/valen/Downloads/kpkn-fit-(beta-test)/android-native/`
- Kotlin main: `android-native/app/src/main/java/com/example/kpkn/`

## Objetivo del proyecto

Migrar la PWA (React/TypeScript/Capacitor) a **Kotlin nativo con Jetpack Compose**.
- La PWA es la **fuente de verdad** de lógica y features.
- `android-native/` es el destino de la migración.
- `android/` (Capacitor) se ignora — no es parte de la migración.

## Estado rápido de la migración Kotlin

✅ Home + RINGS + TopBar dinámica + Calibración
🔲 Entrenamiento | Nutrición | WikiLab | Coach/IA | Perfil | Settings | Room | ViewModels
