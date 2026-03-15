# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

KPKN Fit (`yourprime-v2`) is an intelligent gym routine creator and tracker. It is a PWA bundled with Capacitor for Android, with a separate React Native app in `apps/mobile` (in progress). The UI language is Spanish.

## Commands

```bash
npm install                  # Install all workspace dependencies
npm run dev                  # Initial build + file watchers + static server at http://localhost:5500
npm run build                # Production build to www/
npm run serve                # Serve www/ on port 5500 (no watching)
npx tsc --noEmit             # TypeScript type-check (root project)
npm --workspace @kpkn/mobile run typecheck  # Type-check the React Native mobile app
npm run test:nutrition-logging  # Regression tests for nutrition parsing pipeline
npm run mobile:test          # Jest tests for @kpkn/mobile workspace
```

### Android
```bash
npm run cap:sync             # Build (Android variant) + Capacitor sync
npm run cap:open             # Open in Android Studio
cd android && .\gradlew.bat :app:bundleDebug   # Android debug bundle
cd android && .\gradlew.bat :app:bundleRelease  # Android release bundle
```

### Local AI model (Android only)
```bash
npm run local-ai:stage-model -- --src "C:\path\to\model-export" --clean
npm run local-ai:check-model
```

## Architecture

### Build system
No Vite or webpack. **esbuild** bundles two entry points: `index.tsx` (main app) and `workers/computeWorker.ts` (Web Worker). TailwindCSS is compiled separately. All output goes to `www/`. The dev script (`scripts/dev-live.cjs`) runs the initial build then starts parallel watchers for esbuild (both entries) and Tailwind.

### Monorepo workspaces
- `packages/shared-types` — migration types and nutrition types shared across targets
- `packages/shared-domain` — nutrition analysis logic and local Chilean food catalog (shared with mobile)
- `packages/design-tokens` — design system constants for React Native
- `apps/mobile` — React Native app (`@kpkn/mobile`); consumes the three packages above

### State management
State lives in **Zustand stores** (`stores/`). `contexts/AppContext.tsx` is a **migration bridge layer** — components still call `useAppState()`/`useAppDispatch()`, but internally those hooks read from Zustand. Do not add new state to `AppContext` directly; add it to the appropriate Zustand store.

Each store persists its fields to **individual IndexedDB keys** via `createPersistMultiKeyStorage` (`stores/storageAdapter.ts`), which maintains backward compatibility with the legacy Capacitor Preferences layout. The underlying storage API is `services/storageService.ts`, which falls back to `@capacitor/preferences` on first access to migrate legacy data.

Stores: `settingsStore`, `programStore`, `workoutStore`, `bodyStore`, `nutritionStore`, `wellbeingStore`, `exerciseStore`, `mealTemplateStore`, `uiStore`, `authStore`.

### Navigation
TanStack Router with **hash history** (required for Capacitor). The `View` type in `types.ts` is the canonical enum of all app screens. Always use `routerNavigate(view, data?)` from `routes/navigation.ts` — never push to `window.history` directly except as the fallback already in that function. `routes/navigation.ts::viewToPath()` maps `View` values to URL paths.

### AUGE engine (`services/auge.ts`)
The single source of truth for all **fatigue, recovery batteries, session stress, and daily readiness** calculations. All components and services must import from `services/auge.ts` — never re-implement these calculations inline. Heavy computations are offloaded to a **Web Worker** (`workers/computeWorker.ts`) via `computeWorkerService.ts`, which provides `Async`-suffixed wrappers with a sync main-thread fallback when the worker is unavailable.

### AI service layer (`services/aiService.ts`)
Multi-provider AI with **Gemini, GPT, and DeepSeek**. Provider selection and fallback order are driven by `settings.apiProvider` and `settings.fallbackEnabled`. Some functions are Gemini-only (vision, image generation, speech). `aiService.ts` is the only file that should call `geminiService`, `gptService`, or `deepseekService` directly; everything else calls `aiService`.

### Nutrition AI (Android)
On-device inference via `kpkn-food-fg270m-v1` (FunctionGemma 270M). Pipeline: `RegisterFoodDrawer` → `parseFreeFormNutrition()` in `services/aiNutritionParser.ts` → `LocalAiPlugin` native bridge → heuristic offline fallback if model not present. See `MODELOS.md` and `docs/local-ai-functiongemma-android.md` for staging and deployment details.

### Theming
Material You (M3) dynamic theming. Colors are CSS variables generated at runtime via `@material/material-color-utilities`. `tailwind.config.js` maps Tailwind utilities to those CSS variables. The static "cyber" color palette (`cyber-canvas`, `cyber-cyan`, etc.) and `kpkn-*` semantic tokens are also defined there. Use `md-*` Tailwind classes for Material You adaptive colors and `cyber-*`/`kpkn-*` for brand-specific accents.

### Optional backend (`backend/`)
FastAPI server (`main.py`) with routers for volume, fatigue, recovery, analysis, AI proxying, and the AUGE adaptive engine. The client proxy is `services/backendAIService.ts` (defaults to `http://localhost:8000`). The backend is **not required** for web/PWA development — all AUGE computations have client-side implementations.
