package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayoutXmlSanitizerTest {
    @Test
    fun `nested custom views are converted to inflater compatible view elements`() {
        val original = """
            <o.CharMatcher${'$'}m android:id="@id/root">
                <o.MoreObjects${'$'}ToStringHelper${'$'}a android:id="@id/child" />
            </o.CharMatcher${'$'}m>
        """.trimIndent()

        val expected = """
            <view class="o.CharMatcher${'$'}m" android:id="@id/root">
                <view class="o.MoreObjects${'$'}ToStringHelper${'$'}a" android:id="@id/child" />
            </view>
        """.trimIndent()

        assertEquals(expected, LayoutXmlSanitizer.sanitize(original))
    }

    @Test
    fun `ordinary Android layouts are unchanged`() {
        val original = "<LinearLayout><TextView /></LinearLayout>"
        assertEquals(original, LayoutXmlSanitizer.sanitize(original))
    }

    @Test
    fun `double dollar and hyphenated R8 view names are converted`() {
        val original = "<o.p${'$'}${'$'}ExternalSyntheticLambda1><o.${'$'}r8${'$'}lambda${'$'}abc-def /></o.p${'$'}${'$'}ExternalSyntheticLambda1>"
        val expected = "<view class=\"o.p${'$'}${'$'}ExternalSyntheticLambda1\"><view class=\"o.${'$'}r8${'$'}lambda${'$'}abc-def\" /></view>"

        assertEquals(expected, LayoutXmlSanitizer.sanitize(original))
    }
}
