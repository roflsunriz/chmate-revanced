package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class ImageBoardIntegrationTest {
    @Test
    void rewritesBoardAndThreadUrlsIntoNativeChMateShapes() {
        String board = ImageBoardIntegration.rewriteInputUrl("https://boards.4chan.org/g/");
        assertTrue(board.matches("http://127\\.0\\.0\\.1:28282/ib[0-9a-f]{16}/"));

        String thread = ImageBoardIntegration.rewriteInputUrl(
                "https://boards.4chan.org/g/thread/123456789");
        assertTrue(thread.matches(
                "http://127\\.0\\.0\\.1:28282/test/read\\.cgi/ib[0-9a-f]{16}/[0-9]{9,10}/"));
        assertEquals("https://example.com/news/", ImageBoardIntegration.rewriteInputUrl(
                "https://example.com/news/"));
    }

    @Test
    void syntheticIdsAreStableAndBoardSpecific() {
        ImageBoardClient.Board first = ImageBoardClient.parseBoard("https://zzzchan.xyz/tech/");
        ImageBoardClient.Board second = ImageBoardClient.parseBoard("https://zzzchan.xyz/meta/");
        long firstId = ImageBoardProxyServer.localThreadId(first, 898);
        assertEquals(firstId, ImageBoardProxyServer.localThreadId(first, 898));
        assertNotEquals(firstId, ImageBoardProxyServer.localThreadId(second, 898));
        assertTrue(firstId >= 100_000_000L && firstId <= 999_999_999L);
    }

    @Test
    void exposesNativeBoardSettingsForRegisteredSources() {
        String settings = ImageBoardProxyServer.settingData(
                ImageBoardClient.parseBoard("https://sportschan.org/librejp/"));
        assertTrue(settings.contains("BBS_TITLE=jschan - sportschan.org/librejp\n"));
        assertTrue(settings.contains("BBS_NONAME_NAME=名無しさん\n"));
    }

    @Test
    void opensExplicitJschanThreadWithoutPriorBoardRegistration() {
        String thread = ImageBoardIntegration.rewriteInputUrl(
                "https://sportschan.org/librejp/thread/16592.html");
        assertTrue(thread.matches(
                "http://127\\.0\\.0\\.1:28282/test/read\\.cgi/ib[0-9a-f]{16}/[0-9]{9,10}/"));
        assertEquals("https://example.com/news/", ImageBoardIntegration.rewriteInputUrl(
                "https://example.com/news/"));
        assertEquals("https://sportschan.org/librejp/", ImageBoardIntegration.rewriteInputUrl(
                "https://sportschan.org/librejp/"));
    }

    @Test
    void keepsRemoteLineBreaksInsideOneDatRecord() {
        ImageBoardClient.Post post = new ImageBoardClient.Post(1, "", "", "",
                "first\r\nsecond\rthird\nfourth<> &", null, null);
        assertEquals("first<br>second<br>third<br>fourth&lt;&gt; &amp;",
                ImageBoardProxyServer.datBody(post));
        assertEquals("name  next", ImageBoardProxyServer.datText("name\r\nnext"));
        assertEquals("<br><br>end<br>", ImageBoardProxyServer.datBody(
                new ImageBoardClient.Post(1, "", "", "", "\r\n\rend\n", null, null)));
    }

    @Test
    void restoresProxyPortAndHttpAfterNativeUrlCanonicalization() {
        String path = "/ib0123456789abcdef/dat/123456789.dat";
        assertTrue(ImageBoardIntegration.isProxyUrl("http://127.0.0.1:28282" + path));
        assertTrue(ImageBoardIntegration.isProxyUrl("https://127.0.0.1" + path));
        assertFalse(ImageBoardIntegration.isProxyUrl("http://127.0.0.1/test/dat/123.dat"));
        assertFalse(ImageBoardIntegration.isProxyUrl("https://example.com" + path));
        assertEquals("http://127.0.0.1:28282" + path,
                ImageBoardIntegration.rewriteProxyUrl("https://127.0.0.1" + path));
        assertEquals("http://127.0.0.1/test/dat/123.dat",
                ImageBoardIntegration.rewriteProxyUrl("http://127.0.0.1/test/dat/123.dat"));
        assertEquals("https://example.com" + path,
                ImageBoardIntegration.rewriteProxyUrl("https://example.com" + path));
    }
}
