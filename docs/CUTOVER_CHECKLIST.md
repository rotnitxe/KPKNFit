# KPKN Fit — React Native Cutover Checklist

> Last updated: 2026-03-16
> Covers: Steps 1–12 complete (AAB built and signed)

---

## 1. Migration Status Summary

| Phase | Steps | Status |
|---|---|---|
| **A: REPAIR** | 1–3 | COMPLETE |
| **B: COMPLETE NUTRITION E2E** | 4–5 | COMPLETE |
| **C: MOBILE INFRASTRUCTURE** | 6–7 | COMPLETE |
| **D: REMAINING MODULES** | 8–10 | COMPLETE |
| **E: CUTOVER** | 11 (tests) | COMPLETE |
| **E: CUTOVER** | 12 (release) | COMPLETE — AAB signed with upload key |

---

## 2. Feature Parity Matrix

### 2.1 Screens

| Feature Area | PWA (Capacitor) | RN Mobile | Notes |
|---|---|---|---|
| Home dashboard | Full | `HomeScreen.tsx` (393 lines) | Metric cards, dev diagnostics |
| Workout overview | Full | `WorkoutScreen.tsx` (245 lines) | Session cards, rest timer, quick log |
| Session detail | Full | `SessionDetailScreen.tsx` (111 lines) | Exercise/set listing |
| Nutrition logging | Full | `NutritionLogScreen.tsx` (312 lines) | AI analysis, CRUD, templates |
| Progress | Full | `ProgressScreen.tsx` (269 lines) | Weekly nutrition/workout/wellbeing/body |
| Settings | Full | `SettingsScreen.tsx` (266 lines) | Toggles + cutover readiness panel |
| Program editor | Full | NOT PORTED | Read-only via migration; editing stays in PWA for now |
| Wiki / Biomechanics | Full | NOT PORTED | Low priority for MVP |
| AI Coach / Briefing | Full | NOT PORTED | Requires AI service integration |
| Body lab / Progress charts | Full | NOT PORTED | Charts need RN charting lib |
| Social / Playlist | Full | NOT PORTED | Low priority |
| Auth / Profiling | Full | NOT PORTED | Cloud sync deferred |

### 2.2 Stores (12/12 ported)

| Store | Status | Tests |
|---|---|---|
| `bootstrapStore` | Ready | 10 tests |
| `nutritionStore` | Ready | 17 tests |
| `workoutStore` | Ready | 14 tests |
| `settingsStore` | Ready | Covered via domainState tests |
| `wellbeingStore` | Ready | Covered via domainState tests |
| `mealTemplateStore` | Ready | Covered via domainState tests |
| `bodyStore` | Ready | Tests exist |
| `exerciseStore` | Ready | Tests exist |
| `programStore` | Ready | Tests exist |
| `localAiDiagnosticsStore` | Ready | Tests exist |
| `nutritionFlowDiagnosticsStore` | Ready | No dedicated tests (dev-only) |
| `cutoverStore` | Ready | No dedicated tests (dev-only readiness UI) |

### 2.3 Services (11 implemented)

| Service | Status | Tests |
|---|---|---|
| `migrationImportService` | Ready | Tests exist |
| `migrationHydrationService` | Ready | Tests exist |
| `mobilePersistenceService` | Ready | Covered indirectly |
| `mobileDomainStateService` | Ready | 19 tests |
| `workoutStateService` | Ready | Covered via workoutStore tests |
| `mobileNotificationService` | Ready | Tests exist |
| `widgetSyncService` | Ready | Tests exist |
| `backgroundSyncTask` | Ready | Tests exist |
| `nutritionAnalyzer` | Ready | Tests exist |
| `localAiSmokeTestService` | Ready | Tests exist |
| `nutritionFlowSmokeTestService` | Ready | Dev-only, no dedicated tests |

### 2.4 Native Modules (4 modules, 7 Java files)

| Module | Java Files | Status |
|---|---|---|
| `KPKNLocalAi` | `LocalAiModule.java` (~1100 lines) | Ready — MediaPipe + heuristic fallback |
| `KPKNWidgets` | `WidgetModule.java` (199 lines) | Ready — SharedPreferences sync |
| `KPKNBackground` | `BackgroundModule.java`, `KpknBackgroundWorker.java`, `KpknBackgroundSyncService.java` | Ready — WorkManager |
| `KPKNMigrationBridge` | `MigrationBridgeModule.java` (68 lines) | Ready — reads legacy snapshot |

---

## 3. Test Coverage

### 3.1 Numbers

| Metric | Value |
|---|---|
| Test suites passing | 19 |
| Total tests passing | 160 |
| Pre-existing failures | 1 (App.test.tsx — RNGestureHandlerModule mock) |
| Statement coverage | 43.09% |
| Branch coverage | 76.79% |
| Function coverage | 62.69% |
| Line coverage | 43.09% |

