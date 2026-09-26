package dev.bluemoon.skill;

import dev.bluemoon.BlueMoon;
import dev.bluemoon.mob.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

/** Everything a running skill knows: who casts it, what triggered it and inherited targets. */
public final class SkillMeta {

    public static final int MAX_DEPTH = 32;

    public final BlueMoon plugin;
    public final Entity caster;
    /** The custom mob casting the skill, or null for plain entities (e.g. players using /bm cast). */
    public final ActiveMob mob;
    public final Entity trigger;
    public final Location origin;
    public final List<Entity> inheritedEntities;
    public final List<Location> inheritedLocations;
    public final int depth;

    private SkillMeta(BlueMoon plugin, Entity caster, ActiveMob mob, Entity trigger, Location origin,
                      List<Entity> inheritedEntities, List<Location> inheritedLocations, int depth) {
        this.plugin = plugin;
        this.caster = caster;
        this.mob = mob;
        this.trigger = trigger;
        this.origin = origin;
        this.inheritedEntities = inheritedEntities;
        this.inheritedLocations = inheritedLocations;
        this.depth = depth;
    }

    public static SkillMeta create(BlueMoon plugin, Entity caster, ActiveMob mob, Entity trigger) {
        Location origin = mob != null ? mob.location() : caster.getLocation();
        return new SkillMeta(plugin, caster, mob, trigger, origin, List.of(), List.of(), 0);
    }

    public SkillMeta withTargets(List<Entity> entities, List<Location> locations) {
        return new SkillMeta(plugin, caster, mob, trigger, origin, List.copyOf(entities), List.copyOf(locations), depth + 1);
    }

    public SkillMeta withOrigin(Location newOrigin) {
        return new SkillMeta(plugin, caster, mob, trigger, newOrigin.clone(), inheritedEntities, inheritedLocations, depth);
    }

    public boolean hasInheritedTargets() {
        return !inheritedEntities.isEmpty() || !inheritedLocations.isEmpty();
    }

    public Location casterLocation() {
        return mob != null ? mob.location() : caster.getLocation();
    }

    /** False once the caster is gone; running skills stop at their next delay. */
    public boolean casterAlive() {
        if (mob != null) {
            return !mob.isRemoved();
        }
        return caster.isValid();
    }
}
