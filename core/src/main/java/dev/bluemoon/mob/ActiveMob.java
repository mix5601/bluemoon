package dev.bluemoon.mob;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.model.animation.AnimationLayer;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.effect.KeyframeEffects;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.model.runtime.ModelHost;
import dev.bluemoon.model.runtime.ModelInstance;
import dev.bluemoon.skill.CompiledSkillLine;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.SkillTrigger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/** A living BlueMoon mob: the vanilla entity (hitbox / AI) plus its Blockbench model. */
public final class ActiveMob implements ModelHost {

    private final BlueMoonPlugin plugin;
    private final LivingEntity entity;
    private final MobType type;
    private ModelInstance model;
    private long ticks;
    private boolean dying;
    private int deathTicks;
    private int deathDuration;
    private boolean removed;
    private Location lastLocation;
    private float lastBodyYaw;
    private int movingTicks;
    private UUID owner;
    private int lifetime;

    ActiveMob(BlueMoonPlugin plugin, LivingEntity entity, MobType type) {
        this.plugin = plugin;
        this.entity = entity;
        this.type = type;
        this.lastLocation = entity.getLocation();
        this.lastBodyYaw = entity.getBodyYaw();
    }

    public LivingEntity entity() {
        return entity;
    }

    public MobType type() {
        return type;
    }

    @Override
    public ModelInstance model() {
        return model;
    }

    public boolean isDying() {
        return dying;
    }

    public boolean isRemoved() {
        return removed;
    }

    // --- owner (summons) -----------------------------------------------------

    /**
     * Makes this mob a summon of {@code newOwner}. Summons follow and assist their owner, never
     * attack allies, are not saved with the world and vanish when the owner leaves.
     *
     * @param duration lifetime in ticks, 0 for unlimited
     */
    public void setOwner(Entity newOwner, int duration) {
        owner = newOwner.getUniqueId();
        lifetime = duration;
        entity.getPersistentDataContainer().set(plugin.keyOwner(), PersistentDataType.STRING, owner.toString());
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
    }

    public UUID ownerId() {
        return owner;
    }

    public Entity owner() {
        return owner == null ? null : Bukkit.getEntity(owner);
    }

    public int remainingLifetime() {
        return lifetime;
    }