### 3.2 Coverage by Area

| Area | Stmts | Branch | Funcs | Lines |
|---|---|---|---|---|
| services/ | 78.05% | 77.24% | 80.64% | 78.05% |
| stores/ | 60.80% | 80.83% | 37.93% | 60.80% |
| modules/ | 56.86% | 66.66% | 26.31% | 56.86% |
| storage/ | 32.52% | 85.71% | 40.00% | 32.52% |
| components/ | 0% | — | — | — |
| screens/ | 0% | — | — | — |

### 3.3 Enforced Thresholds

```
global: statements 25%, branches 15%, functions 20%, lines 25%
```

Coverage provider: V8 (bypasses `babel-plugin-istanbul` Windows crash).

---

## 4. Cutover Store System Checks

The `cutoverStore.ts` defines 11 automated system checks and 6 manual sign-off items.
Three stages: `needs-work` → `pilot-ready` → `ready-for-cutover`.

### 4.1 Automated System Checks

| # | Check | What it verifies |
|---|---|---|
| 1 | `migrationBridgeReady` | Native `KPKNMigrationBridge` module is available |
| 2 | `migrationDataReady` | Bootstrap complete + no errors + persisted summary exists + nutrition + settings ready |
| 3 | `nutritionReady` | Nutrition store hydrated + expected log count matches |
| 4 | `workoutReady` | Workout store hydrated + overview present (or correctly empty) |
| 5 | `settingsReady` | Settings status is `ready` + summary is non-null |
| 6 | `wellbeingReady` | Wellbeing status is `ready` (or correctly empty if no legacy data) |
| 7 | `templatesReady` | Meal template status is `ready` (or correctly empty) |
| 8 | `widgetsReady` | Widget module available + not stale + native sync confirms fresh |
| 9 | `backgroundReady` | Background module available + last sync result is `success` |
| 10 | `notificationsReady` | Permission granted + at least one notification scheduled |
| 11 | `localAiReady` | Local AI module available + engine=runtime or model version detected |

### 4.2 Manual Sign-off Items

| # | Key | Description |
|---|---|---|
| 1 | `legacyUpgradeVerified` | Install RN app over existing Capacitor app, verify data migrates |
| 2 | `offlineColdStartVerified` | Kill app, enable airplane mode, cold start — app loads from MMKV/SQLite |
| 3 | `widgetsVerified` | All 4 widgets (Next Session, Nutrition, Battery, Volume) display correct data |
| 4 | `backgroundVerified` | Background sync runs on schedule (check after 15+ min idle) |
| 5 | `notificationsVerified` | Workout reminders, meal reminders, and AUGE battery alerts fire correctly |
| 6 | `nutritionFlowVerified` | Full nutrition flow: free-form text → AI analysis → save → appears in log |

---

## 5. Known Blockers for Step 12 (Release)

### P0 — Must fix before Play Store

All P0 blockers have been resolved:

| Issue | Resolution |
|---|---|
| ~~Release signing uses debug keystore~~ | `signingConfigs.release` reads from `gradle.properties` with `hasProperty` guard; falls back to debug for local dev builds |
| ~~ProGuard/R8 disabled~~ | `enableProguardInReleaseBuilds = true`; 63-line `proguard-rules.pro` covers RN, Hermes, MediaPipe, WorkManager, MMKV, SQLite, Reanimated, Gesture Handler, Notifee, Screens |

### P1 — Should fix before release

| Issue | File | Status |
|---|---|---|
| ~~No `google-services.json`~~ | — | Firebase not used; no action needed |
| ~~Version code/name are defaults~~ | `build.gradle:82-83` | Set to `100000` / `"1.0.0"`; `package.json` synced |
| ~~Hermes compiler path hardcoded~~ | `build.gradle:47` | Line is commented out; RN 0.84 Gradle plugin finds hermesc automatically |
| ~~x86/x86_64 architectures included~~ | `gradle.properties:30` | Limited to `armeabi-v7a,arm64-v8a` for Play Store release |

### P2 — Technical debt (non-blocking)

| Issue | Details |
|---|---|
| Root `types.ts` is 1849 lines | Only ~10 of ~18 AUGE types extracted to shared-types |
| ~69 pre-existing TS errors in PWA | All in legacy components (MyProfileView, SplitAdvancedEditor, etc.) |
| ~~2 pre-existing mobile typecheck errors~~ | Fixed: `WellbeingOverview` now exported from `@kpkn/shared-types` |
| 2 of 6 shared-domain tests fail | Pre-existing test logic issues |
| No component/screen render tests | 0% coverage on screens/ and components/ |
| App.test.tsx always fails | RNGestureHandlerModule native mock issue |

