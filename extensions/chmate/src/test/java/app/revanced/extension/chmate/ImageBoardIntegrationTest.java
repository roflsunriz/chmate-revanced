package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
