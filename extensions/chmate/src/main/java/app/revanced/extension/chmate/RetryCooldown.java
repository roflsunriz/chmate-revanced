package app.revanced.extension.chmate;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RetryCooldown {
    private static final Pattern SAMBA = Pattern.compile(
            "(\\d{1,4})\\s*sec\\s*たたないと書けません.*?\\(.*?(\\d{1,4})\\s*sec\\s*しかたってない",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern AFTER = Pattern.compile(
            "(?:再投稿まで|あと|後)\\s*(\\d{1,4})\\s*秒", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAIT = Pattern.compile(
            "(\\d{1,4})\\s*秒\\s*(?:待て|待って|待ち)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH = Pattern.compile(
            "retry after\\s*(\\d{1,4})\\s*(?:seconds?|sec)", Pattern.CASE_INSENSITIVE);
    private static final long MAX_WAIT_SECONDS = 3600;

    private RetryCooldown() {
    }

    static long delayMillis(String serverMessage) {
        if (serverMessage == null) return 0;
        String message = Normalizer.normalize(serverMessage.replaceAll("<[^>]*>", " "), Normalizer.Form.NFKC);
        if (message.contains("二重書き込み") || message.contains("連続投稿ですか")) return 0;
        Matcher samba = SAMBA.matcher(message);
        if (samba.find()) {
            long remaining = parse(samba.group(1)) - parse(samba.group(2));
            return remaining <= 0 ? 0 : delay(remaining);
        }
        for (Pattern pattern : new Pattern[]{AFTER, WAIT, ENGLISH}) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) return delay(parse(matcher.group(1)));
        }
        return 0;
    }

    private static long parse(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static long delay(long seconds) {
        if (seconds < 0 || seconds > MAX_WAIT_SECONDS) return 0;
        return Math.min(MAX_WAIT_SECONDS, seconds + 1) * 1000;
    }
}
