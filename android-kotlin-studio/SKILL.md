---
name: android-kotlin-studio
description: Production-focused Android engineering guidance for Kotlin, Android Studio, Jetpack Compose, ViewModel, StateFlow/coroutines, Gradle Kotlin DSL, build debugging, targeted refactors, and code review in real repositories. Use when Codex needs to inspect, modify, debug, validate, or review Android app codebases, including mixed Compose + Views, legacy + modern modules, manifest/resource/navigation-sensitive changes, or Gradle/build/runtime failures.
---

# Android Kotlin Studio

Use this skill to work safely in real Android repositories, not toy apps. Optimize for idiomatic Kotlin, small reversible edits, repository-local patterns, Android Studio compatibility, and disciplined validation.

## Workflow

1. Inspect repository shape before editing.
   - Find modules, `settings.gradle(.kts)`, root build files, version catalogs, convention plugins, `buildSrc`, and CI/build scripts when relevant.
   - Detect whether the touched surface uses Compose, Views/XML, or a mixed UI stack.
   - Detect the dominant architecture by locating `ViewModel`, repository/use case layers, presenters, reducers, stores, or custom controllers.
   - Detect async and state primitives such as `StateFlow`, `Flow`, LiveData, RxJava, callbacks, WorkManager, Paging, and SavedStateHandle.
   - Detect DI/build tooling such as Hilt, Dagger, Koin, KSP, KAPT, Room, Navigation, Data Binding, or feature-specific plugins.
2. Match the local pattern before inventing a new one.
   - Find at least one analogous screen, ViewModel, repository, test, or Gradle declaration in the repo.
   - Reuse local naming, packages, resource conventions, navigation style, and error modeling.
3. Choose the smallest safe change set.
   - Prefer local edits over cross-cutting rewrites.
   - Preserve module boundaries and architecture unless the task explicitly requires deeper change.
4. Validate with the narrowest useful command first.
   - Start with module-scoped compile, assemble, unit test, or lint tasks.
   - Expand only when touched files or risk justify it.
5. Summarize what changed, what was validated, remaining risks, and anything that may need manual review in Android Studio.

Read only the relevant reference file when needed:
- Read [references/validation-matrix.md](references/validation-matrix.md) when deciding validation scope.
- Read [references/compose-review-checklist.md](references/compose-review-checklist.md) when touching Compose or mixed UI.
- Read [references/android-failure-patterns.md](references/android-failure-patterns.md) when debugging Android-specific build or runtime failures.

## Core stance

- Prefer safe, reversible edits over cleanup-driven rewrites.
- Prefer idiomatic Kotlin and clarity over cleverness.
- Respect imperfect repository architecture instead of trying to redesign it mid-task.
- Keep UI controllers thin and business logic out of UI layers.
- Avoid new libraries unless the repository lacks an existing, suitable tool.
- Assume the project is opened in Android Studio and should remain comfortable to use there.

## Risk-aware editing strategy

- Start with read-only inspection and a concrete hypothesis before editing.
- Contain changes inside the affected module whenever possible.
- Touch shared Gradle logic, manifests, navigation, resources, DI wiring, or base classes only when necessary.
- Split risky work into small, reviewable steps when multiple subsystems are involved.
- Prefer adapting an existing ViewModel/repository/use case pattern over introducing a fresh layer.
- If the repository mixes legacy and modern patterns, follow the nearest stable local pattern instead of the most fashionable one.
- If fixing the issue would require a broad refactor, stop and note the trade-off instead of silently widening scope.
- Treat generated-code inputs as high-risk surfaces: annotations, manifests, resources, Gradle plugins, version catalogs, schema files, and navigation definitions.

## Kotlin rules

- Prefer `val` by default and use `var` only when mutation is intrinsic to the model.
- Handle nullability explicitly with early returns, `?.`, `?:`, `requireNotNull`, or well-named guards before considering `!!`.
- Use `data class` for immutable UI or domain models when value semantics matter.
- Use `sealed class` or `sealed interface` for states, results, or one-of variants when it improves exhaustiveness and readability.
- Keep functions small, direct, and single-purpose.
- Use extension functions only when they improve readability and stay close to the owning type or domain.
- Prefer descriptive names and readable control flow over compact but opaque chaining.
- Avoid speculative abstraction, giant utility files, and "helper" layers without repeated need.
- Keep mapping, persistence, network, presentation, and navigation responsibilities separated enough to stay understandable.
- Follow the repository's naming, package, and test conventions before applying personal preferences.

