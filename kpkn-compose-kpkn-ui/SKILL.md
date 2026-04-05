---
name: kpkn-compose-kpkn-ui
description: Repo-specific guidance for translating, designing, refining, and reviewing KPKN Compose UI in `android-native`. Use when Codex works on screen layout, interaction flows, visual hierarchy, cards, hero sections, rings, sheets, navigation-facing UI, or PWA-to-Compose UI translation and must preserve KPKN product intent without cloning the web.
---

# KPKN Compose UI

Use this skill when the task touches Compose UI, UX translation, screen structure, visual polish, or UI review for `android-native`.

This skill is not for generic "make it look nice" work. It is for making KPKN screens feel native to Android while still feeling like KPKN: bold, structured, mobile-first, Spanish-language, information-rich, and product-driven.

## Repo context

- The active Kotlin target is [../android-native](../android-native).
- Shared Compose foundations currently live in:
  - [../android-native/app/src/main/java/com/example/kpkn/ui/theme/Theme.kt](../android-native/app/src/main/java/com/example/kpkn/ui/theme/Theme.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/ui/theme/Color.kt](../android-native/app/src/main/java/com/example/kpkn/ui/theme/Color.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/ui/theme/Type.kt](../android-native/app/src/main/java/com/example/kpkn/ui/theme/Type.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/ui/components/SharedComponents.kt](../android-native/app/src/main/java/com/example/kpkn/ui/components/SharedComponents.kt)
- Important existing UI anchors include:
  - [../android-native/app/src/main/java/com/example/kpkn/screens/home/HomeScreen.kt](../android-native/app/src/main/java/com/example/kpkn/screens/home/HomeScreen.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/screens/home/HomeHeaderSection.kt](../android-native/app/src/main/java/com/example/kpkn/screens/home/HomeHeaderSection.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/screens/home/HomeRingsSection.kt](../android-native/app/src/main/java/com/example/kpkn/screens/home/HomeRingsSection.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionScreen.kt](../android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionScreen.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/screens/programdetail/ProgramDetailScreen.kt](../android-native/app/src/main/java/com/example/kpkn/screens/programdetail/ProgramDetailScreen.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/screens/programdetail/components/CompactHeroBanner.kt](../android-native/app/src/main/java/com/example/kpkn/screens/programdetail/components/CompactHeroBanner.kt)
- Repo conventions that matter:
  - [../memory/CONVENTIONS.md](../memory/CONVENTIONS.md)
  - [../CLAUDE.md](../CLAUDE.md)

Read only the relevant reference file when needed:

- Read [references/kpkn-visual-language.md](references/kpkn-visual-language.md) before making visual decisions.
- Read [references/compose-translation-rules.md](references/compose-translation-rules.md) before porting a UI flow from the PWA.
- Read [references/ui-review-checklist.md](references/ui-review-checklist.md) before reviewing or signing off a Compose surface.

## Core doctrine

- Translate product intent, not web layout.
- Preserve user jobs, hierarchy, and emotional tone.
- Keep KPKN bold and intentional, not generic Material boilerplate.
- Prefer Android-native interaction patterns over literal PWA cloning.
- Respect the current repo reality: Material 3 base, strong feature-local UI, and an imperfect but usable design system.
- Improve surgically. Do not stop to rebuild the global design system during a local screen task.

## What KPKN UI should feel like

- Mobile-first and thumb-friendly.
- Strong hierarchy with obvious primary actions.
- Bold headings, often heavy-weight and compact.
- Dense enough to feel powerful, but still readable on a phone.
- Structured in sections rather than giant unbroken forms.
- Expressive with rings, hero panels, chips, and status cues where they add meaning.
- Spanish in product-facing copy unless the task explicitly changes language.
- More "training product" than "generic dashboard kit".

## Current repo reality

- `Theme.kt` and `Color.kt` provide a Material 3 baseline, but the app still uses feature-local accents, gradients, and direct color values in key screens.
- `Type.kt` is still minimal, so many screens currently express hierarchy with local font weights and sizes.
- Home, nutrition, and program-detail surfaces already show the intended KPKN direction:
  - large greetings or hero headers
  - uppercase micro-labels
  - strong rounded corners
  - ring metaphors for progress/recovery
  - compact but expressive KPI presentation
