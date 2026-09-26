# T-004 exact-edit evidence supplement

**TASK_ID / CONTRACT_VERSION / ROLE:** T-004 evidence supplement / v1 / RECORDER  
**STATUS:** SUBMITTED  
**INPUT_BASELINE:** The captured pre-edit working-tree WIP described in `T-004-report.md`, with pre-edit SHA-256 values for existing targets. Full pre-edit file snapshots are not present in the authorized T-004 evidence folder.  
**OUTPUT_CANDIDATE:** Current candidate files, checked against the candidate SHA-256 manifest in `T-004-report.md`.

## ACTUAL_ACTIONS

- Read `T-004-report.md`, `T-004-wip-diff.md`, and the contents of the T-004 evidence folder.
- Compared current SHA-256 values for all 15 scoped source/test paths with the report's candidate hash manifest.
- Added this supplement only. No candidate source, test, report, or build file was changed.

## ARTIFACTS

- `T-004-evidence-supplement.md` — this supplement.
- Existing `T-004-wip-diff.md` remains the abbreviated ledger and was not overwritten.
- No `T-004-exact-delta.patch` was created. The attempted evidence-artifact patch writes recorded in this session were rejected by the patch tool; their assembled payloads are not treated as authenticated records of the original source edits.

## ACCEPTANCE_EVIDENCE

- **AC-1 — ABSENCE REPORTED:** No full pre-edit snapshots or independently retained, authenticated applied-patch records are present among the authorized T-004 artifacts. The existing ledger explicitly identifies itself as abbreviated. The candidate-specific source delta therefore cannot be supplied as an exact, reconstructible patch without inventing or inferring missing before-images. No such reconstruction is presented here.
- **AC-2 — PASS:** Current SHA-256 values for all 15 scoped source/test files match their candidate values in `T-004-report.md`. The evidence-folder listing contained no exact-delta artifact before this supplement was added. No candidate file was written during this task.

## VALIDATION

- Read-only SHA-256 verification only; all 15 candidate hashes matched.
- No tests or builds were run, and no process-control, device, or source-edit commands were used.

## SCOPE_CHECK

Only this authorized evidence supplement was added. No existing report or ledger was modified. No candidate source/test hashes changed during this task.

## LIMITATIONS_AND_RISKS

The report's pre-edit hashes identify prior file contents but cannot recover those contents or establish a line-level delta. The abbreviated ledger records selected hunks and prose, not a complete patch. The source-edit payloads assembled during the failed artifact-write attempts were not verified against retained original applied-patch records and are intentionally not represented as exact evidence. Consequently, T-004 AC-5's requirement for actual candidate-specific deltas against captured WIP remains unevidenced by a full diff artifact.

## BLOCKER

Missing authenticated full pre-edit snapshots or retained exact applied patches for the 15 scoped paths. Under the no-reconstruction boundary, the exact delta cannot be produced from the available evidence.

## DETAILED_REPORT

The current candidate remains stable relative to the candidate hash set recorded in `T-004-report.md`. This confirms identity of the candidate files, not their line-by-line relationship to the captured pre-edit WIP. Existing `T-004-wip-diff.md` says it is abbreviated and omits unchanged surrounding code; hashes alone are not reversible. The requested evidence supplement therefore records the evidence gap rather than presenting a reconstructed patch as an original edit record. The prior timed-out Gradle invocations remain NOT RUN for unit-test results; this bounded evidence task performed no test validation.
