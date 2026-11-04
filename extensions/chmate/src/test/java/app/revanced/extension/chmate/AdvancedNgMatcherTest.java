package app.revanced.extension.chmate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class AdvancedNgMatcherTest {
    @Test
    void numericAndTimeConditionsAcceptFullwidthDigits() {
        AdvancedNgMatcher.Context context = new AdvancedNgMatcher.Context();
        context.number = 88;
        context.date = "2026/10/11(日) 21:30:00";
        context.timestampMillis = AdvancedNgMatcher.parseTimestamp(context.date);
        assertTrue(AdvancedNgMatcher.matches(condition("numeric", "resNumber", "between",
                "８０", "９０", false), context));
        assertTrue(AdvancedNgMatcher.matches(condition("time", "weekday", "eq",
                "０", "", false), context));
        context.body = "ＡＢＣ";
        assertTrue(AdvancedNgMatcher.matches(condition("text", "body", "contains", "ＡＢＣ", "", false), context));
        assertFalse(AdvancedNgMatcher.matches(condition("text", "body", "contains", "ABC", "", false), context));
    }

    @Test
    void normalizesLocalizedNumericInputWithoutChangingStoredTextConditions() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("-1.25", AdvancedNgMatcher.normalizeNumber("−１，２５"));
            Locale.setDefault(Locale.forLanguageTag("ar"));
            assertEquals("-12.5", AdvancedNgMatcher.normalizeNumber("\u061c-١٢٫٥"));
            assertEquals("12", AdvancedNgMatcher.normalizeNumber("১২"));
        } finally {
            Locale.setDefault(original);
        }
    }
    @Test
    void combinesDifferentFieldsAndKeepsBoardAndThreadScopesDistinct() {
        AdvancedNgMatcher.Context context = new AdvancedNgMatcher.Context();
        context.name = "名無し ◆trip";
        context.body = "広告の本文\nhttp://example.test/";
        context.id = "AbCd1234";
        context.threadUrl = "https://lavender.5ch.io/test/read.cgi/net/1536932691/";
        AdvancedNgRule rule = new AdvancedNgRule("a", "", "https://lavender.5ch.io/net/",
                true, true, false, Arrays.asList(
                condition("text", "body", "contains", "広告\nhttp://", "", false),
                condition("text", "id", "regex_i", "^abcd", "", false),
                condition("text", "trip", "contains", "trip", "", false)));
        assertTrue(AdvancedNgMatcher.matches(rule, context));
        assertFalse(AdvancedNgMatcher.matches(new AdvancedNgRule("b", "",
                "https://lavender.5ch.io/test/read.cgi/net/999/", true, true, false,
                rule.conditions), context));
        assertFalse(AdvancedNgMatcher.scopeMatches("https://lavender.5ch.io/other/",
                context.threadUrl));
    }

    @Test
    void handlesNumericTimeFuzzyNegationAndInvalidRegex() {
        AdvancedNgMatcher.Context context = new AdvancedNgMatcher.Context();
        context.number = 88;
        context.body = "abc\ndef";
        context.idCount = 5;
        context.date = "2026/10/11(日) 21:30:00";
        context.timestampMillis = AdvancedNgMatcher.parseTimestamp(context.date);
        assertTrue(AdvancedNgMatcher.matches(condition("numeric", "resNumber", "between",
                "80", "90", false), context));
        assertTrue(AdvancedNgMatcher.matches(condition("numeric", "idCount", "gte",
                "5", "", false), context));
        assertTrue(AdvancedNgMatcher.matches(condition("text", "body", "fuzzy",
                "acd", "", false), context));
        assertTrue(AdvancedNgMatcher.matches(condition("text", "body", "contains",
                "xyz", "", true), context));
        assertFalse(AdvancedNgMatcher.matches(condition("text", "body", "regex",
                "([", "", false), context));
        assertTrue(AdvancedNgMatcher.matches(condition("time", "weekday", "eq",
                "0", "", false), context));
        assertTrue(AdvancedNgMatcher.matches(condition("time", "hour", "eq",
                "21", "", false), context));
        assertEquals(1, context.numeric("lineCount"));
        assertEquals(7, context.numeric("charCount"));
    }

    @Test
    void jsonRoundTripPreservesMultipleConditionsAndState() throws Exception {
        AdvancedNgRule original = new AdvancedNgRule("id", "複合条件", "", false,
                false, true, Arrays.asList(
                condition("text", "name", "regex_i", "spam", "", false),
                condition("numeric", "charCount", "gt", "100", "", true)));
        AdvancedNgRule restored = AdvancedNgRule.deserialize(
                AdvancedNgRule.serialize(Collections.singletonList(original))).get(0);
        assertEquals(original.id, restored.id);
        assertEquals(original.label, restored.label);
        assertFalse(restored.enabled);
        assertFalse(restored.transparent);
        assertTrue(restored.chain);
        assertEquals(2, restored.conditions.size());
        assertTrue(restored.conditions.get(1).negate);
    }

    private static AdvancedNgRule.Condition condition(String type, String field, String mode,
            String value, String second, boolean negate) {
        return new AdvancedNgRule.Condition(type, field, mode, value, second, negate);
    }
}
