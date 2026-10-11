package app.revanced.extension.chmate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageBoardActivity extends Activity {
    private static final String LAST_URL = "image_board_last_url";
    private static final int FILE_CHOOSER_REQUEST = 8201;
    private static final int MAX_THUMBNAIL_BYTES = 1024 * 1024;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ExecutorService imageWorkers = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> thumbnails = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private ImageBoardClient.Board board;
    private long threadId;
    private int generation;
    private EditText urlInput;
    private TextView status;
    private Button backButton;
    private Button postButton;
    private ListView list;
    private WebView postWebView;
    private ValueCallback<Uri[]> fileCallback;
    private PostAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RuntimeState.initialize(this);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        LinearLayout address = new LinearLayout(this);
        address.setOrientation(LinearLayout.HORIZONTAL);
        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(savedInstanceState != null ? savedInstanceState.getString("url", "")
                : UserAgentOverride.preferences(this).getString(LAST_URL,
                "https://boards.4channel.org/po/"));
        address.addView(urlInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button openButton = button(label("開く", "Open"));
        openButton.setOnClickListener(view -> openBoard(urlInput.getText().toString()));
        address.addView(openButton, new LinearLayout.LayoutParams(dp(82), dp(52)));
        root.addView(address, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        backButton = button(label("板へ", "Board"));
        backButton.setOnClickListener(view -> {
            if (postWebView != null) closePostWebView();
            else if (threadId != 0) showCatalog();
        });
        actions.addView(backButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button refresh = button(label("更新", "Refresh"));
        refresh.setOnClickListener(view -> {
            if (postWebView != null) postWebView.reload();
            else if (threadId == 0) showCatalog();
            else showThread(threadId);
        });
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(50), 1));
        postButton = button(label("投稿フォーム", "Post"));
        postButton.setOnClickListener(view -> openPostWebView());
        actions.addView(postButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(actions, matchWrap());

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(dp(8), dp(7), dp(8), dp(7));
        root.addView(status, matchWrap());

        list = new ListView(this);
        adapter = new PostAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            ImageBoardClient.Post post = adapter.items.get(position);
            if (threadId == 0) {
                showThread(post.id);
            } else if (post.mediaUrl != null) {
                openPostWebView(post.mediaUrl);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        openBoard(urlInput.getText().toString());
        if (savedInstanceState != null && savedInstanceState.getLong("thread", 0) > 0) {
            showThread(savedInstanceState.getLong("thread"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("url", urlInput.getText().toString());
        state.putLong("thread", threadId);
        super.onSaveInstanceState(state);
    }

    private void openBoard(String input) {
        ImageBoardClient.Board parsed = ImageBoardClient.parseBoard(input);
        if (parsed == null) {
            urlInput.setError(label("4chan・ふたば・jschanのHTTPS板URLを入力してください",
                    "Enter a 4chan, Futaba, or jschan HTTPS board URL"));
            return;
        }
        board = parsed;
        long requestedThread = ImageBoardClient.parseThreadId(parsed, input);
        hideKeyboard();
        UserAgentOverride.preferences(this).edit().putString(LAST_URL, parsed.pageUrl).apply();
        urlInput.setText(parsed.pageUrl);
        closePostWebView();
        if (requestedThread == 0) showCatalog();
        else showThread(requestedThread);
    }

    private void showCatalog() {
        if (board == null) return;
        threadId = 0;
        final int request = ++generation;
        final ImageBoardClient.Board targetBoard = board;
        status.setText(label("板を読み込み中…", "Loading board…"));
        backButton.setEnabled(false);
        postButton.setEnabled(true);
        loader.execute(() -> {
            try {
                List<ImageBoardClient.ThreadEntry> entries = ImageBoardClient.loadCatalog(targetBoard);
                runOnUiThread(() -> {
                    if (request != generation || isFinishing()) return;
                    adapter.set(entries, true);
                    status.setText(label("スレッド ", "Threads: ") + entries.size());
                    list.setSelection(0);
                });
            } catch (Exception error) {
                showError(request, error);
            }
        });
    }

    private void showThread(long id) {
        if (board == null) return;
        threadId = id;
        final int request = ++generation;
        final ImageBoardClient.Board targetBoard = board;
        status.setText(label("スレッドを読み込み中…", "Loading thread…"));
        backButton.setEnabled(true);
        loader.execute(() -> {
            try {
                List<ImageBoardClient.Post> posts = ImageBoardClient.loadThread(targetBoard, id);
                runOnUiThread(() -> {
                    if (request != generation || isFinishing()) return;
                    adapter.set(posts, false);
                    status.setText(label("レス ", "Posts: ") + posts.size());
                    list.setSelection(0);
                });
            } catch (Exception error) {
                showError(request, error);
            }
        });
    }

    private void showError(int request, Exception error) {
        runOnUiThread(() -> {
            if (request != generation || isFinishing()) return;
            status.setText(label("読み込み失敗: ", "Load failed: ") + error.getMessage());
        });
    }

    private void openPostWebView() {
        if (board == null) return;
        openPostWebView(threadId == 0 ? board.pageUrl : board.threadUrl(threadId));
    }

    private void openPostWebView(String url) {
        if (postWebView != null) closePostWebView();
        hideKeyboard();
        ViewGroup root = (ViewGroup) list.getParent();
        postWebView = new WebView(this);
        WebSettings settings = postWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        postWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (view == postWebView && board.kind != ImageBoardClient.Kind.FOUR_CHAN
                        && pageUrl.startsWith("https://" + board.host + "/")) {
                    view.evaluateJavascript("(function(){var form=document.getElementById('fm')"
                            + "||document.getElementById('postform');"
                            + "if(form)form.scrollIntoView();})();", null);
                }
            }
        });
        postWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        root.addView(postWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        list.setVisibility(View.GONE);
        backButton.setEnabled(true);
        status.setText(label("投稿・認証は掲示板のフォームで行います",
                "Use the board's form to post and complete verification"));
        postWebView.loadUrl(url);
    }

    private void closePostWebView() {
        if (postWebView == null) return;
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        ((ViewGroup) postWebView.getParent()).removeView(postWebView);
        postWebView.destroy();
        postWebView = null;
        list.setVisibility(View.VISIBLE);
        backButton.setEnabled(threadId != 0);
        status.setText(threadId == 0
                ? label("板を表示中", "Board") : label("スレッドを表示中", "Thread"));
    }

    private void hideKeyboard() {
        urlInput.clearFocus();
        InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (postWebView != null) closePostWebView();
        else if (threadId != 0) showCatalog();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        generation++;
        closePostWebView();
        loader.shutdownNow();
        imageWorkers.shutdownNow();
        super.onDestroy();
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String label(String japanese, String english) {
        return Locale.getDefault().getLanguage().equals("ja") ? japanese : english;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class PostAdapter extends BaseAdapter {
        private final List<ImageBoardClient.Post> items = new ArrayList<>();
        private boolean catalog;

        void set(List<? extends ImageBoardClient.Post> posts, boolean isCatalog) {
            items.clear();
            items.addAll(posts);
            catalog = isCatalog;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View reused, ViewGroup parent) {
            ImageBoardClient.Post post = items.get(position);
            LinearLayout row = new LinearLayout(ImageBoardActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            ImageView thumbnail = new ImageView(ImageBoardActivity.this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(thumbnail, new LinearLayout.LayoutParams(dp(72), dp(72)));
            LinearLayout text = new LinearLayout(ImageBoardActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(10), 0, 0, 0);
            TextView title = new TextView(ImageBoardActivity.this);
            title.setTextSize(16);
            String heading = !post.subject.isEmpty() ? post.subject
                    : post.comment.length() > 60 ? post.comment.substring(0, 60) + "…" : post.comment;
            title.setText(heading);
            title.setMaxLines(2);
            text.addView(title, matchWrap());
            TextView meta = new TextView(ImageBoardActivity.this);
            meta.setTextSize(12);
            String count = catalog && post instanceof ImageBoardClient.ThreadEntry
                    ? "  +" + ((ImageBoardClient.ThreadEntry) post).replies : "";
            meta.setText("No." + post.id + count + "  " + post.name + "  " + post.date);
            meta.setMaxLines(2);
            text.addView(meta, matchWrap());
            if (catalog && !post.comment.isEmpty() && !post.comment.equals(heading)) {
                TextView preview = new TextView(ImageBoardActivity.this);
                preview.setText(post.comment);
                preview.setTextSize(14);
                preview.setMaxLines(2);
                text.addView(preview, matchWrap());
            } else if (!catalog && !post.subject.isEmpty()) {
                TextView body = new TextView(ImageBoardActivity.this);
                body.setText(post.comment);
                body.setTextSize(14);
                text.addView(body, matchWrap());
            } else if (!catalog) {
                title.setText(post.comment);
                title.setMaxLines(12);
            }
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (post.thumbnailUrl != null) loadThumbnail(post.thumbnailUrl, thumbnail);
            return row;
        }
    }

    private void loadThumbnail(String url, ImageView target) {
        target.setTag(url);
        Bitmap cached = thumbnails.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        imageWorkers.execute(() -> {
            Bitmap bitmap = fetchThumbnail(url);
            if (bitmap == null) return;
            thumbnails.put(url, bitmap);
            runOnUiThread(() -> {
                if (!isFinishing() && url.equals(target.getTag())) target.setImageBitmap(bitmap);
            });
        });
    }

    private Bitmap fetchThumbnail(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(7_000);
            connection.setReadTimeout(10_000);
            if (connection.getResponseCode() != 200 ||
                    connection.getContentLength() > MAX_THUMBNAIL_BYTES) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_THUMBNAIL_BYTES) return null;
                    output.write(buffer, 0, count);
                }
            }
            byte[] bytes = output.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