- Do not "normalize" all of this into flat generic cards during a local edit.
- Do not introduce a new visual language that fights the current screens.

## KPKN signatures to preserve

- Rings and circular progress metaphors when the domain truly revolves around battery, readiness, or macro progress.
- Hero sections for high-value surfaces like home, nutrition, and program details.
- Strong rounded shapes, usually in the 16-28dp range depending on component size.
- Uppercase micro-labels or chips for status and section metadata.
- Bold Spanish labels and training-oriented terminology.
- Deliberate color accents by feature, not random rainbow decoration.
- Sectioned composition with spacing rhythm, not a wall of controls.

## Workflow

1. Inspect the current feature before designing.
   - Find the nearest Kotlin screen and the nearest analogous KPKN UI pattern.
   - Identify the user job, not just the component tree.
2. Inspect the PWA source only to extract intent.
   - Do not mirror its layout mechanically.
   - Use it to understand hierarchy, missing states, and user tasks.
3. Choose the Android form first.
   - screen
   - sheet
   - dialog
   - segmented tab
   - pager
   - card section
   - inline editor
4. Match KPKN's existing visual language.
   - Reuse the nearest local shape, spacing, typography, and section rhythm.
5. Decide whether the UI should stay feature-local or become shared.
   - Promote to `ui/components/` only when reuse is real.
6. Implement the smallest coherent slice.
   - Keep visual work separate from domain refactors where possible.
7. Validate the flow and readability.
   - Compile first.
   - Then do a targeted visual/interaction check for the touched surface.

## Compose UI rules

- Prefer `Scaffold`, `LazyColumn`, `LazyRow`, sheets, and section-based composition over giant nested `Column` walls.
- Keep feature screens in `screens/<feature>/`.
- Keep reusable primitives in `ui/components/` only when two or more surfaces clearly benefit.
- Keep screen-specific components next to the screen package.
- Keep business logic out of leaf composables.
- Keep `ViewModel` usage at screen or top-level section boundaries, not deep inside presentational leaf nodes.
- Use `MaterialTheme` as the base, then add deliberate feature accents where the screen already does so.
- If the screen already uses direct colors or gradients, refine them locally instead of forcing a repo-wide token refactor.
- Prefer a few strong components over many weak cards.

## Layout and hierarchy rules

- Lead with the main job or main status.
- Put the highest-value information above the fold.
- Break long surfaces into sections with breathing room.
- Favor vertical flow over cramped two-column phone layouts.
- Use chips, pills, and segmented controls to compress choice without losing clarity.
- Turn dense web layouts into stacked sections or multi-step flows when needed.
- Keep bottom actions reachable on tall phones.
- Avoid making the user scroll through decorative content before they reach the first useful action.

## Copy and content rules

- Keep user-facing copy in Spanish unless the task explicitly asks otherwise.
- Prefer short, strong action labels.
- Use explanatory helper text only where the product needs education or reassurance.
- Preserve KPKN domain terms consistently:
  - RINGS
  - batería
  - SNC
  - columna
  - programa
  - sesión
  - nutrición
- Do not replace domain language with vague lifestyle-app wording.

## Color, shape, and typography rules

- Reuse `MaterialTheme.colorScheme` first.
- Add feature accents deliberately when the screen already uses them for meaning.
- Keep color semantics stable:
  - rings and recovery states should feel consistent
  - warnings should not look celebratory
  - success should not steal attention from primary actions
- Use bold type for hierarchy, not as decoration everywhere.
- Preserve strong rounded geometry and KPKN's compact chip/card feel.
- Avoid default purple-heavy compositions unless the nearest KPKN screen already leans that way.
- Avoid flat gray-on-gray layouts with no focal point.

## Motion and interaction rules

- Use motion to clarify hierarchy or state change, not to decorate everything.
- Prefer simple fades, scale, pager movement, and sheet transitions over complex choreography.
- Keep gestures discoverable.
- If a gesture hides a critical action, provide a visible fallback.
- Long-press is acceptable for advanced calibration or power-user behavior only when the screen also teaches it clearly.
- Do not rely on hover-style affordances or desktop interaction assumptions.