## Architecture rules

- Keep Activities and Fragments thin; use them for lifecycle wiring, permissions, entry-point setup, and navigation hand-off.
- Keep presentation logic in `ViewModel` or the repository's equivalent presentation layer.
- Keep business rules in repositories, use cases, interactors, or existing domain services according to the local pattern.
- Distinguish UI state, domain state, and data-transfer concerns.
- Avoid placing navigation decisions deep inside leaf composables or low-level mappers.
- Reuse the repository's dependency injection pattern and module boundaries.
- Improve imperfect architecture only as far as needed to make the touched path safer and easier to verify.
- Do not introduce clean-architecture layers where the repository does not already use them unless the task explicitly asks for it.

## Compose and mixed UI rules

- Prefer Compose for new UI only if the application or module already uses Compose.
- If the repository is primarily Views/XML, do not force Compose for a local fix or small feature.
- In mixed codebases, keep interop localized; do not rewrite neighboring screens to normalize the stack.
- Write small composables with a clear purpose.
- Hoist state when doing so clarifies ownership or improves testability; do not hoist everything mechanically.
- Separate stateful and stateless composables when that separation improves clarity.
- Use side-effect APIs intentionally: `LaunchedEffect`, `DisposableEffect`, `rememberUpdatedState`, `SideEffect`, `produceState`, or lifecycle-aware collection.
- Keep heavy logic, navigation, data loading, and repository calls out of leaf composables.
- Add or update previews when they provide real signal and the project already uses them or the UI change is non-trivial.
- Respect the repository's theme wrappers, string resources, icons, accessibility patterns, and design tokens.
- When editing Views/XML, reuse the local binding, inflation, adapter, and navigation patterns instead of layering ad hoc Compose-like state handling on top.

## Coroutines and state rules

- Prefer coroutines, `Flow`, and `StateFlow` when the repository already uses them.
- Do not add new callback-based async code to a coroutine-first codebase without a strong reason.
- Launch asynchronous work from lifecycle-aware scopes such as `viewModelScope`, `lifecycleScope`, or existing abstractions.
- Model loading, success, empty, and error states explicitly when the feature requires them.
- Keep one-shot events deliberate and consistent with the local event pattern.
- Avoid collecting flows in unstable scopes or recreating collectors on every recomposition.
- Keep dispatchers and cancellation boundaries explicit where the repository expects them.
- Avoid doing heavy work in composables, adapters, or UI callbacks when it belongs in the presentation or data layer.

## Gradle change safety rules

- Inspect the existing build system before editing: `settings.gradle(.kts)`, root build files, version catalogs, convention plugins, `buildSrc`, module build files, and relevant CI scripts.
- Reuse the repository's dependency declaration style.
- Respect version catalogs and central dependency management; do not hardcode versions in modules when the repo centralizes them.
- Add dependencies only when the feature truly needs them and no existing dependency already covers the use case.
- Avoid unrelated version bumps, plugin churn, or "cleanup" during feature work.
- Keep plugin and dependency edits minimal and local.
- Consider build types, flavors, namespaces, manifest placeholders, packaging options, signing setup, and generated sources before editing build logic.
- Do not break module boundaries by importing app-only APIs into shared libraries.
- Treat KSP/KAPT/Hilt/Room/Data Binding/Safe Args/Compose compiler settings as fragile surfaces and change them carefully.
- If a Gradle change affects shared build logic or multiple modules, widen validation accordingly.

## Android Studio awareness

- Keep file placement, imports, package declarations, and resource usage friendly to Android Studio navigation and refactoring.
- Preserve previewability when practical.
- Keep manifest edits merge-safe and variant-aware.
- Respect resource qualifiers, naming conventions, and localization patterns already used by the repo.
- Consider navigation graphs, deep links, Safe Args, and generated code if the repo uses them.
- Consider R8/ProGuard only when touching reflection, serialization, DI, or release-only behavior.
- Avoid leaving the repo in a state that only "should compile after sync"; validate real tasks when possible.

## Heuristics for deciding when not to refactor

