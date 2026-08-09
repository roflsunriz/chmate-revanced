package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdViewCollapserTest {
    @Test
    void recognizesTheStructuralTopAdMarker() {
        assertTrue(AdViewCollapser.isAdvertisementTag("revanced_ad_container"));
        assertTrue(AdViewCollapser.isAdvertisementTag("banner-ad"));
        assertFalse(AdViewCollapser.isAdvertisementTag("toolbarContentTop"));
        assertFalse(AdViewCollapser.isAdvertisementTag(null));
    }
}
