# Complete approved exercise image integration — 2026-10-17

The earlier integration committed the latest 213-image review package but omitted the historical image work still present only in the shared checkout. Reconciliation of the original 110 generated batch records found 82 historical approval receipts, 27 later selected versions, and one explicit retirement. Of the 82 historical images, 81 remain current and were missing from `master`; the other is superseded by the already integrated corrected lumbar Zercher hyperextension (R02). The flat pike push-up remains separate from its elevated-feet version.

The 81 restored images cover 67 definitions and 84 exact published configurations. Their original WebP bytes match the audit's historical hashes. The approval decisions, selected source files, exact IDs and resource hashes are recorded in `catalog/exercises/images/historical-approved-technique-images.json`. Historical approval is not inferred from file existence or from an `integratedIntoApp` flag.

The 329 existing PNG exercise resources also had verified WebP conversions that were missing from the commit. These conversions retain their original resource names, dimensions and pixel-identical alpha. `catalog/exercises/images/legacy-webp-conversions.json` records both source and output hashes and color-compression measurements. Color compression is lossy; the 329 conversions are not described as lossless RGB. The latest 213 reviewed resources and their quality-95 encoding are unchanged.

The resulting committed set contains 623 exercise WebP resources: 329 converted legacy resources, 81 current historical approvals and the 213 recently reviewed images. The superseded historical hyperextension remains in the audit artifacts rather than as another app resource.

## Selection behavior

Folded catalog cards can resolve a configuration before an implement chip is selected. Previously their pager could start with the first implement in the list, which could show a bodyweight image for a machine or dumbbell configuration. The first page now uses the resolved image. The helper also normalizes a legacy `default` image key to an explicitly supplied implement when needed.

For every technique image, an explicit catalog configuration takes precedence over stale definition, database and implement fields. This prevents an old cable ID from hiding a barbell Pendlay image, a bodyweight ID from overriding a machine Sissy image, or an old seated/pronated ID from allowing a different station or grip image.

## Verification

The regression suite uses the historical approval manifest as an independent oracle for all 84 restored configuration bindings. It also checks the selected and folded image of every published catalog configuration, the three manifests' 623 resource files and hashes, the two pike setups, superseded hyperextension handling, and stale-identity restrictions.

The Android instrumentation suite decodes every packaged exercise resource with the Android decoder and renders the production catalog for machine leg extension, cable forward lunge, and both pike setups. It also renders a reviewed mobility band image. This route does not complete onboarding or modify a session. Screenshots and machine-readable evidence are retained under `artifacts/exercise-images-coverage-repair-20261017/`.

Validation uses an isolated checkout of `master` plus only the files in this integration. The unrelated in-progress retirement of `conventional_row__cable`, its generated catalog exports and other non-image work remain outside this commit.

The completed run passed all 73 targeted JVM tests and all six Android instrumentation tests on emulator-5554 (API 36). All 623 packaged WebP hashes, dimensions and real alpha match their manifests; the Android decoder also verified all 623 dimensions and alpha support. The installed APK hash matches the retained build. The five rendered catalog/mobility captures were inspected visually. These checks do not constitute physical-device or full application acceptance.

Of the 553 published strength configurations, 537 resolve an image. The remaining 16 have no exact selected image in the reviewed sets; their IDs are listed in the retained coverage report. All 84 current historical approval bindings and the latest 215 strength/mobility entries are covered. The superseded B13 image remains archived rather than being presented as a second current approval.
