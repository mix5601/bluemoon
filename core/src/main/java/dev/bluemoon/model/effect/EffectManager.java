package dev.bluemoon.model.effect;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.model.bbmodel.BBModel.Animation;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.model.runtime.ModelInstance;
import dev.bluemoon.skill.SkillMeta;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Keeps every skill-spawned {@link EffectModel} ticking. */
public final class EffectManager {

    /** Everything needed to spawn one effect. */
    public static final class Spec {
        public String model;
        public String animation;
        public double speed = 1;
        public LoopMode mode;
        /** Lifetime in ticks; 0 = animation length (or 20 without animation), negative = until removed. */
        public int duration;
        public float scale = 1;
        public float yaw;
        public float pitch;
        public Entity follow;
        /** (right, up, forward) offset from the followed entity. */
        public Vector followOffset = new Vector();
        public boolean followYaw = true;
        /** Ignore world light (glowing effect). */
        public boolean bright = true;
    }

    private final BlueMoonPlugin plugin;
    private final List<EffectModel> effects = new ArrayList<>();

    public EffectManager(BlueMoonPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return the effect, or null if the model does not exist */
    public EffectModel spawn(Spec spec, Location location, SkillMeta meta) {
        ModelBlueprint bp = plugin.models().get(spec.model);
        if (bp == null) {
            plugin.getLogger().warning("이펙트 모델 '" + spec.model + "' 이 없습니다");
            return null;
        }
        ModelInstance model = new ModelInstance(plugin, bp, spec.scale);
        model.setRootPitch(spec.pitch);
        model.setFullBright(spec.bright);
        int duration = spec.duration;
        Animation animation = bp.animation(spec.animation);
        if (spec.animation != null && animation == null) {
            plugin.getLogger().warning("이펙트 모델 '" + spec.model + "' 에 애니메이션 '" + spec.animation + "' 이 없습니다");
        }
        if (animation != null) {
            model.play(animation.name, spec.speed, spec.mode, 0, 0, 10);
            if (duration == 0) {
                LoopMode mode = spec.mode != null ? spec.mode : animation.loop;
                duration = mode == LoopMode.LOOP ? 40 : (int) Math.ceil(animation.length / Math.max(0.01, spec.speed) * 20) + 1;
            }
        } else if (duration == 0) {
            duration = 20;
        }
        model.spawn(location, spec.yaw);
        EffectModel effect = new EffectModel(plugin, model, bp.id, meta, location, spec.yaw, duration,
                spec.follow, spec.followOffset, spec.followYaw);
        effects.add(effect);
        return effect;
    }

    public void tick() {
        for (EffectModel effect : new ArrayList<>(effects)) {
            boolean alive;
            try {
                alive = effect.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "이펙트 모델 처리 오류", ex);
                effect.remove();
                alive = false;
            }
            if (!alive) {
                effects.remove(effect);
            }
        }
    }

    public int count() {
        return effects.size();
    }

    public void removeAll() {
        effects.forEach(EffectModel::remove);
        effects.clear();
    }
}
