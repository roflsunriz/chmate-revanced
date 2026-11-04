package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeAdViewPatchTest {
    @Test
    fun `identifies the native banner by its Android base and both factory markers`() {
        val markers = setOf("setBannerAdRequest %s", "createAdView2 request=%s")
        assertTrue(matchesNativeAdView("Landroid/widget/FrameLayout;", markers))
        assertFalse(matchesNativeAdView("Ljava/lang/Object;", markers))
        assertFalse(matchesNativeAdView("Landroid/widget/FrameLayout;", setOf("setBannerAdRequest %s")))
        assertFalse(matchesNativeAdView("Landroid/widget/FrameLayout;", setOf("ordinary header")))
    }
}
