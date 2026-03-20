# PWA → React Native Master Parity Matrix

**Last updated:** 2026-03-19
**Baseline:** dirty worktree (handoff-approved)
**PWA frozen:** 2026-03-19

---

## Legend

| Status | Meaning |
|--------|---------|
| `parity` | RN equivalent exists and covers PWA behavior |
| `partial` | RN equivalent exists but missing features or visual fidelity |
| `missing` | No RN equivalent exists |
| `native-adapter` | RN has platform-specific implementation that differs intentionally |
| `extract-shared` | Logic duplicated; should be extracted to shared package |
| `dead-confirmed` | PWA file exists but is unused/orphaned |

---

## 1. Routes / Views

All 51 PWA `View` values mapped against RN navigation.

| PWA View | PWA Path | RN Stack | RN Screen | RN File | Status | Notes |
|----------|----------|----------|-----------|---------|--------|-------|
| `home` | `/` | Home | HomeMain | `screens/HomeScreen.tsx` | parity | |
| `auth` | `/auth` | Home | Auth | `screens/Home/AuthScreen.tsx` | parity | |
| `athlete-id` | `/profile/athlete-id` | Profile | AthleteID | `screens/Profile/AthleteIDScreen.tsx` | parity | |
| `my-rings` | `/rings` | Rings | Rings | `screens/Rings/RingsScreen.tsx` | parity | |
| `programs` | `/programs` | Workout | ProgramsList | `screens/Programs/ProgramsScreen.tsx` | parity | |
| `program-detail` | `/programs/:id` | Workout | ProgramDetail | `screens/Workout/ProgramDetailScreen.tsx` | parity | |
| `program-editor` | `/programs/:id/edit` | Workout | MacrocycleEditor / ProgramWizard | `screens/Workout/MacrocycleEditorScreen.tsx` / `ProgramWizardScreen.tsx` | parity | Resolves based on programId presence |
| `session-editor` | `/session-editor` | Workout | SessionEditor | `screens/Workout/SessionEditorScreen.tsx` | parity | Missing from AppNavigator linking config |
| `workout` | `/workout` | Workout | WorkoutMain | `screens/Workout/WorkoutScreen.tsx` | parity | |
| `progress` | `/progress` | Profile | ProgressOverview | `screens/Progress/ProgressScreen.tsx` | parity | |
| `settings` | `/settings` | Settings | Settings | `screens/Settings/SettingsScreen.tsx` | parity | |
| `coach` | `/coach` | Coach | CoachChat | `screens/Coach/CoachChatScreen.tsx` | partial | Visual fidelity needs tightening |
| `log-hub` | `/log-hub` | Workout | LogHub | `screens/Workout/LogHubScreen.tsx` | parity | |
| `achievements` | `/achievements` | Home | Achievements | `screens/Home/AchievementsScreen.tsx` | parity | |
| `log-workout` | `/log-workout` | Workout | LogWorkout | `screens/Workout/LogWorkoutScreen.tsx` | parity | |
| `kpkn` | `/kpkn` | Wiki | WikiHome | `screens/Wiki/WikiHomeScreen.tsx` | parity | Maps to wiki-home |
| `ai-art-studio` | `/ai-art-studio` | Home | AIArtStudio | `screens/Home/AIArtStudioScreen.tsx` | partial | Provider integration incomplete |
| `body-lab` | `/body-lab` | Profile | BodyLab | `screens/Profile/BodyLabScreen.tsx` | parity | |
| `mobility-lab` | `/mobility-lab` | Wiki | WikiMobility | `screens/Wiki/WikiMobilityScreen.tsx` | parity | |
| `training-purpose` | `/training-purpose` | Coach | TrainingPurpose | `screens/Coach/TrainingPurposeScreen.tsx` | parity | |
| `exercise-database` | `/exercise-database` | Workout | ExerciseDatabase | `screens/Exercise/ExerciseDatabaseScreen.tsx` | parity | |
| `food-database` | `/food-database` | Nutrition | FoodDatabase | `screens/Nutrition/FoodDatabaseScreen.tsx` | parity | |
| `smart-meal-planner` | `/smart-meal-planner` | Nutrition | MealPlanner | `screens/Nutrition/MealPlannerScreen.tsx` | parity | |
| `exercise-detail` | `/kpkn/exercise/:id` | Workout | ExerciseDetail | `screens/Exercise/ExerciseDetailScreen.tsx` | parity | |
| `muscle-group-detail` | `/kpkn/muscle/:id` | Wiki | WikiMuscleDetail | `screens/Wiki/WikiMuscleDetailScreen.tsx` | parity | |
| `body-part-detail` | `/kpkn/body-part/:id` | Wiki | BodyPartDetail | `screens/Wiki/BodyPartDetailScreen.tsx` | parity | |
| `muscle-category` | `/kpkn/category/:name` | Wiki | MuscleCategory | `screens/Wiki/MuscleCategoryScreen.tsx` | parity | |
| `chain-detail` | `/kpkn/chain/:id` | Wiki | WikiChainDetail | `screens/Wiki/WikiChainDetailScreen.tsx` | parity | |
| `joint-detail` | `/kpkn/joint/:id` | Wiki | WikiJointDetail | `screens/Wiki/WikiJointDetailScreen.tsx` | parity | |
| `tendon-detail` | `/kpkn/tendon/:id` | Wiki | WikiTendonDetail | `screens/Wiki/WikiTendonDetailScreen.tsx` | parity | |
| `movement-pattern-detail` | `/kpkn/pattern/:id` | Wiki | WikiPatternDetail | `screens/Wiki/WikiPatternDetailScreen.tsx` | parity | |
| `wiki-home` | `/wiki` | Wiki | WikiHome | `screens/Wiki/WikiHomeScreen.tsx` | parity | |
| `wikilab-biomechanics` | `/wiki/biomechanics` | Wiki | WikiBiomechanics | `screens/Wiki/WikiBiomechanicsScreen.tsx` | parity | |
| `nutrition` | `/nutrition` | Nutrition | NutritionDashboard | `screens/Nutrition/NutritionDashboardScreen.tsx` | partial | Visual tightening needed |
| `food-detail` | `/food-database` | Nutrition | FoodDatabase | `screens/Nutrition/FoodDatabaseScreen.tsx` | partial | Bug: SIMPLE_TAB_TARGETS routes to FoodDatabase instead of FoodDetail |
| `session-detail` | `/session-detail` | Workout | SessionDetail | `screens/Workout/SessionDetailScreen.tsx` | parity | |
| `tasks` | `/tasks` | Home | Tasks | `screens/Home/TasksScreen.tsx` | parity | |
| `social-feed` | `/` | Home | SocialFeed | `screens/Home/SocialFeedScreen.tsx` | parity | PWA maps to `/` too |
| `athlete-profile` | `/` | Profile | ProfileMain | `screens/Profile/ProfileScreen.tsx` | parity | PWA maps to `/` |
| `recovery` | `/recovery` | Home | Recovery | `screens/Home/RecoveryScreen.tsx` | parity | |
| `sleep` | `/sleep` | Home | Sleep | `screens/Home/SleepScreen.tsx` | parity | |
| `program-metric-volume` | `/programs/:id/metric/volume` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-strength` | `/programs/:id/metric/strength` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-density` | `/programs/:id/metric/density` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-frequency` | `/programs/:id/metric/frequency` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-banister` | `/programs/:id/metric/banister` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-recovery` | `/programs/:id/metric/recovery` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-adherence` | `/programs/:id/metric/adherence` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `program-metric-rpe` | `/programs/:id/metric/rpe` | Workout | ProgramMetricDetail | `screens/Workout/ProgramMetricDetailScreen.tsx` | parity | |
| `body-progress` | `/body-progress` | Profile | BodyProgress | `screens/Progress/BodyProgressScreen.tsx` | parity | |
| `home-card-page` | `/card/:type` | Home | HomeMain | `screens/HomeScreen.tsx` | partial | Falls through to Home; no dedicated card-page screen |

