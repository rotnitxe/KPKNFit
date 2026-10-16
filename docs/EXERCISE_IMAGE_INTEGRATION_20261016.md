# Reviewed exercise images — 2026-10-16

The Android catalog now binds 213 reviewed images to 215 exact entries: 53 strength configurations and 162 mobility movements. Two pairs intentionally share a resource: wall sit and ankle dorsiflexion. The source selection, final resource hashes, dimensions, quality and reference URLs are recorded in `catalog/exercises/images/reviewed-technique-images.json`.

## Format and quality

All new resources use WebP quality 95 with lossless alpha, original dimensions and no resizing. Color compression is lossy; transparency is pixel-identical to the reviewed PNG. The PNG masters remain in the local audit artifacts.

| Encoding | Total bytes | MiB |
| --- | ---: | ---: |
| Reviewed PNG masters | 238,223,665 | 227.19 |
| Previous lossless WebP package | 147,624,306 | 140.79 |
| Integrated WebP quality 95 | 38,728,026 | 36.93 |

This reduces image storage by 83.74% relative to PNG and 73.77% relative to the lossless WebP package. All 213 images retain identical dimensions and alpha. Fifteen representative images were compared at qualities 90 and 95 on black, gray and white, including band and contact details. The lowest-scoring image was inspected separately. Quality 95 preserved fine detail better; no file needed the configured higher-quality/lossless fallback.

WebP remains appropriate for the application's Android API 24 minimum: its transparency decoder is supported from Android 4.2.1. AVIF decoding begins at Android 12 and would require another compatibility path for older supported devices. See [Android image format support](https://developer.android.com/media/platform/supported-formats) and [WebP encoder options](https://developers.google.com/speed/webp/docs/cwebp).

## Resolution and UI

`ReviewedExerciseTechniqueImages` contains exact IDs. Strength selections take precedence over older implement restrictions and do not become broad definition fallbacks. The catalog image carousel uses the exact selected configuration. The workout media view includes every identity field in its cache key.

Mobility images appear in the picker and both workout mobility overlay modes. They use Coil `AsyncImage`, bounded display dimensions and `ContentScale.Fit`. For persisted mobility series, nonblank `catalogConfigurationId` takes precedence over `exerciseDbId` and the occurrence ID. Names are not used to select imagery.

Six mobility records and sixteen strength profiles were aligned to the reviewed geometry, including the fixed Smith support, manual patellar glide, band routing, pad contacts and machine variants. Existing persisted IDs are retained. The source catalog is exported through the repository scripts to Android, JVM resources and iOS.

## Validation boundaries

The strict catalog gate reports `READY`; compilation/export checks and SHA pins pass. The separate prose-quality audit retains 163 pre-existing findings in the selected definitions, down from 233 at the original HEAD, with no new finding locations. That audit is not reported as a clean pass.

Focused Android regression tests cover exact catalog IDs, the two shared images, missing variants, authoritative identity precedence and existing lookup behavior. Runtime validation evidence is kept under `artifacts/exercise-images-integration-20261016/` together with codec metrics, visual comparisons, preservation checks and build logs.

Only files owned by this integration are included in its commit. Previously uncommitted lookup, catalog and resource work is preserved in the working tree; catalog exports prepared for the commit contain only HEAD plus this integration's source changes.
