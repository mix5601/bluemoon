package dev.bluemoon.skills;

import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.util.Yaml;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Static held-item models from {@code weapons/*.bbmodel}. */
public final class WeaponManager {

    private final BlueMoonSkills plugin;
    private final Map<String, BBModel> weapons = new LinkedHashMap<>();
    private final NamespacedKey weaponKey;

    WeaponManager(BlueMoonSkills plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "weapon");
    }

    void load() {
        weapons.clear();
        List<File> files = new ArrayList<>();
        Yaml.collect(new File(plugin.getDataFolder(), "weapons"), ".bbmodel", files);
        for (File file : files) {
            String id = ModelBlueprint.sanitize(file.getName().replaceFirst("(?i)\\.bbmodel$", ""));
            try {
                BBModel model = BBModelParser.parse(file.toPath());
                model.warnings.forEach(w -> plugin.models().warn(file.getName() + ": " + w));
                weapons.put(id, model);
            } catch (IOException | RuntimeException e) {
                plugin.models().warn(file.getName() + ": 무기 모델 읽기 실패 - " + e.getMessage());
            }
        }
    }

    void contribute(PackGenerator generator) {
        for (Map.Entry<String, BBModel> e : weapons.entrySet()) {
            List<String> warnings = new ArrayList<>();
            generator.addWeapon(e.getKey(), e.getValue(), warnings);
            warnings.forEach(w -> plugin.models().warn("weapons/" + e.getKey() + ": " + w));
        }
    }

    public Collection<String> ids() {
        return Collections.unmodifiableSet(weapons.keySet());
    }

    public boolean exists(String id) {
        return id != null && weapons.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** Item model id to put on any item, e.g. {@code bmskills:weapon/flame_sword}. */
    public NamespacedKey modelKey(String id) {
        return new NamespacedKey(plugin.namespace(), "weapon/" + id.toLowerCase(Locale.ROOT));
    }

    /** Key of the string tag holding the weapon id on items created by {@link #item}. */
    public NamespacedKey weaponTag() {
        return weaponKey;
    }

    /** An item of {@code base} material that looks like the weapon and is tagged with its id. */
    public ItemStack item(String id, Material base) {
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(modelKey(id));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, id.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        return item;
    }

    /** The weapon id stored on an item, or null. */
    public String weaponOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
    }
}