**Route Coverage: 46/51 parity, 5/51 partial, 0 missing**

---

## 2. Navigation Infrastructure

| PWA File | RN Equivalent | Status | Notes |
|----------|---------------|--------|-------|
| `routes/navigation.ts` | `apps/mobile/src/routes/navigation.ts` + `apps/mobile/src/navigation/navigationRef.ts` | parity | Thin bridge layer + full legacy resolver |
| `routes/router.ts` | `apps/mobile/src/routes/router.ts` | parity | TanStack-style router shim |
| `types.ts` (View union) | `apps/mobile/src/navigation/types.ts` (param lists) | parity | Structural adaptation (stack params vs flat views) |

---

## 3. Shared Business Logic

| Domain | PWA File | RN Equivalent | Status | Action |
|--------|----------|---------------|--------|--------|
| AUGE engine | `services/auge.ts` | `apps/mobile/src/services/augeEngine.ts` | extract-shared | Core fatigue/recovery logic duplicated |
| Analysis | `services/analysisService.ts` | `apps/mobile/src/services/analysisService.ts` | extract-shared | Historical fatigue data duplicated |
| Recovery | `services/recoveryService.ts` | `apps/mobile/src/services/recoveryService.ts` | extract-shared | Systemic fatigue calculation duplicated |
| AI gateway | `services/aiService.ts` | `apps/mobile/src/services/aiService.ts` | partial | Multi-provider routing; RN needs Gemini/GPT/DeepSeek wiring |
| Nutrition parser | `services/aiNutritionParser.ts` | `apps/mobile/src/services/aiNutritionParser.ts` | parity | Local AI pipeline adapted |
| Fatigue | `services/fatigueService.ts` | `apps/mobile/src/services/fatigueService.ts` | extract-shared | Session stress calculation duplicated |
| Notifications | `services/notificationService.ts` | `apps/mobile/src/services/notificationService.ts` | native-adapter | Notifee-based, platform-specific |
| Storage | `services/storageService.ts` | `apps/mobile/src/services/storageService.ts` | native-adapter | MMKV-based vs IndexedDB |
| Nutrition analysis | — | `packages/shared-domain/src/nutrition/` | parity | Extracted to shared package |
| Nutrition types | — | `packages/shared-types/src/nutrition.ts` | parity | Extracted to shared package |

