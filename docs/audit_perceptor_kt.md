FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/perception/Perceptor.kt
PACKAGE: ai.closepaw.perception

MAIN CLASSES:
- Perceptor - The Perception Engine. Converts raw AccessibilityNodeInfo tree into a semantic ScreenSnapshot.
- PerceptorCandidateElement - internal candidate element during collection
- PerceptorFilterConfig - configuration for filtering
- PerceptorDiagnosticsCollector - collects diagnostics during perception

OBJECTS / INTERNAL:
- PoolCounters - tracks interactive vs non-interactive element counts

RESPONSIBILITY:
- Traverses AccessibilityNodeInfo tree to collect PerceptionElements
- Applies truncation, sorting, and indexing for LLM prompt
- Generates JSON prompt representation via toPromptJson()
- Handles multi-root (multi-window) perception
- Does NOT store AccessibilityNodeInfo references (prevents memory leaks)
- Roots are NOT recycled — caller is responsible for lifecycle

KEY ALGORITHM:
- Two-pass collection: over-collect both interactive and non-interactive pools
- applyTruncation: score-based headroom via interactiveKeepRatio
- spatialSort: row-based sorting with rowSnapScreenRatio
- enrichEmptyTextElements: fills missing text from description
- toPromptJson(): generates JSON for LLM prompting with bounds, class, clickable, editable, etc.

FILTER CONFIG (PerceptorFilterConfig):
- maxElements, interactiveKeepRatio, visibilityThreshold
- minElementSizePx, rowSnapScreenRatio
- useVisibleToUserFilter, filterKeyboard
- clipBounds, resourceIdOutputDensityThreshold

RECOGNIZED PROPERTIES per element:
- text, description, resourceId, className
- isClickable, isEditable, isScrollable, isEnabled, isSelected, isChecked, isCheckable
- bounds (in screen coordinates), center point
- hintText, rangeInfo (current/min/max), range_percent
- isFocused, isLongClickable

TRAVERSAL ALGORITHM:
- For each root node: check visibility, extract properties, compute visibility ratio, check min size
- Build element key for deduplication (resourceId + className + text + desc + rect + flags)
- Pool caps: interactive capped at interactiveCap, non-interactive at nonInteractiveCap
- Only keep shouldKeep = isInteractive || hasContent elements
- Visibility check: visibleAreaRatio >= visibilityThreshold
- Min size check: rect.width/height > minElementSizePx
- Dedup by computed key
- Recurse children with shouldRecycle=true (recycles child nodes)

MULTI-WINDOW HANDLING:
- Collects roots from all eligible windows
- Sorted by layer for deterministic ordering
- Falls back to rootInActiveWindow if windows enumeration fails
- Detects keyboard visibility (TYPE_INPUT_METHOD)
- Excludes TYPE_ACCESSIBILITY_OVERLAY and TYPE_INPUT_METHOD from roots

SCREENCAPTURE INTEGRATION (AccessibilityPlatform):
- captureAccessibilityTree() collects window roots
- Perceptor.snapshot(roots) produces ScreenSnapshot
- screenshot capture optional based on config

RECOGNIZED INPUT:
- AccessibilityNodeInfo roots (from AccessibilityService)
- screenWidthPx, screenHeightPx for visibility filtering
- PerceptorFilterConfig for all filtering parameters

OUTPUT:
- ScreenSnapshot(timestamp, elements, image?, debug?)

SIDE EFFECTS:
- Calls node.recycleCompat() on processed nodes (when shouldRecycle=true)
- May store diagnostic artifacts if traceRecorder enabled
- Element key computation for dedup

PERSISTENCE:
- None - in-memory perception only

NETWORK:
- None - local Android accessibility tree processing

ANDROID API:
- AccessibilityNodeInfo traversal
- Rect.getBoundsInScreen()
- AccessibilityNodeInfo properties: isVisibleToUser, isClickable, isEditable, etc.
- AccessibilityWindowInfo for multi-window

ERROR HANDLING:
- Empty roots → empty ScreenSnapshot
- Missing fields default to empty/false
- filterConfig defaults used when not specified
- Diagnostic artifacts optional via traceRecorder

TEST COVERAGE:
- PerceptorTest.kt
- PerceptorInternalsTest.kt
- ScreenSummaryTest.kt
- PerceptorFilterConfig (implicitly tested)
- PerceptorDiagnosticsCollector tests

STATUS: CONFIRMED IMPLEMENTED
- Comprehensive accessibility tree traversal and semantic element collection
- Multi-window support with keyboard detection
- Proper deduplication and truncation
- JSON prompt generation with full element metadata
- Configurable filtering and prioritization
- Good test coverage
- Critical component for screen perception → LLM prompt pipeline