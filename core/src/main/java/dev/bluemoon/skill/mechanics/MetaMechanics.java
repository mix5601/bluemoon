package dev.bluemoon.skill.mechanics;

import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.effect.EffectManager;
import dev.bluemoon.model.effect.EffectModel;
import dev.bluemoon.skill.Mechanic;
import dev.bluemoon.skill.Skill;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.TargetAwareMechanic;
import dev.bluemoon.skill.Targets;
import dev.bluemoon.skill.parse.Params;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class MetaMechanics {

    private MetaMechanics() {
    }

    static void runSkill(SkillMeta meta, String name, SkillMeta withMeta) {
        if (name == null || name.isBlank()) {
            return;
        }
        Skill skill = meta.plugin.skills().skill(name);
        if (skill == null) {
            meta.plugin.getLogger().warning("존재하지 않는 스킬 호출: " + name);
            return;
        }
        skill.execute(withMeta);
    }

    /** {@code skill{s=OtherSkill}} runs a metaskill; targets of this line are inherited by it. */
    static final class SkillCall extends Mechanic implements TargetAwareMechanic {
        private final String skill;

        SkillCall(Params p) {
            super(p);
            skill = p.getString("", "skill", "s", "meta", "m");
            if (skill.isBlank()) {
                throw new IllegalArgumentException("skill{} 에는 s=스킬이름 이 필요합니다");
            }
        }

        @Override
        public void castTargets(SkillMeta meta, Targets targets, boolean explicit) {
            SkillMeta next = explicit
                    ? meta.withTargets(targets.entities(), targets.locations())
                    : meta.withTargets(meta.inheritedEntities, meta.inheritedLocations);
            runSkill(meta, skill, next);
        }
    }

    /** {@code randomskill{skills=A,B,C}} */
    static final class RandomSkill extends Mechanic implements TargetAwareMechanic {
        private final List<String> skills;

        RandomSkill(Params p) {
            super(p);
            skills = Arrays.stream(p.getString("", "skills", "s").split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (skills.isEmpty()) {
                throw new IllegalArgumentException("randomskill{} 에는 skills=A,B 가 필요합니다");
            }
        }

        @Override
        public void castTargets(SkillMeta meta, Targets targets, boolean explicit) {
            String pick = skills.get(ThreadLocalRandom.current().nextInt(skills.size()));
            SkillMeta next = explicit
                    ? meta.withTargets(targets.entities(), targets.locations())
                    : meta.withTargets(meta.inheritedEntities, meta.inheritedLocations);
            runSkill(meta, pick, next);
        }
    }

    /**
     * {@code projectile{onTick=A;onHit=B;onEnd=C;v=15;i=1;hr=1;md=30;syo=1.2;g=0}}
     * A virtual projectile: {@code onTick}/{@code onEnd} run at its position ({@code @Origin}),
     * {@code onHit} runs with the hit entity as inherited target.
     */
    static final class Projectile extends Mechanic {
        private final String onStart;
        private final String onTick;
        private final String onHit;
        private final String onEnd;
        private final double velocity;
        private final int interval;
        private final double hitRadius;
        private final double maxDistance;
        private final double startYOffset;
        private final double targetYOffset;
        private final double gravity;
        private final boolean stopAtBlock;
        private final boolean hitPlayers;
        private final boolean hitNonPlayers;
        private final boolean pierce;
        private final EffectManager.Spec modelSpec;

        Projectile(Params p) {
            super(p);
            onStart = p.getString(null, "onstart", "os");
            onTick = p.getString(null, "ontick", "ot");
            onHit = p.getString(null, "onhit", "oh");
            onEnd = p.getString(null, "onend", "oe");
            velocity = p.getDouble(15, "velocity", "v");
            interval = Math.max(1, p.getInt(1, "interval", "i"));
            hitRadius = p.getDouble(1, "hitradius", "hr");
            maxDistance = p.getDouble(30, "maxdistance", "md", "maxrange", "mr");
            startYOffset = p.getDouble(1.2, "startyoffset", "syo");
            targetYOffset = p.getDouble(0, "targetyoffset", "tyo");
            gravity = p.getDouble(0, "gravity", "g");
            stopAtBlock = p.getBoolean(true, "stopatblock", "sb");
            hitPlayers = p.getBoolean(true, "hitplayers", "hp");
            hitNonPlayers = p.getBoolean(true, "hitnonplayers", "hnp");
            pierce = p.getBoolean(false, "pierce", "pi");
            String model = p.getString(null, "model", "m");
            if (model != null && !model.isBlank()) {
                modelSpec = new EffectManager.Spec();
                modelSpec.model = model;
                modelSpec.animation = p.getString(null, "animation", "anim", "a");
                modelSpec.mode = LoopMode.LOOP;
                modelSpec.duration = -1;
                modelSpec.scale = p.getFloat(1, "modelscale", "ms");
                modelSpec.bright = p.getBoolean(true, "bright", "glow");
            } else {
                modelSpec = null;
            }
        }

        private static float yaw(Vector v) {
            return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
        }

        private static float pitch(Vector v) {
            double h = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
            return (float) -Math.toDegrees(Math.atan2(v.getY(), h));
        }

        @Override
        public Mode mode() {
            return Mode.BOTH;
        }

        @Override
        public String defaultTargeter() {
            return "target";
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            launch(meta, target.getLocation().add(0, target.getHeight() / 2 + targetYOffset, 0));
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            launch(meta, target.add(0, targetYOffset, 0));
        }

        private void launch(SkillMeta meta, Location target) {
            Location start = meta.casterLocation().add(0, startYOffset, 0);
            World world = start.getWorld();
            if (world == null || !world.equals(target.getWorld())) {
                return;
            }
            Vector dir = target.toVector().subtract(start.toVector());
            if (dir.lengthSquared() < 1e-6) {
                dir = start.getDirection();
            }
            Vector motion = dir.normalize().multiply(velocity / 20.0);
            Location pos = start.clone();
            pos.setDirection(motion);
            Set<UUID> hit = new HashSet<>();
            SkillMeta base = meta.withTargets(List.of(), List.of());
            EffectModel visual = null;
            if (modelSpec != null) {
                modelSpec.yaw = yaw(motion);
                modelSpec.pitch = pitch(motion);
                visual = meta.plugin.effects().spawn(modelSpec, pos, meta);
            }
            EffectModel model = visual;
            if (onStart != null) {
                runSkill(meta, onStart, base.withOrigin(pos));
            }
            double[] traveled = {0};
            Bukkit.getScheduler().runTaskTimer(meta.plugin, task -> {
                boolean ended = false;
                for (int t = 0; t < interval && !ended; t++) {
                    motion.setY(motion.getY() - gravity / 20.0);
                    double stepLength = motion.length();
                    int subSteps = Math.max(1, (int) Math.ceil(stepLength / 0.5));
                    Vector sub = motion.clone().multiply(1.0 / subSteps);
                    for (int s = 0; s < subSteps && !ended; s++) {
                        pos.add(sub);
                        traveled[0] += sub.length();
                        if (!pos.isChunkLoaded() || traveled[0] >= maxDistance) {
                            ended = true;
                            break;
                        }
                        if (stopAtBlock && pos.getBlock().getType().isSolid()) {
                            ended = true;
                            break;
                        }
                        for (Entity e : world.getNearbyEntities(pos, hitRadius, hitRadius, hitRadius)) {
                            if (e == meta.caster || !(e instanceof LivingEntity) || !e.isValid() || hit.contains(e.getUniqueId())) {
                                continue;
                            }
                            boolean isPlayer = e instanceof Player;
                            if ((isPlayer && !hitPlayers) || (!isPlayer && !hitNonPlayers)) {
                                continue;
                            }
                            hit.add(e.getUniqueId());
                            if (onHit != null) {
                                runSkill(meta, onHit, meta.withTargets(List.of(e), List.of()).withOrigin(pos));
                            }
                            if (!pierce) {
                                ended = true;
                                break;
                            }
                        }
                    }
                }
                if (model != null && !ended) {
                    model.move(pos, yaw(motion), pitch(motion));
                }
                if (onTick != null && !ended) {
                    runSkill(meta, onTick, base.withOrigin(pos));
                }
                if (ended || !meta.plugin.isEnabled()) {
                    if (onEnd != null) {
                        runSkill(meta, onEnd, base.withOrigin(pos));
                    }
                    if (model != null) {
                        model.remove();
                    }
                    task.cancel();
                }
            }, 0L, interval);
        }
    }
}
