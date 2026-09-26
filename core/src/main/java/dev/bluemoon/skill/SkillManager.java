package dev.bluemoon.skill;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.skill.mechanics.BuiltinMechanics;
import dev.bluemoon.skill.parse.Params;
import dev.bluemoon.skill.parse.SkillLineParser;
import dev.bluemoon.skill.parse.SkillLineParser.Component;
import dev.bluemoon.skill.parse.SkillLineParser.ParsedCondition;
import dev.bluemoon.skill.parse.SkillLineParser.ParsedLine;
import dev.bluemoon.skill.parse.SkillParseException;
import dev.bluemoon.util.Yaml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Loads metaskills from {@code plugins/BlueMoon/skills} and compiles skill lines. */
public final class SkillManager {

    private final BlueMoonPlugin plugin;
    private final Map<String, Function<Params, Mechanic>> mechanics = new HashMap<>();
    private final Map<String, Function<Params, Targeter>> targeters = new HashMap<>();
    private final Map<String, Function<Params, Condition>> conditions = new HashMap<>();
    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<String, Skill> scriptCache = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final List<String> errors = new ArrayList<>();

    public SkillManager(BlueMoonPlugin plugin) {
        this.plugin = plugin;
        BuiltinMechanics.register(this);
        Targeters.register(this);
        Conditions.register(this);
    }

    public void registerMechanic(Function<Params, Mechanic> factory, String... names) {
        for (String name : names) {
            mechanics.put(name.toLowerCase(Locale.ROOT), factory);
        }
    }

    public void registerTargeter(Function<Params, Targeter> factory, String... names) {
        for (String name : names) {
            targeters.put(name.toLowerCase(Locale.ROOT), factory);
        }
    }

    public void registerCondition(Function<Params, Condition> factory, String... names) {
        for (String name : names) {
            conditions.put(name.toLowerCase(Locale.ROOT), factory);
        }
    }

    public Collection<String> mechanicNames() {
        return Collections.unmodifiableSet(mechanics.keySet());
    }

