FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/VirtualDisplayPlatform.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- VirtualDisplayPlatform - Implementation of AndroidPlatform for virtual display mode

RELATED KEY FILES (from earlier exploration):
- VdLifecycleArbiter.kt - lifecycle management
- VirtualDisplayConfig.kt - configuration
- VirtualDisplayAppController.kt - app control
- VirtualDisplayInputInjector.kt - input injection
- VirtualDisplayScreenshotProcessor.kt - screenshot processing
- VirtualDisplayWindowAccessor.kt - window accessor
- ShizukuClient.kt - Shizuku client for virtual display
- ShizukuDisplayTransport.kt - display transport
- ShizukuInputTransport.kt - input transport
- ShizukuActivityTaskTransport.kt - activity task transport
- ShizukuActivityLauncher.kt - activity launching
- ShizukuServiceProxyProvider.kt - service proxy
- ShizukuShellExecutor.kt - shell execution
- VirtualDisplayCaptureCoordinator.kt - capture coordination
- ShizukuRuntimeGateway.kt - runtime gateway
- OverlayTouchGate.kt - overlay gate (also in platform)
- NodeActionPerformer.kt - node action performance
- AccessibilityGestureInjector.kt - gesture injection
- AccessibilityPlatform - also referenced

RESPONSIBILITY:
- Virtual display mode implementation for agent operation
- Creates and manages virtual display surface
- Launches target apps on virtual display
- Handles Shizuku integration for privileged operations
- Coordinates screen capture and input injection
- Manages virtual display lifecycle (creation/destruction)
- Routes accessibility events to virtual display app
- Supports background execution of agent on VD

KEY INTERACTIONS:
- Shizuku: provides privileged operations (window management, input)
- AccessibilityService: receives events from virtual display
- Android WindowManager: creates and manages virtual display
- AppController: launches target apps on VD
- Input injectors: keyboard, gestures on VD
- Screenshot processors: captures VD screen state

LIFECYCLE:
- display creation → app launch → agent operation → display destruction
- Managed by VdLifecycleArbiter
- Shizuku integration for elevated operations
- Foreground/agent separation (VD apps run separately)

CAPABILITIES:
- Virtual display creation with configurable dimensions/density
- Launch apps on virtual display
- Agent operates on VD screen via AccessibilityService
- Input injection (tap, type, swipe) on VD
- Screen capture from VD
- Background execution support
- Display termination on session completion

SHIZUKU INTEGRATION:
- ShizukuClient for binder connection
- ShizukuDisplayTransport for display operations
- ShizukuInputTransport for input operations
- ShizukuShellExecutor for shell commands
- Required for certain VD operations (app launch, input)

ANDROID API:
- WindowManager for virtual display creation
- AccessibilityService events on VD
- DisplayMetrics for density/dimensions
- Intent for app launching on VD

ERROR HANDLING:
- Shizuku death/loss handling
- Virtual display disappearance
- Target app crash recovery
- Display dimension mismatches

CRITICAL FLOW (Virtual Display Mode):
1. Session starts in VD mode
2. VdLifecycleArbiter creates virtual display via Shizuku
3. Target app launched on VD via Intent
4. Agent's AccessibilityService monitors VD app screen
5. Agent performs actions via Shizuku-enhanced APIs
6. Screen captures from VD for perception
7. On session end: display destroyed, app terminated

STATUS: CONFIRMED IMPLEMENTED
- Complete virtual display implementation
- Shizuku-dependent privileged operations
- Lifecycle management via VdLifecycleArbiter
- Foreground/agent separation model
- Full cycle: creation → operation → termination