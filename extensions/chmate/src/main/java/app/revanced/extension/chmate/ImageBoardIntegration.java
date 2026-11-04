package app.revanced.extension.chmate;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageBoardIntegration {
    static final String SOURCES_KEY = "image_board_native_sources_v1";
    static final String LOOPBACK_HOST = "127.0.0.1";
    static final int LOOPBACK_PORT = 28282;
    static final String BBS_MENU_URL = "http://" + LOOPBACK_HOST + ":" + LOOPBACK_PORT + "/bbsmenu";
    private static final Map<String, ImageBoardClient.Board> TRANSIENT_BOARDS = new ConcurrentHashMap<>();
    private static volatile Context applicationContext;

    private ImageBoardIntegration() {
    }

    public static boolean isProxyUrl(String input) {
        if (input == null) return false;
        try {
            return isProxyUri(URI.create(input));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isProxyUri(URI uri) {
        String path = uri.getRawPath();
        return LOOPBACK_HOST.equals(uri.getHost()) && path != null
                && path.matches("/(?:test/read\\.cgi/)?ib[0-9a-f]{16}/.*")
                && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
    }

    public static String rewriteProxyUrl(String input) {
        if (input == null) return null;
        try {
            URI uri = URI.create(input);
            String path = uri.getRawPath();
            if (isProxyUri(uri)) {
                return "http://" + LOOPBACK_HOST + ":" + LOOPBACK_PORT + path
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            }
        } catch (IllegalArgumentException ignored) {
            // Preserve malformed input for the original client's error handling.
        }
        return input;
    }

    static void initialize(Context context) {
        if (context == null) return;
        applicationContext = context.getApplicationContext();
        ImageBoardProxyServer.start(applicationContext);
    }

    public static String rewriteInputUrl(String input) {
        if (input == null) return null;
        ImageBoardClient.Board board = ImageBoardClient.parseBoard(input);
        if (board == null) return input;
        Context context = applicationContext;
        long remoteThreadId = ImageBoardClient.parseThreadId(board, input);
        if (board.kind == ImageBoardClient.Kind.JSCHAN
                && remoteThreadId == 0
                && (context == null || !isConfigured(context, board))) return input;
        String token = token(board);
        if (context != null) {
            rememberBoard(context, board);
            ensureBbsMenuRegistered(context);
            ImageBoardProxyServer.start(context);
        }
        TRANSIENT_BOARDS.put(token, board);
        if (remoteThreadId == 0) return localBoardUrl(token);
        long localThreadId = ImageBoardProxyServer.localThreadId(board, remoteThreadId);
        ImageBoardProxyServer.rememberThread(token, localThreadId, remoteThreadId);
        return "http://" + LOOPBACK_HOST + ":" + LOOPBACK_PORT
                + "/test/read.cgi/" + token + "/" + localThreadId + "/";
    }

    static List<ImageBoardClient.Board> configuredBoards(Context context) {
        LinkedHashMap<String, ImageBoardClient.Board> unique = new LinkedHashMap<>();
        String stored = UserAgentOverride.preferences(context).getString(SOURCES_KEY, "");
        if (stored != null) {
            for (String line : stored.split("[\\r\\n]+")) {
                ImageBoardClient.Board board = ImageBoardClient.parseBoard(line);
                if (board != null) unique.put(board.pageUrl, board);
            }
        }
        return new ArrayList<>(unique.values());
    }

    static ImageBoardClient.Board findBoard(Context context, String token) {
        ImageBoardClient.Board transientBoard = TRANSIENT_BOARDS.get(token);
        if (transientBoard != null) return transientBoard;
        for (ImageBoardClient.Board board : configuredBoards(context)) {
            if (token(board).equals(token)) {
                TRANSIENT_BOARDS.put(token, board);
                return board;
            }
        }
        return null;
    }

    static boolean saveSources(Context context, List<ImageBoardClient.Board> boards) {
        StringBuilder value = new StringBuilder();
        for (ImageBoardClient.Board board : boards) {
            if (value.length() > 0) value.append('\n');
            value.append(board.pageUrl);
        }
        boolean saved = UserAgentOverride.preferences(context).edit()
                .putString(SOURCES_KEY, value.toString()).commit();
        if (saved) {
            TRANSIENT_BOARDS.clear();
            for (ImageBoardClient.Board board : boards) TRANSIENT_BOARDS.put(token(board), board);
        }
        return saved;
    }

    static boolean ensureBbsMenuRegistered(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String emptyKey = null;
        for (int i = 0; i < 5; i++) {
            String key = i == 0 ? "bbsMenuUrl" : "bbsMenuUrl" + i;
            String value = preferences.getString(key, "");
            if (BBS_MENU_URL.equals(value)) return true;
            if ((value == null || value.trim().isEmpty()) && emptyKey == null) emptyKey = key;
        }
        return emptyKey != null && preferences.edit().putString(emptyKey, BBS_MENU_URL).commit();
    }

    static String token(ImageBoardClient.Board board) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(board.pageUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder token = new StringBuilder("ib");
            for (int i = 0; i < 8; i++) token.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return token.toString();
        } catch (Exception impossible) {
            return "ib" + Integer.toHexString(board.pageUrl.hashCode()).replace('-', '0');
        }
    }

    static String localBoardUrl(String token) {
        return "http://" + LOOPBACK_HOST + ":" + LOOPBACK_PORT + "/" + token + "/";
    }

    private static void rememberBoard(Context context, ImageBoardClient.Board board) {
        List<ImageBoardClient.Board> boards = configuredBoards(context);
        for (ImageBoardClient.Board existing : boards) {
            if (existing.pageUrl.equals(board.pageUrl)) return;
        }
        boards.add(board);
        saveSources(context, boards);
    }

    private static boolean isConfigured(Context context, ImageBoardClient.Board board) {
        for (ImageBoardClient.Board existing : configuredBoards(context)) {
            if (existing.pageUrl.equals(board.pageUrl)) return true;
        }
        return false;
    }
}
