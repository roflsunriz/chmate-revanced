package app.revanced.extension.chmate;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdvancedNgActivity extends Activity {
    private static final String[] TYPES = {"text", "numeric", "time"};
    private static final String[] TEXT_FIELDS = {
            "all", "name", "body", "mail", "id", "trip", "watchoi", "ip", "be", "url", "threadTitle"
    };
    private static final String[] NUMERIC_FIELDS = {
            "resNumber", "lineCount", "charCount", "idCount", "replyCount",
            "repliedCount", "threadResCount"
    };
    private static final String[] TIME_FIELDS = {"weekday", "hour", "relativeMinutes"};
    private static final String[] TEXT_MODES = {"contains", "regex", "regex_i", "fuzzy"};
    private static final String[] NUMERIC_MODES = {"eq", "gte", "lte", "gt", "lt", "between"};

    private final List<AdvancedNgRule> rules = new ArrayList<>();
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        RuntimeState.initialize(this);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        try {
            rules.addAll(AdvancedNgBridge.loadForEditor(this));
        } catch (JSONException error) {
            new AlertDialog.Builder(this)
                    .setTitle(ja("保存済みNGルールを読み込めません", "Cannot read saved NG rules"))
                    .setMessage(error.getMessage())
                    .setPositiveButton(ja("戻る", "Back"), (dialog, which) -> finish())
                    .show();
            return;
        }

        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView title = text(ja("高度なNGルール", "Advanced NG rules"), 22, true);
        root.addView(title, matchWrap());
        TextView description = text(ja(
                "既存のNG設定と併用できます。複数条件はすべて一致したレスへ適用されます。"
                        + "設定後にスレッドを開き直してください。",
                "Use alongside ChMate NG. All conditions in a rule must match. Reopen the thread after editing."),
                14, false);
        description.setPadding(0, dp(8), 0, dp(12));
        root.addView(description, matchWrap());
        Button add = button(ja("ルールを追加", "Add rule"));
        add.setOnClickListener(view -> editRule(null));
        root.addView(add, matchWrap());
        rows = vertical();
        root.addView(rows, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        renderRules();
    }

    private void renderRules() {
        rows.removeAllViews();
        if (rules.isEmpty()) {
            rows.addView(text(ja("ルールはありません", "No rules"), 15, false), matchWrap());
            return;
        }
        for (AdvancedNgRule rule : new ArrayList<>(rules)) {
            LinearLayout row = vertical();
            row.setPadding(dp(4), dp(12), dp(4), dp(12));
            String label = rule.label.isEmpty() ? summary(rule) : rule.label;
            row.addView(text(label, 17, true), matchWrap());
            row.addView(text(summary(rule), 13, false), matchWrap());
            LinearLayout actions = horizontal();
            CheckBox enabled = new CheckBox(this);
            enabled.setText(ja("有効", "Enabled"));
            enabled.setChecked(rule.enabled);
            enabled.setOnCheckedChangeListener((button, checked) -> replace(rule,
                    rule.withEnabled(checked)));
            actions.addView(enabled, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button edit = button(ja("編集", "Edit"));
            edit.setOnClickListener(view -> editRule(rule));
            actions.addView(edit, new LinearLayout.LayoutParams(dp(80), dp(48)));
            Button delete = button(ja("削除", "Delete"));
            delete.setOnClickListener(view -> new AlertDialog.Builder(this)
                    .setMessage(ja("このルールを削除しますか？", "Delete this rule?"))
                    .setNegativeButton(ja("キャンセル", "Cancel"), null)
                    .setPositiveButton(ja("削除", "Delete"), (dialog, which) -> remove(rule))
                    .show());
            actions.addView(delete, new LinearLayout.LayoutParams(dp(80), dp(48)));
            row.addView(actions, matchWrap());
            View divider = new View(this);
            divider.setBackgroundColor(Color.LTGRAY);
            row.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            rows.addView(row, matchWrap());
        }
    }

    private String summary(AdvancedNgRule rule) {
        StringBuilder result = new StringBuilder(rule.transparent
                ? ja("透明", "Transparent") : ja("通常", "Normal"));
        if (rule.chain) result.append(ja("・連鎖", " · chain"));
        if (!rule.scope.isEmpty()) result.append(" · ").append(rule.scope);
        for (AdvancedNgRule.Condition condition : rule.conditions) {
            result.append("\n").append(condition.negate ? "¬" : "")
                    .append(optionLabel(condition.field)).append(" ").append(optionLabel(condition.mode))
                    .append(" ").append(condition.value);
            if (!condition.second.isEmpty()) result.append("～").append(condition.second);
        }
        return result.toString();
    }

    private void editRule(AdvancedNgRule original) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText label = input(ja("表示名（任意）", "Label (optional)"),
                original == null ? "" : original.label, false);
        form.addView(label, matchWrap());
        EditText scope = input(ja("適用する板またはスレッドのURL（空欄なら全体）",
                "Board or thread URL (blank for all)"), original == null ? "" : original.scope,
                false);
        form.addView(scope, matchWrap());
        CheckBox enabled = check(ja("有効", "Enabled"), original == null || original.enabled);
        form.addView(enabled, matchWrap());
        CheckBox transparent = check(ja("透明あぼーん（空間も消す）", "Transparent hide"),
                original == null || original.transparent);
        form.addView(transparent, matchWrap());
        CheckBox chain = check(ja("参照したレスにも連鎖", "Hide replies that reference a match"),
                original != null && original.chain);
        form.addView(chain, matchWrap());
        TextView conditionTitle = text(ja("条件（すべて一致）", "Conditions (all must match)"), 16, true);
        conditionTitle.setPadding(0, dp(10), 0, 0);
        form.addView(conditionTitle, matchWrap());
        LinearLayout conditionRows = vertical();
        form.addView(conditionRows, matchWrap());
        List<AdvancedNgRule.Condition> conditions = new ArrayList<>();
        if (original != null) conditions.addAll(original.conditions);
        renderConditions(conditionRows, conditions);
        Button add = button(ja("条件を追加", "Add condition"));
        add.setOnClickListener(view -> editCondition(conditions, conditionRows, null, -1));
        form.addView(add, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? ja("NGルールを追加", "Add NG rule")
                        : ja("NGルールを編集", "Edit NG rule"))
                .setView(scroll)
                .setNegativeButton(ja("キャンセル", "Cancel"), null)
                .setPositiveButton(ja("保存", "Save"), null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (conditions.isEmpty()) {
                        toast(ja("条件を1件以上追加してください", "Add at least one condition"));
                        return;
                    }
                    String scopeValue = scope.getText().toString().trim();
                    if (!scopeValue.isEmpty() && AdvancedNgMatcher.scopeKey(scopeValue) == null) {
                        scope.setError(ja("板またはスレッドのURLを入力してください", "Enter a board or thread URL"));
                        return;
                    }
                    AdvancedNgRule updated = new AdvancedNgRule(
                            original == null ? java.util.UUID.randomUUID().toString() : original.id,
                            label.getText().toString().trim(), scopeValue, enabled.isChecked(),
                            transparent.isChecked(), chain.isChecked(), conditions);
                    if (replace(original, updated)) dialog.dismiss();
                }));
        dialog.show();
    }

    private void renderConditions(LinearLayout container,
            List<AdvancedNgRule.Condition> conditions) {
        container.removeAllViews();
        for (int i = 0; i < conditions.size(); i++) {
            final int index = i;
            AdvancedNgRule.Condition condition = conditions.get(i);
            LinearLayout row = horizontal();
            TextView description = text((i + 1) + ". " + optionLabel(condition.field) + " " +
                    optionLabel(condition.mode) + " " + condition.value, 14, false);
            row.addView(description, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button edit = button(ja("編集", "Edit"));
            edit.setOnClickListener(view -> editCondition(conditions, container, condition, index));
            row.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(48)));
            Button delete = button("×");
            delete.setOnClickListener(view -> {
                conditions.remove(index);
                renderConditions(container, conditions);
            });
            row.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
            container.addView(row, matchWrap());
        }
    }

    private void editCondition(List<AdvancedNgRule.Condition> conditions,
            LinearLayout container, AdvancedNgRule.Condition original, int index) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        Spinner type = spinner(TYPES, original == null ? "text" : original.type);
        form.addView(labelled(ja("種類", "Type"), type), matchWrap());
        String initialType = original == null ? "text" : original.type;
        String[] initialFields = initialType.equals("numeric") ? NUMERIC_FIELDS
                : initialType.equals("time") ? TIME_FIELDS : TEXT_FIELDS;
        String[] initialModes = initialType.equals("numeric") ? NUMERIC_MODES
                : initialType.equals("time") ? new String[]{"eq"} : TEXT_MODES;
        Spinner field = spinner(initialFields, original == null ? "body" : original.field);
        form.addView(labelled(ja("対象", "Field"), field), matchWrap());
        Spinner mode = spinner(initialModes, original == null ? "contains" : original.mode);
        form.addView(labelled(ja("比較", "Match"), mode), matchWrap());
        EditText value = input(ja("値。複数行はすべて一致", "Value; all lines must match"),
                original == null ? "" : original.value, true);
        form.addView(value, matchWrap());
        EditText second = input(ja("範囲の終点（範囲指定時）", "Range end (for between)"),
                original == null ? "" : original.second, false);
        form.addView(second, matchWrap());
        second.setVisibility(initialType.equals("numeric") ? View.VISIBLE : View.GONE);
        CheckBox negate = check(ja("条件を反転", "Negate"), original != null && original.negate);
        form.addView(negate, matchWrap());
        TextView hint = text(ja("曜日は日=0～土=6。相対時間は分で指定します。",
                "Weekday: Sunday=0 to Saturday=6. Relative time is in minutes."), 12, false);
        form.addView(hint, matchWrap());
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                String selected = selectedValue(type);
                String previousField = selectedValue(field);
                String previousMode = selectedValue(mode);
                String[] fields = selected.equals("numeric") ? NUMERIC_FIELDS
                        : selected.equals("time") ? TIME_FIELDS : TEXT_FIELDS;
                String[] modes = selected.equals("numeric") ? NUMERIC_MODES
                        : selected.equals("time") ? new String[]{"eq"} : TEXT_MODES;
                configureSpinner(field, fields, previousField);
                configureSpinner(mode, modes, previousMode);
                second.setVisibility(selected.equals("numeric") ? View.VISIBLE : View.GONE);
            }
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? ja("条件を追加", "Add condition")
                        : ja("条件を編集", "Edit condition"))
                .setView(form)
                .setNegativeButton(ja("キャンセル", "Cancel"), null)
                .setPositiveButton(ja("追加", "Apply"), null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String typeValue = selectedValue(type);
                    String fieldValue = selectedValue(field);
                    String modeValue = selectedValue(mode);
                    String valueText = value.getText().toString().trim();
                    String secondText = second.getText().toString().trim();
                    if (!validCondition(typeValue, fieldValue, modeValue, valueText, secondText)) {
                        value.setError(ja("条件の値を確認してください", "Check condition value"));
                        return;
                    }
                    AdvancedNgRule.Condition updated = new AdvancedNgRule.Condition(typeValue,
                            fieldValue, modeValue, valueText, secondText, negate.isChecked());
                    if (index < 0) conditions.add(updated);
                    else conditions.set(index, updated);
                    renderConditions(container, conditions);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private boolean validCondition(String type, String field, String mode,
            String value, String second) {
        if (value.isEmpty() || value.length() > 2048) return false;
        if (type.equals("text")) {
            if ((mode.equals("regex") || mode.equals("regex_i"))) {
                try { Pattern.compile(value); } catch (PatternSyntaxException error) { return false; }
            }
            return true;
        }
        try {
            double number = Double.parseDouble(value);
            if (type.equals("numeric")) {
                return !Double.isNaN(number) && !Double.isInfinite(number) &&
                        (!mode.equals("between") ||
                        (!Double.isNaN(Double.parseDouble(second)) &&
                                !Double.isInfinite(Double.parseDouble(second)) &&
                                Double.parseDouble(second) >= number));
            }
            int integer = Integer.parseInt(value);
            if (field.equals("weekday")) return integer >= 0 && integer <= 6;
            if (field.equals("hour")) return integer >= 0 && integer <= 23;
            return field.equals("relativeMinutes") && integer > 0 && integer <= 10_080;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private boolean replace(AdvancedNgRule oldRule, AdvancedNgRule newRule) {
        List<AdvancedNgRule> updated = new ArrayList<>(rules);
        if (oldRule == null) updated.add(newRule);
        else {
            int index = updated.indexOf(oldRule);
            if (index < 0) return false;
            updated.set(index, newRule);
        }
        return persist(updated);
    }

    private void remove(AdvancedNgRule rule) {
        List<AdvancedNgRule> updated = new ArrayList<>(rules);
        updated.remove(rule);
        persist(updated);
    }

    private boolean persist(List<AdvancedNgRule> updated) {
        try {
            if (!AdvancedNgBridge.saveFromEditor(this, updated)) {
                toast(ja("保存に失敗しました", "Could not save"));
                return false;
            }
            rules.clear();
            rules.addAll(updated);
            renderRules();
            return true;
        } catch (JSONException error) {
            toast(ja("無効なルールです: ", "Invalid rule: ") + error.getMessage());
            return false;
        }
    }

    private LinearLayout labelled(String label, View child) {
        LinearLayout wrapper = vertical();
        wrapper.addView(text(label, 13, false), matchWrap());
        wrapper.addView(child, matchWrap());
        return wrapper;
    }

    private Spinner spinner(String[] values, String selected) {
        Spinner spinner = new Spinner(this);
        configureSpinner(spinner, values, selected);
        return spinner;
    }

    private void configureSpinner(Spinner spinner, String[] values, String selected) {
        String[] display = new String[values.length];
        for (int i = 0; i < values.length; i++) display[i] = optionLabel(values[i]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, display);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setTag(values);
        spinner.setAdapter(adapter);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) spinner.setSelection(i);
        }
    }

    private String selectedValue(Spinner spinner) {
        String[] values = (String[]) spinner.getTag();
        return values[Math.max(0, spinner.getSelectedItemPosition())];
    }

    private String optionLabel(String value) {
        if (!Locale.getDefault().getLanguage().equals("ja")) {
            switch (value) {
                case "text": return "Text";
                case "numeric": return "Number";
                case "time": return "Time";
                case "all": return "All fields";
                case "body": return "Body";
                case "threadTitle": return "Thread title";
                case "resNumber": return "Post number";
                case "lineCount": return "Line count";
                case "charCount": return "Character count";
                case "idCount": return "ID count";
                case "replyCount": return "References";
                case "repliedCount": return "Replies received";
                case "threadResCount": return "Thread posts";
                case "weekday": return "Weekday";
                case "hour": return "Hour";
                case "relativeMinutes": return "Within minutes";
                case "contains": return "Contains (all lines)";
                case "regex": return "Regular expression";
                case "regex_i": return "Regex, ignore case";
                case "fuzzy": return "Fuzzy";
                case "eq": return "Equals";
                case "gte": return "At least";
                case "lte": return "At most";
                case "gt": return "Greater than";
                case "lt": return "Less than";
                case "between": return "Between";
                default: return value;
            }
        }
        switch (value) {
            case "text": return "文字列";
            case "numeric": return "数値";
            case "time": return "時間";
            case "all": return "全体";
            case "name": return "名前";
            case "body": return "本文";
            case "mail": return "メール";
            case "id": return "ID";
            case "trip": return "トリップ";
            case "watchoi": return "ﾜｯﾁｮｲ";
            case "ip": return "IP";
            case "be": return "BE";
            case "url": return "URL";
            case "threadTitle": return "スレッド名";
            case "resNumber": return "レス番号";
            case "lineCount": return "改行数";
            case "charCount": return "文字数";
            case "idCount": return "同じIDのレス数";
            case "replyCount": return "参照数";
            case "repliedCount": return "被参照数";
            case "threadResCount": return "スレッドのレス数";
            case "weekday": return "投稿曜日";
            case "hour": return "投稿時刻";
            case "relativeMinutes": return "投稿からの経過分";
            case "contains": return "含む（各行すべて）";
            case "regex": return "正規表現";
            case "regex_i": return "正規表現・大小無視";
            case "fuzzy": return "あいまい一致";
            case "eq": return "等しい";
            case "gte": return "以上";
            case "lte": return "以下";
            case "gt": return "より大きい";
            case "lt": return "より小さい";
            case "between": return "範囲";
            default: return value;
        }
    }

    private EditText input(String hint, String value, boolean multiline) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setInputType(InputType.TYPE_CLASS_TEXT |
                (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        edit.setSingleLine(!multiline);
        return edit;
    }

    private CheckBox check(String label, boolean enabled) {
        CheckBox check = new CheckBox(this);
        check.setText(label);
        check.setChecked(enabled);
        return check;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        return button;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String ja(String japanese, String english) {
        return Locale.getDefault().getLanguage().equals("ja") ? japanese : english;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
