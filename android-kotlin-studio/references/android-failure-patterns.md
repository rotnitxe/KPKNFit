# Android Failure Patterns

Use this reference when a build or runtime issue is Android-specific, variant-specific, or not obvious from the first error line.

## Build and dependency failures

- `Could not resolve all files for configuration ...`
  - Likely causes: wrong repository setup, version skew, incorrect dependency scope, network/auth issues, or a typo in a coordinate.
  - First checks: the edited module dependency block, version catalog entry, repository declarations, and whether the dependency belongs in `implementation`, `api`, `ksp`, `kapt`, `testImplementation`, or debug-only scope.

- `Duplicate class ... found in modules ...`
  - Likely causes: two transitive artifacts providing the same classes or mismatched BOM/version usage.
  - First checks: recently changed dependency declarations, exclusion rules, and version alignment across modules.

- `Namespace not specified`, plugin application errors, or sync-only failures
  - Likely causes: incorrect plugin order, missing Android plugin configuration, convention plugin drift, or root/shared build logic changes.
  - First checks: `settings.gradle(.kts)`, shared convention plugins, and the edited module build file.

## Manifest and resource failures

- `Manifest merger failed`
  - Likely causes: conflicting attributes, missing `android:exported`, duplicate providers, placeholder mismatches, or flavor-specific manifest collisions.
  - First checks: merged-manifest error details, affected source-set manifests, and manifest placeholder declarations in Gradle.

- `AAPT: error: resource ... not found` or unresolved `R`
  - Likely causes: broken XML, wrong resource name, wrong package namespace, missing qualifier variant, or an earlier resource compilation error.
  - First checks: the first `aapt2` error, recent XML/resource edits, and whether the referenced resource exists in the correct source set.

## Generated code failures

- Hilt, Room, KSP, KAPT, Data Binding, or Safe Args generation failures
  - Likely causes: incorrect annotations, missing plugin wiring, source incompatibility, or an upstream compile/resource error.
  - First checks: the annotated source, generated-code plugin configuration, and the earliest generation-related error rather than the generated output file.

- `BuildConfig` or navigation classes missing
  - Likely causes: build feature disabled, wrong plugin application, namespace/package mismatch, or generation blocked by an earlier error.
  - First checks: module build features, navigation plugin usage, package declarations, and upstream compile/resource errors.

## Compose-specific failures

- Compose compiler/runtime incompatibility or preview-only failure
  - Likely causes: mismatched Compose compiler/runtime versions, missing tooling dependency, unsupported preview setup, or APIs requiring runtime state not available in preview.
  - First checks: Compose version alignment, tooling dependencies, preview wrappers, and whether the preview depends on DI or real repositories.

- Recomposition loops, repeated side effects, or flaky UI state
  - Likely causes: unstable `LaunchedEffect` keys, collecting flows in the wrong place, mutable state recreated in composition, or callbacks triggering state changes during composition.
  - First checks: effect keys, `remember` usage, lifecycle-aware collection, and whether work belongs in the ViewModel instead.

## Runtime and lifecycle failures

- Crash after rotation, background/foreground, or navigation
  - Likely causes: state stored in the wrong owner, missing `SavedStateHandle`, collector scope issues, or assumptions about a single lifecycle pass.
  - First checks: state owner, lifecycle collection pattern, navigation arguments, and configuration-change handling.

- `IllegalStateException`, `ClassCastException`, or `NoSuchMethodError` at runtime
  - Likely causes: incorrect assumptions about lifecycle state, unsafe casting, dependency version mismatch, or release/debug divergence.
  - First checks: the exact stacktrace frame, recent dependency edits, and whether the failure reproduces in only one variant.

## Release-only failures

- Minified release crashes or missing classes
  - Likely causes: R8 removing reflection-sensitive code, missing keep rules, serializer/proxy issues, or feature flags/resources differing by variant.
  - First checks: recent release/shrinker edits, affected libraries, and whether debug succeeds while release fails.

- Signing or packaging failures
  - Likely causes: changed signing configs, duplicate assets, native library packaging conflicts, or variant-specific output configuration.
  - First checks: signing blocks, packaging options, ABI filters, and the exact failing variant task.
