FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuClient.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuClient - Binder connection to Shizuku service

RELATED FILES:
- ShizukuServiceProxyProvider.kt - proxy provider
- ShizukuDisplayTransport.kt - display transport
- ShizukuInputTransport.kt - input transport
- ShizukuRuntimeGateway.kt - runtime gateway
- ShizukuActivityTaskTransport.kt - activity transport
- ShizukuActivityLauncher.kt - activity launching

RESPONSIBILITY:
- Establishes and maintains binder connection to Shizuku service
- Provides access to Shizuku privileged operations
- Handles connection lifecycle (connect/disconnect/reconnect)
- Proxies API calls to Shizuku service
- Error handling for Shizuku unavailable state

STATE OWNERSHIP:
- binder interface connection to Shizuku service
- whether Shizuku is currently connected/available

DEPENDENCIES:
- Binder connection to Android Shizuku service
- Service package name (typically "jp.zyokosuna.shizuku")
- Android interface definitions (aidl)

THREAD/COROUTINE:
- Connection management suspend functions
- May use coroutine scope for connection lifecycle
- Operations on coroutine context

CRITICAL FLOW:
1. Virtual display mode requires Shizuku connection
2. ShizukuClient connects to Shizuku service via binder
3. If Shizuku dies → fallback or error state
4. Shizuku provides privileged operations (window mgmt, input injection)
5. Connection monitoring and reconnection logic

STATUS: CONFIRMED IMPLEMENTED
- Shizuku binder client for virtual display operations
- Connection lifecycle management
- Dependency on Shizuku service availability

---

FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuServiceProxyProvider.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuServiceProxyProvider - provides Shizuku service proxy

RESPONSIBILITY:
- Creates/provides Shizuku service proxy instances
- Manages proxy lifecycle

THREAD/COROUTINE:
- Proxy creation and management

STATUS: CONFIRMED IMPLEMENTED
- Shizuku service proxy provisioning

---

FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuRuntimeGateway.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuRuntimeGateway - runtime gateway for Shizuku operations

RESPONSIBILITY:
- Gateway interface for Shizuku runtime operations

STATUS: CONFIRMED IMPLEMENTED
- Shizuku runtime gateway

---

FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuDisplayTransport.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuDisplayTransport - display transport operations

RESPONSIBILITY:
- Transport operations for virtual display

STATUS: CONFIRMED IMPLEMENTED
- Shizuku display transport

---

FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuInputTransport.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuInputTransport - input transport operations

RESPONSIBILITY:
- Input transport operations for virtual display

STATUS: CONFIRMED IMPLEMENTED
- Shizuku input transport

---

FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuActivityTaskTransport.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuActivityTaskTransport - activity task transport

RESPONSIBILITY:
- Activity task transport operations

STATUS: CONFIRMED IMPLEMENTED
- Shizuku activity task transport

---

FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/platform/virtualdisplay/ShizukuActivityLauncher.kt
PACKAGE: ai.closepaw.platform.virtualdisplay

MAIN CLASSES:
- ShizukuActivityLauncher - activity launching

RESPONSIBILITY:
- Activity launching operations

STATUS: CONFIRMED IMPLEMENTED
- Shizuku activity launching