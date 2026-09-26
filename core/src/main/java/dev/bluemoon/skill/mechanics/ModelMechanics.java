package dev.bluemoon.skill.mechanics;

import dev.bluemoon.mob.ActiveMob;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.effect.EffectManager;
import dev.bluemoon.model.effect.EffectModel;
import dev.bluemoon.model.runtime.ModelInstance;
import dev.bluemoon.skill.Mechanic;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.parse.Params;
import dev.bluemoon.util.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Mechanics that drive the Blockbench model. They act on the caster unless the line has an
 * explicit targeter, so {@code skill{s=X} @target} does not try to animate the player.
 */
final class ModelMechanics {

    private ModelMechanics() {
    }

    abstract static class ModelMechanic extends Mechanic {
        ModelMechanic(Params p) {
            super(p);
        }

        @Override
        public boolean usesInheritedTargets() {
            return false;
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            ActiveMob mob = meta.plugin.mobs().get(target);
            if (mob != null && mob.model() != null) {
                apply(meta, mob, mob.model());
            }
        }

        abstract void apply(SkillMeta meta, ActiveMob mob, ModelInstance model);
    }

    /** {@code model{mid=golem;scale=1}} / {@code model{remove=true}} */
    static final class Model extends Mechanic {
        private final String id;
        private final float scale;
        private final boolean remove;

        Model(Params p) {
            super(p);
            id = p.getString("", "modelid", "mid", "model", "m", "id");
            scale = p.getFloat(1, "scale", "s");
            remove = p.getBoolean(false, "remove", "r");
            if (!remove && id.isBlank()) {
                throw new IllegalArgumentException("model{} 에는 mid=모델이름 이 필요합니다");
            }
        }

        @Override
        public boolean usesInheritedTargets() {
            return false;
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            ActiveMob mob = meta.plugin.mobs().get(target);
            if (mob == null) {
                return;
            }
            if (remove) {
                mob.removeModel();
            } else {
                mob.applyModel(id, scale);
            }
        }
    }

    /** {@code animation{a=slam;speed=1;mode=once|loop|hold;fadein=3;fadeout=3;priority=10}} */
    static final class Animation extends ModelMechanic {
        private final String name;
        private final double speed;
        private final LoopMode mode;
        private final int fadeIn;
        private final int fadeOut;
        private final int priority;
        private final boolean restart;

        Animation(Params p) {
            super(p);
            name = p.getString("", "animation", "anim", "a", "state", "s");
            if (name.isBlank()) {
                throw new IllegalArgumentException("animation{} 에는 a=애니메이션이름 이 필요합니다");
            }
            speed = p.getDouble(1, "speed", "sp");
            String m = p.getString(null, "mode", "loop", "l");
            mode = m == null ? null : switch (m.toLowerCase(Locale.ROOT)) {
                case "loop", "true" -> LoopMode.LOOP;
                case "hold" -> LoopMode.HOLD;
                default -> LoopMode.ONCE;
            };
            fadeIn = p.getInt(-1, "fadein", "lerpin", "li");
            fadeOut = p.getInt(-1, "fadeout", "lerpout", "lo");
            priority = p.getInt(10, "priority", "pr");
            restart = p.getBoolean(true, "restart", "force", "f");
        }

        @Override
        void apply(SkillMeta meta, ActiveMob mob, ModelInstance model) {
            if (!restart && model.isPlaying(name)) {
                return;
            }
            model.play(name, speed, mode, fadeIn, fadeOut, priority);
        }
    }

    /** {@code stopanimation{a=slam;fadeout=3}}; {@code a=*} stops every non-state animation. */
    static final class StopAnimation extends ModelMechanic {
        private final String name;
        private final int fadeOut;

        StopAnimation(Params p) {
            super(p);
            name = p.getString("*", "animation", "anim", "a", "state", "s");
            fadeOut = p.getInt(-1, "fadeout", "lerpout", "lo");
        }

        @Override
        void apply(SkillMeta meta, ActiveMob mob, ModelInstance model) {
            if (name.equals("*")) {
                model.stopAll(fadeOut);
            } else {
                model.stop(name, fadeOut);
            }
        }
    }

    /** {@code tint{color=FF0000;duration=20}}; duration 0 keeps the tint, {@code color=FFFFFF} resets. */
    static final class Tint extends ModelMechanic {
        private final Color color;
        private final int duration;

        Tint(Params p) {
            super(p);
            color = Particles.color(p.getString("FF0000", "color", "c"), Color.RED);
            duration = p.getInt(20, "duration", "d");
        }

