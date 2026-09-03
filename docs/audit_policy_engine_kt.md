FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/tool/PolicyEngine.kt
PACKAGE: ai.closepaw.tool

MAIN CLASSES:
- PolicyEngine - Decides whether tool calls should be allowed, denied, or require approval

MAIN FUNCTIONS:
- check() - primary policy evaluation method
- setApprovalMode() - change approval mode at runtime
- getApprovalMode() - read current approval mode
- reset() - reset to default SMART mode, clear session allowlist
- allowPackageForSession() - add package to session allowlist
- isSessionAllowed() - check if package in session allowlist
- browserScriptDecision() - specialized browser script approval logic

DATA FIELDS:
- approvalMode: AtomicReference<ApprovalMode> - current approval mode (SMART by default)
- sessionAllowedPackages: MutableSet<String> - session-scoped allowlist

sealed interface PolicyDecision:
- Allow - tool call allowed
- Deny(reason) - tool call forbidden
- AskUser(reason, appTier?) - user approval required

sealed interface AppTier:
- CAUTIOUS, NORMAL, BLOCKED (ordinal-based, lower = stricter)

enum class ApprovalMode:
- ALWAYS_ASK, AUTO_APPROVE, SMART

RESPONSIBILITY:
- Canonical policy check ordering (per design doc):
  1. Non-screen-changing tools → Allow
  2. Escape actions (back/home) → Allow
  3. effectiveTier == BLOCKED → Deny (absolute floor)
  4. browser_script → specialized matrix (NORMAL override does NOT bypass)
  5. Session allow-list → Allow (gated by ALWAYS_ASK)
  6. Approval mode dispatch on effective tier

STATE OWNERSHIP:
- approvalMode: effective current mode (AtomicReference for runtime updates)
- sessionAllowedPackages: transient session-scoped allowlist

DEPENDENCIES (injected via constructor):
- initialApprovalMode: ApprovalMode = SMART
- appClassifier: AppClassifier - classifies apps into tiers

NOTABLE DEPENDENCIES:
- AppTier.BLOCKED is absolute floor - cannot be overridden
- Session allow-list is transient, never persisted
- ALWAYS_ASK gates session allow-list (user pref always wins)
- browser_script has special NORMAL override restriction

THREAD/COROUTINE:
- check() is a regular function (not suspend), no coroutine usage
- AtomicReference for approvalMode is thread-safe
- No suspend functions, no Dispatchers
- All operations are effectively single-threaded per session

INPUTS:
- toolName: String - name of tool being evaluated
- params: JSONObject = JSONObject() - tool parameters
- packageName: String? - current foreground app
- destinationPackage: String? - target package for navigation tools

OUTPUTS:
- PolicyDecision - Allow/Deny/AskUser with reason and appTier

SIDE EFFECTS:
- None - check() is pure (except Log.d debug output)
- reset() clears session allowlist and resets mode
- setApprovalMode() atomically updates mode

PERSISTENCE:
- sessionAllowedPackages is in-memory only, cleared on reset()
- approvalMode persistence handled at higher level (AgentSession.handleApproval)

NETWORK:
- None - pure policy decision logic

ANDROID API:
- None directly; uses AppTier classification from AppClassifier

ERROR HANDLING:
- None - returns sealed Decision types
- BLOCKED tier is absolute, even AUTO_APPROVE cannot bypass
- Browser script NORMAL override explicitly blocked

SECURITY:
- CRITICAL: BLOCKED tier is absolute floor for financial/auth apps
- Session allow-list is user-controllable via UI
- Browser script requires explicit approval even in AUTO_APPROVE mode
- "stricter wins" rule for open_app (effective tier = min of current & destination)

TEST COVERAGE:
- PolicyEngineTest.kt
- AppClassifierSecurityTest.kt
- AppClassifierOverrideTest.kt
- ToolRouterTest.kt (integration with policy)
- DefaultBrowserScriptCapabilityGateTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Complete policy decision engine with canonical ordering
- BLOCKED tier is absolute security boundary
- Session allow-list properly gated by ALWAYS_ASK
- Browser script has specialized approval matrix
- Well-tested with comprehensive test coverage
- Clear separation of policy decision from execution