## PWA-to-Compose translation rules

- Preserve information architecture, not exact card geometry.
- Replace web drawers with `ModalBottomSheet` or dedicated mobile screens when clarity improves.
- Replace desktop-heavy tab rows or split panes with mobile sections, pagers, or nested flows.
- Replace wide data tables with stacked cards or grouped metric rows.
- Replace hover help with inline support text, secondary labels, or explicit actions.
- If the web flow is too dense for a phone, split it. Do not squeeze it.
- If the web surface already feels mobile-hostile, treat that as permission to redesign it natively.

## Reuse rules

- Reuse an existing KPKN screen pattern before inventing a new one.
- Reuse shape and spacing patterns from the nearest local screen.
- Create a shared component only when:
  - it is truly reusable
  - its props stay small and readable
  - it reduces duplication without hiding product intent
- Keep feature-specific hero banners, ring widgets, or workflow components local unless they are clearly cross-feature.

## Before editing UI

- Identify the exact user job of the surface.
- Identify the nearest Android-native analogue already in the repo.
- Decide what should remain visually equivalent to existing KPKN screens and what should adapt.
- Check whether the surface needs:
  - a sheet
  - a full screen
  - section cards
  - tabs or pager
  - a sticky or bottom action
- Decide whether the work is visual-only or also changes state/navigation.

## Before creating a shared component

- Search for at least one more real call site.
- Keep props explicit and minimal.
- Make sure the component expresses a stable pattern, not one screen's temporary shape.
- Prefer moving a repeated card shell or header primitive, not an entire feature block with business assumptions.

## Before translating from the PWA

- Identify what is domain-critical versus presentational.
- Find the smallest set of PWA anchors that define the flow.
- Decide what Android-native structure should replace the web structure.
- Keep screenshots, card order, or class names out of the decision unless they still matter for the user job.

## Review stance

- Prioritize clarity, task completion, touch ergonomics, and hierarchy.
- Review whether the screen feels like KPKN, not just whether it compiles.
- Catch over-cloning from the web early.
- Catch under-designed "temporary" UIs that flatten important product value into plain lists.
- Pay attention to empty states, loading states, and edge-state readability.

## Validation

- Use `scripts/build_ui_brief.py` to structure the UI slice before a complex change.
- Use `scripts/suggest_ui_checks.py` to choose the smallest useful validation.
- Typical commands:
  - `cd android-native`
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `.\gradlew.bat :app:assembleDebug`
- If only feature-local UI changed, do not run the entire repo suite by reflex.
- If the UI change also affects navigation, persistence, or models, widen validation.
- Always do at least a mental or manual smoke pass on:
  - hierarchy
  - primary action visibility
  - readable Spanish copy
  - touch target safety
  - scroll and section rhythm

## Do not do these things

- Do not copy the PWA card-for-card into Compose.
- Do not turn KPKN into generic white cards plus default Material buttons.
- Do not rebuild the whole theme system during a small screen task.
- Do not create a giant "common UI kit" because two cards look slightly similar.
- Do not bury primary actions below oversized decorative sections.
- Do not replace bold KPKN hierarchy with tiny neutral text everywhere.
- Do not introduce desktop UI assumptions like hover, side-by-side editor panes, or dense tables when the screen is phone-first.
- Do not mix heavy business logic into presentational composables.
- Do not rewrite nearby screens just to make visual patterns perfectly consistent.

## When to note assumptions

- Note assumptions when the intended Android structure is not obvious from the PWA.
- Note assumptions when the repo has more than one plausible visual pattern for the same kind of surface.
- Note assumptions when the screen needs stronger visual polish but the current theme system is still incomplete.
- If you proceed autonomously, choose the nearest local KPKN pattern and say so.

## Definition of done

- The Compose surface supports the intended user job clearly on mobile.
- The result feels like KPKN, not like a direct web transplant or a generic placeholder.
- Visual hierarchy, spacing, copy, and primary actions are coherent.
- State and navigation ownership remain in the right layer.
- Validation matches the actual risk of the change.
- Any remaining visual debt or intentional deviation is noted honestly.
