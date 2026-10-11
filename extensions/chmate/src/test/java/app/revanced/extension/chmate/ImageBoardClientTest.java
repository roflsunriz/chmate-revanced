package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ImageBoardClientTest {
    @Test
    void acceptsBoardUrlsAndBuildsCorrectDataAndPostingUrls() {
        ImageBoardClient.Board fourChan = ImageBoardClient.parseBoard(
                "https://boards.4channel.org/po/thread/570368");
        assertNotNull(fourChan);
        assertEquals("https://a.4cdn.org/po/catalog.json", fourChan.catalogUrl());
        assertEquals("https://a.4cdn.org/po/thread/570368.json", fourChan.threadDataUrl(570368));
        assertEquals("https://boards.4chan.org/po/thread/570368", fourChan.threadUrl(570368));
        assertEquals(570368, ImageBoardClient.parseThreadId(fourChan,
                "https://boards.4channel.org/po/thread/570368"));

        ImageBoardClient.Board futaba = ImageBoardClient.parseBoard(
                "https://dat.2chan.net/t/futaba.htm");
        assertNotNull(futaba);
        assertEquals("https://dat.2chan.net/t/res/379414.htm", futaba.threadUrl(379414));
        assertEquals(379414, ImageBoardClient.parseThreadId(futaba,
                "https://dat.2chan.net/t/res/379414.htm"));
        assertNull(ImageBoardClient.parseBoard("http://dat.2chan.net/t/futaba.htm"));
        assertNull(ImageBoardClient.parseBoard("https://dat.2chan.net.evil.example/t/futaba.htm"));
        assertNull(ImageBoardClient.parseBoard("https://boards.4chan.org/../po/"));
        ImageBoardClient.Board jschan = ImageBoardClient.parseBoard(
                "https://zzzchan.xyz/tech/thread/898.html");
        assertNotNull(jschan);
        assertEquals(ImageBoardClient.Kind.JSCHAN, jschan.kind);
        assertEquals("https://zzzchan.xyz/tech/catalog.json", jschan.catalogUrl());
        assertEquals("https://zzzchan.xyz/tech/thread/898.json", jschan.threadDataUrl(898));
        assertEquals(898, ImageBoardClient.parseThreadId(jschan,
                "https://zzzchan.xyz/tech/thread/898.html"));
    }

    @Test
    void parsesFutabaThreadAndKeepsReplyOrder() {
        ImageBoardClient.Board board = ImageBoardClient.parseBoard("https://dat.2chan.net/t/futaba.htm");
        String html = "<div class=\"thre\" data-res=\"379414\">"
                + "<img src=\"/t/thumb/123s.jpg\"><span class=\"csb\">無題</span>"
                + "<span class=\"cnm\">名無し</span><span class=\"cnw\">26/10/11(日)</span>"
                + "<span class=\"cno\">No.379414</span><blockquote>OP &amp; test</blockquote>"
                + "<table border=0><tr><td class=rts>…</td><td class=rtd>"
                + "<span class=\"cnm\">返信者</span><span class=\"cno\">No.379482</span>"
                + "<blockquote>&gt;&gt;379414<br>返信</blockquote></td></tr></table>"
                + "</div><!--スレッド終了-->";
        List<ImageBoardClient.ThreadEntry> catalog = ImageBoardClient.parseFutabaCatalog(board, html);
        assertEquals(1, catalog.size());
        assertEquals(1, catalog.get(0).replies);
        assertEquals("OP & test", catalog.get(0).comment);
        assertEquals("https://dat.2chan.net/t/thumb/123s.jpg", catalog.get(0).thumbnailUrl);

        List<ImageBoardClient.Post> posts = ImageBoardClient.parseFutabaThread(board, html);
        assertEquals(2, posts.size());
        assertEquals(379414, posts.get(0).id);
        assertEquals(379482, posts.get(1).id);
        assertEquals(">>379414\n返信", posts.get(1).comment);
    }

    @Test
    void parsesFutabaOpWithoutReplies() {
        ImageBoardClient.Board board = ImageBoardClient.parseBoard("https://dat.2chan.net/t/futaba.htm");
        String html = "<div class='thre' data-res='99'><blockquote>本文</blockquote>"
                + "</div><!--スレッド終了-->";
        assertEquals("本文", ImageBoardClient.parseFutabaThread(board, html).get(0).comment);
    }

    @Test
    void parsesFourChanCatalogAndThread() throws Exception {
        ImageBoardClient.Board board = ImageBoardClient.parseBoard("https://boards.4channel.org/po/");
        String entry = "{\"no\":570368,\"sub\":\"Paper &amp; art\",\"com\":\"A<br>B\","
                + "\"name\":\"Anonymous\",\"tim\":12345,\"ext\":\".png\",\"replies\":2}";
        List<ImageBoardClient.ThreadEntry> catalog = ImageBoardClient.parseFourChanCatalog(board,
                "[{\"page\":1,\"threads\":[" + entry + "]}]");
        assertEquals(1, catalog.size());
        assertEquals("Paper & art", catalog.get(0).subject);
        assertEquals(2, catalog.get(0).replies);
        assertEquals("https://t.4cdn.org/po/12345s.jpg", catalog.get(0).thumbnailUrl);
        assertEquals("https://i.4cdn.org/po/12345.png", catalog.get(0).mediaUrl);
        assertEquals("A\nB", ImageBoardClient.parseFourChanThread(board,
                "{\"posts\":[" + entry + "]}").get(0).comment);
    }

    @Test
    void parsesJschanCatalogThreadAndMedia() throws Exception {
        ImageBoardClient.Board board = ImageBoardClient.parseBoard("https://zzzchan.xyz/tech/");
        String hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String op = "{\"postId\":898,\"subject\":\"Technology\",\"message\":\"A<br>B\","
                + "\"name\":\"Anon\",\"replyposts\":1,\"files\":[{\"filename\":\""
                + hash + ".png\",\"hash\":\"" + hash
                + "\",\"thumbextension\":\".jpg\",\"hasThumb\":true}]}";
        List<ImageBoardClient.ThreadEntry> catalog = ImageBoardClient.parseJschanCatalog(board,
                "[" + op + "]");
        assertEquals(1, catalog.get(0).replies);
        assertEquals("https://zzzchan.xyz/file/" + hash + ".png", catalog.get(0).mediaUrl);
        assertEquals("https://zzzchan.xyz/file/thumb/" + hash + ".jpg",
                catalog.get(0).thumbnailUrl);
        List<ImageBoardClient.Post> posts = ImageBoardClient.parseJschanThread(board,
                op.substring(0, op.length() - 1)
                        + ",\"replies\":[{\"postId\":899,\"message\":\"Reply\"}]}");
        assertEquals(2, posts.size());
        assertEquals(899, posts.get(1).id);
        assertEquals("A\nB", posts.get(0).comment);
        assertEquals("", ImageBoardClient.parseJschanCatalog(board,
                "[{\"postId\":900,\"message\":null}]").get(0).comment);
    }
}
