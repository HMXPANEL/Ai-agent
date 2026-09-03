FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/browser/script/BrowserScriptTool.kt
PACKAGE: ai.closepaw.browser.script (inferred)

MAIN CLASSES:
- BrowserScriptTool - Tool for executing JavaScript in browser via CDP

RELEVANT FILES (from inspection):
- BrowserSessionManager.kt - manages browser session, script execution
- BrowserScriptBridge.kt - bridge between Kotlin and JavaScript
- BrowserScriptJsInterface.kt - JavaScript interface for evaluation
- BrowserScriptPrelude.kt - script prelude/setup
- BrowserScriptRunner.kt - runs scripts in browser context
- DefaultBrowserScriptCapabilityGate - gates browser script execution

RESPONSIBILITY:
- Execute JavaScript in browser pages via Chrome DevTools Protocol (CDP)
- Session management for browser automation
- Script execution with timeout and error handling
- Browser tab selection and navigation
- Script tracing and metadata capture

STATE OWNERSHIP:
- Browser session state (open tabs, CDP connection)
- Script execution state (running, completed, failed, timed out)

DEPENDENCIES:
- Services: browserSessionManager, traceRecorder, settingsStore
- Termux bridge (for Shizuku-assisted browser control)
- Chrome/CDP connection
- DefaultBrowserScriptCapabilityGate for approval/gating

THREAD/COROUTINE:
- suspend fun run(script, timeout) - executes script
- Uses timeouts for script execution
- Coroutine cancellation support

INPUTS:
- script: String - JavaScript to execute
- timeout: timeout in seconds

OUTPUTS:
- Script execution results with outcome, metadata

SIDE EFFECTS:
- CDP commands to Chrome
- Browser tab/window state changes
- Script output capture
- Trace metadata storage

PERSISTENCE:
- None - session-based only

NETWORK:
- Chrome/CDP WebSocket connection
- Localhost browser automation

ANDROID API:
- Chrome CDP integration
- Shizuku bridge for privileged actions

ERROR HANDLING:
- Script timeout
- JavaScript errors
- CDP connection loss
- Browser tab closure

TEST COVERAGE:
- BrowserScriptToolTest.kt
- BrowserScriptBridgeTest.kt
- BrowserScriptJsInterfaceTest.kt
- BrowserScriptPreludeTest.kt
- BrowserSessionManagerTest.kt
- DefaultBrowserScriptCapabilityGateTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Browser automation via CDP
- Session management
- Script execution with timeouts
- Capability gating
- Trace metadata capture