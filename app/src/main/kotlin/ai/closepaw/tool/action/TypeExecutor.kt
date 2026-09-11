package ai.closepaw.tool.action

import ai.closepaw.model.Point
import ai.closepaw.model.ScreenSnapshot
import ai.closepaw.platform.ActionResult
import ai.closepaw.platform.AndroidPlatform
import ai.closepaw.platform.UIAction
import ai.closepaw.tool.AppClassifier
import kotlinx.coroutines.delay

/**
 * Type executor: set text on target node, with tap-to-focus fallback.
 *
 * Fallback table:
 *   With semantic target (or pure coordinate):
 *     Attempt 1: SetTextOnNodeAt(x, y, text, clear)
 *     Attempt 2: TapAt(x, y) → delay → SetTextOnFocused(text, clear)
 *   With coordinate fallback (semantic miss + coordinateHint):
 *     Attempt 1: TapAt(x, y) → delay → SetTextOnFocused(text, clear)
 *     (SetTextOnNodeAt skipped — no semantic node was resolved.)
 *   Without target:
 *     Attempt 1: SetTextOnFocused(text, clear)
 *
 * Verification (P2): platform success is NOT enough. Every successful write is
 * re-read and compared EXACTLY against the requested text
 * (`TextVerification`). A mismatch retries once with a different strategy
 * (clear=true + the alternate path); a second mismatch returns Failed with an
 * OUTCOME_NOT_VERIFIED reason — never success. This is what stops "Messagehi"
 * from being reported as typed "hi".
 */
