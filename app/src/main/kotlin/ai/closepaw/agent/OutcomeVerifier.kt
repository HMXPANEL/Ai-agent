package ai.closepaw.agent

import ai.closepaw.model.PerceptionElement
import ai.closepaw.model.ScreenSnapshot
import ai.closepaw.platform.AndroidPlatform

/**
 * Universal outcome verification (P3/P15).
 *
 * Checks a [StructuredTask]'s machine-checkable criteria against FRESH state —
 * never against the planning snapshot. Every check is app-agnostic: packages,
 * exact field values, and exact message text work for messaging, browsers,
 * settings, email, HMX itself, and any other app.
 *
 * Heuristics are documented per check. Anything a check cannot observe yields
 * FAIL (not-verified), never a pass by default.
 */
internal object OutcomeVerifier {

    data class Check(val name: String, val passed: Boolean, val detail: String)

    /**
     * Run all applicable checks for [criteria]. Returns one line per check plus
     * a summary head line, suitable for history and diagnostics.
     */
    suspend fun verify(
        criteria: TaskVerificationCriteria,
        platform: AndroidPlatform,
        snapshot: ScreenSnapshot?,
    ): List<Check> {
        val checks = mutableListOf<Check>()
        criteria.expectedPackage?.let { expected ->
            val current = runCatching { platform.getCurrentPackageName() }.getOrNull()
            checks += if (current != null && current.equals(expected, ignoreCase = true)) {
                Check("foreground-package", true, "foreground=$current")
            } else {
                Check(
                    "foreground-package", false,
                    "expected=$expected actual=${current ?: "<unknown>"}"
                )
            }
        }
        criteria.expectedFieldText?.let { expected ->
            val elements = snapshot?.elements.orEmpty()
            val match = elements.firstOrNull { it.text == expected && it.isEditable }
                ?: elements.firstOrNull { it.text == expected }
            checks += if (match != null) {
                Check("field-text", true, "field contains exactly '$expected'")
            } else {
                Check(
                    "field-text", false,
                    "no field contains exactly '$expected' " +
                        "(closest=${closestText(elements, expected)})"
                )
            }
        }
        criteria.expectedSentText?.let { expected ->
            // Outgoing bubble heuristic: exact text in a NON-editable element.
            // The input field is editable, so it can never satisfy this check —
            // typed-but-unsent text does not count as sent.
            val elements = snapshot?.elements.orEmpty()
            val bubble = elements.firstOrNull { it.text == expected && !it.isEditable }
            checks += if (bubble != null) {
                Check("sent-message", true, "outgoing message '$expected' present")
            } else {
                val typedOnly = elements.any { it.text == expected && it.isEditable }
                Check(
                    "sent-message", false,
                    if (typedOnly) {
                        "'$expected' is only in an editable field — not sent"
                    } else {
                        "no outgoing message '$expected' found " +
                            "(closest=${closestText(elements, expected)})"
                    }
                )
            }
        }
        criteria.expectedRecipient?.let { expected ->
            val elements = snapshot?.elements.orEmpty()
            val present = elements.any {
                it.text.contains(expected, ignoreCase = true) ||
                    it.description.contains(expected, ignoreCase = true)
            }
            checks += if (present) {
                Check("recipient-context", true, "recipient '$expected' visible")
            } else {
                Check("recipient-context", false, "recipient '$expected' not visible")
            }
        }
        return checks
    }

    /**
     * Full turn verification: tool-flag policy first, criteria checks second,
     * worst wins. A `null` task means "no structured criteria" — the tool-flag
     * assessment stands alone.
     */
    suspend fun verifyTurn(
        task: StructuredTask?,
        executedToolNames: Collection<String>,
        outputs: Collection<String>,
        hasFailure: Boolean,
        platform: AndroidPlatform,
        snapshot: ScreenSnapshot?,
    ): TurnVerificationResult {
        val base = TaskVerification.assess(executedToolNames, outputs, hasFailure)
        if (base == TaskVerification.Assessment.NOT_REQUIRED || task == null) {
            return TurnVerificationResult(
                assessment = base,
                details = if (task == null && base != TaskVerification.Assessment.NOT_REQUIRED) {
                    listOf("VERIFY: no structured criteria; tool-level flags decide")
                } else {
                    listOf("VERIFY: no device actuation; no UI proof required")
                }
            )
        }
        val checks = verify(task.verification, platform, snapshot)
        val lines = checks.map {
            "VERIFY ${it.name}: ${if (it.passed) "PASS" else "FAIL"} — ${it.detail}"
        }
        val failed = checks.any { !it.passed }
        val final = when {
            failed -> TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED
            else -> base
        }
        return TurnVerificationResult(assessment = final, details = lines.ifEmpty {
            listOf("VERIFY: criteria present but nothing checkable this turn")
        })
    }

    private fun closestText(
        elements: List<PerceptionElement>,
        expected: String
    ): String {
        if (elements.isEmpty() || expected.isEmpty()) return "<none>"
        // Cheap similarity: longest text that shares a case-insensitive substring.
        val lower = expected.lowercase()
        val candidate = elements.mapNotNull { it.text.takeIf { t -> t.isNotEmpty() } }
            .maxByOrNull { t ->
                val tl = t.lowercase()
                (tl.commonPrefixWith(lower).length + tl.commonSuffixWith(lower).length)
            }
        return candidate?.take(60) ?: "<none>"
    }
}
