package app.revanced.extension.chmate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AdBlocker {
    private static final String BLOCKED_URL = "https://blocked.invalid/";

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "2mdn.net",
            "admob.com",
            "adsrvr.org",
            "adservice.google.com",
            "adservice.google.co.jp",
            "amazon-adsystem.com",
            "app-measurement.com",
            "applovin.com",
            "applvn.com",
            "crashlyticsreports-pa.googleapis.com",
            "doubleclick.net",
            "firebaseinstallations.googleapis.com",
            "firebaselogging-pa.googleapis.com",
            "googleadservices.com",
            "googleadsserving.cn",
            "googlesyndication.com",
            "googletagmanager.com",
            "googletagservices.com",
            "inmobi.com",
            "ironsrc.com",
            "pangle.io",
            "pangleglobal.com",
            "unityads.unity3d.com"
    ));

    private AdBlocker() {
    }

    public static InetAddress[] getAllByName(String host) throws UnknownHostException {
        rejectHost(host);
        return InetAddress.getAllByName(host);
    }

    public static InetAddress getByName(String host) throws UnknownHostException {
        rejectHost(host);
        return InetAddress.getByName(host);
    }

    public static InetAddress[] blockGetAllByName(String host) throws UnknownHostException {
        throw blocked();
    }

    public static InetAddress blockGetByName(String host) throws UnknownHostException {
        throw blocked();
    }

    public static URLConnection openConnection(URL url) throws IOException {
        rejectUrl(url);
        return url.openConnection();
    }

    public static URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        rejectUrl(url);
        return url.openConnection(proxy);
    }

    public static URLConnection blockOpenConnection(URL url) throws IOException {
        throw blocked();
    }

    public static URLConnection blockOpenConnection(URL url, Proxy proxy) throws IOException {
        throw blocked();
    }

    public static String sanitizeNetworkUrl(String value) {
        return isBlockedUrl(value) ? BLOCKED_URL : value;
    }

    public static String blockNetworkUrl(String value) {
        return BLOCKED_URL;
    }

    public static String sanitizeWebViewUrl(String value) {
        return isBlockedUrl(value) ? "about:blank" : value;
    }

    public static boolean isBlockedUrl(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            return isBlockedHost(uri.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean isBlockedHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        for (String blocked : BLOCKED_HOSTS) {
            if (normalized.equals(blocked) || normalized.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectHost(String host) throws UnknownHostException {
        if (isBlockedHost(host)) {
            throw new UnknownHostException("Blocked advertising host");
        }
    }

    private static UnknownHostException blocked() {
        return new UnknownHostException("Blocked advertising network request");
    }

    private static void rejectUrl(URL url) throws UnknownHostException {
        if (url != null) {
            rejectHost(url.getHost());
        }
    }
}