- Do not refactor unrelated files to satisfy personal style preferences.
- Do not replace the local architecture during a small bug fix.
- Do not migrate Views to Compose, LiveData to Flow, Dagger to Hilt, or Groovy to Kotlin DSL unless the task explicitly includes that migration.
- Do not rename packages, split classes, or move modules unless doing so is necessary for the requested change.
- Do not clean up surrounding code if it increases blast radius without improving correctness, safety, or maintainability of the requested work.
- Do not "modernize" imperfect legacy modules unless the user explicitly wants modernization work.

## When to ask for or note assumptions

- Proceed autonomously when the safest local pattern is clear from repository context.
- Note assumptions explicitly when flavor behavior, backend contracts, data migration expectations, navigation behavior, or package ownership are ambiguous but a reasonable local default exists.
- Ask only when the decision materially changes product behavior, persisted data, public APIs, build outputs, or module boundaries in a non-obvious way.
- If you proceed with an assumption, choose the most local and reversible option, then call it out in the summary.
- If validation cannot run because the Android SDK, emulator, secrets, or required tooling are unavailable, say so plainly and identify the narrowest next check.

## Do not do these things

- Do not change several Gradle files to "fix" a missing dependency before identifying which module actually owns it.
- Do not silence nullability or type issues with `!!`, unsafe casts, or broad catch-all error handling.
- Do not move business logic into composables, Activities, Fragments, adapters, or XML binding expressions just to ship faster.
- Do not create a new base class, wrapper, or utility abstraction for a single call site.
- Do not add a library because it is familiar if Kotlin stdlib, AndroidX, or existing repo code already solves the problem.
- Do not touch `AndroidManifest.xml`, navigation graphs, `libs.versions.toml`, shared convention plugins, or shrinker rules casually.
- Do not treat the last line of a Gradle failure as the root cause without reading the first meaningful error.
- Do not claim success after editing files without at least one relevant validation step unless the environment blocks it and you state that clearly.
- Do not perform broad "cleanup" passes in mixed legacy modules during a small task.
- Do not ignore previews, resources, test fixtures, or flavor-specific code when a UI or build change obviously affects them.

## Review stance

- Focus review on correctness, lifecycle safety, threading, variant behavior, build risk, navigation, resources, and test adequacy.
- Prioritize regression risks and missing validation over style-only commentary.
- Call out Android-specific failure modes such as manifest merges, generated code, preview breakage, release-only shrinker issues, and state ownership drift.
- Prefer specific actionable findings over abstract best-practice lectures.

## Before editing

- Inspect module ownership, affected packages, and nearby patterns.
- Find at least one analogous implementation in the repository.
- Determine whether the change touches UI, state, Gradle, manifest, resources, navigation, DI, generated code, persistence, or tests.
- Decide the narrowest validation command that can confirm the change.
- Identify whether flavors, build types, or module APIs could change the behavior.

## Before modifying Gradle

- Confirm the build change is actually necessary.
- Check version catalogs, `buildSrc`, and convention plugins first.
- Search for the dependency or plugin already being declared elsewhere.
- Verify whether the change is app-only, library-only, debug-only, test-only, or variant-specific.
- Consider whether the change requires manifest placeholders, packaging options, shrinker rules, or KSP/KAPT wiring.
- Plan a validation step that covers the affected module and at least one consumer if APIs or shared build logic change.

## Before touching UI

- Detect whether the surface uses Compose, Views/XML, or interop.
- Reuse local theme, typography, spacing, icon, string, and navigation patterns.
- Keep business logic out of UI files.
- Check whether previews, bindings, adapters, navigation graphs, or resources must change together.
- Consider accessibility, state restoration, configuration changes, and localization impact.

## Before touching state management

- Identify the current state owner: `ViewModel`, presenter, reducer, controller, or screen-local state.
- Preserve existing state models unless a targeted change is necessary.
- Keep loading, error, and event state explicit and consistent with nearby code.
- Verify coroutine scope, dispatcher usage, and collection site.
- Check whether persistence, `SavedStateHandle`, or process death recovery already participates in the flow.

## Compose-specific review checklist

