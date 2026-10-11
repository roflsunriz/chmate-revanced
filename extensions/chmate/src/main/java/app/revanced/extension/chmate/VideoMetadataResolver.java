package app.revanced.extension.chmate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VideoMetadataResolver {
    private static final int MAX_HTML_CHARS = 1024 * 1024;
    private static final Pattern META_TAG = Pattern.compile("<meta\\b([^>]*?)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp);", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_THUMBNAIL = Pattern.compile("^/vi/([-_a-zA-Z0-9]+)/");

    private VideoMetadataResolver() {
    }

    static String sourceUrl(CharSequence link) {
        if (link == null) {
            return null;
        }
        String displayed = link.toString();
        if (isVideoUrl(displayed)) {
            return displayed;
        }
        for (Field field : link.getClass().getFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            try {
                String value = (String) field.get(link);
                if (isVideoUrl(value)) {
                    return value;
                }
            } catch (IllegalAccessException | SecurityException ignored) {
                // A thumbnail still displays even when an upstream wrapper hides its source URL.
            }
        }
        URI thumbnail = uri(displayed);
        if (thumbnail != null && subdomainOf(thumbnail.getHost(), "ytimg.com")) {
            Matcher match = YOUTUBE_THUMBNAIL.matcher(thumbnail.getPath());
            if (match.find()) {
                return "https://www.youtube.com/watch?v=" + match.group(1);
            }
        }
        return null;
    }

    static boolean isVideoUrl(String candidate) {
        URI uri = uri(candidate);
        if (uri == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath();
        if (path == null) return false;
        if (subdomainOf(host, "youtube.com")) {
            return path.equals("/watch") || path.startsWith("/shorts/") || path.startsWith("/live/");
        }
        if (host.equals("youtu.be")) return path.length() > 1;
        if (NiconicoThumbnailResolver.videoId(candidate) != null) return true;
        if (host.equals("live2.nicovideo.jp")) return path.startsWith("/watch/lv");
        if (subdomainOf(host, "dailymotion.com")) return path.startsWith("/video/");
        if (subdomainOf(host, "veoh.com")) return path.startsWith("/watch/");
        if (subdomainOf(host, "tiktok.com")) return host.equals("vt.tiktok.com") || path.contains("/video/");
        if (subdomainOf(host, "bilibili.com")) return path.startsWith("/video/");
        if (subdomainOf(host, "redtube.com")) return path.matches("/[0-9]+/?");
        if (subdomainOf(host, "xvideos.com")) return path.startsWith("/video");
        if (subdomainOf(host, "sextar.jp")) return path.startsWith("/video/");
        if (subdomainOf(host, "caribbeancom.com")) return path.startsWith("/moviepages/");
        if (subdomainOf(host, "imgur.com")) return path.matches("(?i).*\\.(mp4|gifv)");
        if (subdomainOf(host, "liveleak.com")) return path.startsWith("/view");
        if (host.equals("vine.co")) return path.startsWith("/v/");
        if (host.equals("locipo.jp")) return path.startsWith("/article/");
        return host.equals("gyao.yahoo.co.jp") && path.startsWith("/player/");
    }

    static VideoMetadata fetch(String pageUrl) {
        if (!isVideoUrl(pageUrl)) {
            return null;
        }
        try {
            String videoId = NiconicoThumbnailResolver.videoId(pageUrl);
            if (videoId != null) {
                try {
                    VideoMetadata metadata = parseNiconico(NiconicoThumbnailResolver.fetchXml(videoId), pageUrl);
                    if (metadata != null) {
                        String likes = fetchNiconicoLikes(videoId);
                        return new VideoMetadata(metadata.sourceUrl, metadata.title, metadata.description,
                                metadata.views, metadata.comments, metadata.mylists, likes,
                                metadata.publishedAt, metadata.author);
                    }
                } catch (IOException ignored) {
                    // The public watch page may still provide metadata.
                }
            }
            String oembed = oembedEndpoint(pageUrl);
            if (oembed != null) {
                try {
                    VideoMetadata metadata = parseOEmbed(readJson(oembed), pageUrl);
                    if (metadata != null) {
                        URI source = uri(pageUrl);
                        String host = source == null ? null : source.getHost().toLowerCase(Locale.ROOT);
                        if (subdomainOf(host, "youtube.com") || "youtu.be".equals(host)) {
                            try {
                                return merge(metadata, parseHtml(readHtml(pageUrl), pageUrl));
                            } catch (IOException ignored) {
                                return metadata;
                            }
                        }
                        return metadata;
                    }
                } catch (IOException ignored) {
                    // The public page may still provide Open Graph metadata.
                }
            }
            return parseHtml(readHtml(pageUrl), pageUrl);
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    static VideoMetadata parseNiconico(String xml, String pageUrl) {
        if (xml == null || !xml.contains("<nicovideo_thumb_response status=\"ok\"")) {
            return null;
        }
        String title = xmlTag(xml, "title");
        if (title == null || title.isEmpty()) {
            return null;
        }
        return new VideoMetadata(pageUrl, title, xmlTag(xml, "description"),
                xmlTag(xml, "view_counter"), xmlTag(xml, "comment_num"),
                xmlTag(xml, "mylist_counter"), null, xmlTag(xml, "first_retrieve"),
                xmlTag(xml, "user_nickname"));
    }

    static String niconicoLikesUrl(String id) {
        return "https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search"
                + "?q=&fields=contentId,likeCounter&filters%5BcontentId%5D%5B0%5D=" + id
                + "&_sort=-viewCounter&_limit=1&_context=chmate-revanced";
    }

    private static String fetchNiconicoLikes(String id) {
        try {
            JSONObject result = new JSONObject(readJson(niconicoLikesUrl(id)));
            JSONObject meta = result.optJSONObject("meta");
            JSONArray videos = result.optJSONArray("data");
            if (meta == null || meta.optInt("status") != 200 || videos == null || videos.length() != 1) {
                return null;
            }
            JSONObject video = videos.optJSONObject(0);
            if (video == null || !id.equals(video.optString("contentId")) || video.isNull("likeCounter")) {
                return null;
            }
            long likes = video.optLong("likeCounter", -1);
            return likes < 0 ? null : Long.toString(likes);
        } catch (IOException | JSONException exception) {
            return null;
        }
    }

    static VideoMetadata parseHtml(String html, String pageUrl) {
        if (html == null) {
            return null;
        }
        Map<String, String> meta = new HashMap<>();
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            Map<String, String> attributes = new HashMap<>();
            Matcher matcher = ATTRIBUTE.matcher(tags.group(1));
            while (matcher.find()) {
                attributes.put(matcher.group(1).toLowerCase(Locale.ROOT),
                        matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            }
            String key = first(attributes, "property", "name", "itemprop");
            String value = attributes.get("content");
            if (key != null && value != null) {
                String normalizedKey = key.toLowerCase(Locale.ROOT);
                if (!meta.containsKey(normalizedKey)) {
                    meta.put(normalizedKey, decode(value));
                }
            }
        }
        String title = first(meta, "og:title", "twitter:title", "title");
        if (title == null) {
            Matcher titleMatch = TITLE_TAG.matcher(html);
            if (titleMatch.find()) {
                title = decode(titleMatch.group(1));
            }
        }
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        return new VideoMetadata(pageUrl, title.trim(),
                first(meta, "og:description", "twitter:description", "description"),
                first(meta, "video:view_count", "video:views", "userinteractioncount", "interactioncount"),
                first(meta, "video:comment_count", "video:comments"),
                first(meta, "video:mylist_count"),
                first(meta, "video:like_count", "video:likes"),
                first(meta, "article:published_time", "video:release_date", "datepublished"),
                first(meta, "author", "article:author", "video:actor"));
    }

    private static String oembedEndpoint(String pageUrl) throws IOException {
        URI source = uri(pageUrl);
        if (source == null) return null;
        String host = source.getHost().toLowerCase(Locale.ROOT);
        String endpoint;
        if (subdomainOf(host, "youtube.com") || host.equals("youtu.be")) {
            endpoint = "https://www.youtube.com/oembed?format=json&url=";
        } else if (subdomainOf(host, "dailymotion.com")) {
            endpoint = "https://www.dailymotion.com/services/oembed?url=";
        } else if (subdomainOf(host, "tiktok.com")) {
            endpoint = "https://www.tiktok.com/oembed?url=";
        } else {
            return null;
        }
        return endpoint + URLEncoder.encode(pageUrl, "UTF-8");
    }

    private static VideoMetadata merge(VideoMetadata base, VideoMetadata details) {
        if (details == null) return base;
        return new VideoMetadata(base.sourceUrl, base.title,
                base.description != null && !base.description.isEmpty() ? base.description : details.description,
                details.views, details.comments, details.mylists, details.likes,
                details.publishedAt,
                base.author != null && !base.author.isEmpty() ? base.author : details.author);
    }

    private static VideoMetadata parseOEmbed(String json, String pageUrl) {
        try {
            JSONObject data = new JSONObject(json);
            String title = data.optString("title", "").trim();
            if (title.isEmpty()) return null;
            return new VideoMetadata(pageUrl, title, data.optString("description", null),
                    null, null, null, null, null, data.optString("author_name", null));
        } catch (JSONException exception) {
            return null;
        }
    }

    private static String readJson(String endpoint) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "chmate-revanced");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Video oEmbed response: " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream();
                    InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                StringBuilder response = new StringBuilder();
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (response.length() + count > 64 * 1024) {
                        throw new IOException("Video oEmbed response exceeds limit");
                    }
                    response.append(buffer, 0, count);
                }
                return response.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readHtml(String pageUrl) throws IOException {
        String current = pageUrl;
        for (int redirect = 0; redirect < 4; redirect++) {
            if (!isVideoUrl(current)) {
                throw new IOException("Unsupported video redirect");
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("Video redirect has no location");
                    }
                    current = URI.create(current).resolve(location).toString();
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Video metadata response: " + status);
                }
                try (InputStream input = connection.getInputStream();
                        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    StringBuilder html = new StringBuilder();
                    char[] buffer = new char[4096];
                    int count;
                    while ((count = reader.read(buffer)) != -1 && html.length() < MAX_HTML_CHARS) {
                        html.append(buffer, 0, Math.min(count, MAX_HTML_CHARS - html.length()));
                    }
                    return html.toString();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many video redirects");
    }

    private static URI uri(String candidate) {
        if (candidate == null) {
            return null;
        }
        try {
            URI result = URI.create(candidate);
            String scheme = result.getScheme();
            if (scheme == null || result.getHost() == null || result.getUserInfo() != null ||
                    !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) ||
                    (result.getPort() != -1 && result.getPort() != 80 && result.getPort() != 443)) {
                return null;
            }
            return result;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean subdomainOf(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    private static String xmlTag(String xml, String name) {
        Matcher matcher = Pattern.compile("<" + name + ">([^<]*)</" + name + ">", Pattern.DOTALL).matcher(xml);
        return matcher.find() ? decode(matcher.group(1)).trim() : null;
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String decode(String source) {
        String withoutTags = source.replaceAll("<[^>]*>", "");
        Matcher matcher = ENTITY.matcher(withoutTags);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String entity = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement;
            switch (entity) {
                case "amp": replacement = "&"; break;
                case "quot": replacement = "\""; break;
                case "apos": replacement = "'"; break;
                case "lt": replacement = "<"; break;
                case "gt": replacement = ">"; break;
                case "nbsp": replacement = " "; break;
                default:
                    try {
                        int codePoint = entity.startsWith("#x") ?
                                Integer.parseInt(entity.substring(2), 16) : Integer.parseInt(entity.substring(1));
                        replacement = Character.isValidCodePoint(codePoint) ?
                                new String(Character.toChars(codePoint)) : "";
                    } catch (NumberFormatException exception) {
                        replacement = "";
                    }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static final class VideoMetadata {
        final String sourceUrl;
        final String title;
        final String description;
        final String views;
        final String comments;
        final String mylists;
        final String likes;
        final String publishedAt;
        final String author;

        VideoMetadata(String sourceUrl, String title, String description, String views,
                String comments, String mylists, String likes, String publishedAt, String author) {
            this.sourceUrl = sourceUrl;
            this.title = title;
            this.description = description;
            this.views = views;
            this.comments = comments;
            this.mylists = mylists;
            this.likes = likes;
            this.publishedAt = publishedAt;
            this.author = author;
        }
    }
}
