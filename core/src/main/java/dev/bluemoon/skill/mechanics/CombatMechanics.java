package dev.bluemoon.skill.mechanics;

import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.mob.MobType;
import dev.bluemoon.skill.Mechanic;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.parse.Params;
import dev.bluemoon.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

final class CombatMechanics {

    /** Set while a skill deals damage, so the damage does not fire the caster's {@code ~onAttack} again. */
    private static int skillDamageDepth;

    private CombatMechanics() {
    }

    static boolean isSkillDamage() {
        return skillDamageDepth > 0;
    }

    /** Skills never hurt the caster's owner / summons (the caster itself only via an explicit @self). */
    static boolean friendly(SkillMeta meta, Entity target) {
        return target != meta.caster && meta.plugin.mobs().allies(meta.caster, target);
    }

    /** {@code damage{amount=5;ignorearmor=false}} */
    static final class Damage extends Mechanic {
        private final double amount;
        private final boolean ignoreArmor;

        Damage(Params p) {
            super(p);
            amount = p.getDouble(1, "amount", "a", "damage", "d");
            ignoreArmor = p.getBoolean(false, "ignorearmor", "ia");
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (!(target instanceof LivingEntity le) || le.isDead() || friendly(meta, target)) {
                return;
            }
            skillDamageDepth++;
            try {
                if (ignoreArmor) {
                    DamageSource.Builder b = DamageSource.builder(DamageType.MAGIC);
                    if (meta.caster != null) {
                        b.withCausingEntity(meta.caster).withDirectEntity(meta.caster);
                    }
                    le.damage(amount, b.build());
                } else {
                    le.damage(amount, meta.caster);
                }
            } finally {
                skillDamageDepth--;
            }
        }
    }

    /** {@code heal{amount=5}} */
    static final class Heal extends Mechanic {
        private final double amount;

