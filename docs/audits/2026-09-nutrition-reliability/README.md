# Natural meal logging reliability — September 2026

> **Continuation completed.** [CONTINUAR.md](CONTINUAR.md) records the starting
> handoff; this report records the completed JVM/APK validation and the final
> emulator evidence, while retaining the earlier installation incident as a
> limitation.


Approved implementation plan: [.opencode plan](../../../.opencode/plans/2026-09-12_nutrition-natural-language-reliability.md).
Behavioral contract: [interpretation revision 2](../../contracts/nutrition_interpretation_v2.md).

## Baseline and methodology

Android is the executable product. This audit follows the real description flow,
including meal-template matching, parser, food retrieval/ranking, cooking
and portion resolution, the interpretation bridge, Compose editing and persistence.
The review used code inspection, existing unit tests and JVM reflection probes
against compiled Android components. Component probes are not device E2E results.

Baseline on 2026-09-12: 482 targeted unit tests, 481 passed and one failed, zero
skipped, 54 seconds. Filters: `com.example.kpkn.domain.nutrition.*`,
`com.example.kpkn.data.food.*`, and
`com.example.kpkn.screens.nutrition.NutritionViewModelTest`.

The failing assertion was exact lookup of `papa fritas` in
`NutritionResolutionConsistencyTest`; both singular/plural inputs can still parse
as potato with FRITO. It was not classified as a proven E2E phrase failure. Some
passing assertions were invalid product oracles: accepting torta as white bread,
checking only a positive amount for slices, and treating lactose-free milk as plain
milk plus an excluded lactose ingredient.

## Confirmed baseline defects

| Area | Trigger and observed component behavior | Required invariant |
|---|---|---|
| Templates | Stored oat/milk/egg meal matches `avena` (score .7667), `avena sin leche ni huevo` (.8375) and changed oat mass (.74), all above .68 | Current mentions, exclusions and explicit amounts take precedence |
| Fractions | Half and one cup milk both 180 g; two cups 480 g | Same measure scales proportionally |
| Slices | One/two apple slices both 100 g; bread slices become whole-piece portions | Slice measure and its multiplier survive |
| Decimal separator | `0,5 kg arroz` becomes 5000 g; `1,5 tazas` becomes five cups | Parse the numeric token before punctuation |
| Written numbers | `ciento cincuenta gramos` splits 100 and 50; `treinta y dos` splits a food list | Parse compound Spanish numbers as one expression |
| Mention preservation | Fried and cooked chicken collapse; unspecified/explicit repeated rice depends on order; trailing unquantified salad disappears | Preserve independent mentions and attached attributes |
| Product attributes | `leche sin lactosa` becomes generic milk plus excluded lactose | Keep product-composition restrictions during matching |
| Identity | Turkey breast maps to chicken; peanut paste maps to pasta; family dedup removes milk/cheese variants | Family is not a selected identity |
| Retrieval | Explicit Hallulla Ideal global fixture is dropped for local bread candidates | Compare all compatible candidates before source priority |
| Nutrient basis | gen133 Pan Integral computes 530 kcal for 100 g despite 265 kcal/100 g declaration; whole chicken has similar mismatch | Nutrient reference weight is independent of eaten portion |
| Cooking state | Assumed cooked salmon returns raw gen009 and announces cooked | Announced state agrees with profile or explicit conversion |
| Confidence | Numeric plausibility and existence of loggedFood turn uncertain matches AUTO | Identity evidence and material questions govern acceptance |
| UI/editing | Critical questions folded, ranges discarded, repeated estimate-size scaling drifts | One visible material question and immutable scaling base |
| Persistence/learning | Memory-first save closes before Room success; immediate draft learning and incomplete forget | Acknowledge durable save, preserve failed draft, scoped confirmed learning |

The nutrient-basis audit included egg as a genuine per-serving control. Therefore
rewriting all food serving sizes to 100 g would be a regression. Original source
provenance is required before changing ambiguous legacy nutrient values.

