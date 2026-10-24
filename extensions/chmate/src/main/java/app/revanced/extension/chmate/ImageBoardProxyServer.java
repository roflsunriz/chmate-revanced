package app.revanced.extension.chmate;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageBoardProxyServer {
    private static final Charset SHIFT_JIS = Charset.forName("MS932");
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final long CATALOG_CACHE_MILLIS = 30_000L;
    private static final Map<String, Long> THREADS = new ConcurrentHashMap<>();
    private static final Map<String, CatalogCache> CATALOGS = new ConcurrentHashMap<>();
    private static final Object START_LOCK = new Object();
    private static volatile ServerSocket serverSocket;
    private static volatile Context applicationContext;
    private static ExecutorService workers;

    private ImageBoardProxyServer() {
    }

    static void start(Context context) {
        if (context == null) return;
        applicationContext = context.getApplicationContext();
        synchronized (START_LOCK) {
            if (serverSocket != null && !serverSocket.isClosed()) return;
            try {
                ServerSocket server = new ServerSocket(ImageBoardIntegration.LOOPBACK_PORT, 24,
                        InetAddress.getByName(ImageBoardIntegration.LOOPBACK_HOST));
                server.setReuseAddress(true);
                serverSocket = server;
                workers = Executors.newFixedThreadPool(4, runnable -> {
                    Thread thread = new Thread(runnable, "ChMate imageboard proxy worker");
                    thread.setDaemon(true);
                    return thread;
                });
                Thread acceptor = new Thread(() -> accept(server), "ChMate imageboard proxy");
                acceptor.setDaemon(true);
                acceptor.start();
            } catch (IOException ignored) {
                // Another ChMate process can already own the fixed loopback endpoint.
            }
        }
    }

    static long localThreadId(ImageBoardClient.Board board, long remoteId) {
        if (remoteId >= 100_000_000L && remoteId <= 9_999_999_999L) return remoteId;
        String identity = board.pageUrl + ':' + remoteId;
        return 100_000_000L + (identity.hashCode() & 0x7fffffffL) % 900_000_000L;
    }

    static void rememberThread(String token, long localId, long remoteId) {
        THREADS.put(token + ':' + localId, remoteId);
    }

    private static void accept(ServerSocket server) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                ExecutorService executor = workers;
                if (executor != null) executor.execute(() -> handle(socket));
                else socket.close();
            } catch (SocketException closed) {
                return;
            } catch (IOException ignored) {
                // Keep accepting after an isolated client failure.
            }
        }
    }

    private static void handle(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(20_000);
            Request request = readRequest(client);
            if (request == null || !("GET".equals(request.method) || "HEAD".equals(request.method))) {
                send(client, 405, "text/plain; charset=UTF-8",
                        "Method not allowed".getBytes(StandardCharsets.UTF_8), null, false);
                return;
            }
            Response response = route(request.path);
            send(client, response.status, response.contentType, response.body,
                    request.headers.get("range"), "HEAD".equals(request.method));
        } catch (Exception ignored) {
            try {
                send(socket, 500, "text/plain; charset=UTF-8",
                        "Imageboard proxy error".getBytes(StandardCharsets.UTF_8), null, false);
            } catch (Exception ignoredAgain) {
                // The client can close while the upstream request is failing.
            }
        }
    }

    private static Response route(String rawPath) throws Exception {
        String path = URLDecoder.decode(rawPath.split("\\?", 2)[0], "UTF-8");
        Context context = applicationContext;
        if (context == null) return Response.notFound();
        if ("/bbsmenu".equals(path) || "/bbsmenu/".equals(path)) return bbsMenu(context);

        String[] parts = path.split("/");
        if (parts.length >= 3 && "test".equals(parts[1]) && "read.cgi".equals(parts[2])
                && parts.length >= 5) {
            return thread(context, parts[3], parseLong(parts[4]));
        }
        if (parts.length < 3) return Response.notFound();
        String token = parts[1];
        if ("subject.txt".equals(parts[2])) return subject(context, token);
        if (parts.length >= 4 && "dat".equals(parts[2]) && parts[3].endsWith(".dat")) {
            return thread(context, token, parseLong(parts[3].substring(0, parts[3].length() - 4)));
        }
        if (parts.length == 3 && parts[2].isEmpty()) return boardPage(context, token);
        return Response.notFound();
    }

    private static Response bbsMenu(Context context) {
        StringBuilder html = new StringBuilder("<html><body><b>Imageboards</b><br>\n");
        for (ImageBoardClient.Board board : ImageBoardIntegration.configuredBoards(context)) {
            String token = ImageBoardIntegration.token(board);
            html.append("<a href=\"").append(ImageBoardIntegration.localBoardUrl(token)).append("\">")
                    .append(escapeHtml(label(board))).append("</a><br>\n");
        }
        html.append("</body></html>\n");
        return Response.ok("text/html; charset=UTF-8", html.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Response boardPage(Context context, String token) throws Exception {
        ImageBoardClient.Board board = ImageBoardIntegration.findBoard(context, token);
        if (board == null) return Response.notFound();
        StringBuilder html = new StringBuilder("<html><head><title>")
                .append(escapeHtml(label(board))).append("</title></head><body>\n");
        for (ImageBoardClient.ThreadEntry entry : catalog(token, board)) {
            long localId = localThreadId(board, entry.id);
            rememberThread(token, localId, entry.id);
            html.append("<a href=\"/test/read.cgi/").append(token).append('/').append(localId)
                    .append("/\">").append(escapeHtml(title(entry))).append("</a><br>\n");
        }
        html.append("</body></html>\n");
        return Response.ok("text/html; charset=UTF-8", html.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Response subject(Context context, String token) throws Exception {
        ImageBoardClient.Board board = ImageBoardIntegration.findBoard(context, token);
        if (board == null) return Response.notFound();
        StringBuilder subject = new StringBuilder();
        for (ImageBoardClient.ThreadEntry entry : catalog(token, board)) {
            long localId = localThreadId(board, entry.id);
            rememberThread(token, localId, entry.id);
            subject.append(localId).append(".dat<>").append(datText(title(entry)))
                    .append(" (").append(Math.max(1, entry.replies + 1)).append(")\n");
        }
        return Response.ok("text/plain; charset=Shift_JIS", subject.toString().getBytes(SHIFT_JIS));
    }

    private static Response thread(Context context, String token, long localId) throws Exception {
        if (localId <= 0) return Response.notFound();
        ImageBoardClient.Board board = ImageBoardIntegration.findBoard(context, token);
        if (board == null) return Response.notFound();
        long remoteId = resolveRemoteThread(token, localId, board);
        if (remoteId <= 0) return Response.notFound();
        List<ImageBoardClient.Post> posts = ImageBoardClient.loadThread(board, remoteId);
        StringBuilder dat = new StringBuilder();
        for (int index = 0; index < posts.size(); index++) {
            ImageBoardClient.Post post = posts.get(index);
            dat.append(datText(emptyTo(post.name, "名無しさん"))).append("<>")
                    .append("<>").append(datText(post.date)).append("<>")
                    .append(datBody(post)).append("<>");
            if (index == 0) dat.append(datText(title(post)));
            dat.append('\n');
        }
        return Response.ok("text/plain; charset=Shift_JIS", dat.toString().getBytes(SHIFT_JIS));
    }

    private static long resolveRemoteThread(String token, long localId,
            ImageBoardClient.Board board) throws Exception {
        Long mapped = THREADS.get(token + ':' + localId);
        if (mapped != null) return mapped;
        for (ImageBoardClient.ThreadEntry entry : catalog(token, board)) {
            long candidate = localThreadId(board, entry.id);
            rememberThread(token, candidate, entry.id);
            if (candidate == localId) return entry.id;
        }
        return 0;
    }

    private static List<ImageBoardClient.ThreadEntry> catalog(String token,
            ImageBoardClient.Board board) throws Exception {
        CatalogCache cached = CATALOGS.get(token);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < CATALOG_CACHE_MILLIS) return cached.entries;
        List<ImageBoardClient.ThreadEntry> loaded = Collections.unmodifiableList(
                new ArrayList<>(ImageBoardClient.loadCatalog(board)));
        CATALOGS.put(token, new CatalogCache(now, loaded));
        return loaded;
    }

    private static String datBody(ImageBoardClient.Post post) {
        StringBuilder body = new StringBuilder(datText(post.comment).replace("\n", "<br>"));
        if (post.thumbnailUrl != null) body.append("<br>").append(datText(post.thumbnailUrl));
        if (post.mediaUrl != null && !post.mediaUrl.equals(post.thumbnailUrl)) {
            body.append("<br>").append(datText(post.mediaUrl));
        }
        return body.toString();
    }

    private static String title(ImageBoardClient.Post post) {
        String title = emptyTo(post.subject, post.comment);
        title = emptyTo(title, Long.toString(post.id)).replace('\n', ' ').trim();
        return title.length() > 80 ? title.substring(0, 80) : title;
    }

    private static String label(ImageBoardClient.Board board) {
        String kind = board.kind == ImageBoardClient.Kind.FOUR_CHAN ? "4chan"
                : board.kind == ImageBoardClient.Kind.FUTABA ? "ふたば" : "jschan";
        return kind + " - " + board.host + "/" + board.code;
    }

    private static String datText(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("<br>", "&lt;br&gt;");
    }

    private static String escapeHtml(String value) {
        return datText(value).replace("\"", "&quot;");
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Request readRequest(Socket socket) throws IOException {
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (header.size() < MAX_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) break;
            header.write(value);
            int expected = matched == 0 || matched == 2 ? '\r' : '\n';
            if (value == expected) matched++;
            else matched = value == '\r' ? 1 : 0;
            if (matched == 4) break;
        }
        String text = header.toString("ISO-8859-1");
        String[] lines = text.split("\\r\\n");
        if (lines.length == 0) return null;
        String[] first = lines[0].split(" ");
        if (first.length < 2) return null;
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        return new Request(first[0], first[1], headers);
    }

    private static void send(Socket socket, int status, String contentType, byte[] body,
            String range, boolean headOnly) throws IOException {
        int from = 0;
        int actualStatus = status;
        if (status == 200 && range != null && range.startsWith("bytes=")) {
            String value = range.substring(6).split("-", 2)[0];
            try {
                from = Integer.parseInt(value);
                if (from >= 0 && from < body.length) actualStatus = 206;
                else from = 0;
            } catch (NumberFormatException ignored) {
                from = 0;
            }
        }
        int length = body.length - from;
        String reason = actualStatus == 200 ? "OK" : actualStatus == 206 ? "Partial Content"
                : actualStatus == 404 ? "Not Found" : actualStatus == 405 ? "Method Not Allowed" : "Error";
        StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(actualStatus).append(' ')
                .append(reason).append("\r\nContent-Type: ").append(contentType)
                .append("\r\nContent-Length: ").append(length)
                .append("\r\nAccept-Ranges: bytes\r\nConnection: close\r\n");
        if (actualStatus == 206) headers.append("Content-Range: bytes ").append(from).append('-')
                .append(body.length - 1).append('/').append(body.length).append("\r\n");
        headers.append("\r\n");
        OutputStream output = socket.getOutputStream();
        output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!headOnly && length > 0) output.write(body, from, length);
        output.flush();
    }

    private static final class Request {
        final String method;
        final String path;
        final Map<String, String> headers;

        Request(String method, String path, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }
    }

    private static final class Response {
        final int status;
        final String contentType;
        final byte[] body;

        Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        static Response ok(String contentType, byte[] body) {
            return new Response(200, contentType, body);
        }

        static Response notFound() {
            return new Response(404, "text/plain; charset=UTF-8",
                    "Not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class CatalogCache {
        final long loadedAt;
        final List<ImageBoardClient.ThreadEntry> entries;

        CatalogCache(long loadedAt, List<ImageBoardClient.ThreadEntry> entries) {
            this.loadedAt = loadedAt;
            this.entries = entries;
        }
    }
}
