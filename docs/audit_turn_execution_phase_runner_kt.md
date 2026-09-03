FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/agent/TurnExecutionPhaseRunner.kt
PACKAGE: ai.closepaw.agent

MAIN CLASSES:
- TurnExecutionPhaseRunner - Executes selected tool calls for a turn and emits side effects

INTERNAL DATA CLASSES:
- SingleToolCallResult - snapshot + toolResult pair
- ObservationCapture - observation + snapshot pair

MAIN FUNCTIONS:
- executeActions() - executes tool calls sequentially, handles success/failure abortion
- executeSingleToolCall() - executes one tool call through ToolRouter
- captureObservationWithSnapshot() - captures post-action screen
- formatToolResult() - formats tool result for history

RESPONSIBILITY:
- Executes tool calls one per turn (or aborts on failure)
- Manages snapshot updates between sequential tools
- Captures post-action screen observations
- Emits history items, trace events, and UI status updates
- Handles approval requirements during tool execution

STATE OWNED:
- executedToolIds: Set<String> - IDs of tools executed in this turn
- terminatedEarly: Boolean - whether loop was broken early
- lastTerminalResult: ToolCallResult? - result of last tool call

DEPENDENCIES (injected via constructor):
- config: AgentExecutionConfig
- services: SessionServices (provides toolRouter, platform, historyManager, appClassifier, platform, traceRecorder, recordingService, userResponseChannel, config, termuxSnapshot, appSkillRepository, agentSkillManager, memoryStore, memoryRecaller)
- eventDispatcher: AgentEventDispatcher
- trace: AgentTrace

NOTABLE DEPENDENCIES FROM services:
- services.toolRouter.execute() - the core tool execution gateway
- services.platform.captureScreen() - screen capture
- services.platform.getCurrentPackageName() - package detection
- services.appClassifier.maskIfBlocked() - privacy gating
- services.historyManager.addItem() - history management
- services.traceRecorder - trace recording
- services.userResponseChannel - user approval handling
- services.memoryStore - memory operations

THREAD/COROUTINE:
- executeActions() is a suspend function
- Iterates through toolCallsToExecute sequentially (not parallel)
- delay(PRE_EXECUTION_DELAY_MS) before first action
- delay(POST_ACTION_SETTLE_MS) after each action for UI settling
- captureObservationWithSnapshot() has delay(POST_ACTION_SETTLE_MS)
- All on whatever coroutine context is active
- Uses withContext(Dispatchers.IO) in TermuxShellTool execution (not directly)

INPUTS:
- turnId: String
- turnNumber: Int
- initialSnapshot: ScreenSnapshot - starting screen state
- toolCallsToExecute: List<ToolCallRequest> - tools to execute this turn

OUTPUTS:
- ExecutionPhaseResult containing:
  - executedToolIds: Set<String> - which tools ran
  - terminatedEarly: Boolean - whether remaining tools were skipped
  - lastTerminalResult: ToolCallResult? - result of last tool

SIDE EFFECTS:
- Emits turnPhaseChanged(EXECUTION) event
- Emits actionProposed events for each tool
- Adds ResponseItem.FunctionCall to history for each tool
- Adds ResponseItem.FunctionCallOutput to history
- Emits screenCaptured events (POST_ACTION phase)
- Emits actionExecuted events with outcome
- Emits status symbols (✓/✗/⊘)
- May emit approvalRequired events
- May capture and emit post-action screen

PERSISTENCE:
- History items via services.historyManager
- Trace via trace

NETWORK:
- Tool execution may involve network (TermuxShellTool → HTTP bridge, BrowserScriptTool → CDP/Chrome)
- OKHttp calls for Termux, CDP WebSocket for browser
- All network is delegated to the executed tools

ANDROID API:
- AccessibilityService actions via NodeActionPerformer, gestureInjector
- Screen capture via services.platform.captureScreen()
- Package name via services.platform.getCurrentPackageName()
- App classifier via services.appClassifier

ERROR HANDLING:
- If tool returns Error result, execution aborts early (terminatedEarly=true)
- Post-action screen capture failures are caught with runCatching, falls back to text-only observation
- Approval requirements routed through userResponseChannel
- Individual tool failures don't cascade unless configured to abort

SECURITY:
- Tool execution gated by PolicyEngine checks (already validated before planning)
- BLOCKED app masking via appClassifier.maskIfBlocked()
- Post-action screen also masked if user navigated to blocked app
- Approval flow via userResponseChannel ensures user consent

TEST COVERAGE:
- TurnExecutionPhaseRunnerTest.kt
- All individual tool tests (MobileActionToolTest, OpenAppToolTest, TermuxShellToolTest, etc.)
- PolicyEngineTest.kt
- ToolRouterTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Robust tool execution with proper snapshot management
- Error handling that aborts on failure but recovers gracefully
- Post-action observation capture with fallback
- Proper event emission for UI updates
- Well-integrated with policy and approval systems