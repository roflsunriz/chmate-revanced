package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ParallelRefreshBridgeTest {
    @Test
    void runsRefreshPartitionsConcurrentlyAndCombinesErrors() throws Exception {
        FakeWork work = new FakeWork();
        List<FakeItem> items = Arrays.asList(
                new FakeItem("host/board", "one"), new FakeItem("host/board", "two"),
                new FakeItem("host/board", "three"), new FakeItem("host/board", "four"));

        FakeResult result = (FakeResult) ParallelRefreshBridge.run(
                work, new FakeClient(), items, true);

        assertEquals(4, result.errors);
        assertEquals("first", result.summary);
        assertTrue(work.maximumActive.get() > 1, "refresh calls must overlap");
    }

    @Test
    void keepsDifferentBoardsTogetherWhenFourWorkersAreNotNeeded() {
        List<List<?>> partitions = ParallelRefreshBridge.partition(Arrays.asList(
                new FakeItem("a/one", "1"), new FakeItem("a/one", "2"),
                new FakeItem("b/two", "3"), new FakeItem("b/two", "4")), 4);
        assertEquals(2, partitions.size());
        assertEquals(2, partitions.get(0).size());
        assertEquals(2, partitions.get(1).size());
    }

    private static final class FakeClient {
    }

    private static final class FakeItem {
        final String board;
        final String title;

        FakeItem(String board, String title) {
            this.board = board;
            this.title = title;
        }
    }

    private static final class FakeResult {
        final int errors;
        final String summary;

        FakeResult(int errors, String summary) {
            this.errors = errors;
            this.summary = summary;
        }
    }

    private static final class FakeWork {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maximumActive = new AtomicInteger();

        @SuppressWarnings("unused")
        private FakeResult check(FakeClient client, List<FakeItem> items, boolean download)
                throws InterruptedException {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(80);
                return new FakeResult(items.size(), "first");
            } finally {
                active.decrementAndGet();
            }
        }
    }
}
