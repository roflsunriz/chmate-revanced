package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

final class PostRetryBridgeTest {
    @Test
    void resubmitsOnlyAfterAnExplicitCooldown() throws Throwable {
        FakeClient client = new FakeClient();
        PostRetryBridge.enter(client, new Board(), new Payload(), false, new Callback(), new Transport());
        assertEquals("posted", PostRetryBridge.retryOrThrow(new IOException("再投稿まで0秒待ってください")));
        assertEquals(1, client.attempts);

        PostRetryBridge.enter(client, new Board(), new Payload(), false, new Callback(), new Transport());
        assertThrows(IOException.class, () -> PostRetryBridge.retryOrThrow(new IOException("接続に失敗しました")));
        assertEquals(1, client.attempts);
    }

    @Test
    void retriesThreadCreationWithItsOriginalSubject() throws Throwable {
        FakeClient client = new FakeClient();
        Board board = new Board();
        Payload payload = new Payload();
        PostRetryBridge.enterThread(client, board, payload, "元の件名", false,
                new Callback(), new Transport());
        assertEquals("thread posted", PostRetryBridge.retryOrThrow(new IOException("あと0秒くらい")));
        assertEquals("元の件名", client.lastSubject);
        assertEquals(1, client.threadAttempts);
    }

    @Test
    void retriesAtMostTwiceAndKeepsTheLatestFailure() throws Throwable {
        FakeClient client = new FakeClient();
        client.remainingCooldownFailures = 1;
        PostRetryBridge.enter(client, new Board(), new Payload(), false, new Callback(), new Transport());
        assertEquals("posted", PostRetryBridge.retryOrThrow(new IOException("あと0秒くらい")));
        assertEquals(2, client.attempts);

        client.remainingCooldownFailures = 2;
        PostRetryBridge.enter(client, new Board(), new Payload(), false, new Callback(), new Transport());
        assertThrows(IOException.class, () -> PostRetryBridge.retryOrThrow(new IOException("あと0秒くらい")));
        assertEquals(4, client.attempts);
    }

    @Test
    void interruptionCancelsBeforeAnyResubmission() {
        FakeClient client = new FakeClient();
        PostRetryBridge.enter(client, new Board(), new Payload(), false, new Callback(), new Transport());
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> PostRetryBridge.retryOrThrow(new IOException("あと10秒くらい")));
            assertEquals(0, client.attempts);
        } finally {
            Thread.interrupted();
        }
    }

    public static final class FakeClient {
        int attempts;
        int threadAttempts;
        String lastSubject;
        int remainingCooldownFailures;

        public Object post(Board board, Payload payload, boolean mode, Callback callback, Transport transport)
                throws IOException {
            attempts++;
            if (remainingCooldownFailures > 0) {
                remainingCooldownFailures--;
                throw new IOException("あと0秒くらい");
            }
            return "posted";
        }

        public Object createThread(Board board, Payload payload, String subject, boolean mode,
                Callback callback, Transport transport) {
            threadAttempts++;
            lastSubject = subject;
            return "thread posted";
        }
    }

    public static final class Board { }
    public static final class Payload { }
    public static final class Callback { }
    public static final class Transport { }
}
