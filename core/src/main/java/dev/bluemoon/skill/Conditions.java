package dev.bluemoon.skill;

import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.skill.parse.Params;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Built-in conditions. {@code ?cond} checks the caster, {@code ?~cond} checks each target. */
final class Conditions {

    private Conditions() {
    }

    static void register(SkillRegistry m) {
        m.registerCondition(p -> {
            double c = p.getDouble(0.5, "chance", "c");
            return (meta, e) -> ThreadLocalRandom.current().nextDouble() < c;
        }, "chance");
        m.registerCondition(p -> {
            double min = p.getDouble(0, "min");
            double max = p.getDouble(1, "max");
            return (meta, e) -> {
                if (!(e instanceof LivingEntity le)) {
                    return false;
                }
                double pct = le.getHealth() / maxHealth(le);
                return pct >= min && pct <= max;
            };
        }, "healthpercent", "hp");
        m.registerCondition(p -> {
            double min = p.getDouble(0, "min");
            double max = p.getDouble(Double.MAX_VALUE, "max");
            return (meta, e) -> e instanceof LivingEntity le && le.getHealth() >= min && le.getHealth() <= max;
        }, "health");
        m.registerCondition(p -> (meta, e) -> Targeters.currentTarget(e) != null, "hastarget");
        m.registerCondition(p -> (meta, e) -> e instanceof Player, "isplayer");
        m.registerCondition(p -> (meta, e) -> e.isOnGround(), "onground");
        m.registerCondition(p -> {
            double min = p.getDouble(0, "min");
            double max = p.getDouble(Double.MAX_VALUE, "max");
            return (meta, e) -> {
                if (!e.getWorld().equals(meta.casterLocation().getWorld())) {
                    return false;
                }
                double d = e.getLocation().distance(meta.casterLocation());
                return d >= min && d <= max;
            };
        }, "distance");
        m.registerCondition(p -> (meta, e) -> {
            long t = e.getWorld().getTime();
            return t < 12300 || t > 23850;
        }, "day");
        m.registerCondition(p -> (meta, e) -> {
            long t = e.getWorld().getTime();
            return t >= 12300 && t <= 23850;
        }, "night");
        m.registerCondition(p -> (meta, e) -> e.getWorld().hasStorm(), "raining");
        m.registerCondition(p -> {
            Set<String> types = set(p.getString("", "types", "type", "t"));
            return (meta, e) -> types.contains(e.getType().name().toLowerCase(Locale.ROOT));
        }, "entitytype");
        m.registerCondition(p -> {
            Set<String> types = set(p.getString("", "types", "type", "t"));
            return (meta, e) -> {
                ActiveMob am = meta.plugin.mobs().get(e);
                return am != null && (types.isEmpty() || types.contains(am.type().id().toLowerCase(Locale.ROOT)));
            };
        }, "mobtype");
        m.registerCondition(p -> {
            String anim = p.getString("", "animation", "anim", "a");
            return (meta, e) -> {
                ActiveMob am = meta.plugin.mobs().get(e);
                return am != null && am.model() != null && am.model().isPlaying(anim);
            };
        }, "playinganimation", "isanimating");
        m.registerCondition(p -> (meta, e) -> meta.caster instanceof LivingEntity le && le.hasLineOfSight(e),
                "lineofsight", "los");
        m.registerCondition(p -> (meta, e) -> undead(e.getType()), "undead", "isundead");
        m.registerCondition(p -> {
            double angle = p.getDouble(90, "angle", "a");
            return (meta, e) -> behind(meta.casterLocation(), e.getLocation(), angle);
        }, "behind", "backstab");
    }

    private static Tag<EntityType> undeadTag;

    static boolean undead(EntityType type) {
        if (undeadTag == null) {
            undeadTag = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, NamespacedKey.minecraft("undead"), EntityType.class);
        }
        return undeadTag != null && undeadTag.isTagged(type);
    }

    /**
     * True when {@code subject} faces away from {@code attacker}: the angle between the subject's
     * facing and the direction to the attacker is larger than {@code 180 - angle / 2}.
     */
    static boolean behind(Location attacker, Location subject, double angle) {
        Vector facing = subject.getDirection().setY(0);
        Vector toAttacker = attacker.toVector().subtract(subject.toVector()).setY(0);
        if (facing.lengthSquared() < 1e-6 || toAttacker.lengthSquared() < 1e-6) {
            return false;
        }
        double between = Math.toDegrees(facing.angle(toAttacker));
        return between >= 180 - angle / 2;
    }

    static double maxHealth(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20 : inst.getValue();
    }

    private static Set<String> set(String raw) {
        if (raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