- Keep composables small, readable, and purpose-specific.
- Pass only the state and callbacks each composable actually needs.
- Use `remember` and `rememberSaveable` intentionally rather than defensively.
- Use stable item keys in lazy lists when identity matters.
- Keep `LaunchedEffect` keys precise and avoid effect restart loops.
- Prefer lifecycle-aware state collection when the repository already uses it.
- Avoid triggering navigation, repository calls, or analytics from deep leaf composables unless that is the established local pattern.
- Keep preview-only sample data local and lightweight.
- Reuse the repository's theme and design-system wrappers.
- Check recomposition-sensitive code for unnecessary object recreation, repeated work in composition, or hidden side effects.

## After editing

- Re-read the diff for accidental renames, import churn, formatting noise, or unrelated cleanup.
- Check package declarations, resource references, manifest hooks, navigation wiring, and generated-code inputs.
- Confirm new or changed public APIs are used consistently by callers.
- Remove dead code, temporary logging, and debugging artifacts.
- Keep comments rare and limited to genuinely non-obvious logic.

## Validation

- Start with the narrowest relevant command.
- Prefer module-scoped tasks before whole-repo builds.
- Use `./gradlew` or `gradlew.bat` depending on shell and platform.
- Typical commands to adapt to the repository:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :feature:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :feature:lint`
  - `./gradlew test`
- Expand validation when Gradle files, version catalogs, manifests, navigation, resources, DI wiring, generated code, or shared APIs change.
- Expand validation when multiple modules are affected or when the failure is variant-specific.
- If the change is tiny and isolated, do not jump straight to the full suite.
- If build configuration changed, run at least one compile or assemble path that exercises the edited configuration.

## Debugging build failures

- Read the first meaningful error, not just the final summary line.
- Separate root cause from cascading failures.
- Reproduce with the narrowest failing task.
- Check recent edits to Gradle, manifests, resources, generated-code inputs, and dependency wiring first.
- If the error mentions generated types such as `R`, `BuildConfig`, Hilt, Room, Safe Args, or KSP/KAPT output, inspect the upstream inputs that generate them.
- Use stacktraces or info logging only when the narrow task output is insufficient.
- Fix one root cause at a time and rerun the same narrow task before widening validation.
- Read [references/android-failure-patterns.md](references/android-failure-patterns.md) when the failure is Android-specific, variant-specific, or unclear.

## Debugging runtime/UI bugs

- Reproduce the problem path before editing.
- Decide whether the symptom is state, lifecycle, threading, navigation, resource, rendering, or data-mapping related.
- Inspect logs, crash traces, and the relevant state owner before refactoring.
- For UI glitches, check recomposition triggers, remembered state, layout constraints, resource qualifiers, and preview/runtime divergence.
- For data bugs, follow the path from source to mapper to ViewModel to UI.
- For lifecycle issues, inspect collector scope, `repeatOnLifecycle` usage, cancellation, and one-shot event handling.
- Change the smallest thing that tests the hypothesis, then validate again.

## Android-specific failure patterns

- Expect manifest merge failures, placeholder mismatches, exported-flag issues, duplicate providers, and variant-only manifest differences.
- Expect resource merge failures, duplicate names, wrong qualifiers, broken XML, and stale `R` generation after resource errors.
- Expect Compose compiler/runtime mismatches, preview-only failures, and tooling dependency gaps in mixed UI codebases.
- Expect KSP/KAPT/Hilt/Room/Data Binding/Safe Args failures to originate from source annotations or plugin wiring, not the generated code itself.
- Expect duplicate-class and dependency-resolution failures from version skew or misplaced dependency scopes.
- Expect navigation crashes from wrong arguments, graph registration mismatches, or fragment/composable destination wiring issues.
- Expect coroutine/lifecycle bugs from collecting in the wrong scope or retriggering work on recomposition.
- Expect release-only failures caused by R8/ProGuard, reflection-sensitive libraries, or flavor-specific packaging differences.
- Read [references/android-failure-patterns.md](references/android-failure-patterns.md) for common symptoms, likely owners, and first checks.

## Definition of done

- The change follows the repository's existing local patterns closely enough to feel native.
- Kotlin code is idiomatic, readable, and avoids unnecessary abstraction.
- UI, presentation, domain, and data concerns remain separated enough for the repository's architecture.
- Gradle, manifests, navigation, resources, previews, and generated-code touchpoints have been considered when applicable.
- At least one relevant validation step has run, or the blocker is explicitly stated.
- The final summary names changed files, validation performed, remaining risks, and any manual review areas.
