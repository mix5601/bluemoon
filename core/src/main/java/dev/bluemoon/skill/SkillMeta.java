package dev.bluemoon.skill;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.model.runtime.ModelHost;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

/** Everything a running skill knows: who casts it, what triggered it and inherited targets. */
public final class SkillMeta {

    public static final int MAX_DEPTH = 32;

    public final BlueMoonPlugin plugin;
    public final Entity caster;
    /** The custom mob casting the skill, or null for plain entities (e.g. players using /bm cast). */
    public final ActiveMob mob;
    /** Model used by {@code @ModelPart}: the casting mob, or the effect whose timeline runs the skill. */
    public final ModelHost host;
    public final Entity trigger;
    public final Location origin;
    public final List<Entity> inheritedEntities;
    public final List<Location> inheritedLocations;
    public final int depth;

    private SkillMeta(BlueMoonPlugin plugin, Entity caster, ActiveMob mob, ModelHost host, Entity trigger, Location origin,
                      List<Entity> inheritedEntities, List<Location> inheritedLocations, int depth) {
        this.plugin = plugin;
        this.caster = caster;
        this.mob = mob;
        this.host = host;
        this.trigger = trigger;
        this.origin = origin;
        this.inheritedEntities = inheritedEntities;
        this.inheritedLocations = inheritedLocations;
        this.depth = depth;
    }

    public static SkillMeta create(BlueMoonPlugin plugin, Entity caster, ActiveMob mob, Entity trigger) {
        Location origin = mob != null ? mob.location() : caster.getLocation();
        return new SkillMeta(plugin, caster, mob, mob, trigger, origin, List.of(), List.of(), 0);
    }

    /** Skill started by a model other than the caster's own (effect timeline keyframes). */
    public SkillMeta withHost(ModelHost newHost) {
        return new SkillMeta(plugin, caster, mob, newHost, trigger, origin, inheritedEntities, inheritedLocations, depth);
    }

    public SkillMeta withTargets(List<Entity> entities, List<Location> locations) {
        return new SkillMeta(plugin, caster, mob, host, trigger, origin, List.copyOf(entities), List.copyOf(locations), depth + 1);
    }

    public SkillMeta withOrigin(Location newOrigin) {
        return new SkillMeta(plugin, caster, mob, host, trigger, newOrigin.clone(), inheritedEntities, inheritedLocations, depth);
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