        Heal(Params p) {
            super(p);
            amount = p.getDouble(1, "amount", "a");
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target instanceof LivingEntity le && !le.isDead()) {
                AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                le.setHealth(Math.min(cap, le.getHealth() + amount));
            }
        }
    }

    /** {@code ignite{ticks=60}} */
    static final class Ignite extends Mechanic {
        private final int ticks;

        Ignite(Params p) {
            super(p);
            ticks = p.getInt(60, "ticks", "t", "duration", "d");
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (friendly(meta, target)) {
                return;
            }
            target.setFireTicks(Math.max(target.getFireTicks(), ticks));
        }
    }

    /** {@code potion{type=slowness;duration=100;level=1}} (level starts at 1). */
    static final class Potion extends Mechanic {
        private final PotionEffectType type;
        private final int duration;
        private final int level;
        private final boolean particles;
        private final boolean icon;

        Potion(Params p) {
            super(p);
            String name = p.getString("speed", "type", "t").toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(legacyPotion(name));
            type = key == null ? null : Registry.EFFECT.get(key);
            if (type == null) {
                throw new IllegalArgumentException("알 수 없는 포션 효과: " + name);
            }
            duration = p.getInt(100, "duration", "d");
            level = Math.max(1, p.getInt(1, "level", "l", "lvl"));
            particles = p.getBoolean(true, "hasparticles", "particles", "p");
            icon = p.getBoolean(true, "hasicon", "icon", "i");
        }

        private static String legacyPotion(String name) {
            return switch (name) {
                case "slow" -> "slowness";
                case "fast_digging" -> "haste";
                case "slow_digging" -> "mining_fatigue";
                case "increase_damage" -> "strength";
                case "heal" -> "instant_health";
                case "harm" -> "instant_damage";
                case "jump" -> "jump_boost";
                case "confusion" -> "nausea";
                case "damage_resistance" -> "resistance";
                default -> name;
            };
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (!(target instanceof LivingEntity le)) {
                return;
            }
            if (type.getCategory() == PotionEffectTypeCategory.HARMFUL && friendly(meta, target)) {
                return;
            }
            le.addPotionEffect(new PotionEffect(type, duration, level - 1, false, particles, icon));
        }
    }

    /** {@code lightning{}} strikes real lightning, {@code effect:lightning{}} only the visual. */
    static final class Lightning extends Mechanic {
        private final boolean visualOnly;

        Lightning(Params p, boolean visualOnly) {
            super(p);
            this.visualOnly = visualOnly;
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
            if (visualOnly) {
                target.getWorld().strikeLightningEffect(target);
            } else {
                target.getWorld().strikeLightning(target);
            }
        }
    }

    /**
     * {@code summon{type=ZOMBIE;amount=2;radius=3}} — {@code type} may also be a BlueMoon mob, which
     * becomes the caster's summon ({@code owner=false} to disable, {@code duration} ticks,
     * {@code max} summons of this type per caster: the oldest is dismissed).
     */
    static final class Summon extends Mechanic {
        private final String type;
        private final int amount;
        private final double radius;
        private final boolean owned;
        private final int duration;
        private final int max;

        Summon(Params p) {
            super(p);
            type = p.getString("ZOMBIE", "type", "t", "mob", "m");
            amount = Math.max(1, p.getInt(1, "amount", "a"));
            radius = p.getDouble(0, "radius", "r", "noise", "n");
            owned = p.getBoolean(true, "owner", "o");
            duration = p.getInt(0, "duration", "d");
            max = p.getInt(0, "max");
        }

        @Override
        public Mode mode() {
            return Mode.LOCATION;
        }

        @Override
        public String defaultTargeter() {
            return "selflocation";
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            MobType custom = meta.plugin.mobs().type(type);
            EntityType vanilla = null;
            if (custom == null) {
                NamespacedKey key = NamespacedKey.fromString(type.toLowerCase(Locale.ROOT));
                vanilla = key == null ? null : Registry.ENTITY_TYPE.get(key);
                if (vanilla == null || vanilla.getEntityClass() == null || !vanilla.isSpawnable()) {
                    meta.plugin.getLogger().warning("summon: 알 수 없는 몹 '" + type + "'");
                    return;
                }
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < amount; i++) {
                Location at = target.clone();
                if (radius > 0) {
                    at.add(rnd.nextDouble(-radius, radius), 0, rnd.nextDouble(-radius, radius));
                }
                if (custom != null) {
                    if (owned && max > 0) {
                        List<ActiveMob> existing = new ArrayList<>(meta.plugin.mobs().summonsOf(meta.caster));
                        existing.removeIf(m -> m.type() != custom);
                        for (int k = 0; k <= existing.size() - max; k++) {
                            existing.get(k).expire();
                        }
                    }
                    meta.plugin.mobs().spawn(custom, at, owned ? meta.caster : null, duration);
                } else {
                    at.getWorld().spawnEntity(at, vanilla);
                }
            }
        }
    }

    /** {@code command{c="say hi <target.name>"}} runs a console command per target. */
    static final class Command extends Mechanic {
        private final String command;

        Command(Params p) {
            super(p);
            String c = p.getString("", "command", "cmd", "c");
            command = c.startsWith("/") ? c.substring(1) : c;
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (!command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Text.placeholders(command, meta, target));
            }
        }
    }

    /** {@code remove{}} removes the target (for BlueMoon mobs also the model). */
    static final class Remove extends Mechanic {
        Remove(Params p) {
            super(p);
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            if (target instanceof org.bukkit.entity.Player) {
                return;
            }
            ActiveMob am = meta.plugin.mobs().get(target);
            if (am != null) {
                am.removeModel();
            }
            target.remove();
        }
    }

    /** {@code invulnerable{ticks=10}}: the target ignores all damage for a while (dodge i-frames). */
    static final class Invulnerable extends Mechanic {
        private final int ticks;

        Invulnerable(Params p) {
            super(p);
            ticks = p.getInt(10, "ticks", "t", "duration", "d");
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            meta.plugin.skills().setInvulnerable(target, ticks);
        }
    }
}
