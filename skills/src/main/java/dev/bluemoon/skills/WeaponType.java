package dev.bluemoon.skills;

import dev.bluemoon.skill.CompiledSkillLine;
import dev.bluemoon.skill.SkillTrigger;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A weapon from {@code weapons/*.yml}: item look (Blockbench model), name, lore, stats and
 * automatic skills that run while it is held ({@code ~onAttack}, {@code ~onDamaged},
 * {@code ~onKill}, {@code ~onTimer:N}).
 */
public final class WeaponType {

    final String id;
    String model;
    Material material = Material.NETHERITE_SWORD;
    String display;
    final List<String> lore = new ArrayList<>();
    Double attackDamage;
    Double attackSpeed;
    final Map<SkillTrigger, List<CompiledSkillLine>> lines = new EnumMap<>(SkillTrigger.class);
    final List<CompiledSkillLine> timers = new ArrayList<>();

    WeaponType(String id) {
        this.id = id;
        this.model = id;
    }

    public String id() {
        return id;
    }

    public String model() {
        return model;
    }

    public Material material() {
        return material;
    }

    List<CompiledSkillLine> lines(SkillTrigger trigger) {
        return lines.getOrDefault(trigger, List.of());
    }
}
