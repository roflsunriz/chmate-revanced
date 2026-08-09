package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolbarTopAdSlotDetectorTest {
    @Test
    fun `slot hidden in sibling layouts is collapsed in the visible thread layout`() {
        val layouts = mapOf(
            "hidden.xml" to layout(slotVisibility = " android:visibility=\"gone\""),
            "thread.xml" to layout(slotVisibility = ""),
            "chained.xml" to layout(slotVisibility = " android:visibility=\"gone\"", withIntermediate = true),
        )

        assertEquals(setOf("top_ad_slot"), ToolbarTopAdSlotDetector.detect(layouts))
    }

    @Test
    fun `one off toolbar header without hidden cross layout evidence is preserved`() {
        assertEquals(
            emptySet<String>(),
            ToolbarTopAdSlotDetector.detect(mapOf("header.xml" to layout(slotVisibility = ""))),
        )
    }

    private fun layout(slotVisibility: String, withIntermediate: Boolean = false): String {
        val toolbarAnchor = if (withIntermediate) "intermediate" else "top_ad_slot"
        val intermediate = if (withIntermediate) {
            """<o.Intermediate android:id="@id/intermediate" android:layout_height="wrap_content" android:layout_width="0dp" app:layout_constraintTop_toBottomOf="@id/top_ad_slot" />"""
        } else {
            ""
        }
        return """
            <o.Root xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <o.AdSlot android:id="@id/top_ad_slot" android:layout_height="wrap_content"
                    android:layout_width="0dp"$slotVisibility
                    app:layout_constraintTop_toTopOf="parent" />
                $intermediate
                <o.Toolbar android:id="@id/toolbar" android:tag="toolbarContentTop"
                    android:layout_height="wrap_content" android:layout_width="0dp"
                    app:layout_constraintTop_toBottomOf="@id/$toolbarAnchor" />
            </o.Root>
        """.trimIndent()
    }
}
