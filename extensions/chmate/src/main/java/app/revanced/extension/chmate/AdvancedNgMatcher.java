package app.revanced.extension.chmate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class AdvancedNgMatcher {
    private static final Pattern DATE = Pattern.compile(
            "(?:20)?(\\d{2})/(\\d{1,2})/(\\d{1,2}).*?(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRIP = Pattern.compile("◆[^\\s]+", Pattern.UNICODE_CASE);
    private static final Pattern WATCHOI = Pattern.compile("ﾜｯﾁｮｲ[^\\s]+", Pattern.UNICODE_CASE);
    private static final Pattern IP = Pattern.compile("(?:IP:)?(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern BE = Pattern.compile("BE[:：]\\s*\\d+");

    static final class Context {
        String name = "";
        String mail = "";
        String date = "";
        String body = "";
        String id = "";
        String threadTitle = "";
        String threadUrl = "";
        int number;
        int idCount;
        int replyCount;
        int repliedCount;
        int threadResCount;
        long timestampMillis;

        String text(String field) {
            switch (field) {
                case "name": return name;
                case "mail": return mail;
                case "date": return date;
                case "body": return body;
                case "id": return id;
                case "threadTitle": return threadTitle;
                case "trip": return extract(TRIP, name);
                case "watchoi": return extract(WATCHOI, name + " " + date);
                case "ip": return extract(IP, name + " " + date);
                case "be": return extract(BE, name + " " + date + " " + body);
                case "url": return extract(URL, body);
                case "all": return name + '\t' + mail + '\t' + date + '\t' + body + '\t'
                        + id + '\t' + threadTitle;
                default: return "";
            }
        }

        double numeric(String field) {
            switch (field) {
                case "resNumber": return number;
                case "lineCount":
                    int lines = 0;
                    for (int i = 0; i < body.length(); i++) if (body.charAt(i) == '\n') lines++;
                    return lines;
                case "charCount": return body.codePointCount(0, body.length());
                case "idCount": return idCount;
                case "replyCount": return replyCount;
                case "repliedCount": return repliedCount;
                case "threadResCount": return threadResCount;
                default: return Double.NaN;
            }
        }
    }

    private AdvancedNgMatcher() { }

    static boolean matches(AdvancedNgRule rule, Context context) {
        if (!rule.enabled || !scopeMatches(rule.scope, context.threadUrl)) return false;
        for (AdvancedNgRule.Condition condition : rule.conditions) {
            if (!matches(condition, context)) return false;
        }
        return true;
    }

    static boolean matches(AdvancedNgRule.Condition condition, Context context) {
        boolean result;
        switch (condition.type) {
            case "text": result = textMatches(condition, context.text(condition.field)); break;
            case "numeric": result = numericMatches(condition, context.numeric(condition.field)); break;
            case "time": result = timeMatches(condition, context.timestampMillis); break;
            default: return false;
        }
        return condition.negate ? !result : result;
    }

    private static boolean textMatches(AdvancedNgRule.Condition condition, String text) {
        if (condition.value.isEmpty()) return false;
        if (condition.mode.equals("regex") || condition.mode.equals("regex_i")) {
            try {
                int flags = condition.mode.equals("regex_i")
                        ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
                return Pattern.compile(condition.value, flags).matcher(text).find();
            } catch (PatternSyntaxException ignored) {
                return false;
            }
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String token : condition.value.split("\\n")) {
            String needle = token.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) continue;
            if (condition.mode.equals("fuzzy")) {
                if (!fuzzy(lowerText, needle)) return false;
            } else if (!lowerText.contains(needle)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fuzzy(String text, String token) {
        int index = 0;
        for (int i = 0; i < text.length() && index < token.length(); i++) {
            if (text.charAt(i) == token.charAt(index)) index++;
        }
        return index == token.length();
    }

    private static boolean numericMatches(AdvancedNgRule.Condition condition, double actual) {
        if (Double.isNaN(actual) || Double.isInfinite(actual)) return false;
        try {
            double value = Double.parseDouble(condition.value);
            switch (condition.mode) {
                case "eq": return actual == value;
                case "gte": return actual >= value;
                case "lte": return actual <= value;
                case "gt": return actual > value;
                case "lt": return actual < value;
                case "between":
                    return actual >= value && actual <= Double.parseDouble(condition.second);
                default: return false;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean timeMatches(AdvancedNgRule.Condition condition, long timestamp) {
        if (timestamp <= 0) return false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        try {
            int value = Integer.parseInt(condition.value);
            switch (condition.field) {
                case "weekday": return calendar.get(Calendar.DAY_OF_WEEK) - 1 == value;
                case "hour": return calendar.get(Calendar.HOUR_OF_DAY) == value;
                case "relativeMinutes":
                    long elapsed = System.currentTimeMillis() - timestamp;
                    return elapsed >= 0 && elapsed <= value * 60_000L;
                default: return false;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static long parseTimestamp(String value) {
        Matcher matcher = DATE.matcher(value);
        if (!matcher.find()) return 0;
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setLenient(false);
            calendar.set(2000 + Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)) - 1,
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)),
                    matcher.group(6) == null ? 0 : Integer.parseInt(matcher.group(6)));
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    static boolean scopeMatches(String scope, String currentUrl) {
        if (scope == null || scope.isEmpty()) return true;
        String expected = scopeKey(scope);
        String actual = scopeKey(currentUrl);
        return expected != null && actual != null &&
                (expected.equals(actual) || actual.startsWith(expected + "/"));
    }

    static String scopeKey(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host == null) return null;
            String[] parts = uri.getPath().split("/");
            if (parts.length >= 5 && "test".equals(parts[1]) &&
                    "read.cgi".equals(parts[2])) {
                return host.toLowerCase(Locale.ROOT) + "/" + parts[3] + "/" + parts[4];
            }
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                return host.toLowerCase(Locale.ROOT) + "/" + parts[1];
            }
        } catch (URISyntaxException ignored) {
            // An invalid scope must never match every thread.
        }
        return null;
    }

    private static String extract(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group() : "";
    }
}
