package app.revanced.extension.chmate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImageBoardClient {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Pattern BOARD_CODE = Pattern.compile("[a-z0-9]{1,12}");
    private static final Pattern FUTABA_THREAD = Pattern.compile(
            "<div\\s+class=['\"]thre['\"]\\s+data-res=['\"](\\d+)['\"]([\\s\\S]*?)</div>\\s*<!--スレッド終了-->",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_REPLY = Pattern.compile(
            "<td\\s+class=['\"]?rtd['\"]?[^>]*>([\\s\\S]*?)</td>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_SUBJECT = Pattern.compile(
            "<span\\s+class=['\"]csb['\"]>([\\s\\S]*?)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_NAME = Pattern.compile(
            "<span\\s+class=['\"]cnm['\"]>([\\s\\S]*?)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_DATE = Pattern.compile(
            "<span\\s+class=['\"]cnw['\"]>([\\s\\S]*?)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_NUMBER = Pattern.compile(
            "<span\\s+class=['\"]cno['\"]>No\\.(\\d+)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_COMMENT = Pattern.compile(
            "<blockquote[^>]*>([\\s\\S]*?)</blockquote>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_THUMB = Pattern.compile(
            "<img[^>]+src=['\"]([^'\"]*?/thumb/[^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUTABA_MEDIA = Pattern.compile(
            "<a[^>]+href=['\"]([^'\"]*?/src/[^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

    enum Kind { FOUR_CHAN, FUTABA, JSCHAN }

    static final class Board {
        final Kind kind;
        final String host;
        final String code;
        final String pageUrl;

        Board(Kind kind, String host, String code, String pageUrl) {
            this.kind = kind;
            this.host = host;
            this.code = code;
            this.pageUrl = pageUrl;
        }

        String catalogUrl() {
            if (kind == Kind.FOUR_CHAN) return "https://a.4cdn.org/" + code + "/catalog.json";
            if (kind == Kind.JSCHAN) return "https://" + host + "/" + code + "/catalog.json";
            return pageUrl;
        }

        String threadUrl(long id) {
            if (kind == Kind.FOUR_CHAN) return pageUrl + "thread/" + id;
            if (kind == Kind.JSCHAN) {
                return "https://" + host + "/" + code + "/thread/" + id + ".html";
            }
            return "https://" + host + "/" + code + "/res/" + id + ".htm";
        }

        String threadDataUrl(long id) {
            if (kind == Kind.FOUR_CHAN) {
                return "https://a.4cdn.org/" + code + "/thread/" + id + ".json";
            }
            if (kind == Kind.JSCHAN) {
                return "https://" + host + "/" + code + "/thread/" + id + ".json";
            }
            return threadUrl(id);
        }
    }

    static class Post {
        final long id;
        final String subject;
        final String name;
        final String date;
        final String comment;
        final String thumbnailUrl;
        final String mediaUrl;

        Post(long id, String subject, String name, String date, String comment,
                String thumbnailUrl, String mediaUrl) {
            this.id = id;
            this.subject = subject;
            this.name = name;
            this.date = date;
            this.comment = comment;
            this.thumbnailUrl = thumbnailUrl;
            this.mediaUrl = mediaUrl;
        }
    }

    static final class ThreadEntry extends Post {
        final int replies;

        ThreadEntry(Post post, int replies) {
            super(post.id, post.subject, post.name, post.date, post.comment,
                    post.thumbnailUrl, post.mediaUrl);
            this.replies = replies;
        }
    }

    private ImageBoardClient() { }

    static Board parseBoard(String input) {
        try {
            URI uri = new URI(input.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            String[] segments = uri.getPath().split("/");
            if (segments.length < 2 || !BOARD_CODE.matcher(segments[1]).matches()) return null;
            String code = segments[1];
            if (host.equals("boards.4chan.org") || host.equals("boards.4channel.org")) {
                // The JSON API serves both domains. On Android WebView, the 4channel
                // hostname can fail TLS while the matching 4chan page opens normally.
                return new Board(Kind.FOUR_CHAN, "boards.4chan.org", code,
                        "https://boards.4chan.org/" + code + "/");
            }
            if (host.endsWith(".2chan.net") && host.length() > ".2chan.net".length()) {
                return new Board(Kind.FUTABA, host, code,
                        "https://" + host + "/" + code + "/futaba.htm");
            }
            if (host.contains("2chan.net.") || host.contains("4chan.org.") ||
                    host.contains("4channel.org.")) return null;
            if (!host.equals("localhost") && !host.endsWith(".localhost")) {
                return new Board(Kind.JSCHAN, host, code,
                        "https://" + host + "/" + code + "/index.html");
            }
        } catch (URISyntaxException ignored) {
            // The input field reports unsupported URLs to the user.
        }
        return null;
    }

    static long parseThreadId(Board board, String input) {
        if (board == null) return 0;
        try {
            URI uri = new URI(input.trim());
            if (!board.host.equalsIgnoreCase(uri.getHost()) &&
                    !(board.kind == Kind.FOUR_CHAN
                            && "boards.4channel.org".equalsIgnoreCase(uri.getHost()))) return 0;
            String path = uri.getPath();
            String prefix = board.kind == Kind.FUTABA
                    ? "/" + board.code + "/res/" : "/" + board.code + "/thread/";
            if (!path.startsWith(prefix)) return 0;
            String number = path.substring(prefix.length());
            if (board.kind == Kind.FUTABA && number.endsWith(".htm")) {
                number = number.substring(0, number.length() - 4);
            }
            if (board.kind == Kind.JSCHAN && number.endsWith(".html")) {
                number = number.substring(0, number.length() - 5);
            }
            return number.matches("[0-9]{1,15}") ? Long.parseLong(number) : 0;
        } catch (URISyntaxException | NumberFormatException ignored) {
            return 0;
        }
    }

    static List<ThreadEntry> loadCatalog(Board board) throws IOException, JSONException {
        String data = fetchText(board.catalogUrl(), board.kind == Kind.FUTABA);
        if (board.kind == Kind.FOUR_CHAN) return parseFourChanCatalog(board, data);
        if (board.kind == Kind.JSCHAN) return parseJschanCatalog(board, data);
        return parseFutabaCatalog(board, data);
    }

    static List<Post> loadThread(Board board, long id) throws IOException, JSONException {
        String data = fetchText(board.threadDataUrl(id), board.kind == Kind.FUTABA);
        if (board.kind == Kind.FOUR_CHAN) return parseFourChanThread(board, data);
        if (board.kind == Kind.JSCHAN) return parseJschanThread(board, data);
        return parseFutabaThread(board, data);
    }

    static List<ThreadEntry> parseFourChanCatalog(Board board, String json) throws JSONException {
        JSONArray pages = new JSONArray(json);
        List<ThreadEntry> result = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pages.length(); pageIndex++) {
            JSONArray threads = pages.getJSONObject(pageIndex).optJSONArray("threads");
            if (threads == null) continue;
            for (int index = 0; index < threads.length(); index++) {
                JSONObject thread = threads.getJSONObject(index);
                Post post = fourChanPost(board, thread);
                result.add(new ThreadEntry(post, thread.optInt("replies", 0)));
            }
        }
        return result;
    }

    static List<Post> parseFourChanThread(Board board, String json) throws JSONException {
        JSONArray posts = new JSONObject(json).getJSONArray("posts");
        List<Post> result = new ArrayList<>();
        for (int index = 0; index < posts.length(); index++) {
            result.add(fourChanPost(board, posts.getJSONObject(index)));
        }
        return result;
    }

    private static Post fourChanPost(Board board, JSONObject object) {
        long id = object.optLong("no", 0);
        long imageId = object.optLong("tim", 0);
        String extension = object.optString("ext", "");
        String thumb = imageId > 0 ? "https://t.4cdn.org/" + board.code + "/" + imageId + "s.jpg" : null;
        String media = imageId > 0 && extension.matches("\\.[a-zA-Z0-9]{1,6}")
                ? "https://i.4cdn.org/" + board.code + "/" + imageId + extension : null;
        return new Post(id, clean(object.optString("sub", "")),
                clean(object.optString("name", "Anonymous")),
                clean(object.optString("now", "")), clean(object.optString("com", "")), thumb, media);
    }

    static List<ThreadEntry> parseJschanCatalog(Board board, String json) throws JSONException {
        JSONArray threads = new JSONArray(json);
        List<ThreadEntry> result = new ArrayList<>();
        for (int index = 0; index < threads.length(); index++) {
            JSONObject item = threads.getJSONObject(index);
            result.add(new ThreadEntry(jschanPost(board, item), item.optInt("replyposts", 0)));
        }
        return result;
    }

    static List<Post> parseJschanThread(Board board, String json) throws JSONException {
        JSONObject thread = new JSONObject(json);
        List<Post> result = new ArrayList<>();
        result.add(jschanPost(board, thread));
        JSONArray replies = thread.optJSONArray("replies");
        if (replies != null) {
            for (int index = 0; index < replies.length(); index++) {
                result.add(jschanPost(board, replies.getJSONObject(index)));
            }
        }
        return result;
    }

    private static Post jschanPost(Board board, JSONObject item) {
        JSONArray files = item.optJSONArray("files");
        String media = null;
        String thumb = null;
        if (files != null && files.length() > 0) {
            JSONObject file = files.optJSONObject(0);
            if (file != null) {
                String filename = file.optString("filename", "");
                String hash = file.optString("hash", "");
                String thumbExtension = file.optString("thumbextension", "");
                if (filename.matches("[a-fA-F0-9]{32,128}\\.[A-Za-z0-9]{1,8}")) {
                    media = "https://" + board.host + "/file/" + filename;
                }
                if (file.optBoolean("hasThumb", false) &&
                        hash.matches("[a-fA-F0-9]{32,128}") &&
                        thumbExtension.matches("\\.[A-Za-z0-9]{1,8}")) {
                    thumb = "https://" + board.host + "/file/thumb/" + hash + thumbExtension;
                }
            }
        }
        return new Post(item.optLong("postId", 0), clean(jsonString(item, "subject", "")),
                clean(jsonString(item, "name", "Anon")), jsonString(item, "date", ""),
                clean(jsonString(item, "message", "")), thumb, media);
    }

    private static String jsonString(JSONObject object, String key, String fallback) {
        return object.isNull(key) ? fallback : object.optString(key, fallback);
    }

    static List<ThreadEntry> parseFutabaCatalog(Board board, String html) {
        List<ThreadEntry> result = new ArrayList<>();
        Matcher matcher = FUTABA_THREAD.matcher(html);
        while (matcher.find()) {
            long id = Long.parseLong(matcher.group(1));
            String block = matcher.group(2);
            Post post = futabaPost(board, block, id);
            result.add(new ThreadEntry(post, countMatches(FUTABA_REPLY, block)));
        }
        return result;
    }

    static List<Post> parseFutabaThread(Board board, String html) {
        Matcher thread = FUTABA_THREAD.matcher(html);
        List<Post> result = new ArrayList<>();
        if (!thread.find()) return result;
        String block = thread.group(2);
        int firstReply = block.indexOf("<table border=0>");
        result.add(futabaPost(board, block.substring(0, firstReply < 0 ? block.length() : firstReply),
                Long.parseLong(thread.group(1))));
        Matcher replies = FUTABA_REPLY.matcher(block);
        while (replies.find()) {
            String reply = replies.group(1);
            String number = extract(FUTABA_NUMBER, reply);
            if (!number.isEmpty()) result.add(futabaPost(board, reply, Long.parseLong(number)));
        }
        return result;
    }

    private static Post futabaPost(Board board, String html, long id) {
        String subject = clean(extract(FUTABA_SUBJECT, html));
        if ("無題".equals(subject)) subject = "";
        String name = clean(extract(FUTABA_NAME, html));
        String date = clean(extract(FUTABA_DATE, html));
        String comment = clean(extract(FUTABA_COMMENT, html));
        String thumb = absolute(board, extract(FUTABA_THUMB, html));
        String media = absolute(board, extract(FUTABA_MEDIA, html));
        return new Post(id, subject, name, date, comment, thumb, media);
    }

    private static String absolute(Board board, String path) {
        if (path.isEmpty()) return null;
        try {
            URI resolved = new URI("https://" + board.host + "/").resolve(path);
            return "https".equalsIgnoreCase(resolved.getScheme())
                    && board.host.equalsIgnoreCase(resolved.getHost()) ? resolved.toString() : null;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    static String clean(String html) {
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n");
        text = TAG.matcher(text).replaceAll("");
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String number = matcher.group(1);
            try {
                int codePoint = number.charAt(0) == 'x'
                        ? Integer.parseInt(number.substring(1), 16) : Integer.parseInt(number);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : ""));
            } catch (NumberFormatException ignored) {
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace("&gt;", ">").replace("&lt;", "<")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .replace("&apos;", "'").replace("&ndash;", "–").replace("&mdash;", "—")
                .replace("&hellip;", "…")
                .replace("&amp;", "&").trim();
    }

    private static String extract(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static int countMatches(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String fetchText(String url, boolean shiftJis) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ChMate-ReVanced/1.0");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_RESPONSE_BYTES) {
                        throw new IOException("Board response is too large");
                    }
                    output.write(buffer, 0, count);
                }
            }
            Charset charset = shiftJis ? Charset.forName("Shift_JIS") : StandardCharsets.UTF_8;
            return new String(output.toByteArray(), charset);
        } finally {
            connection.disconnect();
        }
    }
}