Before installing the correction, the running emulator APK was also checked via
Home -> REGISTRO DE HOY -> Agregar comida: `media taza de leche` displayed **110
kcal**, P 6 g, C 9 g, G 6 g, and an enabled GUARDAR button. The synthetic draft was
not saved. This is a device baseline for the half-cup defect, separate from the
component-only observations above.

## Implementation and validation status

Implementation and final JVM validation are complete. After the exclusion/context
and tortilla-composition corrections, the complete nutrition suite passed **570
tests across 53 suites, zero failures, errors or skipped tests**, `BUILD SUCCESSFUL`
in 1m40s. The directed final run passed **91/91** tests across the nine required
classes. `assembleBaseDebug` also passed in 14s and produced the APK recorded
below. The final corrected APK was exercised on `emulator-5554` using a fresh
synthetic profile; the former populated profile remains covered by the incident
limitation below.

Machine-readable evidence: [unit test summary](unit-test-summary.json) and
[independent corpus](independent-corpus.json).

| Final corpus dimension | Result | Meaning |
|---|---|---|
| Identity | 23/23 | Correct supported identity, or preserved explicit uncertainty |
| Mention coverage | 23/23; 0 additions, 0 omissions | Substitutions count as both an omission and an addition |
| Quantity | 23/23 | Declared quantities, physical equivalences and justified household ranges |
| Nutrient reference intervals | 14/14; 9 not evaluated | No fabricated ground truth for uncertain recipes |
| Clarification policy | 23/23; questions in 6/23 descriptions | Questions checked for the affected mention; fixture frequency only |
| Exact arithmetic controls | 4/4; MAE 0.151 kcal, maximum 0.396 kcal | Scaling and integer rounding, not accuracy of an unweighed meal |
| Reserved narrative cases | 8/8 | Meaning preserved or material uncertainty made explicit |

### Implemented paths under validation

- Number normalization precedes list splitting; independent mentions keep their
  preparation, local size, product restrictions and excluded ingredients.
- The drawer analyzes current mentions before consulting history. Template
  compatibility is bidirectional, so a partial description cannot restore a meal.
- Food identity and source survive retrieval and ranking. `NutrientBasis` separates
  the nutrient denominator from the portion and converts genuine volume profiles
  using the same density as consumed volume.
- `FoodInterpretationV2.interpretResolved` receives an already resolved mention.
  The drawer uses that result through clarification, resizing and saving, with an
  immutable nutrient base for repeated size edits.
- Material questions are displayed outside advanced details. Accepting uncertainty
  preserves estimated status and ranges; it does not teach a confirmed habit.
- Saving awaits a Room transaction containing the log and daily goal snapshot.
  Confirmed learning occurs after success, calibration updates are serialized and
  forgetting clears the related associations. Historical totals are not rewritten.

No entity fields, exported schemas or database version were changed. The `room`
plan flag covers existing entity-to-domain adapters and transaction behavior.

First integrated compilation passed (`compileBaseDebugKotlin`, 2m46s). The first
pure-JVM regression run executed 91 tests, 87 passed and four failed. New language
regressions and the eight logger-result tests passed; remaining failures identified
preset preservation, an obsolete half-teaspoon quantity assertion, a status-name
expectation and the peanut-paste match. The initial 13-case pipeline corpus had
12/13 identity, 13/13 coverage, 13/13 quantity, 13/13 nutrition checks and 12/13
clarification-policy checks. These are interim debugging results, not release gates.

The new durable-save test also exposed an actual asynchronous snapshot write left
after the awaited log write. Closing the database after save could trigger the
application crash hook and terminate the JVM (exit 10). The correction must await
the complete log/snapshot transaction; isolating the test Application does not
substitute for fixing the dangling write.

