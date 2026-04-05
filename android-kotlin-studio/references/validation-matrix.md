# Validation Matrix

Use this reference to choose the narrowest useful Android validation command before expanding scope. Adapt the launcher to the environment: use `./gradlew` on Unix-like shells and `gradlew.bat` on Windows shells.

## Source-only Kotlin change inside one Android module

- Start with `:module:compileDebugKotlin` when the edit is source-only and does not touch manifests or resources.
- Use `:module:assembleDebug` instead when the module contains Android resources, generated code inputs, or UI wiring likely to surface only during assemble.
- Expand to one consumer module compile if the edited module exposes APIs that other modules use.

## Compose or UI change

- Start with `:module:assembleDebug`.
- Add `:module:lint` when colors, strings, accessibility, themes, or resources changed.
- Add nearby unit/UI tests if the repository already has them for that screen or ViewModel.
- If previews are important in the repo, open or compile the changed preview-bearing file path as part of manual review.

## Views/XML, resources, or manifest change

- Start with `:module:assembleDebug`.
- Expand to the relevant flavor/build-type assemble task if the edit is variant-specific.
- Add `:module:lint` when resources, themes, permissions, or exported components changed.

## ViewModel, repository, mapper, or state-flow change

- Start with the narrowest compile or unit-test task covering the touched module.
- Prefer `:module:testDebugUnitTest` when unit tests exist nearby.
- Expand to a consuming UI module assemble when public interfaces or state contracts changed.

## Shared library module change

- Start with the library module compile or unit-test task.
- Expand to one consumer module compile or assemble if the library API changed.
- Expand further only when the shared module is used across many entry points or flavors.

## Gradle or dependency-management change

- Start with the smallest task that exercises the changed configuration, usually an affected module `assembleDebug`.
- If shared build logic, version catalogs, root plugins, or convention plugins changed, run at least one representative consumer-module assemble.
- Expand to tests or lint only when the configuration change impacts them directly.

## Navigation, Safe Args, Data Binding, Hilt, Room, or KSP/KAPT input change

- Start with `:module:assembleDebug` because generated sources often surface here.
- Expand to a consumer module assemble if generation spans modules.
- Use stacktraces only after the narrow assemble output stops being informative.

## Release-only, shrinker, or signing-sensitive change

- Start with the relevant release task such as `:app:assembleRelease` or `:app:minifyReleaseWithR8`.
- Expand to variant-specific tasks if flavors or signing configs differ.
- Do not assume a debug build validates release behavior.

## When not to expand

- Do not jump straight to a full monorepo build for a local source edit inside one module.
- Do not run full UI/instrumentation suites unless the change touches instrumentation paths or the local signal is insufficient.
- Do not widen validation because of habit; widen it because the blast radius increased.
