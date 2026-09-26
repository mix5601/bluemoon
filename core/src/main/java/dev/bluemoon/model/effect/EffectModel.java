package dev.bluemoon.model.effect;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.runtime.ModelHost;
import dev.bluemoon.model.runtime.ModelInstance;
import dev.bluemoon.skill.SkillMeta;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * A Blockbench model spawned by a skill (slash, magic circle, projectile, ...). It plays its
 * animation, runs the animation's timeline scripts as the caster and disappears afterwards.
 */
public final class EffectModel implements ModelHost {

    private final BlueMoonPlugin plugin;
    private final ModelInstance model;
    private final SkillMeta meta;
    private final String modelId;
    private Location location;
    private float yaw;
    private final int duration;
    private final Entity follow;
    private final Vector followOffset;
    private final boolean followYaw;
    private int age;
    private boolean removed;

    EffectModel(BlueMoonPlugin plugin, ModelInstance model, String modelId, SkillMeta meta, Location location,
                float yaw, int duration, Entity follow, Vector followOffset, boolean followYaw) {
        this.plugin = plugin;
        this.model = model;
        this.modelId = modelId;
        this.meta = meta;
        this.location = location.clone();
        this.yaw = yaw;
        this.duration = duration;
        this.follow = follow;
        this.followOffset = followOffset;
        this.followYaw = followYaw;
    }

    @Override
    public ModelInstance model() {
        return model;
    }

    @Override
    public Location bonePosition(String name) {
        return model.partLocation(name, location);
    }

    public Location location() {
        return location.clone();
    }

    public boolean isRemoved() {
        return removed;
    }

    /** Moves the effect (projectiles call this every tick). */
    public void move(Location to, float newYaw, float pitch) {
        location = to.clone();
        yaw = newYaw;
        model.setRootPitch(pitch);
    }

    public void remove() {
        if (!removed) {
            removed = true;
            model.remove();
        }
    }

    /** @return false once the effect is gone */
    boolean tick() {
        if (removed) {
            return false;
        }
        if (follow != null) {
            if (!follow.isValid()) {
                remove();
                return false;
            }
            Location base = follow.getLocation();
            float baseYaw = followYaw ? base.getYaw() : yaw;
            location = base.add(rotate(followOffset, baseYaw));
            yaw = baseYaw;
        }
        List<EffectKeyframe> fired = model.tick(location, yaw, 0, 0, age / 20.0);
        for (EffectKeyframe effect : fired) {
            KeyframeEffects.run(plugin, effect, this, location, modelId + " 이펙트 타임라인",
                    () -> meta.withHost(this).withOrigin(location));
        }
        age++;
        if (duration > 0 && age >= duration) {
            remove();
            return false;
        }
        return true;
    }

    /** Offset given as (right, up, forward) relative to a yaw. */
    public static Vector rotate(Vector offset, float yaw) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // right of the facing direction is (-fz, 0, fx)
        return new Vector(-fz * offset.getX() + fx * offset.getZ(), offset.getY(), fx * offset.getX() + fz * offset.getZ());
    }
}