---

## 4. Zustand Stores

| PWA Store | RN Store | Status | Notes |
|-----------|----------|--------|-------|
| `stores/settingsStore.ts` | `apps/mobile/src/stores/settingsStore.ts` | parity | MMKV-backed |
| `stores/programStore.ts` | `apps/mobile/src/stores/programStore.ts` | parity | |
| `stores/workoutStore.ts` | `apps/mobile/src/stores/workoutStore.ts` | parity | In-flight dirty |
| `stores/bodyStore.ts` | `apps/mobile/src/stores/bodyStore.ts` | parity | |
| `stores/nutritionStore.ts` | `apps/mobile/src/stores/nutritionStore.ts` | parity | |
| `stores/wellbeingStore.ts` | `apps/mobile/src/stores/wellbeingStore.ts` | parity | |
| `stores/exerciseStore.ts` | `apps/mobile/src/stores/exerciseStore.ts` | parity | |
| `stores/uiStore.ts` | `apps/mobile/src/stores/uiStore.ts` | parity | |
| `stores/authStore.ts` | `apps/mobile/src/stores/authStore.ts` | parity | |
| `stores/mealTemplateStore.ts` | `apps/mobile/src/stores/mealTemplateStore.ts` | parity | |
| — | `apps/mobile/src/stores/augeRuntimeStore.ts` | native-adapter | RN-specific AUGE runtime |
| — | `apps/mobile/src/stores/cutoverStore.ts` | native-adapter | RN-specific cutover tracking |

