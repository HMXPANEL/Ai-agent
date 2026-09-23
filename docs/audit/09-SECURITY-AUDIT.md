# Security Audit — 09

**Boundary:** User → LLM → tool call → Android → external app. What the LLM can cause, and what gates it.

## 1. What the LLM can reach

| Capability | Path | Gate |
|---|---|---|
| UI actions (tap/type/scroll/swipe, system buttons, app launch) | `mobile_action`/`system_button`/`open_app` → `ToolRouter` → platform | PolicyEngine (BLOCKED absolute; back/home escape-only) + approval (SMART/ALWAYS_ASK) + TOCTOU re-check + verification gate |
| Shell (toybox one-shot) | `shell` → `ProcessBuilder(sh -c)`, 10s timeout | Blocklist (`am/pm/reboot/su/env/xargs/find`), metachar reject, 4096-char cap; non-screen-changing → auto-allowed |
| Full bash | `termux_shell` → bridge `/v1/exec` | Capability-gated (snapshot); workspace jail; 1MB/120s/64KB caps; single-exec lock; pgid kill |
| Browser JS in real Chrome | `browser_script` → CDP | Pref default off; capability gate + `DefaultBrowserScriptCapabilityGate`; SMART always-ask; 8192-char cap; per-session relay token |
| File/memory writes | `remember_experience` → `MemoryStore`; skills; history | `MemoryEditGate`; BLOCKED-app memory-gate blocks agent writes; package-name validation |
| Network (indirect) | Via shell/Termux/browser tools only; no raw-socket tool | Same gates as the carrying tool |
| Credentials | `AuthStore` read-only at request time | Never in backups; redacted in logs/traces; per-provider EncryptedSharedPreferences |

## 2. Mechanisms (all confirmed in source)

- **PolicyEngine** (`tool/PolicyEngine.kt:48-111`): canonical order — non-screen→Allow; back/home→Allow; BLOCKED→Deny (unbypassable, allowlist cannot override); browser_script SMART→Ask; session allowlist gated by ALWAYS_ASK; tier+mode dispatch. Pure `check()` + `Log.d`.
- **Approval UX:** `ToolRouter` 60s timeout; `UserResponseChannel` single-pending 5-min; `AgentSession.handleApproval:721-754` package validation (invalid APPROVED→DENIED); DENIED never mutates allowlist; `reset()` clears.
- **TOCTOU:** foreground-change during approval → Cancel (`ToolRouter.kt:219`); snapshot re-capture post-approval (`:273`). Capability NOT re-checked (gap, P1).
- **Verification gate:** unverified `complete_task(success)` → recoverable Error, same-turn + cross-turn (`TaskVerificationTracker`).
- **Storage:** `AuthStore` EncryptedSharedPreferences (MasterKey AES256_GCM); init failure throws (no silent fallback); OAuth refresh under mutex with generation guard. Backup excludes credentials by design (`ChatPersistenceManager.kt:233-235`); `backup_rules.xml` excludes `auth_store`.
- **Redaction:** `SensitiveDataFilter` (key=value, Authorization, Bearer, OTP, token shapes) applied in `RuntimeLogger`, `CrashReportStore`, `DiagnosticOverlay`; `CognitionTraceRedactor` in traces. Tests: `SensitiveDataFilterTest`, `CognitionTraceRedactorSecurityTest`.
- **Privacy masking:** BLOCKED content masked pre-turn/post-action/capture (`ObservationBuilder`, `VirtualDisplayPlatform.kt:335-342`, `CapturePrivacyGateTest`).
- **Termux:** loopback-only, workspace jail (`workspace_escape` 400), atomic deploy, pidfile identity, package-scoped PendingIntent (`RECEIVER_EXPORTED` API33+).
- **Browser:** per-session 256-bit `RelayAuthToken`, artifact byte cap, `markBroken` invalidates generation.
- **OAuth:** PKCE S256, state CSRF, localhost:1455 2-min server, DNS-only retry. Sign-in skips `OAuthCodexValidator` (dead code).

## 3. Residual risks

1. Tokens decrypted in memory; JWT parsed without signature verification (both already flagged HIGH RISK in `audit_auth_store_kt`/`audit_openai_oauth_kt`; accepted, document).
2. Termux bridge localhost:18422 has no auth token (loopback reliance). Either add token or record rationale (P1).
3. No execution-time capability re-check (P1).
4. `security-crypto:1.1.0-alpha06` in auth path (P0-track).
5. `InsecureSslConfig` trust-all exists as debug/eval hook (`INSECURE_SSL_FOR_EVAL`, default false; release forced false). Confirm no release path enables it (P1-verify; code shows release `false` literal).
6. Legacy secure prefs left on disk after onboarding migration (noted smell in `onboarding_wizard.md`). Wipe or document (P2).
