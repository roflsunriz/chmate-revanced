package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObfuscatedFileResourcesTest {
    @Test
    fun `file aliases are found with qualifiers and removed`() {
        val xml = """
            <resources>
                <item type="mipmap" name="APKTOOL_DUMMYVAL_0x7f0e0000">aG.xml</item>
                <item type="drawable" name="color_value">#3333b5e5</item>
                <item type="string" name="text">plain text</item>
            </resources>
        """.trimIndent()

        assertEquals(
            listOf(ObfuscatedFileResource("mipmap", "APKTOOL_DUMMYVAL_0x7f0e0000", "aG.xml", "-anydpi-v26")),
            ObfuscatedFileResources.find(xml, "values-anydpi-v26"),
        )
        check("aG.xml" !in ObfuscatedFileResources.removeAliases(xml))
        check("#3333b5e5" in ObfuscatedFileResources.removeAliases(xml))
    }

    @Test
    fun `binary XML null values retain their typed scalar data`() {
        assertEquals("@0x7f080123", binaryXmlFallbackValue(0x01, 0x7f080123))
        assertEquals("?0x010100d4", binaryXmlFallbackValue(0x02, 0x010100d4))
        assertEquals("true", binaryXmlFallbackValue(0x12, 1))
        assertEquals("#ff336699", binaryXmlFallbackValue(0x1c, 0xff336699.toInt()))
    }

    @Test
    fun `binary XML attributes without names are removed`() {
        assertEquals(
            "<view android:id=\"@id/title\" app:known=\"1\" />",
            removeUnnamedXmlAttributes(
                "<view android:id=\"@id/title\" android:=\"0x1\" =\"bad\" app:known=\"1\" />",
            ),
        )
    }
}
