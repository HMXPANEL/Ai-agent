# Phase 8 — Completion Report

## Objective

Repository-wide documentation pass: resolve all terminology inconsistencies to source truth; sweep TODO/FIXME/planned/placeholder/stub markers; leave no stale counts.

## Documents Reviewed

All of `doc/main/**`, `doc/dev/development.md`, `README.md`; `docs/audit/03` (§C), `docs/audit/10` (source-truth table), `docs/DOCUMENT-CLASSIFICATION.md` (R4).

## Source Areas Reviewed (re-counted, not trusted from memory)

`history/HistoryConfig.kt:6` (=2), `protocol/SessionState.kt` (6), `onboarding/OnboardingState.kt:55-68` (ApiKey 11: 6 manual + 5 OAuth incl. `OAuthFinishing`), `ui/overlay/model/CapsuleMode.kt` (9), `app/MainActivity.kt:348-390` (`onMainAppVisible` in onStart/onResume/onNewIntent; hidden in onStop; NOT onCreate).

## Changes Made

1. `planning.md`: Screen Compression default 3→2.
2. `turn_prompt_anatomy.md`: `recentFullScreens = 3`→2 (cites `HistoryConfig.kt`).
3. `data_schemas.md`: ApiKey 10→11 + full 11-name list; OAuth 4→5 states.
4. `ui/overlay.md`: CapsuleMode file-tree comment 8→9 modes (incl. Hidden); `onMainAppVisible` call sites onResume→onStart/onResume/onNewIntent.
5. `onboarding_step_states.md`: "23-case"→"24-case" (6+11+7).
6. `ui/capsule/user_flows.md`: visibility convergence onCreate-claim → onStart/onResume/onNewIntent + onStop-hidden (cites `MainActivity.kt:367-390`).

## Documentation Corrections

All 7 doc-vs-doc contradictions from `03`§C are now closed (SessionState fixed Phase 1; roles fixed Phase 4; the remaining 5 here). Residual grep for stale values returns only the corrected text itself.

## Implemented Features Confirmed

No behavior changed (docs-only). Counts verified by re-reading sealed types at edit time per R4.

## Missing / Deferred Features

Marker sweep result: normative docs contain **zero** phantom-feature markers. Only two TODOs exist, both HTML comments in `README.md` (publish-privacy-policy link, CONTRIBUTING link — legitimate release chores, left in place). "Planned" hits are runtime concepts (planned-but-unexecuted tool calls), not roadmap promises. `LOCAL_LFM` factory-throw already documented in `llm.md` (Phase 2 leftover closed — no edit needed).

## Contradictions Resolved

All known doc-doc contradictions closed. `docs/audit/01` CONTRADICTORY flags for these files are now stale (registry itself is a historical audit artifact; noted in Final report rather than rewriting history per R3).

## Historical Documents Preserved

Yes — untouched.

## Tests / Validation Performed

No local builds per operator instruction. Static: zero source files in diff; post-edit grep sweep; diff reviewed.

## Remaining Issues

None for this phase.

## Git Commit

`docs(phase-8): complete documentation reconciliation`

## Git Push

BLOCKED — invalid credentials (see Phase 0). COMMITTED ONLY.

## Phase Status

COMPLETE