The second, broader run completed 530 tests: 503 passed and 27 failed. Durable
save and logger-result regressions passed. Failures included obsolete substitution
oracles, real alias/identity issues, compatible-template matching and a stale
250 ml cup expectation. Those failures were resolved by checking the declared food and source basis,
rather than relaxing assertions to accept any result.
The 23-case independent corpus at this stage passed identity 22/23, coverage
23/23, quantity 23/23, nutrition 22/23 and clarification policy 22/23. These remain
interim values; omitted-portion cases must assert meaningful household ranges.

The catalog basis review found matching source records in the existing USDA
offline asset for whole milk (FDC 171265), skim milk (171269) and olive oil
(171413). Their existing rounded nutrient values are per 100 g. A display unit of
ml does not make those values per 100 ml. Corrections preserve the existing values
and record their basis; genuine per-volume custom profiles remain distinct.

The third run completed 549 tests: 541 passed and eight failed. The independent
23-case corpus passed identity, mention coverage and household-range checks in all
23 cases. Nutrient reference checks passed in 14 cases; nine cases deliberately
had no independently justified nutrient oracle and were reported as not evaluated.
Six descriptions required clarification, including deliberately ambiguous product
attributes and recipes. This is a fixture frequency, not a user-population estimate.

All eight reserved narrative/negation cases passed in this run. They included
quantity repair (`dos huevos, perdón, uno`), replacement of a side dish,
non-consumption followed by a new positive clause, local recipe exclusions,
sugar-free yogurt plus separately added sugar, an elliptical household measure,
an unresolved reference to a previous portion and an exclusion attached to bread.
Cases that cannot be resolved faithfully may pass only by preserving the affected
mention and exposing the relevant uncertainty. These cases were authored before
execution; once failures were used for correction they became regression evidence,
not a permanently blind benchmark.

Cross-review also identified two edit regressions (a pre-sized portion multiplied
twice, and a manual macro correction lost on later resizing) and an ambiguity
between branded pack weight and consumed weight. These are included in the final
validation gate rather than treated as out-of-scope observations.

The third run's oil failure was a real identity defect, not an obsolete oracle:
`aceite` could select tuna in oil because the query had no known family and a
contained ingredient token outranked the oil profile. Simple-food matching now
requires an equivalent food head or a supported exact alias regardless of whether
a family is known. A second real failure rejected a previously confirmed chicken
cut because a generic chicken query lacked the ontology alias for that cut; the
fix preserves the learned cut while keeping explicit cut constraints distinct.

The fourth run completed 553 tests, with four failures. Package ambiguity, logger
editing and durable-save tests passed. Follow-up JVM diagnosis confirmed that oil
now resolves to the correct profile and 13.5 g / 119 kcal for a tablespoon; its
remaining unnecessary review was caused by conflating curated-catalog membership
with a literal `LOCAL` provenance string. Making the USDA provenance explicit must
not change the eligibility of the same curated profile. Two remaining assertions
still expected a Gouda query to be resolved through a cheddar-only catalog; the
correct behavior preserves Gouda, its measured 40 g and the identity question.

Validation separates identity, additions/omissions, quantities, arithmetic and
clarifications. A passing test corpus does not certify arbitrary real-world meal
descriptions or the accuracy of unobserved portion sizes.

## Device QA findings after the first green gate

The installed APK was exercised through Home -> REGISTRO DE HOY -> Agregar comida.
Half/one/two cups of milk displayed 124/247/494 g and 75/151/302 kcal; decimal
`0,5 kg arroz cocido` displayed 500 g and 650 kcal. `200 g arroz` displayed a
material dry/cooked question outside advanced details; the actual Save action was
disabled until a choice. Accepting uncertainty retained 260–720 kcal and enabled
Save. A natural rice/chicken/salad lunch retained all three mentions and displayed
material questions sequentially. Fried and cooked chicken remained two separate
100 g rows (223 and 166 kcal).

This device pass found gaps not certified by the earlier unit corpus:

- Generic `ensalada` used the unmatched mixed-dish density 160 kcal/P10/C16/G6
  per 100 g. Its explicit 100 g form even had a zero-width energy interval, despite
  unknown composition. Existing size-proportionality assertions did not establish
  a nutritional reference for this food.
