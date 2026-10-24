package app.revanced.extension.chmate;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ImageBoardSourcesActivity extends Activity {
    private EditText sources;
    private IntegrationStrings strings;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        RuntimeState.initialize(this);
        ImageBoardIntegration.initialize(this);
        strings = IntegrationStrings.current();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        TextView title = new TextView(this);
        title.setText(strings.imageBoardSources);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, matchWrap());
        TextView description = new TextView(this);
        description.setText(strings.sourceDescription);
        description.setTextSize(15);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(12);
        descriptionParams.bottomMargin = dp(16);
        content.addView(description, descriptionParams);

        sources = new EditText(this);
        sources.setMinLines(8);
        sources.setGravity(android.view.Gravity.TOP);
        sources.setHint(strings.sourceHint);
        sources.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        StringBuilder current = new StringBuilder();
        for (ImageBoardClient.Board board : ImageBoardIntegration.configuredBoards(this)) {
            if (current.length() > 0) current.append('\n');
            current.append(board.pageUrl);
        }
        sources.setText(current.toString());
        content.addView(sources, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        Button save = new Button(this);
        save.setText(strings.save);
        save.setAllCaps(false);
        save.setOnClickListener(view -> save());
        content.addView(save, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void save() {
        LinkedHashMap<String, ImageBoardClient.Board> unique = new LinkedHashMap<>();
        for (String line : sources.getText().toString().split("[\\r\\n]+")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            ImageBoardClient.Board board = ImageBoardClient.parseBoard(value);
            if (board == null) {
                sources.setError(strings.invalidSource + value);
                return;
            }
            unique.put(board.pageUrl, board);
            if (unique.size() > 64) {
                sources.setError(strings.invalidSource + "maximum 64 boards");
                return;
            }
        }
        List<ImageBoardClient.Board> boards = new ArrayList<>(unique.values());
        if (!ImageBoardIntegration.saveSources(this, boards)) {
            Toast.makeText(this, SettingsStrings.current().saveFailed, Toast.LENGTH_LONG).show();
            return;
        }
        if (!ImageBoardIntegration.ensureBbsMenuRegistered(this)) {
            Toast.makeText(this, strings.noBbsMenuSlot, Toast.LENGTH_LONG).show();
            return;
        }
        ImageBoardProxyServer.start(this);
        Toast.makeText(this, strings.saved, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
