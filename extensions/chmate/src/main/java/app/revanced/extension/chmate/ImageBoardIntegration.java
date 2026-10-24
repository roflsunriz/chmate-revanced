package app.revanced.extension.chmate;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
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
        if (board.kind == ImageBoardClient.Kind.JSCHAN
                && (context == null || !isConfigured(context, board))) return input;
        String token = token(board);
        TRANSIENT_BOARDS.put(token, board);
        if (context != null) {
            rememberBoard(context, board);
            ensureBbsMenuRegistered(context);
            ImageBoardProxyServer.start(context);
        }
        long remoteThreadId = ImageBoardClient.parseThreadId(board, input);
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
        for (ImageBoardClient.Board board : TRANSIENT_BOARDS.values()) {
            unique.put(board.pageUrl, board);
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
            TRANSIENT_BOARDS.put(token(board), board);
        }
        return UserAgentOverride.preferences(context).edit()
                .putString(SOURCES_KEY, value.toString()).commit();
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
