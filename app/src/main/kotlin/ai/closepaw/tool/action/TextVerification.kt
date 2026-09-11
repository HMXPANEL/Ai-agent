package ai.closepaw.tool.action

/**
 * Exact text-entry verification (pure, JVM-testable).
 *
 * Decides whether an editable field really contains the requested text.
 * The critical distinction is hint/placeholder text vs actual content:
 * several apps (notably messaging apps with custom input views) expose the
 * placeholder through the same text channel, so `"Message" + "hi"` must read
 * as a mismatch — never as success.
 */
object TextVerification {

    enum class Verdict {
        /** Field content equals the requested text exactly. */
        EXACT,

        /** Field looks untouched: empty or still showing only the hint. */
        STILL_HINT_OR_EMPTY,

        /** Content differs (wrong node, appended to stale text, truncated…). */
        MISMATCH,
    }

    /**
     * @param requested exact text that was asked for.
     * @param actual current field content, or null if unreadable.
     * @param hint placeholder/hint text when known, else null.
     */
    fun verify(requested: String, actual: String?, hint: String?): Verdict {
        if (actual == null) return Verdict.MISMATCH
        if (actual == requested) return Verdict.EXACT
        val hintText = hint?.takeIf { it.isNotEmpty() }
        if (actual.isEmpty()) return Verdict.STILL_HINT_OR_EMPTY
        // Hint masquerading as content: field shows hint, or hint+requested
        // glued together ("Messagehi"). Both mean the write did not land cleanly.
        if (hintText != null) {
            if (actual == hintText) return Verdict.STILL_HINT_OR_EMPTY
            if (actual == hintText + requested) return Verdict.MISMATCH
            if (actual.startsWith(hintText) && actual.endsWith(requested) &&
                actual.length == hintText.length + requested.length
            ) {
                return Verdict.MISMATCH
            }
        }
        return Verdict.MISMATCH
    }

    /**
     * Decide whether pre-write content should be treated as empty.
     * Guards the platform append path when `isShowingHintText` is unreliable
     * (custom views exposing placeholder via `node.text`): content that equals
     * the known hint — or is blank — counts as empty.
     */
    fun treatAsEmptyForWrite(existing: String?, hint: String?): Boolean {
        if (existing.isNullOrEmpty()) return true
        val hintText = hint?.takeIf { it.isNotEmpty() } ?: return false
        return existing == hintText
    }
}