- `avena sin leche ni huevo` correctly excluded milk and egg from totals, but the
  full device catalog selected the branded product `Vivo PRO BIÓTICOS` for oats.
  This exact catalog interaction was absent from the small unit fixtures.
- `leche sin lactosa` preserved its product attribute but offered cheese and
  condensed/flavored milk as candidate options. Food-head checks must apply to
  every candidate, including queries with composition attributes.
- Saved uncertainty ranges were serialized correctly but hidden from Home and
  Nutrition log rows when reopened.
- Cancelling a drag-dismiss with `Seguir editando` left a hidden drawer and an
  invisible blocking surface. The unsaved draft was still reachable by Back and
  could be explicitly discarded, but the cancel action did not restore editing.
- Resizing an inferred rice portion incorrectly marked it as explicit mass and
  introduced a dry/cooked weight question. Numeric scaling itself remained stable:
  after selecting cooked, Large -> Small -> Large yielded 180/72/180 g and
  234/94/234 kcal.

A bounded independent review then found that the parser extracted a multiplier
from `tres leches`, accepting plain milk automatically; an old golden expectation
encoded this wrong behavior. It also found that bare `tortilla` selected a wheat
tortilla without checking its materially ambiguous composition. Both cases are
added as post-review regressions rather than retroactively described as blind
corpus successes.

The targeted post-QA regression run passed 66/66 tests with a successful full Kotlin
compilation. The following complete suite ran 564 tests, with two remaining failures:
lexicalized numeric-name protection also captured bare `dos quesos`/`cuatro quesos`,
and generic whey selected a branded supplement instead of the generic profile.
The numeric-name protection was limited to actual standalone names or recipe
contexts; bare cheese counts remain quantities. Whey now recognizes the existing
supplement ontology equivalence to powder, while generic milk still cannot match
powdered milk. Review also fixed normalization of scoop/scoops and verified
30/60/15 g for one/two/half scoops. Earlier green checkpoints are not substitutes
for the final device evidence below.

## Continuation final — exclusion and composition blocks

The pending exclusion/context block is now implemented in `TagResolver`,
`ContextDetector` and `HouseholdPortions`. Shape and portion inference use only
consumed mentions; the original description remains available for explicit meal
occasion detection; later combination scaling filters excluded tags; and staple
priors run before the generic lateral-food fallback. The pending composition block
now exposes `TagResolver.resolveDeclaredComposition`: it re-runs the current
pipeline with the selected identity, keeps the stable tag id and only carries a
mass or utensil amount that was actually declared. An inferred wheat-wrap amount
is therefore re-inferred for an egg/potato tortilla. The drawer replaces the old
card immediately, preserves the corrected name on failure, blocks saving while
resolving, and ignores stale asynchronous responses.

The new regression cases cover both exclusion orders, explicit oat mass, a
positive three-component breakfast, tortilla identity replacement with and
without an explicit mass, and the post-clarification Unsure invariant. Evidence
is in the nine-test `NutritionQaRegressionTest` suite and the nine-class directed
log: `/tmp/kpkn-nutrition-directed-final-after-unsure-fix.log`. The complete final
log is `/tmp/kpkn-nutrition-full-final-after-unsure-fix.log`; its XML totals are
570 tests across 53 suites, all green. The generated corpus is copied to
[independent-corpus.json](independent-corpus.json), and the summary records the
same 570/53 result in [unit-test-summary.json](unit-test-summary.json).

The final APK was built by `assembleBaseDebug` and has SHA-256
`cc8302eddb193d2daeb445d32eb768e4174fdc7c04d322faf8e578d6597630da` and size
253,239,524 bytes. Before its replacement install, the current emulator profile
was archived and verified at
`/tmp/kpkn-nutrition-test-profile-before-unsure-fix-install.tar` (3,614,208 bytes,
SHA-256 `ec06844c54ddcd1016e31204f7a8f882cbde5a320fab5c5b9063be62bb8c22e0`). The
install with `adb install --no-incremental -r` returned `Success`.

