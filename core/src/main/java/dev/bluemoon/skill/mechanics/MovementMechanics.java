package dev.bluemoon.skill.mechanics;

import dev.bluemoon.skill.Mechanic;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.parse.Params;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.Locale;

/** Velocities are in blocks per tick (vanilla units). */
final class MovementMechanics {

    private MovementMechanics() {
    }

    private static Vector horizontal(Location from, Location to) {
        Vector v = to.toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1e-6) {
            v = from.getDirection().setY(0);
        }
        return v.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : v.normalize();
    }

    private static boolean finite(Vector v) {
        return Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ());
    }

    /** {@code throw{v=1.5;vy=0.5}} knocks targets away from the caster ({@code fromorigin=true}: from the origin). */
    static final class Throw extends Mechanic {
        private final double v;
        private final double vy;
        private final boolean fromOrigin;

        Throw(Params p) {
            super(p);
            v = p.getDouble(1.5, "velocity", "v");
            vy = p.getDouble(0.5, "velocityy", "vy");
            fromOrigin = p.getBoolean(false, "fromorigin", "fo");
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target == meta.caster || CombatMechanics.friendly(meta, target)) {
                return;
            }
            Location from = fromOrigin ? meta.origin : meta.casterLocation();
            Vector vel = horizontal(from, target.getLocation()).multiply(v).setY(vy);
            if (finite(vel)) {
                target.setVelocity(vel);
            }
        }
    }

    /** {@code pull{v=1}} pulls targets toward the caster ({@code toorigin=true}: toward the skill origin). */
    static final class Pull extends Mechanic {
        private final double v;
        private final boolean toOrigin;

        Pull(Params p) {
            super(p);
            v = p.getDouble(1, "velocity", "v");
            toOrigin = p.getBoolean(false, "toorigin", "to");
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target == meta.caster || CombatMechanics.friendly(meta, target)) {
                return;
            }
            Location center = toOrigin ? meta.origin : meta.casterLocation();
            Vector dir = center.toVector().subtract(target.getLocation().toVector());
            double dist = dir.length();
            if (dist < 0.5) {
                return;
            }
            Vector vel = dir.normalize().multiply(Math.min(v, dist * 0.25)).setY(0.3);
            if (finite(vel)) {
                target.setVelocity(vel);
            }
        }
    }

    /** {@code leap{vy=0.9}} makes the caster jump so it lands roughly on the target. */
    static final class Leap extends Mechanic {
        private final double vy;
        private final double maxSpeed;

        Leap(Params p) {
            super(p);
            vy = p.getDouble(0.9, "velocityy", "vy", "height", "h");
            maxSpeed = p.getDouble(3, "velocity", "v", "max");
        }

        @Override
        public Mode mode() {
            return Mode.LOCATION;
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            Location from = meta.caster.getLocation();
            if (!from.getWorld().equals(target.getWorld())) {
                return;
            }
            double dx = target.getX() - from.getX();
            double dz = target.getZ() - from.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // airtime with gravity 0.08 and 2% drag per tick, roughly
            double airTicks = Math.max(1, 2 * vy / 0.08);
            double horizontal = Math.min(maxSpeed, dist / airTicks * 1.2);
            Vector vel = dist < 1e-3 ? new Vector(0, vy, 0) : new Vector(dx / dist * horizontal, vy, dz / dist * horizontal);
            if (finite(vel)) {
                meta.caster.setVelocity(vel);
            }
        }
    }

    /** {@code lunge{v=1.2;vy=0.3}} dashes the caster toward the target. */
    static final class Lunge extends Mechanic {
        private final double v;
        private final double vy;

        Lunge(Params p) {
            super(p);
            v = p.getDouble(1.2, "velocity", "v");
            vy = p.getDouble(0.3, "velocityy", "vy");
        }

        @Override
        public Mode mode() {
            return Mode.LOCATION;
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            Vector vel = horizontal(meta.caster.getLocation(), target).multiply(v).setY(vy);
            if (finite(vel)) {
                meta.caster.setVelocity(vel);
            }
        }
    }

    /** {@code velocity{mode=set|add|multiply;x=0;y=1;z=0}} */
    static final class Velocity extends Mechanic {
        private final String mode;
        private final Vector vec;

        Velocity(Params p) {
            super(p);
            mode = p.getString("set", "mode", "m").toLowerCase(Locale.ROOT);
            vec = new Vector(p.getDouble(0, "x"), p.getDouble(0, "y"), p.getDouble(0, "z"));
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            Vector cur = target.getVelocity();
            Vector vel = switch (mode) {
                case "add" -> cur.add(vec);
                case "multiply", "mult" -> cur.multiply(vec);
                default -> vec.clone();
            };
            if (finite(vel)) {
                target.setVelocity(vel);
            }
        }
    }

    /** {@code teleport{}} moves the caster to the target location. */
    static final class Teleport extends Mechanic {
        Teleport(Params p) {
            super(p);
        }

        @Override
        public Mode mode() {
            return Mode.LOCATION;
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            Location to = target.clone();
            Location cur = meta.caster.getLocation();
            to.setYaw(cur.getYaw());
            to.setPitch(cur.getPitch());
            meta.caster.teleport(to);
        }
    }
}