    private void tickOwner() {
        Entity o = Bukkit.getEntity(owner);
        if (o == null || !o.isValid()) {
            expire();
            return;
        }
        if (lifetime > 0 && --lifetime == 0) {
            expire();
            return;
        }
        if (!o.getWorld().equals(entity.getWorld())) {
            entity.teleport(o.getLocation());
            return;
        }
        if (ticks % 10 != 0 || !type.followOwner() || !(entity instanceof Mob mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && (!target.isValid() || plugin.mobs().allies(entity, target))) {
            mob.setTarget(null);
            target = null;
        }
        double distance = o.getLocation().distance(entity.getLocation());
        if (distance > type.teleportDistance()) {
            entity.teleport(o.getLocation());
        } else if (target == null && distance > type.followDistance()) {
            mob.getPathfinder().moveTo(o.getLocation(), 1.2);
        }
    }

    /** Ends a summon: plays {@code despawn} (or {@code death}) and removes the entity. */
    public void expire() {
        if (dying) {
            return;
        }
        lastLocation = entity.getLocation();
        lastBodyYaw = entity.getBodyYaw();
        dying = true;
        String state = model != null && model.blueprint.animation(type.animation("despawn")) != null ? "despawn" : "death";
        beginDeathAnimation(state);
        entity.remove();
    }

    public Location location() {
        return dying || !entity.isValid() ? lastLocation.clone() : entity.getLocation();
    }

    // --- model ---------------------------------------------------------------

    public boolean applyModel(String id, float scale) {
        ModelBlueprint bp = plugin.models().get(id);
        if (bp == null) {
            plugin.getLogger().warning(type.id() + ": 모델 '" + id + "' 이 없습니다");
            return false;
        }
        if (model != null) {
            model.remove();
        }
        model = new ModelInstance(plugin, bp, scale);
        model.spawn(location(), entity.getBodyYaw());
        model.setState(type.animation("idle"));
        entity.getPersistentDataContainer().set(plugin.keyModel(), PersistentDataType.STRING,
                bp.id + ";" + scale);
        entity.setInvisible(true);
        return true;
    }

    /** Removes the model for good (also forgets it on the entity). */
    public void removeModel() {
        if (model != null) {
            model.remove();
            model = null;
        }
        if (entity.isValid()) {
            entity.getPersistentDataContainer().remove(plugin.keyModel());
            entity.setInvisible(false);
        }
    }

    /** Despawns the display entities only; the model is restored when the entity loads again. */
    void despawnModel() {
        if (model != null) {
            model.remove();
            model = null;
        }
    }

    /** Re-creates the model saved on the entity (after chunk load / reload). */
    void restoreModel() {
        String saved = entity.getPersistentDataContainer().get(plugin.keyModel(), PersistentDataType.STRING);
        if (saved != null) {
            String[] parts = saved.split(";");
            float scale = 1f;
            if (parts.length > 1) {
                try {
                    scale = Float.parseFloat(parts[1]);
                } catch (NumberFormatException ignored) {
                    // keep 1
                }
            }
            applyModel(parts[0], scale);
        } else if (type.modelId() != null) {
            applyModel(type.modelId(), type.modelScale());
        }
    }

    @Override
    public Location bonePosition(String name) {
        return model == null ? null : model.partLocation(name, location());
    }

    /** Plays the animation mapped to a state (attack, hurt, spawn, ...) if the model has it. */
    public void playState(String state, int priority) {
        if (model != null) {
            model.play(type.animation(state), 1.0, LoopMode.ONCE, -1, -1, priority);
        }
    }

    // --- lifecycle -----------------------------------------------------------

    /** @return false when this mob is finished and should be forgotten */
    boolean tick() {
        if (removed) {
            return false;
        }
        if (!dying && !entity.isValid()) {
            // unloaded or removed without dying
            despawnModel();
            removed = true;
            return false;
        }
        ticks++;
        Location loc;
        if (dying) {
            loc = lastLocation;
        } else {
            loc = entity.getLocation();
            if (loc.getWorld().equals(lastLocation.getWorld())) {
                double dx = loc.getX() - lastLocation.getX();
                double dz = loc.getZ() - lastLocation.getZ();
                if (dx * dx + dz * dz > 0.0004) {
                    movingTicks = 3;
                } else if (movingTicks > 0) {
                    movingTicks--;
                }
            }
            lastLocation = loc;
            lastBodyYaw = entity.getBodyYaw();
            if (model != null) {
                model.setState(type.animation(movingTicks > 0 ? "walk" : "idle"));
            }
        }

        if (model != null) {
            float headYaw = dying ? 0 : wrap(loc.getYaw() - lastBodyYaw);
            float pitch = dying ? 0 : loc.getPitch();
            List<EffectKeyframe> fired = model.tick(loc, lastBodyYaw, headYaw, pitch, ticks / 20.0);
            Location center = loc.clone().add(0, entity.getHeight() / 2, 0);
            for (EffectKeyframe effect : fired) {
                KeyframeEffects.run(plugin, effect, this, center, type.id() + " 모델 타임라인", () ->
                        SkillMeta.create(plugin, entity, this, entity instanceof Mob mob ? mob.getTarget() : null));
            }
        }

        if (dying) {
            if (++deathTicks >= deathDuration) {
                despawnModel();
                removed = true;
                return false;
            }
            return true;
        }

        for (CompiledSkillLine line : type.timers()) {
            if (ticks % line.timerInterval() == 0) {
                line.execute(SkillMeta.create(plugin, entity, this, null));
            }
        }
        if (owner != null && !dying) {
            tickOwner();
        }
        return true;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return Math.max(-85f, Math.min(85f, d));
    }

    public void trigger(SkillTrigger trigger, Entity cause) {
        if (removed) {
            return;
        }
        fire(trigger, cause);
        if (trigger == SkillTrigger.ATTACK || trigger == SkillTrigger.DAMAGED) {
            fire(SkillTrigger.COMBAT, cause);
        }
    }

    private void fire(SkillTrigger trigger, Entity cause) {
        List<CompiledSkillLine> lines = type.lines(trigger);
        if (lines.isEmpty()) {
            return;
        }
        SkillMeta meta = SkillMeta.create(plugin, entity, this, cause);
        for (CompiledSkillLine line : lines) {
            line.execute(meta);
        }
    }

    void onDamaged(Entity damager) {
        if (dying) {
            return;
        }
        if (model != null) {
            model.tint(plugin.settings().hurtTint(), plugin.settings().hurtTintTicks());
            playState("hurt", 5);
        }
        trigger(SkillTrigger.DAMAGED, damager);
    }

    void onAttack(Entity victim) {
        if (dying) {
            return;
        }
        playState("attack", 6);
        trigger(SkillTrigger.ATTACK, victim);
    }

    void onDeath(Entity killer) {
        if (dying) {
            return;
        }
        lastLocation = entity.getLocation();
        lastBodyYaw = entity.getBodyYaw();
        dying = true;
        trigger(SkillTrigger.DEATH, killer);
        beginDeathAnimation("death");
    }

    private void beginDeathAnimation(String state) {
        if (model == null) {
            deathDuration = 0;
            return;
        }
        model.setState(null);
        model.stopAll(0);
        String death = type.animation(state);
        if (model.play(death, 1.0, LoopMode.HOLD, 2, 0, 100)) {
            AnimationLayer layer = model.layer(death);
            float length = layer == null ? 1f : layer.animation.length;
            deathDuration = Math.max(1, (int) Math.ceil(length * 20) + 5);
        } else {
            model.tint(Color.fromRGB(0xFF5050), 0);
            deathDuration = 10;
        }
    }

    public long ticks() {
        return ticks;
    }
}