---

## 5. UI Components — Major Cards/Surfaces

| PWA Component | RN Equivalent | Status | Notes |
|---------------|---------------|--------|-------|
| `components/MyRingsView.tsx` | `screens/Rings/RingsScreen.tsx` | parity | Adjusted; uses BatteryRingCard + stat cards |
| `components/BatteryRingCard.tsx` | `apps/mobile/src/components/activity/BatteryRingCard.tsx` | parity | Skia-based rings |
| `components/ActivityRingsCard.tsx` | `apps/mobile/src/components/activity/ActivityRingsCard.tsx` | parity | Training/nutrition/recovery rings |
| `components/StreakCard.tsx` | `apps/mobile/src/components/activity/StreakCard.tsx` | parity | |
| `components/WeeklyFatigueCard.tsx` | `apps/mobile/src/components/analytics/WeeklyFatigueCard.tsx` | parity | |
| `components/SleepTrackerWidget.tsx` | `apps/mobile/src/components/analytics/SleepTrackerWidget.tsx` | parity | |
| `components/TabBar.tsx` | `apps/mobile/src/components/navigation/KpknBottomBar.tsx` | parity | M3 pill-shaped, 6-tab layout |
| `components/SettingsComponent.tsx` | `screens/Settings/SettingsScreen.tsx` | parity | Tab-less flat layout adapted for RN |
| `components/CoachChatView.tsx` | `screens/Coach/CoachChatScreen.tsx` | partial | Needs visual tightening |
| `components/home/AugeTelemetryPanel.tsx` | `components/auge/AugeStatusCard.tsx` | partial | Simplified; missing combined/individual ring toggle |
| `components/NutritionDashboard.tsx` | `screens/Nutrition/NutritionDashboardScreen.tsx` | partial | Needs visual tightening |

---

## 6. Tests

| Test File | Status | Notes |
|-----------|--------|-------|
| `__tests__/navigation/navigationRef.test.ts` | passing | Legacy view resolution tests |
| `__tests__/navigation/routesCompat.test.ts` | passing | Router shim compatibility tests |
| `__tests__/screens/settingsScreen.test.tsx` | passing | Settings surface tests |
| `__tests__/screens/ringsSurface.test.tsx` | passing | Rings screen visual tests |
| `__tests__/screens/profileSurface.test.tsx` | passing | Profile screen tests |
| `__tests__/screens/wikiSecondaryViews.test.tsx` | passing | Wiki secondary views |
| All existing suites (51 total) | passing | 274 tests |

---

## 7. Known Bugs / Blockers

| ID | Area | Description | Severity |
|----|------|-------------|----------|
| B1 | Navigation | `food-detail` view routes to FoodDatabase instead of FoodDetail in SIMPLE_TAB_TARGETS | medium |
| B2 | Navigation | `session-editor`, `ProgramWizard`, `SplitEditor` missing from AppNavigator linking config | low (works via RN navigate) |
| B3 | AI Art | AI image generation/editing parity incomplete — provider contracts differ | high |
| B4 | Home | `home-card-page` falls through to HomeMain — no dedicated card page screen | low |
| B5 | AUGE | AUGE engine logic duplicated between PWA and RN — needs shared extraction | medium |
| B6 | Visual | CoachChat, NutritionDashboard, AIArtStudio need visual tightening vs PWA | medium |

---

## 8. Summary

- **Route coverage:** 46/51 parity, 5 partial, 0 missing
- **Navigation infrastructure:** fully bridged via legacy view resolver
- **Stores:** all 10 core stores adapted, 2 RN-specific stores added
- **Shared packages:** `shared-types`, `shared-domain`, `design-tokens` properly consumed
- **Tests:** 51 suites / 274 tests passing
- **Top priority fixes:** B1 (food-detail route), B2 (missing linking paths)
