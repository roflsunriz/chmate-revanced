package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceNameSanitizerTest {
    @Test
    fun `numeric definitions and typed references receive a valid prefix`() {
        val original = """
            <resources>
                <string name="2131886216">ChMate</string>
                <style name="2131951961" parent="2131951955" />
                <item name="icon" type="drawable">@drawable/2131231234</item>
                <item name="optional" type="id">@+id/2131362252</item>
            </resources>
        """.trimIndent()
        val expected = """
            <resources>
                <string name="res_2131886216">ChMate</string>
                <style name="res_2131951961" parent="res_2131951955" />
                <item name="icon" type="drawable">@drawable/res_2131231234</item>
                <item name="optional" type="id">@+id/res_2131362252</item>
            </resources>
        """.trimIndent()

        assertEquals(expected, ResourceNameSanitizer.sanitizeXml(original))
    }

    @Test
    fun `numeric enum values and missing apktool values become declared symbols`() {
        val attrs = """
            <resources>
                <attr name="pullSetting"><flag name="2131362722" value="2" /></attr>
                <attr name="displayOptions"><flag name="zero" value="0" /></attr>
            </resources>
        """.trimIndent()
        val usage = """
            <view pullSetting="APKTOOL_MISSING_0x7f0a0153|2131362722" />
            <style><item name="displayOptions">APKTOOL_MISSING_0x7f0a0326</item></style>
        """.trimIndent()
        val symbols = ResourceNameSanitizer.numericAttributeSymbols(attrs)
        val definitions = ResourceNameSanitizer.findMissingAttributeValues(usage)
        val sanitizedAttrs = ResourceNameSanitizer.addMissingAttributeDefinitions(
            ResourceNameSanitizer.sanitizeXml(attrs, symbols),
            definitions,
            mapOf(
                "pullSetting" to mapOf("7f0a0153" to 1),
                "displayOptions" to mapOf("7f0a0326" to 4),
            ),
        )

        assertEquals(
            "<view pullSetting=\"missing_7f0a0153|res_2131362722\" />\n" +
                "<style><item name=\"displayOptions\">missing_7f0a0326</item></style>",
            ResourceNameSanitizer.sanitizeXml(usage, symbols),
        )
        check("<flag name=\"missing_7f0a0153\" value=\"0x00000001\" />" in sanitizedAttrs)
        check("<flag name=\"missing_7f0a0326\" value=\"0x00000004\" />" in sanitizedAttrs)
    }

    @Test
    fun `attribute IDs are recovered from sanitized public resources`() {
        val publicXml = """
            <resources>
                <public type="attr" name="pullSetting" id="0x7f04020e" />
                <public type="id" name="res_2131297014" id="0x7f0902f6" />
            </resources>
        """.trimIndent()

        assertEquals(
            mapOf("pullSetting" to 0x7f04020e),
            ResourceNameSanitizer.attributeResourceIds(publicXml),
        )
    }

    @Test
    fun `application layout attributes retain their compiled resource IDs and scalar types`() {
        val attrs = """
            <resources>
                <attr name="layout_constraintEnd_toEndOf"><enum name="res_2131296739" value="0" /></attr>
                <attr name="pullSetting"><flag name="res_2131297014" value="2" /></attr>
            </resources>
        """.trimIndent()
        val layout = """
            <o.Root xmlns:android="http://schemas.android.com/apk/res/android">
                <o.Toolbar layout_constraintEnd_toEndOf="res_2131296739" pullSetting="res_2131297014" />
            </o.Root>
        """.trimIndent()

        assertEquals(
            """
                <o.Root xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:android="http://schemas.android.com/apk/res/android">
                    <o.Toolbar app:layout_constraintEnd_toEndOf="res_2131296739" app:pullSetting="res_2131297014" />
                </o.Root>
            """.trimIndent(),
            ResourceNameSanitizer.qualifyApplicationAttributes(
                layout,
                ResourceNameSanitizer.applicationAttributeNames(attrs),
            ),
        )
    }

    @Test
    fun `numeric file names are prefixed while ordinary names remain unchanged`() {
        assertEquals("res_2130771968.xml", ResourceNameSanitizer.sanitizeFileName("2130771968.xml"))
        assertEquals("res_2131099999.png", ResourceNameSanitizer.sanitizeFileName("2131099999.png"))
        assertEquals("activity_home.xml", ResourceNameSanitizer.sanitizeFileName("activity_home.xml"))
    }

    @Test
    fun `dummy names and raw resource ids are resolved`() {
        val publicXml = """
            <resources>
                <public type="color" name="APKTOOL_DUMMYVAL_0x7f060043" id="0x7f060043" />
                <public type="mipmap" name="APKTOOL_DUMMYVAL_0x7f0e0001" id="0x7f0e0001" />
            </resources>
        """.trimIndent()
        val references = ResourceNameSanitizer.resourceIdReferences(publicXml)
        val decoded = "<adaptive-icon background=\"@2131099715\" foreground=\"@2131623937\" />"

        assertEquals(
            "<adaptive-icon background=\"@color/res_7f060043\" foreground=\"@mipmap/res_7f0e0001\" />",
            ResourceNameSanitizer.sanitizeXml(decoded, resourceIdReferences = references),
        )
    }

    @Test
    fun `undeclared raw enum values receive a symbolic definition`() {
        val attrs = "<resources><attr name=\"paletteButtonStyle\"><enum name=\"zero\" value=\"0\" /></attr></resources>"
        val layout = "<view app:paletteButtonStyle=\"1\" />"
        val symbolic = ResourceNameSanitizer.symbolicAttributes(attrs)
        val definitions = ResourceNameSanitizer.findRawSymbolicValues(layout, symbolic)

        assertEquals("<view app:paletteButtonStyle=\"raw_1\" />", ResourceNameSanitizer.sanitizeRawSymbolicValues(layout, symbolic))
        check("<enum name=\"raw_1\" value=\"1\" />" in ResourceNameSanitizer.addRawSymbolicDefinitions(attrs, definitions, symbolic))
    }
}
