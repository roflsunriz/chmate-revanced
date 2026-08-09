package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManifestClassNameSanitizerTest {
    @Test
    fun `hyphens in an R8 component class are replaced and recorded`() {
        ManifestClassNameSanitizer.reset()

        val original = "o.${'$'}r8${'$'}lambda${'$'}abc-def"
        val sanitized = "o.${'$'}r8${'$'}lambda${'$'}abc_def"

        assertEquals(sanitized, ManifestClassNameSanitizer.sanitize(original))
        assertEquals(mapOf(original to sanitized), ManifestClassNameSanitizer.replacements())
    }

    @Test
    fun `valid component classes are unchanged and not recorded`() {
        ManifestClassNameSanitizer.reset()

        assertEquals("jp.example.Valid${'$'}Nested", ManifestClassNameSanitizer.sanitize("jp.example.Valid${'$'}Nested"))
        assertEquals(emptyMap<String, String>(), ManifestClassNameSanitizer.replacements())
    }
}
