package app.revanced.extension.chmate;

import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdBlockerTest {
    @Test
    void blocksExactAndSubdomainHostsWithoutOvermatching() {
        assertTrue(AdBlocker.isBlockedHost("doubleclick.net"));
        assertTrue(AdBlocker.isBlockedHost("pagead2.googlesyndication.com."));
        assertFalse(AdBlocker.isBlockedHost("notdoubleclick.net"));
        assertFalse(AdBlocker.isBlockedHost("example.com"));
    }

    @Test
    void sanitizesOnlyAdvertisingUrlsForApplicationCode() {
        assertEquals("https://blocked.invalid/", AdBlocker.sanitizeNetworkUrl(
                "https://googleads.g.doubleclick.net/pagead/id"));
        assertEquals("https://example.com/thread/1", AdBlocker.sanitizeNetworkUrl(
                "https://example.com/thread/1"));
    }

    @Test
    void advertisingSdkDnsIsAlwaysRejected() {
        assertThrows(UnknownHostException.class, () -> AdBlocker.blockGetByName("unlisted.example"));
    }
}
