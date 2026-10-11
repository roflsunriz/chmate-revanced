package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RetryCooldownTest {
    @Test
    void waitsForExplicitRemainingSecondsWithOneSecondMargin() {
        assertEquals(31_000, RetryCooldown.delayMillis("頻繁に投稿しすぎです。再投稿まで３０秒待ってください"));
        assertEquals(53_000, RetryCooldown.delayMillis(
                "ＥＲＲＯＲ - 593 60 sec たたないと書けません。(1回目、8 sec しかたってない)"));
        assertEquals(11_000, RetryCooldown.delayMillis("もうちょっと落ち着いて書き込みしてください。あと10秒くらい"));
        assertEquals(6_000, RetryCooldown.delayMillis("おいこら！5秒待て！"));
        assertEquals(16_000, RetryCooldown.delayMillis("retry after 15 seconds"));
    }

    @Test
    void doesNotRetryAmbiguousOrUnboundedErrors() {
        assertEquals(0, RetryCooldown.delayMillis("連続投稿ですか？"));
        assertEquals(0, RetryCooldown.delayMillis("通信が切断されました"));
        assertEquals(0, RetryCooldown.delayMillis("あと9999秒くらい"));
        assertEquals(0, RetryCooldown.delayMillis("60 sec たたないと書けません。(1回目、60 sec しかたってない)"));
    }
}
