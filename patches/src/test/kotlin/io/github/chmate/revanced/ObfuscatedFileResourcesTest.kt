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
}
