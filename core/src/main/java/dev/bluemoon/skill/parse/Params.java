package dev.bluemoon.skill.parse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Key/value options of a mechanic, targeter or condition, e.g. {@code damage{amount=5;ia=true}}.
 * Keys are case-insensitive and every getter accepts several aliases.
 */
public final class Params {

    public static final Params EMPTY = new Params(Map.of());

    private final Map<String, String> values;

    public Params(Map<String, String> values) {
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((k, v) -> copy.put(k.toLowerCase(Locale.ROOT), v));
        this.values = Collections.unmodifiableMap(copy);
    }

    public boolean has(String... keys) {
        for (String key : keys) {
            if (values.containsKey(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public String getString(String def, String... keys) {
        for (String key : keys) {
            String value = values.get(key.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        return def;
    }

    public double getDouble(double def, String... keys) {
        String raw = getString(null, keys);
        if (raw == null) {
            return def;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public float getFloat(float def, String... keys) {
        return (float) getDouble(def, keys);
    }

    public int getInt(int def, String... keys) {
        return (int) Math.round(getDouble(def, keys));
    }

    public boolean getBoolean(boolean def, String... keys) {
        String raw = getString(null, keys);
        if (raw == null) {
            return def;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> def;
        };
    }

    public Map<String, String> asMap() {
        return values;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
