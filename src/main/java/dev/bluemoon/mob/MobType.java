package dev.bluemoon.mob;

import dev.bluemoon.BlueMoon;
import dev.bluemoon.skill.CompiledSkillLine;
import dev.bluemoon.skill.SkillTrigger;
import dev.bluemoon.util.Yaml;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A mob definition from {@code plugins/BlueMoon/mobs/*.yml} (MythicMobs style keys). */
public final class MobType {

    public record Drop(Material material, int min, int max, double chance) {
    }

    private final String id;
    private EntityType entityType = EntityType.ZOMBIE;
    private String display;
    private double health = 20;
    private Double damage;
    private Double armor;
    private Double movementSpeed;
    private Double knockbackResistance;
    private Double followRange;
    private Double hitboxScale;
    private String modelId;
    private float modelScale = 1f;
    private final Map<String, String> animations = new HashMap<>();
    private boolean silent = true;
    private boolean preventSunburn = true;
    private boolean despawn = false;
    private boolean showNameplate = false;
    private boolean preventDrops = false;
    private final List<Drop> drops = new ArrayList<>();
    private int exp = -1;
    private final Map<SkillTrigger, List<CompiledSkillLine>> triggers = new EnumMap<>(SkillTrigger.class);
    private final List<CompiledSkillLine> timers = new ArrayList<>();

    private MobType(String id) {
        this.id = id;
    }

    public static MobType load(BlueMoon plugin, String id, ConfigurationSection sec, String file) {
        MobType t = new MobType(id);
        String where = file + " > " + id;

        String typeName = Yaml.getString(sec, "Type", "ZOMBIE").toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(typeName);
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null || type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())
                || !type.isSpawnable()) {
            plugin.getLogger().warning(where + ": Type '" + typeName + "' 은 소환 가능한 생물이 아닙니다. ZOMBIE 를 사용합니다.");
        } else {
            t.entityType = type;
        }
        t.display = Yaml.getString(sec, "Display", null);
        t.health = Yaml.getDouble(sec, "Health", 20);
        t.damage = optional(sec, "Damage");
        t.armor = optional(sec, "Armor");
        t.movementSpeed = optional(sec, "MovementSpeed");
        t.knockbackResistance = optional(sec, "KnockbackResistance");
        t.followRange = optional(sec, "FollowRange");
        t.hitboxScale = optional(sec, "HitboxScale");

        ConfigurationSection model = Yaml.section(sec, "Model");
        if (model != null) {
            t.modelId = Yaml.getString(model, "Id", null);
            t.modelScale = (float) Yaml.getDouble(model, "Scale", 1);
        } else {
            t.modelId = Yaml.getString(sec, "Model", null);
        }
        if (t.modelId != null && plugin.models().get(t.modelId) == null) {
            plugin.getLogger().warning(where + ": 모델 '" + t.modelId + "' 을 찾을 수 없습니다 (models 폴더의 .bbmodel 파일 이름)");
        }

        ConfigurationSection anims = Yaml.section(sec, "Animations");
        if (anims != null) {
            for (String state : anims.getKeys(false)) {
                t.animations.put(state.toLowerCase(Locale.ROOT), anims.getString(state));
            }
        }

        ConfigurationSection options = Yaml.section(sec, "Options");
        if (options != null) {
            t.silent = Yaml.getBoolean(options, "Silent", t.modelId != null);
            t.preventSunburn = Yaml.getBoolean(options, "PreventSunburn", true);
            t.despawn = Yaml.getBoolean(options, "Despawn", false);
            t.showNameplate = Yaml.getBoolean(options, "ShowNameplate", false);
            t.preventDrops = Yaml.getBoolean(options, "PreventOtherDrops", false);
        } else {
            t.silent = t.modelId != null;
        }

        for (String raw : Yaml.stringList(sec, "Drops")) {
            String[] parts = raw.trim().split("\\s+");
            if (parts[0].equalsIgnoreCase("exp")) {
                t.exp = parts.length > 1 ? parseInt(parts[1], 0) : 0;
                continue;
            }
            Material m = Material.matchMaterial(parts[0]);
            if (m == null || !m.isItem()) {
                plugin.getLogger().warning(where + ": 알 수 없는 드롭 아이템 '" + parts[0] + "'");
                continue;
            }
            int min = 1;
            int max = 1;
            if (parts.length > 1) {
                String[] range = parts[1].split("-");
                min = parseInt(range[0], 1);
                max = range.length > 1 ? parseInt(range[1], min) : min;
            }
            double chance = parts.length > 2 ? parseDouble(parts[2], 1) : 1;
            t.drops.add(new Drop(m, Math.max(0, min), Math.max(min, max), chance));
        }

        for (CompiledSkillLine line : plugin.skills().compileAll(Yaml.stringList(sec, "Skills"), where)) {
            SkillTrigger trigger = line.trigger() == null ? SkillTrigger.COMBAT : line.trigger();
            if (trigger == SkillTrigger.TIMER) {
                t.timers.add(line);
            } else {
                t.triggers.computeIfAbsent(trigger, k -> new ArrayList<>()).add(line);
            }
        }
        return t;
    }

    private static Double optional(ConfigurationSection sec, String name) {
        String k = Yaml.key(sec, name);
        return k == null ? null : sec.getDouble(k);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String id() {
        return id;
    }

    public EntityType entityType() {
        return entityType;
    }

    public String display() {
        return display;
    }

    public double health() {
        return health;
    }

    public Double damage() {
        return damage;
    }

    public Double armor() {
        return armor;
    }

    public Double movementSpeed() {
        return movementSpeed;
    }

    public Double knockbackResistance() {
        return knockbackResistance;
    }

    public Double followRange() {
        return followRange;
    }

    public Double hitboxScale() {
        return hitboxScale;
    }

    public String modelId() {
        return modelId;
    }

    public float modelScale() {
        return modelScale;
    }

    /** Animation name for a state such as idle, walk, attack, hurt, death, spawn. */
    public String animation(String state) {
        return animations.getOrDefault(state, state);
    }

    public boolean silent() {
        return silent;
    }

    public boolean preventSunburn() {
        return preventSunburn;
    }

    public boolean despawn() {
        return despawn;
    }

    public boolean showNameplate() {
        return showNameplate;
    }

    public boolean preventDrops() {
        return preventDrops;
    }

    public List<Drop> drops() {
        return drops;
    }

    public int exp() {
        return exp;
    }

    public List<CompiledSkillLine> lines(SkillTrigger trigger) {
        return triggers.getOrDefault(trigger, List.of());
    }

    public List<CompiledSkillLine> timers() {
        return timers;
    }
}
