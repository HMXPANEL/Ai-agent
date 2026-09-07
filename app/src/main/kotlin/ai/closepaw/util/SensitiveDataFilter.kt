package ai.closepaw.util

/**
 * Redacts credential-like values before they reach diagnostic storage or Logcat.
 *
 * This is intentionally conservative: labels are retained for useful diagnostics, values are
 * replaced with a fixed marker, and common bearer/key/JWT shapes are covered even without a
 * preceding label.
 */
open class SensitiveDataFilter {
    open fun redact(value: String): String {
        var result = value
        result = KEY_VALUE_PATTERN.replace(result) { match ->
            "${match.groups[1]?.value ?: "secret"}=[REDACTED]"
        }
        result = BEARER_PATTERN.replace(result, "Bearer [REDACTED]")
        result = AUTH_HEADER_PATTERN.replace(result) { match ->
            "${match.groups[1]?.value ?: "Authorization"}: [REDACTED]"
        }
        result = OTP_PATTERN.replace(result) { match ->
            "${match.groups[1]?.value ?: "code"} [REDACTED]"
        }
        result = TOKEN_SHAPE_PATTERN.replace(result, "[REDACTED]")
        return result
    }

    companion object {
        private val FLAGS = setOf(RegexOption.IGNORE_CASE)

        private val KEY_VALUE_PATTERN = Regex(
            "\\b(password|passwd|pwd|passcode|secret|api[_ -]?key|access[_ -]?token|" +
                "refresh[_ -]?token|id[_ -]?token|client[_ -]?secret|private[_ -]?key)" +
                "\\s*(?:=|:)\\s*([^,;\\s}\\]]+)",
            FLAGS
        )
        private val AUTH_HEADER_PATTERN = Regex(
            "\\b(authorization|proxy-authorization)\\s*:\\s*([^\\r\\n]+)",
            FLAGS
        )
        private val BEARER_PATTERN = Regex("\\bBearer\\s+[A-Za-z0-9._~+/-]+=*", FLAGS)
        private val OTP_PATTERN = Regex(
            "\\b(password|passcode|otp|one[- ]?time[- ]?(?:password|code)|verification(?: code)?)" +
                "\\s*(?:is|=|:)??\\s*\\d{4,8}\\b",
            FLAGS
        )
        private val TOKEN_SHAPE_PATTERN = Regex(
            "(?<![A-Za-z0-9])(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|" +
                "xox[baprs]-[A-Za-z0-9-]{12,}|AIza[0-9A-Za-z_-]{20,}|eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})(?![A-Za-z0-9])"
        )
    }
}
