# Repo Structure

## Intent

Keep the repository root focused on active app entrypoints, build/config files, and assets that are still referenced by the current PWA/Capacitor workflow.

Everything else should live in a more specific place:

- `docs/`: living documentation and archived planning material
- `artifacts/`: generated diagnostics, screenshots, diffs, figma dumps, and legacy build outputs
- `scripts/`: reusable scripts, including ad-hoc helpers under `scripts/ad-hoc/`
- `packages/`: shared packages used by the active codebase
- `android-native/`: Kotlin/Android migration work

## Current Top-Level Rules

- Keep active web app source roots at the top level for now:
  `components/`, `contexts/`, `data/`, `hooks/`, `routes/`, `services/`, `stores/`, `utils/`, `workers/`
- Keep active build/config entrypoints at the top level:
  `App.tsx`, `index.tsx`, `index.html`, `manifest.json`, `service-worker.js`, `package.json`, `tsconfig.json`, `tailwind.config.js`, `capacitor.config.json`
- Keep only assets that are still referenced directly by the current build in the root.
- Put one-off helper scripts in `scripts/ad-hoc/` instead of the root.
- Put migration plans, audits, and superseded RN material in `docs/archive/`.
- Put logs, screenshots, diffs, figma exports, and legacy generated JS/CSS in `artifacts/`.

## Archive Layout

- `docs/archive/react-native-migration/`
  Old plans, manifests, and porting docs from the cancelled PWA -> React Native effort.
- `docs/archive/reports/`
  Audit writeups, reports, and ad-hoc narrative documents that are worth keeping but should not live in the root.
- `artifacts/logs/`
  Test logs and typecheck outputs.
- `artifacts/diffs/`
  Git/database/history snapshots and comparison dumps.
- `artifacts/screenshots/`
  Emulator or UI capture images.
- `artifacts/figma/`
  Figma export JSON/TXT dumps.
- `artifacts/legacy-root-build/`
  Root-level generated outputs that should not be confused with the real build output in `www/`.

## Deliberately Not Moved Yet

The following directories were left in place because moving them can break local tooling, IDEs, or parallel migration work:

- `android-kotlin-studio/`
- `kpkn-auge-engine/`
- `kpkn-compose-kpkn-ui/`
- `kpkn-nutrition-ai/`
- `kpkn-parity-auditor/`
- `kpkn-program-workout-flow/`
- `kpkn-pwa-to-kotlin-migrator/`

If you want, those can be consolidated later under a dedicated parent such as `workspaces/` or `migration/`, but that should be a separate pass with path verification.
