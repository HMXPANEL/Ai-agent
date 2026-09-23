# ClosePaw Documentation Classification & Source-of-Truth Rules

**Created:** Phase 0 (2026-09-24) · **HEAD:** `db9f1aa` · **Status:** NORMATIVE (this document governs all other docs)

## 1. Source-of-truth hierarchy (binding)

1. Actual executable source code (`app/src/main/kotlin/`)
2. Tests demonstrating behavior (`app/src/test/`, `app/src/androidTest/`, `eval/tests/`)
3. Build configuration / manifest / resources (`*.gradle.kts`, `AndroidManifest.xml`, `res/`, `assets/`)
4. Current audit reports (`docs/audit/00–09`)
5. Current normative documentation (`doc/main/*`, `README.md`, this file)
6. Historical documentation (`docs/PHASE_*`, `docs/HMX_*`, `doc/dev/scroll_visualizer_*`)
7. Plans for future functionality (unmarked "planned/deferred" text inside normative docs is a defect — see §4)

## 2. Document classes

- **NORMATIVE** — must match source; update when implementation changes.
- **HISTORICAL** — frozen record; never rewrite; add `> **Current-status note:**` quote-block only.
- **AUDIT** — findings at a point in time; update only via addendum/pointer, never silent rewrite.
- **PLAN/STATUS** — frozen once superseded; completion marked only with source proof.
- **EXPERIMENTAL** — unproven path; must be labeled as such in-text.

## 3. Classification registry

### 3a. Normative (update on implementation change)

`README.md`, `SECURITY.md`, `PRIVACY_POLICY.md`, `CLAUDE.md`, `AGENTS.md`(stub→CLAUDE.md),
all of `doc/main/**` **except** `doc/main/infra/tool/click_transport_experiment.md`,
`doc/release/signing.md`, `doc/release/play-store/*`, `doc/release/privacy/index.md`,
`doc/dev/development.md`, `doc/dev/visual_debug_guide.md`,
`docs/audit/00–11` (living audits), `docs/DOCUMENT-CLASSIFICATION.md` (this file).

### 3b. Historical (frozen; status noted, not rewritten)

`docs/PHASE_1_REPORT.md`, `docs/PHASE_2_REPORT.md`, `docs/PHASE_2_STATUS.md`,
`docs/PHASE_2_IMPLEMENTATION_AUDIT.md` (superseded on storage decision by `PHASE_2_STATUS.md`),
`docs/PHASE_3_REPORT.md`, `docs/PHASE_3_STATUS.md`, `docs/PHASE_4_STATUS.md`,
`docs/BASELINE.md` (normative **corrections record** for the 15 per-file audits — see §3d),
`docs/HMX_MASTER_PLAN.md`, `docs/HMX_UNIVERSAL_ARCHITECTURE_PLAN.md`,
`docs/PROVIDER_FORENSIC_AUDIT.md` (forensic finding; routing verdict remains current),
`doc/dev/scroll_visualizer_plan_codex.md`, `doc/dev/scroll_visualizer_review_codex.md`.

### 3c. Experimental

`doc/main/infra/tool/click_transport_experiment.md` (2026-03-06 single-device experiment; informs `ActionPriorityOrder`, not a spec).

### 3d. Audits with known drift (pointer headers added Phase 0; BASELINE is normative over them)

| File | Drift | Trust instead |
|---|---|---|
| `docs/audit_browser_script_kt.md` | tool path `browser/script/` | `docs/BASELINE.md` D-002 → `tool/impl/BrowserScriptTool.kt` |
| `docs/audit_llm_client_kt.md` | "two implementations" | `docs/BASELINE.md` D-003 → 4 clients |
| `docs/audit_policy_engine_kt.md` | browser_script always-ask in AUTO_APPROVE | `docs/BASELINE.md` D-004 → code returns Allow |
| `docs/audit_termux_bridge_manager_kt.md` | `snapshot(enabled)` suspend | `docs/BASELINE.md` D-006 → synchronous |

## 4. Rules

1. **R1 — Source wins.** Doc contradicts source → fix the normative doc, never reinterpret the code in prose.
2. **R2 — History frozen.** Add `> **Current-status note (YYYY-MM-DD):** ...` quote-blocks; never edit historical meaning.
3. **R3 — Audits append-only.** New findings → new dated note or new audit file; never silently rewrite a finding.
4. **R4 — Counts from source.** State/enum cardinalities cited in docs must be re-counted from the sealed type at edit time. Verified 2026-09-24: `SessionState`=6, `CapsuleMode`=9, `ApiKeyStepState`=11, `HistoryConfig.recentFullScreens` default=2.
5. **R5 — No phantom tools.** A tool is documented as available only with definition + registration + execution path + test evidence.
6. **R6 — Gate honesty.** Verification/capability/policy gates documented exactly as wired (advertisement-time vs execution-time stated).
7. **R7 — Test honesty.** "Tested" requires a named test file + behavior; compilation ≠ tested.
8. **R8 — Unknown explicit.** Insufficient evidence → `**Status: UNKNOWN** — <what evidence is missing>`.
9. **R9 — No user-work destruction.** Pre-existing uncommitted changes are preserved; never `reset --hard` / `clean -fd` / `checkout -- .` without explicit authorization.
10. **R10 — No local builds.** Per operator instruction, no Gradle build/APK/test execution on this machine; validation is static (source reads, grep consistency scans, markdown checks, diff review).

## 5. Ownership

| Area | Owner doc(s) | Backed by |
|---|---|---|
| Architecture/execution flow | `docs/audit/02-ARCHITECTURE-MAP.md`, `doc/main/README.md`, `doc/main/agent/overview.md` | `session/*`, `agent/*` |
| LLM/providers | `doc/main/infra/llm.md`, `docs/PROVIDER_FORENSIC_AUDIT.md` | `llm/*`, `auth/*` |
| Tools/policy/approval | `doc/main/infra/tools.md`, `doc/main/infra/tool/mobile_action.md` | `tool/*` |
| Perception | `doc/main/infra/perception.md` | `perception/*` |
| Verification | `doc/main/agent/loop.md`, `docs/audit/04-MISSING-FEATURES.md` (gaps) | `agent/OutcomeVerifier.kt`, `TaskVerification*` |
| Session/memory/storage | `doc/main/infra/session.md`, `doc/main/agent/memory.md`, `doc/main/app/history/*` | `session/*`, `memory/*`, `storage/*`, `history/*` |
| Browser/Termux/Shizuku/VD | `doc/main/infra/browser.md`, `doc/main/app/termux_shell.md`, `doc/main/infra/virtual_display.md` | `browser/*`, `termux/*`, `platform/*` |
| Security | `doc/main/../..` `SECURITY.md`, `PRIVACY_POLICY.md`, `docs/audit/09-SECURITY-AUDIT.md` | `auth/*`, `tool/PolicyEngine.kt`, `util/SensitiveDataFilter.kt` |
| Build/tests/perf | `doc/dev/development.md`, `doc/main/eval/eval.md`, `docs/audit/07/08` | `*.gradle.kts`, manifest, `app/src/test|androidTest` |