    public void load() {
        skills.clear();
        scriptCache.clear();
        errors.clear();
        File dir = new File(plugin.getDataFolder(), "skills");
        for (File file : Yaml.files(dir)) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                ConfigurationSection sec = yml.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                String where = file.getName() + " > " + key;
                if (skills.containsKey(key.toLowerCase(Locale.ROOT))) {
                    error(where, "같은 이름의 스킬이 이미 있습니다");
                    continue;
                }
                List<CompiledSkillLine> lines = compileAll(Yaml.stringList(sec, "Skills"), where);
                List<Condition.Entry> conds = compileConditions(Yaml.stringList(sec, "Conditions"), false, where);
                List<Condition.Entry> targetConds = compileConditions(Yaml.stringList(sec, "TargetConditions"), true, where);
                double cooldown = Yaml.getDouble(sec, "Cooldown", 0);
                skills.put(key.toLowerCase(Locale.ROOT), new Skill(key, lines, cooldown, conds, targetConds));
            }
        }
    }

    public Skill skill(String name) {
        return name == null ? null : skills.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<String> skillNames() {
        List<String> names = new ArrayList<>();
        skills.values().forEach(s -> names.add(s.name()));
        return names;
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public List<CompiledSkillLine> compileAll(List<String> raws, String where) {
        List<CompiledSkillLine> out = new ArrayList<>();
        for (String raw : raws) {
            CompiledSkillLine line = compile(raw, where);
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    /** Compiles one line; returns null and records an error when it is invalid. */
    public CompiledSkillLine compile(String raw, String where) {
        try {
            ParsedLine p = SkillLineParser.parse(raw);
            if (p.isDelay()) {
                return new CompiledSkillLine(p, null, null, null, List.of(), null, 0);
            }
            Function<Params, Mechanic> factory = mechanics.get(p.mechanic());
            if (factory == null) {
                throw new SkillParseException("알 수 없는 메카닉 '" + p.mechanic() + "'");
            }
            Mechanic mechanic = factory.apply(p.params());
            Targeter targeter = p.targeter() == null ? null : targeter(p.targeter());
            Targeter defaultTargeter = targeter(new Component(mechanic.defaultTargeter(), Params.EMPTY));
            List<Condition.Entry> conds = new ArrayList<>();
            for (ParsedCondition pc : p.conditions()) {
                conds.add(condition(pc));
            }
            SkillTrigger trigger = null;
            int interval = 0;
            if (p.trigger() != null) {
                trigger = SkillTrigger.parse(p.trigger());
                if (trigger == null) {
                    throw new SkillParseException("알 수 없는 트리거 '~" + p.trigger() + "'");
                }
                if (trigger == SkillTrigger.TIMER) {
                    try {
                        interval = Math.max(1, Integer.parseInt(p.triggerArg() == null ? "20" : p.triggerArg()));
                    } catch (NumberFormatException e) {
                        throw new SkillParseException("~onTimer 간격이 숫자가 아닙니다: " + p.triggerArg());
                    }
                }
            }
            return new CompiledSkillLine(p, mechanic, targeter, defaultTargeter, conds, trigger, interval);
        } catch (SkillParseException | IllegalArgumentException e) {
            error(where, e.getMessage() + "  →  " + raw);
            return null;
        }
    }

    public List<Condition.Entry> compileConditions(List<String> raws, boolean target, String where) {
        List<Condition.Entry> out = new ArrayList<>();
        for (String raw : raws) {
            try {
                out.add(condition(SkillLineParser.parseConditionEntry(raw, target)));
            } catch (SkillParseException | IllegalArgumentException e) {
                error(where, e.getMessage() + "  →  " + raw);
            }
        }
        return out;
    }

    /** Compiles a Blockbench timeline keyframe script (one skill line per row) into an anonymous skill. */
    public Skill compileScript(String script, String where) {
        return scriptCache.computeIfAbsent(script, s -> {
            List<String> rows = new ArrayList<>();
            for (String row : s.split("\\R")) {
                String r = row.trim();
                if (!r.isEmpty() && !r.startsWith("#") && !r.startsWith("//")) {
                    rows.add(r);
                }
            }
            return new Skill(where, compileAll(rows, where), 0, List.of(), List.of());
        });
    }

    private Targeter targeter(Component c) throws SkillParseException {
        Function<Params, Targeter> factory = targeters.get(c.name());
        if (factory == null) {
            throw new SkillParseException("알 수 없는 타겟터 '@" + c.name() + "'");
        }
        return factory.apply(c.params());
    }

    private Condition.Entry condition(ParsedCondition pc) throws SkillParseException {
        Function<Params, Condition> factory = conditions.get(pc.name());
        if (factory == null) {
            throw new SkillParseException("알 수 없는 조건 '" + pc.name() + "'");
        }
        return new Condition.Entry(pc.name(), factory.apply(pc.params()), pc.negate(), pc.targetCondition());
    }

    private void error(String where, String message) {
        String msg = where + ": " + message;
        errors.add(msg);
        plugin.getLogger().warning(msg);
    }

    // --- cooldowns ---------------------------------------------------------

    public boolean onCooldown(Entity caster, String skill) {
        Map<String, Long> map = cooldowns.get(caster.getUniqueId());
        if (map == null) {
            return false;
        }
        Long until = map.get(skill.toLowerCase(Locale.ROOT));
        return until != null && until > System.currentTimeMillis();
    }

    /** Seconds left on a cooldown, 0 if ready. */
    public double cooldownRemaining(Entity caster, String skill) {
        Map<String, Long> map = cooldowns.get(caster.getUniqueId());
        Long until = map == null ? null : map.get(skill.toLowerCase(Locale.ROOT));
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }

    public void setCooldown(Entity caster, String skill, double seconds) {
        cooldowns.computeIfAbsent(caster.getUniqueId(), k -> new HashMap<>())
                .put(skill.toLowerCase(Locale.ROOT), System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public void clearCooldowns(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
