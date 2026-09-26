package dev.bluemoon.skill;

import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.skill.parse.Params;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Built-in targeters ({@code @Self}, {@code @PlayersInRadius{r=5}}, {@code @ModelPart{bone=hand}} ...). */
final class Targeters {

    private Targeters() {
    }

    static void register(SkillManager m) {
        m.registerTargeter(p -> meta -> Targets.of(meta.caster), "self", "caster", "boss", "mob", "me");
        m.registerTargeter(p -> meta -> Targets.of(currentTarget(meta.caster)), "target", "t");
        m.registerTargeter(p -> meta -> Targets.of(meta.trigger), "trigger");

        m.registerTargeter(p -> {
            double r = radius(p, 10);
            return meta -> Targets.entities(nearby(meta.casterLocation(), r, meta, true));
        }, "playersinradius", "pir");
        m.registerTargeter(p -> {
            double r = radius(p, 10);
            return meta -> Targets.entities(nearby(meta.casterLocation(), r, meta, false));
        }, "entitiesinradius", "eir", "livingentitiesinradius", "leir", "livinginradius");
        m.registerTargeter(p -> {
            double r = radius(p, 10);
            Set<String> types = typeSet(p);
            return meta -> {
                List<Entity> out = new ArrayList<>();
                for (Entity e : nearby(meta.casterLocation(), r, meta, false)) {
                    ActiveMob am = meta.plugin.mobs().get(e);
                    if (am != null && (types.isEmpty() || types.contains(am.type().id().toLowerCase(Locale.ROOT)))) {
                        out.add(e);
                    }
                }
                return Targets.entities(out);
            };
        }, "mobsinradius", "mir");
        m.registerTargeter(p -> {
            double r = radius(p, 32);
            return meta -> {
                Location c = meta.casterLocation();
                return Targets.of(nearby(c, r, meta, true).stream()
                        .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(c)))
                        .orElse(null));
            };
        }, "nearestplayer", "np");
        m.registerTargeter(p -> meta -> Targets.entities(meta.casterLocation().getWorld().getPlayers()),
                "playersinworld", "world");
        m.registerTargeter(p -> {
            double r = radius(p, 5);
            return meta -> Targets.entities(nearby(meta.origin, r, meta, false));
        }, "entitiesnearorigin", "eno");
        m.registerTargeter(p -> {
            double r = radius(p, 5);
            return meta -> Targets.entities(nearby(meta.origin, r, meta, true));
        }, "playersnearorigin", "pno");

        // --- locations ------------------------------------------------------
        m.registerTargeter(p -> meta -> Targets.of(meta.casterLocation()), "selflocation", "casterlocation", "sl");
        m.registerTargeter(p -> meta -> {
            Entity t = currentTarget(meta.caster);
            return t == null ? Targets.NONE : Targets.of(t.getLocation());
        }, "targetlocation", "targetloc", "tl");
        m.registerTargeter(p -> meta -> meta.trigger == null ? Targets.NONE : Targets.of(meta.trigger.getLocation()),
                "triggerlocation", "triggerloc");
        m.registerTargeter(p -> meta -> Targets.of(meta.origin), "origin", "source");
        m.registerTargeter(p -> {
            double f = p.getDouble(5, "forward", "f");
            double y = p.getDouble(0, "yoffset", "y");
            return meta -> {
                Location l = meta.casterLocation();
                Vector dir = l.getDirection().setY(0);
                if (dir.lengthSquared() < 1e-6) {
                    dir = new Vector(0, 0, 1);
                }
                return Targets.of(l.clone().add(dir.normalize().multiply(f)).add(0, y, 0));
            };
        }, "forward");
        m.registerTargeter(p -> {
            double r = radius(p, 5);
            int points = p.getInt(8, "points", "p", "amount", "a");
            double y = p.getDouble(0, "yoffset", "y");
            return meta -> {
                Location c = meta.casterLocation();
                List<Location> out = new ArrayList<>();
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    out.add(c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r));
                }
                return Targets.locations(out);
            };
        }, "ring");
        m.registerTargeter(p -> {
            double r = radius(p, 5);
            double minR = p.getDouble(0, "minradius", "minr");
            int amount = p.getInt(1, "amount", "a");
            return meta -> {
                Location c = meta.casterLocation();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                List<Location> out = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    double a = rnd.nextDouble(Math.PI * 2);
                    double d = minR + rnd.nextDouble() * Math.max(0, r - minR);
                    out.add(c.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d));
                }
                return Targets.locations(out);
            };
        }, "randomlocationsnearcaster", "rlnc");
        m.registerTargeter(p -> {
            String name = p.getString("", "bone", "b", "part", "p", "locator", "l");
            return meta -> {
                if (meta.mob == null) {
                    return Targets.NONE;
                }
                Location l = meta.mob.bonePosition(name);
                return Targets.of(l);
            };
        }, "modelpart", "bone", "locator");
    }

    static Entity currentTarget(Entity caster) {
        if (caster instanceof Mob mob) {
            return mob.getTarget();
        }
        if (caster instanceof Player player) {
            return player.getTargetEntity(32);
        }
        return null;
    }

    private static double radius(Params p, double def) {
        return p.getDouble(def, "radius", "r");
    }

    private static Set<String> typeSet(Params p) {
        String raw = p.getString("", "types", "type", "t");
        if (raw.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static List<Entity> nearby(Location center, double r, SkillMeta meta, boolean playersOnly) {
        World world = center.getWorld();
        List<Entity> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        double r2 = r * r;
        for (Entity e : world.getNearbyEntities(center, r, r, r)) {
            if (e == meta.caster || !(e instanceof LivingEntity) || !e.isValid()) {
                continue;
            }
            if (playersOnly && !(e instanceof Player)) {
                continue;
            }
            if (e instanceof Player pl && (pl.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }
            if (e.getLocation().distanceSquared(center) <= r2) {
                out.add(e);
            }
        }
        return out;
    }
}
