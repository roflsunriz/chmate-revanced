package app.revanced.extension.chmate;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private SettingsStrings strings;
    private EditText userAgentInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RuntimeState.initialize(this);
        strings = SettingsStrings.current();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        content.setLayoutDirection(strings.rightToLeft ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        TextView title = new TextView(this);
        title.setText(strings.title);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText(strings.description);
        description.setTextSize(15);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(12);
        descriptionParams.bottomMargin = dp(24);
        content.addView(description, descriptionParams);

        TextView label = new TextView(this);
        label.setText(strings.userAgent);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(label, matchWrap());

        userAgentInput = new EditText(this);
        userAgentInput.setSingleLine(true);
        userAgentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        userAgentInput.setHint(strings.hint);
        userAgentInput.setText(UserAgentOverride.preferences(this)
                .getString(UserAgentOverride.USER_AGENT_KEY, ""));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(6);
        inputParams.bottomMargin = dp(20);
        content.addView(userAgentInput, inputParams);

        content.addView(button(strings.save, view -> save()), matchWrap());
        content.addView(button(strings.saveAndRestart, view -> {
            if (save()) {
                restart();
            }
        }), matchWrap());
        content.addView(button(strings.restart, view -> restart()), matchWrap());
        content.addView(button(strings.reset, view -> {
            userAgentInput.setText("");
            save();
        }), matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        setContentView(scrollView);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    private boolean save() {
        String value = userAgentInput.getText().toString().trim();
        if (value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            userAgentInput.setError(strings.invalid);
            return false;
        }

        boolean saved = UserAgentOverride.preferences(this)
                .edit()
                .putString(UserAgentOverride.USER_AGENT_KEY, value)
                .commit();
        if (saved) {
            Toast.makeText(this, strings.saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, strings.saveFailed, Toast.LENGTH_LONG).show();
        }
        return saved;
    }

    private void restart() {
        if (!RestartController.restart(this)) {
            Toast.makeText(this, strings.restartFailed, Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
