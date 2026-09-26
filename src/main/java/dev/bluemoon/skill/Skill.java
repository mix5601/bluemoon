package dev.bluemoon.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** A metaskill: an ordered list of skill lines with optional cooldown, conditions and delays. */
public final class Skill {

    private final String name;
    private final List<CompiledSkillLine> lines;
    private final double cooldownSeconds;
    private final List<Condition.Entry> conditions;
    private final List<Condition.Entry> targetConditions;

    public Skill(String name, List<CompiledSkillLine> lines, double cooldownSeconds,
                 List<Condition.Entry> conditions, List<Condition.Entry> targetConditions) {
        this.name = name;
        this.lines = List.copyOf(lines);
        this.cooldownSeconds = cooldownSeconds;
        this.conditions = List.copyOf(conditions);
        this.targetConditions = List.copyOf(targetConditions);
    }

    public String name() {
        return name;
    }

    public List<CompiledSkillLine> lines() {
        return lines;
    }

    /** @return false when blocked by cooldown or conditions */
    public boolean execute(SkillMeta meta) {
        if (meta.depth > SkillMeta.MAX_DEPTH) {
            return false;
        }
        SkillManager manager = meta.plugin.skills();
        if (cooldownSeconds > 0 && manager.onCooldown(meta.caster, name)) {
            return false;
        }
        for (Condition.Entry c : conditions) {
            if (!c.check(meta, meta.caster)) {
                return false;
            }
        }
        SkillMeta run = meta;
        if (!targetConditions.isEmpty() && meta.hasInheritedTargets()) {
            List<Entity> passed = new ArrayList<>();
            for (Entity e : meta.inheritedEntities) {
                if (targetConditions.stream().allMatch(c -> c.check(meta, e))) {
                    passed.add(e);
                }
            }
            List<Location> locations = meta.inheritedLocations;
            if (passed.isEmpty() && locations.isEmpty()) {
                return false;
            }
            run = meta.withTargets(passed, locations);
        }
        if (cooldownSeconds > 0) {
            manager.setCooldown(meta.caster, name, cooldownSeconds);
        }
        run(run, 0);
        return true;
    }

    private void run(SkillMeta meta, int start) {
        for (int i = start; i < lines.size(); i++) {
            CompiledSkillLine line = lines.get(i);
            if (line.isDelay()) {
                if (line.delayTicks() <= 0) {
                    continue;
                }
                int next = i + 1;
                Bukkit.getScheduler().runTaskLater(meta.plugin, () -> {
                    if (meta.casterAlive() && meta.plugin.isEnabled()) {
                        run(meta, next);
                    }
                }, line.delayTicks());
                return;
            }
            line.execute(meta);
        }
    }
}
