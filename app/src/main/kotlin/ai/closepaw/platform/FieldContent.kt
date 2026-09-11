package ai.closepaw.platform

/**
 * Observed content of an editable field (P2 verified text entry).
 *
 * `content` is the ACTUAL committed value: empty when the field is empty or
 * shows only its placeholder. `hint` is the placeholder when the platform
 * exposes one separately (often null on custom views — hence [content] must
 * never be trusted to exclude it without [TextVerification]-style checks).
 */
data class FieldContent(
    val content: String?,
    val hint: String?,
    val found: Boolean,
) {
    companion object {
        fun missing(): FieldContent = FieldContent(content = null, hint = null, found = false)
    }
}
