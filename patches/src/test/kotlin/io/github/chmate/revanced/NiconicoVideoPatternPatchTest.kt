package io.github.chmate.revanced

import java.util.regex.Pattern
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NiconicoVideoPatternPatchTest {
    @Test
    fun `expands niconico ids without changing other video services`() {
        val original = "https?://(?:www\\.dailymotion\\.com/video/.*|www\\.nicovideo\\.jp/watch/sm[0-9]+.*|nico\\.ms/sm[0-9]+.*)"
        val expanded = expandNiconicoVideoPattern(original)
        assertNotNull(expanded)
        val matcher = Pattern.compile(expanded!!)
        assertTrue(matcher.matcher("https://www.nicovideo.jp/watch/sm9").find())
        assertTrue(matcher.matcher("https://www.nicovideo.jp/watch/so44540860").find())
        assertTrue(matcher.matcher("https://sp.nicovideo.jp/watch/nm6049209").find())
        assertTrue(matcher.matcher("https://nico.ms/so44540860").find())
        assertTrue(matcher.matcher("https://www.dailymotion.com/video/abc").find())
        assertFalse(matcher.matcher("https://evil.nicovideo.jp/watch/sm9").find())
        assertFalse(matcher.matcher("https://www.nicovideo.jp.evil.example/watch/sm9").find())
    }
}
