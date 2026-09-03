FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/tool/ToolRouter.kt
PACKAGE: ai.closepaw.tool

MAIN CLASSES:
- ToolRouter - Executes tool calls with state machine and policy-based approval

INTERNAL CLASSES:
- ToolCallState - tracks state of a tool call (Validating, AwaitingApproval, Scheduled, Executing, Success, Error, Cancelled)
- CancellationToken - per-call cancellation signaling
- Pending approval handlers map
- SimpleToolRouterContext - implements ToolRouterContext interface

COMpanion OBJECT:
- TAG = "ToolRouter"
- APPROVAL_TIMEOUT_MS = 60_000L (60 seconds)

DATA FIELDS:
- registry: ToolRegistry - tool discovery and schema
- policyEngine: PolicyEngine - approval decisions
- activeToolCalls: ConcurrentHashMap - tracks non-terminal states
- cancellationTokens: ConcurrentHashMap - per-call cancellation
- pendingApprovals: ConcurrentHashMap - user approval pending

RESPONSIBILITY:
- Full tool call lifecycle management:
  1. VALIDATING - check tool exists, validate params
  2. POLICY CHECK - policyEngine decides Allow/Deny/AskUser
  3. AWAITING_APPROVAL - wait for user decision (if needed)
  4. EXECUTING - run the tool invocation
  5. TERMINAL STATE - success/error/cancelled with cleanup
- Handles TOCTOU guard: re-checks foreground app during approval wait
- Manages per-call cancellation tokens
- Provides resolveApproval(), cancel(), cancelAll(), getState()

STATE OWNERSHIP:
- activeToolCalls: maps callId -> ToolCallState (terminal states excluded)
- cancellationTokens: maps callId -> CancellationToken
- pendingApprovals: maps callId -> CompletableDeferred<ApprovalDecision>

DEPENDENCIES (injected via constructor):
- registry: ToolRegistry - tool name→spec mapping, schema generation
- policyEngine: PolicyEngine - approval/deny/allow decisions

NOTABLE DEPENDENCIES FROM registry:
- registry.get(name) - look up tool by name
- registry.generateResponsesApiTools() - generate FunctionTool for LLM
- registry.createFilteredCopy() - filter tools by allowed set

DEPENDENCIES FROM policyEngine:
- policyEngine.check() - Allow/Deny/AskUser decision
- policyEngine.appClassifier - app tier classification
- policyEngine.reset() - reset approval mode
- policyEngine.allowPackageForSession() - session allowlist

THREAD/COROUTINE:
- execute() is a suspend function
- Uses withTimeout(APPROVAL_TIMEOUT_MS) for user approval waiting
- Uses ConcurrentHashMap for thread-safe maps
- Cancellation via token.cancel() 
- cleanupCall() removes from tracking maps
- isCancelled() checks both context and token

INPUTS:
- toolName: String - name of tool to execute
- params: JSONObject - tool parameters
- context: ToolRouterContext - platform, snapshot, isCancelled()
- packageName: String? - foreground app (optional)
- callId: String? - LLM call ID (generated if not provided)
- onStateChange: ((ToolCallState) -> Unit)? - state change callback
- onApprovalRequired: suspend (ApprovalDetails) -> Unit - user approval callback

OUTPUTS:
- ToolCallResult with success/error/cancelled status
- Contains: callId, output (for Success), observation (for Success), error, exception

SIDE EFFECTS:
- Adds/removes from activeToolCalls, cancellationTokens, pendingApprovals
- Emits state changes via onStateChange callback
- Waits for user approval (blocking with timeout)
- Executes tool invocation via invocation.execute(context)
- Captures post-action snapshot if approval was waited on
- Cleans up tracking maps on all exit paths
- Emits approval resolution events

PERSISTENCE:
- None - all in-memory per-session

NETWORK:
- Indirect: tool execution may involve network (TermuxShellTool HTTP, BrowserScriptTool CDP)
- All network is within the executed tool's scope

ANDROID API:
- Platform operations via context.platform (AndroidPlatform)
- Screen snapshot via context.currentSnapshot
- App classification via context.appClassifier

ERROR HANDLING:
- Unknown tool → ToolCallResult.Error("Unknown tool: $toolName")
- Validation failure → ToolCallResult.Error with details
- Policy deny → ToolCallResult.Cancelled
- User deny/abort/timeout → ToolCallResult.Cancelled
- Execution exception → ToolCallResult.Failure
- Always ensures cleanupCall() in finally block

SECURITY:
- Policy-based access control via policyEngine
- App classification blocks BLOCKED apps (financial/auth)
- Browser script has its own approval matrix
- TOCTOU guard prevents app-switch during approval wait
- Cancellation tokens prevent orphan tool execution

TEST COVERAGE:
- ToolRouterTest.kt
- ToolCallStateTest.kt
- PolicyEngineTest.kt
- AppClassifierSecurityTest.kt
- AppClassifierOverrideTest.kt
- ToolRegistryTest.kt
- WriteTodosToolTest.kt
- TermuxShellToolTest.kt
- SystemActionToolsTest.kt
- DelegateTaskToolTest.kt
- CompleteTaskToolTest.kt
- ScratchpadToolTest.kt
- MobileActionToolTest.kt
- OpenAppToolTest.kt
- BrowserScriptToolTest.kt
- DefaultBrowserScriptCapabilityGateTest.kt
- BrowserUseSkillAssetTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Complete tool execution state machine
- Policy-based approval with user override
- Robust cleanup and cancellation mechanisms
- TOCTOU guards for app switching
- Comprehensive test coverage
- Central tool routing and execution hub