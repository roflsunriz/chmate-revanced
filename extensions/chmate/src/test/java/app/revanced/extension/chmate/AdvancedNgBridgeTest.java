package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class AdvancedNgBridgeTest {
    @Test
    void appliesRuleAndChainsToRepliesWithoutChangingOriginalFlags() throws Exception {
        Field cache = AdvancedNgBridge.class.getDeclaredField("cachedRules");
        cache.setAccessible(true);
        Object previous = cache.get(null);
        AdvancedNgRule rule = new AdvancedNgRule("one", "", "", true, true, true,
                Collections.singletonList(new AdvancedNgRule.Condition("text", "body",
                        "contains", "block this", "", false)));
        cache.set(null, Collections.singletonList(rule));
        try {
            FakeList list = new FakeList();
            FakeResponse matched = new FakeResponse(1, "block this", new int[0]);
            FakeResponse reply = new FakeResponse(2, "ordinary reply", new int[]{1});
            list.add(matched);
            list.add(reply);
            FakeEvaluator evaluator = new FakeEvaluator(list);
            String fields = "number|name|mail|date|body|id|anchors";
            assertEquals(16 | 4096, AdvancedNgBridge.apply(0, matched, fields, evaluator));
            assertEquals(8 | 16 | 4096,
                    AdvancedNgBridge.apply(8, reply, fields, evaluator));
            assertEquals(0, AdvancedNgBridge.apply(0,
                    new FakeResponse(3, "unrelated", new int[0]), fields, evaluator));

            AdvancedNgBridge.enter(matched, evaluator);
            AdvancedNgBridge.enter(reply, evaluator);
            assertEquals(16 | 4096, AdvancedNgBridge.finish(0, fields, true));
            assertEquals(16 | 4096, AdvancedNgBridge.finish(0, fields, true));
            AdvancedNgBridge.enter(matched, evaluator);
            assertEquals(0, AdvancedNgBridge.finish(0, fields, false));
        } finally {
            cache.set(null, previous);
        }
    }

    public static final class FakeEvaluator {
        final FakeList list;
        FakeEvaluator(FakeList list) { this.list = list; }
    }

    public static final class FakeList extends ArrayList<FakeResponse> {
        public String getTitle() { return "Test thread"; }
    }

    public static final class FakeResponse {
        final int number;
        final String name = "anonymous";
        final String mail = "";
        final String date = "2026/10/11(日) 21:00:00";
        final String body;
        final String id = "same-id";
        final int[] anchors;

        FakeResponse(int number, String body, int[] anchors) {
            this.number = number;
            this.body = body;
            this.anchors = anchors;
        }
    }
}
