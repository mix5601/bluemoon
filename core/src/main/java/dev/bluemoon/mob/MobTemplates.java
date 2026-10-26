package dev.bluemoon.mob;

import dev.bluemoon.util.Yaml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * MythicMobs style {@code Template:} inheritance for mob definitions.
 *
 * <ul>
 *   <li>{@code Template: A} or {@code Template: A,B} — later templates and the mob itself win</li>
 *   <li>plain values are overridden, sections ({@code Options}, {@code BossBar}, ...) are merged key by key</li>
 *   <li>{@code Skills} and {@code Drops} are appended to the template's lists</li>
 *   <li>{@code Template} and {@code Abstract} themselves are never inherited</li>
 * </ul>
 */
final class MobTemplates {

    /** Top level lists that are appended instead of replaced. */
    private static final Set<String> APPENDED = Set.of("skills", "drops");
    private static final Set<String> NOT_INHERITED = Set.of("template", "abstract");
    /** Keys are never split into paths, so any character that cannot appear in a key works. */
    private static final char SEPARATOR = '\u0001';

    private final Map<String, ConfigurationSection> raw;
    private final Consumer<String> warn;
    private final Map<String, ConfigurationSection> resolved = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();

    /**
     * @param raw  mob definitions as written, keyed by lower case id
     * @param warn receives problems such as missing templates or loops
     */
    MobTemplates(Map<String, ConfigurationSection> raw, Consumer<String> warn) {
        this.raw = raw;
        this.warn = warn;
    }

    /** True for definitions that only exist to be inherited from. */
    static boolean isAbstract(ConfigurationSection sec) {
        return Yaml.getBoolean(sec, "Abstract", false);
    }

    /** The definition with all its templates applied, or null when there is no such mob. */
    ConfigurationSection resolve(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        ConfigurationSection done = resolved.get(key);
        if (done != null) {
            return done;
        }
        ConfigurationSection own = raw.get(key);
        if (own == null) {
            return null;
        }
        if (!resolving.add(key)) {
            warn.accept(own.getName() + ": 템플릿이 자기 자신을 다시 상속합니다 (순환 참조)");
            return null;
        }
        MemoryConfiguration out = new MemoryConfiguration();
        out.options().pathSeparator(SEPARATOR);
        String templates = Yaml.getString(own, "Template", null);
        if (templates != null) {
            for (String name : templates.split(",")) {
                name = name.trim();
                if (name.isEmpty()) {
                    continue;
                }
                ConfigurationSection parent = resolve(name);
                if (parent == null) {
                    if (!raw.containsKey(name.toLowerCase(Locale.ROOT))) {
                        warn.accept(own.getName() + ": 템플릿 '" + name + "' 을 찾을 수 없습니다");
                    }
                    continue;
                }
                merge(out, parent, true);
            }
        }
        merge(out, own, true);
        resolving.remove(key);
        resolved.put(key, out);
        return out;
    }

    private static void merge(ConfigurationSection into, ConfigurationSection from, boolean top) {
        for (String key : from.getKeys(false)) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (top && NOT_INHERITED.contains(lower)) {
                continue;
            }
            Object value = from.get(key);
            String existing = Yaml.key(into, key);
            if (value instanceof ConfigurationSection child) {
                ConfigurationSection target = existing == null ? null : into.getConfigurationSection(existing);
                if (target == null) {
                    if (existing != null) {
                        into.set(existing, null);
                    }
                    target = into.createSection(key);
                }
                merge(target, child, false);
            } else if (top && APPENDED.contains(lower) && value instanceof List<?> list
                    && existing != null && into.get(existing) instanceof List<?> old) {
                List<Object> joined = new ArrayList<>(old);
                joined.addAll(list);
                into.set(existing, joined);
            } else {
                if (existing != null && !existing.equals(key)) {
                    into.set(existing, null);
                }
                into.set(key, value instanceof List<?> copy ? new ArrayList<>(copy) : value);
            }
        }
    }
}