---

## 6. Step 12 Action Items

### 6.1 Release Signing (Play App Signing — Upload Key) — DONE

Upload keystore generated and configured:
- Keystore: `apps/mobile/android/app/kpkn-upload.keystore`
- Alias: `kpkn-upload`
- Algorithm: RSA 2048-bit, PKCS12
- Subject: CN=KPKNFit, OU=Mobile, O=KPKN, L=Santiago, ST=RM, C=CL
- Validity: 10000 days (expires ~2053)
- `gradle.properties` KPKN_RELEASE_* variables filled and active
- AAB signed and verified with `jarsigner`

### 6.2 ProGuard / R8 Rules — DONE

`enableProguardInReleaseBuilds = true` with 63-line `proguard-rules.pro` covering all native dependencies.

### 6.3 Version Sync — DONE

- `versionCode 100000` (scheme: major*100000 + minor*1000 + patch)
- `versionName "1.0.0"`
- `package.json` version synced to `"1.0.0"`

### 6.4 Hermes Path Fix — DONE

Line 47 in `build.gradle` is already commented out. RN 0.84+ Gradle plugin finds `hermesc` automatically.

### 6.5 Architecture — DONE

`gradle.properties` limited to `armeabi-v7a,arm64-v8a` (ARM only) for Play Store release.

### 6.6 Build AAB — DONE

```
Output: apps/mobile/android/app/build/outputs/bundle/release/app-release.aab
Size: ~499 MB (includes kpknLocalAiPack asset pack with FunctionGemma 270M model)
Signed: CN=KPKNFit, OU=Mobile, O=KPKN, L=Santiago, ST=RM, C=CL (SHA384withRSA)
Build time: 47s (incremental) / 5m24s (clean)
```

### 6.6 Pre-release Validation

1. Run full test suite: `npx jest --coverage` (all 160 tests pass)
2. Run mobile typecheck: `npm --workspace @kpkn/mobile run typecheck`
3. Install AAB on physical device via `bundletool`
4. Run through all 6 manual sign-off items in cutoverStore
5. Verify all 11 system checks pass on device
6. Confirm cutover stage reaches `ready-for-cutover`

---

## 7. Architecture Reference

```
kpkn-fit-(beta-test)/
├── apps/mobile/                    # React Native app
│   ├── src/
│   │   ├── screens/               # 6 screens (Home, Workout, SessionDetail, Nutrition, Progress, Settings)
│   │   ├── stores/                # 12 Zustand stores
│   │   ├── services/              # 11 services
│   │   ├── modules/               # 4 native module bridges (localAi, widgets, background, migrationBridge)
│   │   ├── navigation/            # React Navigation (bottom tabs + workout stack)
│   │   ├── storage/               # MMKV + SQLite
│   │   ├── components/            # ScreenShell, ErrorBoundary, PrimaryButton
│   │   ├── types/                 # workout.ts, nutrition.ts
│   │   └── utils/                 # generateId, homeHelpers
│   ├── android/                   # Android native (Java modules, Gradle config)
│   ├── jest.config.js             # V8 coverage, thresholds
│   ├── jest.setup.js              # MMKV/SQLite/Notifee mocks
│   └── package.json               # @kpkn/mobile, RN 0.84.1
├── packages/
│   ├── shared-types/              # Cross-platform types (auge, workout, nutrition, migration, wellbeing)
│   ├── shared-domain/             # AUGE engine, nutrition analysis, food catalog
│   └── design-tokens/             # Colors, spacing, typography for RN
├── services/                      # PWA services (AUGE facade, AI providers, etc.)
├── stores/                        # PWA Zustand stores
├── components/                    # PWA components (100+)
├── backend/                       # Optional FastAPI server
└── docs/                          # Documentation
```

---

## 8. Decision Log

| Decision | Rationale |
|---|---|
| V8 coverage provider instead of babel-istanbul | `babel-plugin-istanbul` crashes on Windows due to `minimatch` v9 incompatibility in `test-exclude` |
| No component/screen render tests | Screens are thin UI layers; business logic is tested via store/service tests. Render tests need RN testing infra improvements (gesture handler mocks, navigation mocks) |
| Jefe AI directly wrote test code | Worker AI (Qwen/Gemini) produced incorrect mocks/types repeatedly; direct implementation was more efficient |
| `cutoverStore` is dev-only UI | Readiness panel only appears in Settings when `__DEV__` is true |
| 6 screens for MVP | Core fitness tracking loop (home → workout → nutrition → progress → settings). Advanced features (wiki, coach, social) deferred |