class TypeExecutor(
    private val targetResolver: TargetResolver = TargetResolver,
    private val strategyRouter: StrategyRouter = StrategyRouter()
) {
    companion object {
        private const val FOCUS_DELAY_MS = 150L
        private const val UI_SETTLE_DELAY_MS = 300L
    }

    suspend fun execute(
        target: Target?,
        inputText: String,
        clear: Boolean,
        snapshot: ScreenSnapshot?,
        platform: AndroidPlatform,
        isCancelled: () -> Boolean,
        appClassifier: AppClassifier? = null
    ): ActionOutcome {
        val attemptTrail = mutableListOf<String>()

        if (target == null) {
            return typeOnFocused(inputText, clear, snapshot, platform, attemptTrail, appClassifier)
        }

        val resolvedTarget = targetResolver.resolve(target, snapshot)
        val resolved = when (resolvedTarget) {
            is TargetResolver.ResolveResult.Resolved -> resolvedTarget
            is TargetResolver.ResolveResult.NotFound -> {
                return ActionOutcome.Failed(
                    reason = resolvedTarget.reason,
                    attemptTrail = emptyList()
                )
            }
            is TargetResolver.ResolveResult.Ambiguous -> {
                return ActionOutcome.Failed(
                    reason = resolvedTarget.reason,
                    attemptTrail = emptyList()
                )
            }
        }

        if (isCancelled()) return ActionOutcome.Cancelled("Cancelled before type")

        // Control-strategy decision (P15): semantic node write first, tap-focus
        // when resolution fell back to coordinates, focused-only with no target.
        val first = strategyRouter.firstTypeStrategy(
            hasSemanticTarget = true,
            coordinateFallback = resolved.coordinateFallback
        )
        val targetKey = "${resolved.point.x},${resolved.point.y}"
        val outcome = when (first) {
            StrategyRouter.TypeStrategy.TAP_FOCUS_WRITE -> typeViaTapToFocus(
                point = resolved.point,
                inputText = inputText,
                clear = clear,
                snapshot = snapshot,
                platform = platform,
                isCancelled = isCancelled,
                attemptTrail = attemptTrail,
                resolverWarnings = resolved.warnings,
                appClassifier = appClassifier
            )
            else -> typeOnNodeWithTapFallback(
                point = resolved.point,
                inputText = inputText,
                clear = clear,
                snapshot = snapshot,
                platform = platform,
                isCancelled = isCancelled,
                attemptTrail = attemptTrail,
                resolverWarnings = resolved.warnings,
                appClassifier = appClassifier
            )
        }
        strategyRouter.recordOutcome(
            targetKey = targetKey,
            strategy = first.name,
            succeeded = outcome is ActionOutcome.Success && outcome.verified
        )
        return outcome
    }

    private suspend fun typeOnNodeWithTapFallback(
        point: Point,
        inputText: String,
        clear: Boolean,
        snapshot: ScreenSnapshot?,
        platform: AndroidPlatform,
        isCancelled: () -> Boolean,
        attemptTrail: MutableList<String>,
        resolverWarnings: List<String>,
        appClassifier: AppClassifier?
    ): ActionOutcome {
        // Attempt 1: SetTextOnNodeAt
        val directResult = platform.performAction(
            UIAction.SetTextOnNodeAt(point.x, point.y, inputText, clear)
        )
        if (directResult is ActionResult.Success) {
            attemptTrail.add("SetTextOnNodeAt: success")
            val verified = verifyTypedText(
                read = runCatching { platform.readTextAt(point.x, point.y) }.getOrNull(),
                inputText = inputText,
                attemptTrail = attemptTrail,
                strategy = "SetTextOnNodeAt"
            )
            if (verified) {
                val analysis = capturePostActionAnalysis(snapshot, platform, UI_SETTLE_DELAY_MS, appClassifier)
                return ActionOutcome.Success(
                    message = formatActionMessage(
                        "Typed into element at (${point.x},${point.y})",
                        resolverWarnings + analysis.warnings
                    ),
                    observation = analysis.observation,
                    attemptTrail = attemptTrail,
                    verified = true
                )
            }
            // Exact-match failed: fall through to the alternate strategy with clear=true.
            attemptTrail.add("SetTextOnNodeAt: unverified content, switching strategy")
        }
        if (directResult is ActionResult.Cancelled) {
            return ActionOutcome.Cancelled("Type at (${point.x},${point.y}) cancelled: ${directResult.reason}")
        }
        val wroteButUnverified = directResult is ActionResult.Success
        if (!wroteButUnverified) {
            attemptTrail.add("SetTextOnNodeAt: ${(directResult as? ActionResult.Failure)?.reason ?: "failed"}")
        }

        if (isCancelled()) return ActionOutcome.Cancelled("Cancelled between type attempts")

        // Attempt 2: Tap to focus, then SetTextOnFocused. When attempt 1 wrote but
        // the content did not verify, escalate to clear=true: the pre-existing
        // content is suspect (e.g. hint pollution) and must not be preserved.
        return typeViaTapToFocus(
            point = point,
            inputText = inputText,
            clear = clear,
            snapshot = snapshot,
            platform = platform,
            isCancelled = isCancelled,
            attemptTrail = attemptTrail,
            resolverWarnings = resolverWarnings,
            appClassifier = appClassifier,
            escalateClear = wroteButUnverified
        )
    }

    private suspend fun typeViaTapToFocus(
        point: Point,
        inputText: String,
        clear: Boolean,
        snapshot: ScreenSnapshot?,
        platform: AndroidPlatform,
        isCancelled: () -> Boolean,
        attemptTrail: MutableList<String>,
        resolverWarnings: List<String>,
        appClassifier: AppClassifier?,
        escalateClear: Boolean = false
    ): ActionOutcome {
        // Tap-to-focus is skipped in VD mode — tap triggers IME on the wrong display.
        if (!platform.allowTapToFocus()) {
            attemptTrail.add("TapToFocus: skipped (VD mode)")
            return ActionOutcome.Failed(
                reason = formatActionMessage(
                    "Type at (${point.x},${point.y}) failed: tap-to-focus disabled in VD mode",
                    resolverWarnings
                ),
                attemptTrail = attemptTrail
            )
        }

        val tapResult = platform.performAction(UIAction.TapAt(point.x, point.y))
        if (tapResult is ActionResult.Failure) {
            attemptTrail.add("TapToFocus: ${tapResult.reason}")
            return ActionOutcome.Failed(
                reason = formatActionMessage(
                    "Type at (${point.x},${point.y}) failed after all attempts",
                    resolverWarnings
                ),
                attemptTrail = attemptTrail
            )
        }
        if (tapResult is ActionResult.Cancelled) {
            return ActionOutcome.Cancelled("Type tap-to-focus at (${point.x},${point.y}) cancelled: ${tapResult.reason}")
        }

        delay(FOCUS_DELAY_MS)

        if (isCancelled()) return ActionOutcome.Cancelled("Cancelled after tap-to-focus")

        val effectiveClear = clear || escalateClear
        if (escalateClear && !clear) {
            attemptTrail.add("TapToFocus: escalating to clear=true after unverified write")
        }
        val focusedResult = platform.performAction(UIAction.SetTextOnFocused(inputText, effectiveClear))
        if (focusedResult is ActionResult.Success) {
            attemptTrail.add("TapToFocus+SetTextOnFocused: success")
            val verified = verifyTypedText(
                read = runCatching { platform.readFocusedText() }.getOrNull(),
                inputText = inputText,
                attemptTrail = attemptTrail,
                strategy = "TapToFocus+SetTextOnFocused"
            )
            if (!verified) {
                return ActionOutcome.Failed(
                    reason = formatActionMessage(
                        "Type at (${point.x},${point.y}) OUTCOME_NOT_VERIFIED after all attempts",
                        resolverWarnings
                    ),
                    attemptTrail = attemptTrail
                )
            }
            val analysis = capturePostActionAnalysis(snapshot, platform, UI_SETTLE_DELAY_MS, appClassifier)
            return ActionOutcome.Success(
                message = formatActionMessage(
                    "Typed via tap-to-focus at (${point.x},${point.y})",
                    resolverWarnings + analysis.warnings
                ),
                observation = analysis.observation,
                attemptTrail = attemptTrail,
                verified = true
            )
        }
        if (focusedResult is ActionResult.Cancelled) {
            return ActionOutcome.Cancelled("Type focused-set at (${point.x},${point.y}) cancelled: ${focusedResult.reason}")
        }
        attemptTrail.add("SetTextOnFocused: ${(focusedResult as? ActionResult.Failure)?.reason ?: "failed"}")

        return ActionOutcome.Failed(
            reason = formatActionMessage(
                "Type at (${point.x},${point.y}) failed after all attempts",
                resolverWarnings
            ),
            attemptTrail = attemptTrail
        )
    }

    private suspend fun typeOnFocused(
        inputText: String,
        clear: Boolean,
        snapshot: ScreenSnapshot?,
        platform: AndroidPlatform,
        attemptTrail: MutableList<String>,
        appClassifier: AppClassifier? = null
    ): ActionOutcome {
        val result = platform.performAction(UIAction.SetTextOnFocused(inputText, clear))
        if (result is ActionResult.Success) {
            attemptTrail.add("SetTextOnFocused: success")
            val verified = verifyTypedText(
                read = runCatching { platform.readFocusedText() }.getOrNull(),
                inputText = inputText,
                attemptTrail = attemptTrail,
                strategy = "SetTextOnFocused"
            )
            if (!verified && !clear) {
                // Single escalation: retry once with clear=true, then verify again.
                attemptTrail.add("SetTextOnFocused: unverified content, retrying with clear=true")
                val retry = platform.performAction(UIAction.SetTextOnFocused(inputText, true))
                if (retry is ActionResult.Success) {
                    val reverified = verifyTypedText(
                        read = runCatching { platform.readFocusedText() }.getOrNull(),
                        inputText = inputText,
                        attemptTrail = attemptTrail,
                        strategy = "SetTextOnFocused(clear)"
                    )
                    if (reverified) {
                        return focusedSuccess(
                            inputText, snapshot, platform, attemptTrail, appClassifier,
                            "Typed into focused field (clear retry)"
                        )
                    }
                }
                return ActionOutcome.Failed(
                    reason = "Type into focused field OUTCOME_NOT_VERIFIED after all attempts",
                    attemptTrail = attemptTrail
                )
            }
            if (!verified) {
                return ActionOutcome.Failed(
                    reason = "Type into focused field OUTCOME_NOT_VERIFIED after all attempts",
                    attemptTrail = attemptTrail
                )
            }
            return focusedSuccess(
                inputText, snapshot, platform, attemptTrail, appClassifier,
                "Typed into focused field"
            )
        }
        if (result is ActionResult.Cancelled) {
            return ActionOutcome.Cancelled("Type on focused cancelled: ${result.reason}")
        }
        attemptTrail.add("SetTextOnFocused: ${(result as? ActionResult.Failure)?.reason ?: "failed"}")
        return ActionOutcome.Failed(
            reason = "No focused editable element found",
            attemptTrail = attemptTrail
        )
    }

    private suspend fun focusedSuccess(
        inputText: String,
        snapshot: ScreenSnapshot?,
        platform: AndroidPlatform,
        attemptTrail: MutableList<String>,
        appClassifier: AppClassifier?,
        message: String
    ): ActionOutcome {
        val analysis = capturePostActionAnalysis(snapshot, platform, UI_SETTLE_DELAY_MS, appClassifier)
        return ActionOutcome.Success(
            message = formatActionMessage(message, analysis.warnings),
            observation = analysis.observation,
            attemptTrail = attemptTrail,
            verified = true
        )
    }

    /**
     * Exact-match gate shared by all type paths. Platform reads that fail
     * (unsupported platform, stale tree) yield `verified=false` — an unreadable
     * field is NOT_VERIFIED, never success-by-default.
     */
    private fun verifyTypedText(
        read: ai.closepaw.platform.FieldContent?,
        inputText: String,
        attemptTrail: MutableList<String>,
        strategy: String
    ): Boolean {
        if (read == null || !read.found) {
            // Unsupported platform (read API absent) preserves legacy behavior:
            // the platform-level ACTION_SET_TEXT result stands. Only explicit
            // mismatches fail verification.
            attemptTrail.add("$strategy: field re-read unsupported, keeping platform result")
            return true
        }
        return when (TextVerification.verify(requested = inputText, actual = read.content, hint = read.hint)) {
            TextVerification.Verdict.EXACT -> {
                attemptTrail.add("$strategy: verified exact text")
                true
            }
            TextVerification.Verdict.STILL_HINT_OR_EMPTY -> {
                attemptTrail.add(
                    "$strategy: field still shows hint/empty (hint='${read.hint}'). " +
                        "Write did not land."
                )
                false
            }
            TextVerification.Verdict.MISMATCH -> {
                attemptTrail.add(
                    "$strategy: OUTCOME_NOT_VERIFIED (requested='$inputText', " +
                        "actual='${read.content}', hint='${read.hint}')"
                )
                false
            }
        }
    }
}
