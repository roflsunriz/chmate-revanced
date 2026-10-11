package app.revanced.extension.chmate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NiconicoThumbnailResolver {
    private static final Pattern WATCH_PATH = Pattern.compile("^/watch/((?:sm|nm|so)[0-9]+)(?:/|$)");
    private static final Pattern SHORT_PATH = Pattern.compile("^/((?:sm|nm|so)[0-9]+)(?:/|$)");
    private static final Pattern THUMBNAIL = Pattern.compile("<thumbnail_url>\\s*([^<]+)\\s*</thumbnail_url>");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private NiconicoThumbnailResolver() {
    }

    public static String resolve(String pageUrl) {
        String id = videoId(pageUrl);
        if (id == null) {
            return null;
        }

        try {
            return thumbnailUrl(fetchXml(id));
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    static String fetchXml(String id) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL("https://ext.nicovideo.jp/api/getthumbinfo/" + id);
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Niconico thumbnail response: " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String videoId(String pageUrl) {
        if (pageUrl == null) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(pageUrl);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null ||
                !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        Pattern pattern;
        if (normalizedHost.equals("nicovideo.jp") || normalizedHost.equals("www.nicovideo.jp") ||
                normalizedHost.equals("sp.nicovideo.jp")) {
            pattern = WATCH_PATH;
        } else if (normalizedHost.equals("nico.ms")) {
            pattern = SHORT_PATH;
        } else {
            return null;
        }
        Matcher match = pattern.matcher(path);
        return match.find() ? match.group(1) : null;
    }

    static String thumbnailUrl(String xml) {
        if (xml == null || !xml.contains("<nicovideo_thumb_response status=\"ok\"")) {
            return null;
        }
        Matcher match = THUMBNAIL.matcher(xml);
        if (!match.find()) {
            return null;
        }
        String candidate = match.group(1).trim().replace("&amp;", "&");
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
            return null;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals("nimg.jp") || normalizedHost.endsWith(".nimg.jp") ? candidate : null;
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_RESPONSE_BYTES) {
                throw new IOException("Niconico thumbnail response exceeds limit");
            }
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
