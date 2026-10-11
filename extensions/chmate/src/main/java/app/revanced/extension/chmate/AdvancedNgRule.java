package app.revanced.extension.chmate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class AdvancedNgRule {
    static final String STORAGE_KEY = "advanced_ng_rules_v1";

    final String id;
    final String label;
    final String scope;
    final boolean enabled;
    final boolean transparent;
    final boolean chain;
    final List<Condition> conditions;

    AdvancedNgRule(String id, String label, String scope, boolean enabled,
            boolean transparent, boolean chain, List<Condition> conditions) {
        this.id = id;
        this.label = label;
        this.scope = scope;
        this.enabled = enabled;
        this.transparent = transparent;
        this.chain = chain;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
    }

    static AdvancedNgRule create(String label, String scope, boolean transparent,
            boolean chain, List<Condition> conditions) {
        return new AdvancedNgRule(UUID.randomUUID().toString(), label, scope,
                true, transparent, chain, conditions);
    }

    AdvancedNgRule withEnabled(boolean value) {
        return new AdvancedNgRule(id, label, scope, value, transparent, chain, conditions);
    }

    JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("id", id);
        result.put("label", label);
        result.put("scope", scope);
        result.put("enabled", enabled);
        result.put("transparent", transparent);
        result.put("chain", chain);
        JSONArray items = new JSONArray();
        for (Condition condition : conditions) items.put(condition.toJson());
        result.put("conditions", items);
        return result;
    }

    static AdvancedNgRule fromJson(JSONObject object) throws JSONException {
        JSONArray items = object.getJSONArray("conditions");
        List<Condition> conditions = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            conditions.add(Condition.fromJson(items.getJSONObject(i)));
        }
        if (conditions.isEmpty() || conditions.size() > 16) {
            throw new JSONException("Advanced NG rule must have 1-16 conditions");
        }
        return new AdvancedNgRule(object.getString("id"), object.optString("label", ""),
                object.optString("scope", ""), object.optBoolean("enabled", true),
                object.optBoolean("transparent", true), object.optBoolean("chain", false),
                conditions);
    }

    static String serialize(List<AdvancedNgRule> rules) throws JSONException {
        JSONArray array = new JSONArray();
        for (AdvancedNgRule rule : rules) array.put(rule.toJson());
        return array.toString();
    }

    static List<AdvancedNgRule> deserialize(String value) throws JSONException {
        List<AdvancedNgRule> result = new ArrayList<>();
        if (value == null || value.isEmpty()) return result;
        JSONArray array = new JSONArray(value);
        if (array.length() > 500) throw new JSONException("Too many advanced NG rules");
        for (int i = 0; i < array.length(); i++) {
            result.add(fromJson(array.getJSONObject(i)));
        }
        return result;
    }

    static final class Condition {
        final String type;
        final String field;
        final String mode;
        final String value;
        final String second;
        final boolean negate;

        Condition(String type, String field, String mode, String value,
                String second, boolean negate) {
            this.type = type;
            this.field = field;
            this.mode = mode;
            this.value = value;
            this.second = second;
            this.negate = negate;
        }

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("type", type);
            object.put("field", field);
            object.put("mode", mode);
            object.put("value", value);
            object.put("second", second);
            object.put("negate", negate);
            return object;
        }

        static Condition fromJson(JSONObject object) throws JSONException {
            String type = object.getString("type");
            String field = object.getString("field");
            String mode = object.getString("mode");
            String value = object.getString("value");
            if (value.length() > 2048 || !type.matches("text|numeric|time")) {
                throw new JSONException("Invalid NG condition");
            }
            return new Condition(type, field, mode, value,
                    object.optString("second", ""), object.optBoolean("negate", false));
        }
    }
}
