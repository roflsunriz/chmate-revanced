package io.github.chmate.revanced

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

class ChMateResourcePatchTest {
    @Test
    fun `settings activity stays in the host process`() {
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
        assertEquals("app.revanced.extension.chmate.SettingsActivity", activity.getAttribute("android:name"))
    }

    private fun applicationElement(xml: String): Element {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        return document.getElementsByTagName("application").item(0) as Element
    }
}