        @Override
        void apply(SkillMeta meta, ActiveMob mob, ModelInstance model) {
            model.tint(color, duration);
        }
    }

    /** {@code bonevisibility{bone=sword;visible=false}} */
    static final class BoneVisibility extends ModelMechanic {
        private final String bone;
        private final boolean visible;

        BoneVisibility(Params p) {
            super(p);
            bone = p.getString("", "bone", "b", "part", "p");
            visible = p.getBoolean(true, "visible", "v");
            if (bone.isBlank()) {
                throw new IllegalArgumentException("bonevisibility{} 에는 bone=본이름 이 필요합니다");
            }
        }

        @Override
        void apply(SkillMeta meta, ActiveMob mob, ModelInstance model) {
            model.setBoneVisible(bone, visible);
        }
    }

    /**
     * {@code modeleffect{m=slash;a=swing;f=1.5;y=1;follow=false;yaw=caster;scale=1}} spawns a
     * standalone Blockbench model. Its animation's timeline keyframes run as skills of the caster
     * ({@code @Origin} = effect position, {@code @ModelPart} = the effect's bones).
     */
    static final class ModelEffect extends Mechanic {
        private final EffectManager.Spec template = new EffectManager.Spec();
        private final double forward;
        private final double up;
        private final double side;
        private final boolean follow;
        private final String yawMode;
        private final float yawOffset;
        private final boolean usePitch;

        ModelEffect(Params p) {
            super(p);
            template.model = p.getString("", "model", "m", "mid", "modelid");
            if (template.model.isBlank()) {
                throw new IllegalArgumentException("modeleffect{} 에는 m=모델이름 이 필요합니다");
            }
            template.animation = p.getString(null, "animation", "anim", "a");
            template.speed = p.getDouble(1, "speed", "sp");
            String mode = p.getString(null, "mode", "loop");
            template.mode = mode == null ? null : switch (mode.toLowerCase(Locale.ROOT)) {
                case "loop", "true" -> LoopMode.LOOP;
                case "hold" -> LoopMode.HOLD;
                default -> LoopMode.ONCE;
            };
            template.duration = p.getInt(0, "duration", "d");
            template.scale = p.getFloat(1, "scale", "s");
            template.followYaw = p.getBoolean(true, "rotate", "followyaw");
            template.bright = p.getBoolean(true, "bright", "glow");
            forward = p.getDouble(0, "forward", "f");
            up = p.getDouble(0, "yoffset", "y");
            side = p.getDouble(0, "side", "right");
            follow = p.getBoolean(false, "follow", "fo");
            yawMode = p.getString("caster", "yaw").toLowerCase(Locale.ROOT);
            yawOffset = p.getFloat(0, "yawoffset", "yo");
            usePitch = p.getBoolean(false, "pitch");
        }

        @Override
        public Mode mode() {
            return Mode.BOTH;
        }

        @Override
        public void castEntity(SkillMeta meta, Entity target) {
            spawn(meta, target.getLocation(), target);
        }

        @Override
        public void castLocation(SkillMeta meta, Location target) {
            spawn(meta, target, null);
        }

        private void spawn(SkillMeta meta, Location base, Entity target) {
            Location casterLoc = meta.caster.getLocation();
            float yaw = switch (yawMode) {
                case "target" -> base.getYaw();
                case "none" -> 0f;
                case "caster" -> casterLoc.getYaw();
                default -> {
                    try {
                        yield Float.parseFloat(yawMode);
                    } catch (NumberFormatException e) {
                        yield casterLoc.getYaw();
                    }
                }
            } + yawOffset;
            EffectManager.Spec spec = copy(template);
            spec.yaw = yaw;
            spec.pitch = usePitch ? casterLoc.getPitch() : 0f;
            Vector offset = new Vector(side, up, forward);
            if (follow && target != null) {
                spec.follow = target;
                spec.followOffset = offset;
            }
            Location at = base.clone().add(EffectModel.rotate(offset, yaw));
            meta.plugin.effects().spawn(spec, at, meta);
        }

        private static EffectManager.Spec copy(EffectManager.Spec t) {
            EffectManager.Spec s = new EffectManager.Spec();
            s.model = t.model;
            s.animation = t.animation;
            s.speed = t.speed;
            s.mode = t.mode;
            s.duration = t.duration;
            s.scale = t.scale;
            s.followYaw = t.followYaw;
            s.bright = t.bright;
            return s;
        }
    }
}
