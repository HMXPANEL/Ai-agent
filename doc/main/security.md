# Security & Trust Boundaries

> Scope: what the LLM can cause, what gates it, and what is explicitly NOT guaranteed.
> Every claim below is backed by source (Phase 6, 2026-09-24). For the full audit see
> `docs/audit/09-SECURITY-AUDIT.md`. User-facing privacy promises: `PRIVACY_POLICY.md`.

## Trust chain

```
User → LLM → tool call → ToolRouter → PolicyEngine → capability → approval
  → TOCTOU check → execution → verification
```

Three layers are distinct: the **LLM tool call** (untrusted model output),
**ToolRouter execution** (policy/approval/cancellation), and the **platform operation**
(one atomic Android API call). See [infra/tools.md](infra/tools.md).

## Implemented

- **PolicyEngine** (`tool/PolicyEngine.kt`): BLOCKED tier is an absolute floor (Deny in every
  mode; allow-lists cannot override); back/home always Allow (anti-trap escape);
  `browser_script` SMART always Asks (AUTO_APPROVE Allows at policy but runtime capability
  gates still apply); session allow-list gated by ALWAYS_ASK; `reset()` clears.
- **Approval UX**: 60s router timeout; single-pending `UserResponseChannel` (5-min);
  package-validated (`handleApproval`: invalid APPROVED→DENIED); DENIED never mutates the
  allow-list.
- **TOCTOU**: foreground-package change during approval → Cancelled; snapshot re-captured
  post-approval. Capability is NOT re-checked at execution (limitation, P1).
- **Verification gate**: unverified `complete_task(success)` → recoverable Error, same-turn
  and cross-turn (`TaskVerificationTracker`). See [agent/loop.md](agent/loop.md).
- **Credentials**: `AuthStore` = EncryptedSharedPreferences (MasterKey AES256_GCM),
  per-provider JSON; Keystore failure **throws** (no silent fallback); OAuth refresh under
  mutex with generation guard. Credentials never enter backups (`ChatPersistenceManager`)
  or `backup_rules.xml` (`auth_store` excluded).
- **Redaction**: `SensitiveDataFilter` applied in `RuntimeLogger`, `CrashReportStore`,
  `DiagnosticOverlay`; `CognitionTraceRedactor` in traces. BLOCKED content masked at three
  capture points + capture-layer artifact gating.
- **Termux**: loopback-only bridge, workspace jail (`workspace_escape` 400), 1MB body /
  120s / 64KB caps, single-exec 409 lock, process-group kill on timeout/disconnect.
- **Browser**: per-session 256-bit relay token, artifact byte cap (atomic CAS), `markBroken`
  invalidates generation; both transports require Shizuku.
- **OAuth**: PKCE S256 + state CSRF, localhost:1455 with 2-min timeout, DNS-only retry.
- **Eval SSL bypass**: `INSECURE_SSL_FOR_EVAL` is a `"false"` literal in release builds
  (`app/build.gradle.kts`); debug-only opt-in via `insecureSslForEval` property.

## Known limitations (not vulnerabilities per se — documented behavior)

- Tokens exist decrypted in memory at request time; JWTs parsed without signature
  verification (both flagged HIGH RISK in `docs/audit_auth_store_kt.md` /
  `docs/audit_openai_oauth_kt.md`).
- Termux bridge `127.0.0.1:18422` has no auth token (loopback reliance; P1: add token or
  record rationale).
- No execution-time capability re-check (P1).
- `security-crypto:1.1.0-alpha06` is alpha in the auth path (P0-track stable).
- Legacy `onboarding_secure_prefs` file left on disk post-migration; nothing reads it
  (`OnboardingStore.kt`); wipe queued (P2).
- `OAuthCodexValidator` is dead code — sign-in path does not validate (wire-or-delete, P2).

## Explicitly NOT claimed

- No sandbox around shell/Termux beyond the jail + caps above. No execution sandbox for
  browser JS beyond Chrome itself. Memory files are unencrypted at rest, but agent-written
  entries are redacted through `SensitiveDataFilter` before persistence (explicit user
  file edits stay verbatim). Real-device validation of Shizuku/VD/Termux flows is pending (see
  `docs/audit/04-MISSING-FEATURES.md`).

## Related

- [infra/tools.md](infra/tools.md) (policy matrix, CapabilityManager, tool table)
- [infra/browser.md](infra/browser.md) (relay auth, artifact cap, dual gates)
- [app/termux_shell.md](app/termux_shell.md) (bridge security model)
- [agent/loop.md](agent/loop.md) (verification gate)
- `docs/audit/09-SECURITY-AUDIT.md` (full findings + residual risks)
