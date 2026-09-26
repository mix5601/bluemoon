package dev.bluemoon.skills;

import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.skill.CompiledSkillLine;
import dev.bluemoon.skill.SkillTrigger;
import dev.bluemoon.util.Text;
import dev.bluemoon.util.Yaml;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Weapons: static held-item models from {@code weapons/*.bbmodel} and weapon definitions from
 * {@code weapons/*.yml} (name, lore, stats, automatic skills).
 */
public final class WeaponManager {

    private static final Set<SkillTrigger> WEAPON_TRIGGERS = EnumSet.of(
            SkillTrigger.COMBAT, SkillTrigger.ATTACK, SkillTrigger.DAMAGED, SkillTrigger.KILL, SkillTrigger.TIMER);

    private final BlueMoonSkills plugin;
    private final Map<String, BBModel> models = new LinkedHashMap<>();
    private final Map<String, WeaponType> types = new LinkedHashMap<>();
    private final NamespacedKey weaponKey;

    WeaponManager(BlueMoonSkills plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "weapon");
    }

    void load() {
        models.clear();
        types.clear();
        File dir = new File(plugin.getDataFolder(), "weapons");
        List<File> files = new ArrayList<>();
        Yaml.collect(dir, ".bbmodel", files);
        for (File file : files) {
            String id = ModelBlueprint.sanitize(file.getName().replaceFirst("(?i)\\.bbmodel$", ""));
            try {
                BBModel model = BBModelParser.parse(file.toPath());
                model.warnings.forEach(w -> plugin.models().warn(file.getName() + ": " + w));
                models.put(id, model);
            } catch (IOException | RuntimeException e) {
                plugin.models().warn(file.getName() + ": 무기 모델 읽기 실패 - " + e.getMessage());
            }
        }
        for (File file : Yaml.files(dir)) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                ConfigurationSection sec = yml.getConfigurationSection(key);
                if (sec != null) {
                    WeaponType type = load(key.toLowerCase(Locale.ROOT), sec, file.getName() + " > " + key);
                    types.put(type.id, type);
                }
            }
        }
        // a model without a definition is still a usable weapon
        for (String id : models.keySet()) {
            types.computeIfAbsent(id, WeaponType::new);
        }
    }

    private WeaponType load(String id, ConfigurationSection sec, String where) {
        WeaponType t = new WeaponType(id);
        t.model = Yaml.getString(sec, "Model", id).toLowerCase(Locale.ROOT);
        if (!models.containsKey(t.model)) {
            plugin.models().warn(where + ": 무기 모델 '" + t.model + "' 이 weapons 폴더에 없습니다");
        }
        Material m = Material.matchMaterial(Yaml.getString(sec, "Material", "NETHERITE_SWORD"));
        if (m != null && m.isItem()) {
            t.material = m;
        } else {
            plugin.models().warn(where + ": Material 이 올바르지 않아 NETHERITE_SWORD 를 씁니다");
        }
        t.display = Yaml.getString(sec, "Display", null);
        t.lore.addAll(Yaml.stringList(sec, "Lore"));
        ConfigurationSection stats = Yaml.section(sec, "Attributes");
        if (stats != null) {
            if (Yaml.key(stats, "AttackDamage") != null) {
                t.attackDamage = Yaml.getDouble(stats, "AttackDamage", 1);
            }
            if (Yaml.key(stats, "AttackSpeed") != null) {
                t.attackSpeed = Yaml.getDouble(stats, "AttackSpeed", 4);
            }
        }
        for (CompiledSkillLine line : plugin.skills().compileAll(Yaml.stringList(sec, "Skills"), where)) {
            SkillTrigger trigger = line.trigger() == null ? SkillTrigger.ATTACK : line.trigger();
            if (!WEAPON_TRIGGERS.contains(trigger)) {
                plugin.models().warn(where + ": 무기 스킬은 ~onAttack, ~onDamaged, ~onCombat, ~onKill, ~onTimer 만 쓸 수 있습니다: "
                        + line.raw());
                continue;
            }
            if (trigger == SkillTrigger.TIMER) {
                t.timers.add(line);
            } else {
                t.lines.computeIfAbsent(trigger, k -> new ArrayList<>()).add(line);
            }
        }
        return t;
    }

    void contribute(PackGenerator generator) {
        for (Map.Entry<String, BBModel> e : models.entrySet()) {
            List<String> warnings = new ArrayList<>();
            generator.addWeapon(e.getKey(), e.getValue(), warnings);
            warnings.forEach(w -> plugin.models().warn("weapons/" + e.getKey() + ": " + w));
        }
    }

    public Collection<String> ids() {
        return Collections.unmodifiableSet(types.keySet());
    }

    public boolean exists(String id) {
        return id != null && types.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public WeaponType type(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    /** Item model id of a weapon model, e.g. {@code bmskills:weapon/star_greatsword}. */
    public NamespacedKey modelKey(String model) {
        return new NamespacedKey(plugin.namespace(), "weapon/" + model.toLowerCase(Locale.ROOT));
    }

    /** Key of the string tag holding the weapon id on weapon items. */
    public NamespacedKey weaponTag() {
        return weaponKey;
    }

    /** The weapon item as defined in its yml (material, name, lore, stats). */
    public ItemStack item(String id) {
        WeaponType type = type(id);
        return item(id, type == null ? Material.NETHERITE_SWORD : type.material);
    }

    /** The weapon item on a custom base material. */
    public ItemStack item(String id, Material base) {
        WeaponType type = type(id);
        String weaponId = id.toLowerCase(Locale.ROOT);
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(modelKey(type == null ? weaponId : type.model));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, weaponId);
        if (type != null) {
            if (type.display != null) {
                meta.displayName(plain(type.display));
            }
            if (!type.lore.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                type.lore.forEach(line -> lore.add(plain(line)));
                meta.lore(lore);
            }
            if (type.attackDamage != null) {
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        new NamespacedKey(plugin, "weapon_damage"), type.attackDamage - 1,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            }
            if (type.attackSpeed != null) {
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        new NamespacedKey(plugin, "weapon_speed"), type.attackSpeed - 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Component plain(String legacy) {
        return Text.color(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** The weapon id stored on an item, or null. */
    public String weaponOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
    }
}
