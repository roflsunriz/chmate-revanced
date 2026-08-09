package io.github.chmate.revanced

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

class ChMateResourcePatchTest {
    @Test
    fun `settings activity is internal and stays in the host process`() {
        val application = applicationElement(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application />
            </manifest>
            """.trimIndent(),
        )

        application.addSettingsActivity(application.ownerDocument, "jp.example.HomeActivity")

        val activity = application.getElementsByTagName("activity").item(0) as Element
        assertFalse(activity.hasAttribute("android:process"))
        assertEquals("false", activity.getAttribute("android:exported"))
        assertEquals(0, activity.getElementsByTagName("intent-filter").length)
        assertEquals("app.revanced.extension.chmate.SettingsActivity", activity.getAttribute("android:name"))
    }

    @Test
    fun `adds an internal ReVanced entry to the root ChMate settings`() {
        val document = document(
            """
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android" />
            """.trimIndent(),
        )

        assertTrue(document.isEmptyPreferenceScreen())
        document.addSettingsPreference()

        val preference = document.getElementsByTagName("Preference").item(0) as Element
        val intent = preference.getElementsByTagName("intent").item(0) as Element
        assertEquals("chmateRevancedSettings", preference.getAttribute("android:key"))
        assertEquals("ChMate ReVanced", preference.getAttribute("android:title"))
        assertEquals("jp.co.airfront.android.a2chMate", intent.getAttribute("android:targetPackage"))
        assertEquals(
            "app.revanced.extension.chmate.SettingsActivity",
            intent.getAttribute("android:targetClass"),
        )
        assertFalse(document.isEmptyPreferenceScreen())
    }

    private fun applicationElement(xml: String): Element {
        val document = document(xml)
        return document.getElementsByTagName("application").item(0) as Element
    }

    private fun document(xml: String) = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
}
