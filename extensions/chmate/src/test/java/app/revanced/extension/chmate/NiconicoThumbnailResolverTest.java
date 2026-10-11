package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

final class NiconicoThumbnailResolverTest {
    @Test
    void recognizesSupportedWatchLinksWithoutTrustingLookalikeHosts() {
        assertEquals("sm9", NiconicoThumbnailResolver.videoId("https://www.nicovideo.jp/watch/sm9?ref=thread"));
        assertEquals("sm9", NiconicoThumbnailResolver.videoId("https://nico.ms/sm9"));
        assertEquals("sm123", NiconicoThumbnailResolver.videoId("https://sp.nicovideo.jp/watch/sm123"));
        assertEquals("so44540860", NiconicoThumbnailResolver.videoId("https://www.nicovideo.jp/watch/so44540860"));
        assertEquals("nm6049209", NiconicoThumbnailResolver.videoId("https://nico.ms/nm6049209"));
        assertNull(NiconicoThumbnailResolver.videoId("https://nicovideo.jp.evil.example/watch/sm9"));
        assertNull(NiconicoThumbnailResolver.videoId("javascript://www.nicovideo.jp/watch/sm9"));
        assertNull(NiconicoThumbnailResolver.videoId("https://www.nicovideo.jp/watch/sm9evil"));
        assertNull(NiconicoThumbnailResolver.videoId("https://www.nicovideo.jp/watch/lv123"));
    }

    @Test
    void acceptsOnlySuccessfulThumbnailResponsesFromNiconicoCdn() {
        String xml = "<?xml version=\"1.0\"?><nicovideo_thumb_response status=\"ok\"><thumb>"
                + "<thumbnail_url>https://nicovideo.cdn.nimg.jp/thumbnails/9/9?x=1&amp;y=2</thumbnail_url>"
                + "</thumb></nicovideo_thumb_response>";
        assertEquals("https://nicovideo.cdn.nimg.jp/thumbnails/9/9?x=1&y=2",
                NiconicoThumbnailResolver.thumbnailUrl(xml));
        assertNull(NiconicoThumbnailResolver.thumbnailUrl(xml.replace("status=\"ok\"", "status=\"fail\"")));
        assertNull(NiconicoThumbnailResolver.thumbnailUrl(xml.replace("nicovideo.cdn.nimg.jp", "nimg.jp.evil.example")));
        assertNull(NiconicoThumbnailResolver.thumbnailUrl(xml.replace("https://", "http://")));
    }
}
