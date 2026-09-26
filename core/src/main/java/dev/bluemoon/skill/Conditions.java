package dev.bluemoon.skill;

import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.skill.parse.Params;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Built-in conditions. {@code ?cond} checks the caster, {@code ?~cond} checks each target. */
final class Conditions {

    private Conditions() {
    }

    static void register(SkillManager m) {
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
