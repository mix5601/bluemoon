package dev.bluemoon.skill.mechanics;

import dev.bluemoon.skill.Mechanic;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.parse.Params;
import dev.bluemoon.util.Particles;
import dev.bluemoon.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Locale;

final class EffectMechanics {

    private EffectMechanics() {
    }

    /** Shared particle options: particle, amount, hspread, vspread, speed, yoffset, color, size, material. */
    abstract static class ParticleBase extends Mechanic {
        final Particle particle;
        final int amount;
        final double hs;
        final double vs;
        final double speed;
        final double yOffset;
        final Color color;
        final float size;
        final Material material;

        ParticleBase(Params p) {
            super(p);
            String name = p.getString("flame", "particle", "p");
            particle = Particles.parse(name);
            if (particle == null) {
                throw new IllegalArgumentException("알 수 없는 파티클: " + name);
            }
            amount = p.getInt(10, "amount", "a");
            hs = p.getDouble(0, "hspread", "hs");
            vs = p.getDouble(0, "vspread", "vs");
            speed = p.getDouble(0, "speed", "s");
            yOffset = p.getDouble(0, "yoffset", "y");
            color = Particles.color(p.getString("FF0000", "color", "c"), Color.RED);
            size = p.getFloat(1, "size");
            material = Particles.material(p.getString("stone", "material", "m"), Material.STONE);
        }

        @Override
        public Mode mode() {
            return Mode.LOCATION;
        }

        void spawn(Location at, int count) {
            Particles.spawn(particle, at, count, hs, vs, hs, speed, color, size, material);
        }
    }

    /** {@code particle{p=flame;a=20;hs=0.5;vs=0.5;s=0.02;y=1}} */
    static final class ParticleEffect extends ParticleBase {
        ParticleEffect(Params p) {
            super(p);
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            spawn(target.add(0, yOffset, 0), amount);
        }
    }

    /** {@code particlering{p=flame;r=3;points=24}} */
    static final class ParticleRing extends ParticleBase {
        private final double radius;
        private final int points;

        ParticleRing(Params p) {
            super(p);
            radius = p.getDouble(3, "radius", "r");
            points = Math.max(1, p.getInt(24, "points", "pts"));
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                spawn(target.clone().add(Math.cos(a) * radius, yOffset, Math.sin(a) * radius), Math.max(1, amount / points));
            }
        }
    }

    /** {@code particleline{p=crit;distance=0.3}} draws from the caster (or origin) to each target. */
    static final class ParticleLine extends ParticleBase {
        private final double step;
        private final boolean fromOrigin;

        ParticleLine(Params p) {
            super(p);
            step = Math.max(0.05, p.getDouble(0.3, "distancebetween", "distance", "db"));
            fromOrigin = p.getBoolean(false, "fromorigin", "fo");
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            Location start = (fromOrigin ? meta.origin : meta.casterLocation()).clone().add(0, 1, 0);
            if (!start.getWorld().equals(target.getWorld())) {
                return;
            }
            Location end = target.clone().add(0, yOffset + 1, 0);
            Vector dir = end.toVector().subtract(start.toVector());
            double len = dir.length();
            if (len < 1e-3) {
                return;
            }
            dir.normalize().multiply(step);
            Location cur = start.clone();
            for (double d = 0; d <= len; d += step) {
                spawn(cur, 1);
                cur.add(dir);
            }
        }
    }

    /** {@code sound{s=entity.ender_dragon.growl;v=1;p=1}} */
    static final class Sound extends Mechanic {
        private final String sound;
        private final float volume;
        private final float pitch;

        Sound(Params p) {
            super(p);
            String s = p.getString("entity.experience_orb.pickup", "sound", "s").toLowerCase(Locale.ROOT);
            // accept enum style names like ENTITY_GENERIC_EXPLODE when they carry no dots
            sound = s.contains(".") || s.contains(":") ? s : s.replace('_', '.');
            volume = p.getFloat(1, "volume", "v");
            pitch = p.getFloat(1, "pitch", "p");
        }

        @Override
        public Mode mode() {
            return Mode.LOCATION;
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            target.getWorld().playSound(target, sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    /** {@code message{m="&c<caster.name>: 안녕"}} */
    static final class Message extends Mechanic {
        private final String message;

        Message(Params p) {
            super(p);
            message = p.getString("", "message", "msg", "m");
        }

        @Override
        public String defaultTargeter() {
            return "trigger";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target instanceof Player player) {
                player.sendMessage(Text.color(Text.placeholders(message, meta, target)));
            }
        }
    }

    /** {@code actionmessage{m="..."}} */
    static final class ActionMessage extends Mechanic {
        private final String message;

        ActionMessage(Params p) {
            super(p);
            message = p.getString("", "message", "msg", "m");
        }

        @Override
        public String defaultTargeter() {
            return "trigger";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target instanceof Player player) {
                player.sendActionBar(Text.color(Text.placeholders(message, meta, target)));
            }
        }
    }

    /** {@code sendtitle{title="&c경고";subtitle="...";fadein=5;stay=40;fadeout=10}} */
    static final class SendTitle extends Mechanic {
        private final String title;
        private final String subtitle;
        private final Title.Times times;

        SendTitle(Params p) {
            super(p);
            title = p.getString("", "title", "t");
            subtitle = p.getString("", "subtitle", "st");
            times = Title.Times.times(
                    Duration.ofMillis(p.getInt(10, "fadein", "fi") * 50L),
                    Duration.ofMillis(p.getInt(40, "stay", "d", "duration") * 50L),
                    Duration.ofMillis(p.getInt(10, "fadeout", "fo") * 50L));
        }

        @Override
        public String defaultTargeter() {
            return "trigger";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target instanceof Player player) {
                player.showTitle(Title.title(
                        Text.color(Text.placeholders(title, meta, target)),
                        Text.color(Text.placeholders(subtitle, meta, target)),
                        times));
            }
        }
    }
}
