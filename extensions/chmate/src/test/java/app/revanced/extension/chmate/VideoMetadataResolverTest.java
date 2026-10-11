package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VideoMetadataResolverTest {
    @Test
    void extractsNiconicoFieldsAndEscapedDescription() {
        String xml = "<nicovideo_thumb_response status=\"ok\"><thumb>"
                + "<title>動画 &amp; テスト</title><description>説明&lt;例&gt;</description>"
                + "<view_counter>1234</view_counter><comment_num>56</comment_num>"
                + "<mylist_counter>7</mylist_counter><first_retrieve>2026-10-11T12:00:00+09:00</first_retrieve>"
                + "<user_nickname>投稿者</user_nickname></thumb></nicovideo_thumb_response>";
        VideoMetadataResolver.VideoMetadata metadata = VideoMetadataResolver.parseNiconico(xml,
                "https://www.nicovideo.jp/watch/sm9");
        assertEquals("動画 & テスト", metadata.title);
        assertEquals("説明<例>", metadata.description);
        assertEquals("1234", metadata.views);
        assertEquals("56", metadata.comments);
        assertEquals("7", metadata.mylists);
        assertEquals("2026-10-11T12:00:00+09:00", metadata.publishedAt);
        assertEquals("投稿者", metadata.author);
        assertTrue(VideoMetadataResolver.niconicoLikesUrl("sm33589395")
                .contains("filters%5BcontentId%5D%5B0%5D=sm33589395"));
        assertNull(VideoMetadataResolver.parseNiconico(xml.replace("status=\"ok\"", "status=\"fail\""),
                metadata.sourceUrl));
    }

    @Test
    void extractsAvailableOpenGraphFieldsRegardlessOfAttributeOrder() {
        String html = "<head><meta content='動画 &amp; 概要' property='og:title'>"
                + "<meta name=\"description\" content=\"説明 &#39;引用&#39;\">"
                + "<meta content=\"123\" itemprop=\"userInteractionCount\">"
                + "<meta property=\"article:published_time\" content=\"2026-10-11\">"
                + "<meta name=\"author\" content=\"Alice\"></head>";
        VideoMetadataResolver.VideoMetadata metadata = VideoMetadataResolver.parseHtml(html,
                "https://www.dailymotion.com/video/example");
        assertEquals("動画 & 概要", metadata.title);
        assertEquals("説明 '引用'", metadata.description);
        assertEquals("123", metadata.views);
        assertEquals("2026-10-11", metadata.publishedAt);
        assertEquals("Alice", metadata.author);
        assertNull(VideoMetadataResolver.parseHtml("<html><head></head></html>", metadata.sourceUrl));
    }

    @Test
    void resolvesOriginalLinksWithoutAcceptingLookalikeOrLocalHosts() {
        assertTrue(VideoMetadataResolver.isVideoUrl("https://www.youtube.com/watch?v=abc"));
        assertFalse(VideoMetadataResolver.isVideoUrl("https://youtube.com.evil.example/watch?v=abc"));
        assertFalse(VideoMetadataResolver.isVideoUrl("http://127.0.0.1/video"));
        assertFalse(VideoMetadataResolver.isVideoUrl("file:///private/video"));
        assertFalse(VideoMetadataResolver.isVideoUrl("https://i.imgur.com/photo.jpg"));
        assertTrue(VideoMetadataResolver.isVideoUrl("https://i.imgur.com/clip.mp4"));
        Wrapped wrapped = new Wrapped("https://www.youtube.com/watch?v=abc",
                "https://i.ytimg.com/vi/abc/default.jpg");
        assertEquals("https://www.youtube.com/watch?v=abc", VideoMetadataResolver.sourceUrl(wrapped));
        assertEquals("https://www.youtube.com/watch?v=abc",
                VideoMetadataResolver.sourceUrl("https://i.ytimg.com/vi/abc/default.jpg"));
    }

    public static final class Wrapped implements CharSequence {
        public final String original;
        private final String thumbnail;

        Wrapped(String original, String thumbnail) {
            this.original = original;
            this.thumbnail = thumbnail;
        }

        @Override public int length() { return thumbnail.length(); }
        @Override public char charAt(int index) { return thumbnail.charAt(index); }
        @Override public CharSequence subSequence(int start, int end) { return thumbnail.subSequence(start, end); }
        @Override public String toString() { return thumbnail; }
    }
}