The final device pass used the APK above and fresh UI-tree dumps. Because the
available Nutrition route required an active plan, this pass used a synthetic
maintenance plan solely for QA; it did not use or overwrite a user profile. It
covered oat
exclusions and a positive three-component breakfast, tortilla composition choices
and explicit-mass preservation, the corrected tortilla Unsure path, rice
Large→Small→Large editing, salad/unknown-preparation ranges, drag-dismiss recovery,
save/reopen persistence, and cleanup. The fresh profile ended with only the
pre-existing synthetic rice log (260 kcal; range 260–720 kcal). Exact dump paths
and the historical install incident are recorded in [device-qa.json](device-qa.json).

### Emulator installation incident

The initial `adb install -r` selected incremental installation and reported Success.
The existing package then became unavailable; a subsequent streamed replacement
using `adb install --no-incremental -r` restored the app, but its former emulator
profile was gone. The observed package first-install time was reset. No `pm clear`,
explicit uninstall, Room migration or data-wipe command was run. This is an observed
installation failure; it is not evidence that an app database migration failed.

The user was informed and apologized to immediately. They did not know of a local
backup. The existing `default_boot` snapshot showed the pre-profile welcome screen
and could not recover the former profile; no useful backup was found. Subsequent
QA uses the fresh test profile. Therefore this run **does not demonstrate preservation
of a populated profile through an APK upgrade**, even though historical serialized
nutrients are covered by unit tests. Future replacement installs must use
`--no-incremental` and a verified profile backup before modifying a populated target.

## Reproduction environment

Commands run from `android-native/` with:

```sh
export JAVA_HOME=/home/rotnitxe/.local/share/kpkn-android/jdk
export ANDROID_HOME=/home/rotnitxe/.local/share/kpkn-android/sdk
export GRADLE_USER_HOME=/tmp/kpkn-gradle
timeout 600 ./gradlew --no-daemon --console=plain --warning-mode=summary --offline \
  testBaseDebugUnitTest \
  --tests 'com.example.kpkn.domain.nutrition.*' \
  --tests 'com.example.kpkn.data.food.*' \
  --tests 'com.example.kpkn.data.repository.Nutrition*' \
  --tests 'com.example.kpkn.screens.nutrition.NutritionViewModelTest'
```

Local Gradle locking and ADB require local sockets outside this session's default
sandbox; the approved commands remain offline. Emulator: `emulator-5554`.
UI route: Home -> REGISTRO DE HOY -> Agregar comida. Use fresh UI-tree bounds for
input; do not infer coordinates from screenshots. Use streamed `--no-incremental`
installs and verify a backup before replacing any populated test profile.

After the incident, a single synthetic uncertain rice log (260 kcal, range
260–720 kcal) was saved and survived force-stop/cold launch without duplication.
Before the final corrected-APK replacement, the fresh test profile was backed up to
`/tmp/kpkn-nutrition-test-profile-before-unsure-fix-install.tar` (3,614,208 bytes;
archive verified, SHA-256 `ec06844c54ddcd1016e31204f7a8f882cbde5a320fab5c5b9063be62bb8c22e0`).
This backup contains the new test profile, not the lost original profile; the final
QA cleanup removed only the temporary tortilla and unknown-preparation records.

## Compatibility and source limits

- No Room schema migration or retroactive recalculation of logged totals.
- No new remote model dependency; local-first behavior is preserved.
- The semantic source dataset has 19,405 instruction/output records without
  adequate provenance for nutritional certification. It may support language.
- iOS food entry is incomplete and no equivalent backend pipeline was located;
  this delivery documents behavior for future parity rather than claiming it.
- Sources: [USDA Foundation Foods](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/),
  [FAO/INFOODS food matching](https://www.fao.org/4/ap805e/ap805e.pdf).
