package app.revanced.extension.chmate;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VideoMetadataBridge {
    static final String ENABLED_KEY = "video_metadata_enabled";
    // Bound background requests triggered by one untrusted response.
    private static final int MAX_VIDEOS_PER_RESPONSE = 16;
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;
    private static final WeakHashMap<ViewGroup, State> ROWS = new WeakHashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static final LinkedHashMap<String, CacheEntry> CACHE = new LinkedHashMap<String, CacheEntry>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > 128;
        }
    };

    private VideoMetadataBridge() {
    }

    public static void setLinks(ViewGroup row, CharSequence[] links) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            CharSequence[] copy = links == null ? null : links.clone();
            row.post(() -> setLinks(row, copy));
            return;
        }
        if (!UserAgentOverride.preferences(row.getContext()).getBoolean(ENABLED_KEY, true)) {
            links = null;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (links != null) {
            for (CharSequence link : links) {
                String source = VideoMetadataResolver.sourceUrl(link);
                if (source != null) {
                    unique.add(source);
                    if (unique.size() == MAX_VIDEOS_PER_RESPONSE) break;
                }
            }
        }
        List<String> urls = new ArrayList<>(unique);
        State previous = ROWS.get(row);
        if (previous != null && previous.urls.equals(urls)) return;
        if (previous != null) {
            LinearLayout container = previous.container.get();
            if (container != null) row.removeView(container);
            ROWS.remove(row);
        }
        if (urls.isEmpty()) {
            row.requestLayout();
            return;
        }

        LinearLayout container = new LinearLayout(row.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(row.getContext(), 8), dp(row.getContext(), 8),
                dp(row.getContext(), 8), dp(row.getContext(), 4));
        int color = textColor(row);
        for (String url : urls) {
            TextView card = new TextView(row.getContext());
            card.setText(url);
            card.setTextColor(color);
            card.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            card.setLineSpacing(dp(row.getContext(), 2), 1.0f);
            card.setPadding(dp(row.getContext(), 10), dp(row.getContext(), 8),
                    dp(row.getContext(), 10), dp(row.getContext(), 8));
            card.setTextDirection(View.TEXT_DIRECTION_LOCALE);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(row.getContext(), 6));
            background.setColor(Color.argb(12, Color.red(color), Color.green(color), Color.blue(color)));
            background.setStroke(dp(row.getContext(), 1),
                    Color.argb(45, Color.red(color), Color.green(color), Color.blue(color)));
            card.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(row.getContext(), 6);
            container.addView(card, params);
        }
        row.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        State state = new State(urls, container);
        ROWS.put(row, state);
        row.requestLayout();

        WeakReference<ViewGroup> rowReference = new WeakReference<>(row);
        for (int index = 0; index < urls.size(); index++) {
            final int cardIndex = index;
            String url = urls.get(index);
            WORKERS.execute(() -> {
                VideoMetadataResolver.VideoMetadata metadata = cachedFetch(url);
                ViewGroup currentRow = rowReference.get();
                if (currentRow != null) {
                    currentRow.post(() -> show(currentRow, state, cardIndex, metadata));
                }
            });
        }
    }

    public static int extraHeight(ViewGroup row, int width) {
        State state = ROWS.get(row);
        LinearLayout container = state == null ? null : state.container.get();
        if (container == null || container.getVisibility() != View.VISIBLE) return 0;
        container.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return container.getMeasuredHeight();
    }

    public static void layout(ViewGroup row) {
        State state = ROWS.get(row);
        LinearLayout container = state == null ? null : state.container.get();
        if (container == null || container.getVisibility() != View.VISIBLE) return;
        int height = container.getMeasuredHeight();
        int top = row.getMeasuredHeight() - height;
        container.layout(0, top, row.getMeasuredWidth(), top + height);
    }

    private static void show(ViewGroup row, State expected, int index,
            VideoMetadataResolver.VideoMetadata metadata) {
        if (ROWS.get(row) != expected) return;
        LinearLayout container = expected.container.get();
        if (container == null || index >= container.getChildCount()) return;
        TextView card = (TextView) container.getChildAt(index);
        if (metadata == null) {
            card.setVisibility(View.GONE);
            boolean visible = false;
            for (int i = 0; i < container.getChildCount(); i++) {
                visible |= container.getChildAt(i).getVisibility() == View.VISIBLE;
            }
            container.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            card.setText(format(metadata));
            card.setMaxLines(6);
            card.setEllipsize(TextUtils.TruncateAt.END);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(view -> {
                boolean expanded = Boolean.TRUE.equals(view.getTag());
                card.setTag(!expanded);
                card.setMaxLines(expanded ? 6 : Integer.MAX_VALUE);
                card.setEllipsize(expanded ? TextUtils.TruncateAt.END : null);
                row.requestLayout();
            });
        }
        row.requestLayout();
    }

    private static VideoMetadataResolver.VideoMetadata cachedFetch(String url) {
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            CacheEntry found = CACHE.get(url);
            if (found != null && now - found.timestamp < CACHE_MILLIS) return found.metadata;
        }
        VideoMetadataResolver.VideoMetadata result = VideoMetadataResolver.fetch(url);
        synchronized (CACHE) {
            CACHE.put(url, new CacheEntry(now, result));
        }
        return result;
    }

    private static SpannableStringBuilder format(VideoMetadataResolver.VideoMetadata metadata) {
        Locale locale = Locale.getDefault();
        VideoMetadataStrings labels = VideoMetadataStrings.forLocale(locale);
        SpannableStringBuilder output = new SpannableStringBuilder(metadata.title);
        output.setSpan(new StyleSpan(Typeface.BOLD), 0, output.length(), 0);
        StringBuilder stats = new StringBuilder();
        appendStat(stats, labels.views, metadata.views, locale);
        appendStat(stats, labels.comments, metadata.comments, locale);
        appendStat(stats, labels.mylists, metadata.mylists, locale);
        appendStat(stats, labels.likes, metadata.likes, locale);
        if (stats.length() > 0) output.append('\n').append(stats);
        if (metadata.publishedAt != null && !metadata.publishedAt.isEmpty()) {
            output.append('\n').append(labels.publishedAt).append(": ").append(metadata.publishedAt);
        }
        if (metadata.author != null && !metadata.author.isEmpty()) {
            output.append('\n').append(labels.author).append(": ").append(metadata.author);
        }
        if (metadata.description != null && !metadata.description.trim().isEmpty()) {
            output.append('\n').append(metadata.description.trim());
        }
        return output;
    }

    private static void appendStat(StringBuilder output, String label, String value, Locale locale) {
        if (value == null || value.isEmpty()) return;
        if (output.length() > 0) output.append(" · ");
        output.append(label).append(' ');
        try {
            output.append(NumberFormat.getIntegerInstance(locale).format(Long.parseLong(value)));
        } catch (NumberFormatException exception) {
            output.append(value);
        }
    }

    private static int textColor(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView) return ((TextView) child).getCurrentTextColor();
        }
        TypedArray colors = row.getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try {
            return colors.getColor(0, Color.BLACK);
        } finally {
            colors.recycle();
        }
    }

    private static int dp(Context context, int value) {
        return Math.max(1, Math.round(value * context.getResources().getDisplayMetrics().density));
    }

    private static final class State {
        final List<String> urls;
        final WeakReference<LinearLayout> container;

        State(List<String> urls, LinearLayout container) {
            this.urls = urls;
            this.container = new WeakReference<>(container);
        }
    }

    private static final class CacheEntry {
        final long timestamp;
        final VideoMetadataResolver.VideoMetadata metadata;

        CacheEntry(long timestamp, VideoMetadataResolver.VideoMetadata metadata) {
            this.timestamp = timestamp;
            this.metadata = metadata;
        }
    }
}
