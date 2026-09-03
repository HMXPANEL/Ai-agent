package ai.closepaw.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataFilterTest {
    private val filter = SensitiveDataFilter()

    @Test
    fun redactsPasswordBearerApiKeyOtpAndAuthorizationHeader() {
        val input = "password: foo Authorization: Bearer abc.def_123 api_key=sk-1234567890123456 otp=123456"
        val output = filter.redact(input)

        assertFalse(output.contains("foo"))
        assertFalse(output.contains("abc.def_123"))
        assertFalse(output.contains("sk-1234567890123456"))
        assertFalse(output.contains("123456"))
        assertTrue(output.contains("[REDACTED]"))
    }

    @Test
    fun redactsJwtLikeSecretsWithoutLabels() {
        val jwt = "eyJaaaaaaaaaaaaaaaaaaaa.aaaaaaaaaaa.bbbbbbbbbbb"
        assertFalse(filter.redact(jwt).contains(jwt))
    }
}
