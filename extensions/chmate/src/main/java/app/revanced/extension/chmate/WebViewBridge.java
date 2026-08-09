package app.revanced.extension.chmate;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.Map;

public final class WebViewBridge {
    private WebViewBridge() {
    }

    public static void setUserAgentString(WebSettings settings, String value) {
        settings.setUserAgentString(UserAgentOverride.resolve(value));
    }

    public static String getDefaultUserAgent(Context context) {
        return UserAgentOverride.resolve(WebSettings.getDefaultUserAgent(context));
    }

    public static void loadUrl(WebView webView, String url) {
        webView.loadUrl(AdBlocker.sanitizeWebViewUrl(url));
    }

    public static void loadUrl(WebView webView, String url, Map<String, String> headers) {
        webView.loadUrl(AdBlocker.sanitizeWebViewUrl(url), headers);
    }

    public static void blockLoadUrl(WebView webView, String url) {
        webView.loadUrl("about:blank");
    }

    public static void blockLoadUrl(WebView webView, String url, Map<String, String> headers) {
        webView.loadUrl("about:blank");
    }
}
