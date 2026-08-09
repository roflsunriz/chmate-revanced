package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdElementClassifierTest {
    @Test
    fun `known ad SDK classes are detected`() {
        assertTrue(AdElementClassifier.isAdvertisement("com.google.android.gms.ads.AdView", null, null))
        assertTrue(AdElementClassifier.isAdvertisement("com.example.BannerAdView", null, null))
        assertTrue(AdElementClassifier.isAdSdkClass("Lcom/google/android/gms/ads/internal/client/zzay;"))
        assertTrue(AdElementClassifier.isAdSdkClass("com.applovin.sdk.AppLovinSdk"))
    }

    @Test
    fun `ad resource containers are detected`() {
        assertTrue(AdElementClassifier.isAdvertisement("FrameLayout", "@id/ad_container", null))
        assertTrue(AdElementClassifier.isAdvertisement("FrameLayout", null, "banner_ad"))
    }

    @Test
    fun `ordinary headers and content are not false positives`() {
        assertFalse(AdElementClassifier.isAdvertisement("FrameLayout", "@id/thread_header", null))
        assertFalse(AdElementClassifier.isAdvertisement("TextView", "@id/read_status", null))
        assertFalse(AdElementClassifier.isAdSdkClass("Lcom/google/android/gms/common/GoogleApiAvailability;"))
        assertFalse(AdElementClassifier.isAdSdkClass("Ljp/co/airfront/android/a2chMate/Foo;"))
    }
}
