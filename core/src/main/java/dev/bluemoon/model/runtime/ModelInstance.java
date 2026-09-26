package dev.bluemoon.model.runtime;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.Settings;
import dev.bluemoon.model.animation.AnimationLayer;
import dev.bluemoon.model.animation.PoseCalculator;
import dev.bluemoon.model.bbmodel.BBModel.Animation;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.runtime.ModelBlueprint.Bone;
import dev.bluemoon.model.runtime.ModelBlueprint.LocatorPoint;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A spawned Blockbench model: one {@link ItemDisplay} per bone with geometry, animated every tick.
 */
public final class ModelInstance {

    private static final Comparator<AnimationLayer> LAYER_ORDER =
            Comparator.comparingInt((AnimationLayer l) -> l.priority).thenComparingLong(l -> l.order);

    private final BlueMoonPlugin plugin;
    public final ModelBlueprint blueprint;
    private final PoseCalculator pose;
    private final ItemDisplay[] displays;
    private final Matrix4f[] lastSent;
    private final boolean[] hidden;
    private final List<AnimationLayer> layers = new ArrayList<>();
    private final Matrix4f tmp = new Matrix4f();
    private final float scale;
    private long order;
    private AnimationLayer stateLayer;
    private Color tint = Color.WHITE;
    private int tintTicks;
    private Location lastLocation;
    private float lastYaw = Float.NaN;
    private float lastBodyYaw;
    private float rootPitch;
    private boolean fullBright;
    private float lastPitch;

    public ModelInstance(BlueMoonPlugin plugin, ModelBlueprint blueprint, float scale) {
        this.plugin = plugin;
        this.blueprint = blueprint;
        this.scale = scale;
        this.pose = new PoseCalculator(blueprint);
        int n = blueprint.bones.size();
        this.displays = new ItemDisplay[n];
        this.lastSent = new Matrix4f[n];
        this.hidden = new boolean[n];
        for (Bone b : blueprint.bones) {
            hidden[b.index] = !b.visibleByDefault;
        }
    }

    public float scale() {
        return scale;
    }

    public void spawn(Location location, float bodyYaw) {
        Settings s = plugin.settings();
        pose.compute(layers, 0, 0, 0);
        Location at = location.clone();
        at.setYaw(bodyYaw);
        at.setPitch(rootPitch);
        for (Bone bone : blueprint.bones) {
            if (!bone.hasGeometry()) {
                continue;
            }
            Matrix4f m = pose.displayMatrix(bone, scale, new Matrix4f());
            ItemStack item = hidden[bone.index] ? new ItemStack(Material.AIR) : boneItem(bone, Color.WHITE);
            displays[bone.index] = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(plugin.keyModelPart(), PersistentDataType.BYTE, (byte) 1);
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(s.viewRange());
                d.setShadowRadius(0);
                d.setShadowStrength(0);
                d.setInterpolationDuration(s.interpolationTicks());
                d.setTeleportDuration(s.teleportTicks());
                d.setTransformationMatrix(m);
                if (fullBright) {
                    d.setBrightness(new Display.Brightness(15, 15));
                }
            });
            lastSent[bone.index] = m;
        }
        lastLocation = at;
        lastYaw = bodyYaw;
        lastBodyYaw = bodyYaw;
        lastPitch = rootPitch;
    }

    /** Renders at full light (glowing effects). Call before {@link #spawn}. */
    public void setFullBright(boolean fullBright) {
        this.fullBright = fullBright;
    }

    /** Tilts the whole model (used by projectile models); mobs keep 0. */
    public void setRootPitch(float pitch) {
        this.rootPitch = pitch;
    }

    private ItemStack boneItem(Bone bone, Color color) {
        ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey(plugin.namespace(), bone.modelPath));
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Advances animations and updates every display.
     *
     * @param headYaw head yaw relative to the body
     * @return animation effect keyframes that fired this tick
     */
    public List<EffectKeyframe> tick(Location location, float bodyYaw, float headYaw, float pitch, double lifeTime) {
        List<EffectKeyframe> fired = new ArrayList<>(0);
        Iterator<AnimationLayer> it = layers.iterator();
        while (it.hasNext()) {
            AnimationLayer layer = it.next();
            boolean wasStopping = layer.isStopping();
            List<EffectKeyframe> f = layer.advance(0.05);
            if (!f.isEmpty() && !wasStopping) {
                fired.addAll(f);
            }
            if (layer.isFinished()) {
                it.remove();
                if (layer == stateLayer) {
                    stateLayer = null;
                }
            }
        }

        if (tintTicks > 0 && --tintTicks == 0) {
            applyTint(Color.WHITE);
        }

        pose.compute(layers, headYaw, pitch, lifeTime);
        lastBodyYaw = bodyYaw;

        boolean moved = lastLocation == null
                || !location.getWorld().equals(lastLocation.getWorld())
                || location.distanceSquared(lastLocation) > 1e-6
                || Math.abs(bodyYaw - lastYaw) > 0.01f
                || Math.abs(rootPitch - lastPitch) > 0.01f;
        Location at = null;
        if (moved) {
            at = location.clone();
            at.setYaw(bodyYaw);
            at.setPitch(rootPitch);
            lastLocation = at;
            lastYaw = bodyYaw;
            lastPitch = rootPitch;
        }

        for (Bone bone : blueprint.bones) {
            ItemDisplay d = displays[bone.index];
            if (d == null || !d.isValid()) {
                continue;
            }
            if (at != null) {
                d.teleport(at);
            }
            Matrix4f m = pose.displayMatrix(bone, scale, tmp);
            Matrix4f last = lastSent[bone.index];
            if (last == null || !m.equals(last, 1e-4f)) {
                d.setTransformationMatrix(m);
                d.setInterpolationDelay(0);
                if (last == null) {
                    lastSent[bone.index] = new Matrix4f(m);
                } else {
                    last.set(m);
                }
            }
        }
        return fired;
    }

    /**
     * Plays an animation from the Blockbench file.
     *
     * @param mode    null to use the loop mode saved in Blockbench
     * @param fadeIn  ticks, negative for the configured default
     * @param fadeOut ticks, negative for the configured default
     */
    public boolean play(String name, double speed, LoopMode mode, int fadeIn, int fadeOut, int priority) {
        Animation animation = blueprint.animation(name);
        if (animation == null) {
            return false;
        }
        int def = plugin.settings().fadeTicks();
        stop(name, 0);
        AnimationLayer layer = new AnimationLayer(animation, mode != null ? mode : animation.loop, speed,
                fadeIn < 0 ? def : fadeIn, fadeOut < 0 ? def : fadeOut, priority, order++);
        layers.add(layer);
        layers.sort(LAYER_ORDER);
        return true;
    }

    /** Switches the looping base animation (idle / walk), cross-fading from the previous one. */
    public void setState(String name) {
        Animation animation = blueprint.animation(name);
        if (stateLayer != null && stateLayer.animation == animation) {
            return;
        }
        int fade = plugin.settings().fadeTicks();
        if (stateLayer != null) {
            stateLayer.stop(fade);
            stateLayer = null;
        }
        if (animation == null) {
            return;
        }
        stateLayer = new AnimationLayer(animation, LoopMode.LOOP, 1.0, fade, fade, 0, order++);
        layers.add(stateLayer);
        layers.sort(LAYER_ORDER);
    }

    public void stop(String name, int fadeOut) {
        int fade = fadeOut < 0 ? plugin.settings().fadeTicks() : fadeOut;
        for (AnimationLayer layer : layers) {
            if (layer != stateLayer && layer.name().equalsIgnoreCase(name)) {
                layer.stop(fade);
            }
        }
    }

    public void stopAll(int fadeOut) {
        int fade = fadeOut < 0 ? plugin.settings().fadeTicks() : fadeOut;
        for (AnimationLayer layer : layers) {
            if (layer != stateLayer) {
                layer.stop(fade);
            }
        }
    }

    public boolean isPlaying(String name) {
        for (AnimationLayer layer : layers) {
            if (!layer.isStopping() && !layer.isFinished() && layer.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public AnimationLayer layer(String name) {
        for (AnimationLayer layer : layers) {
            if (layer.name().equalsIgnoreCase(name) && !layer.isFinished()) {
                return layer;
            }
        }
        return null;
    }

    /** Colors every bone; {@code ticks <= 0} keeps the color until changed again. */
    public void tint(Color color, int ticks) {
        applyTint(color);
        tintTicks = Math.max(0, ticks);
    }

    private void applyTint(Color color) {
        tint = color;
        for (Bone bone : blueprint.bones) {
            ItemDisplay d = displays[bone.index];
            if (d != null && d.isValid() && !hidden[bone.index]) {
                d.setItemStack(boneItem(bone, color));
            }
        }
    }

    public boolean setBoneVisible(String name, boolean visible) {
        Bone bone = blueprint.bone(name);
        if (bone == null) {
            return false;
        }
        hidden[bone.index] = !visible;
        ItemDisplay d = displays[bone.index];
        if (d != null && d.isValid()) {
            d.setItemStack(visible ? boneItem(bone, tint) : new ItemStack(Material.AIR));
        }
        return true;
    }

    /** World position of a bone pivot or Blockbench locator, or null if unknown. */
    public Location partLocation(String name, Location base) {
        LocatorPoint locator = blueprint.locators.get(name.toLowerCase(java.util.Locale.ROOT));
        Bone bone;
        Vector3f point;
        if (locator != null) {
            bone = locator.bone();
            point = locator.position();
        } else {
            bone = blueprint.bone(name);
            if (bone == null) {
                return null;
            }
            point = bone.origin;
        }
        Vector3f offset = pose.pointOffset(bone, point, scale, lastBodyYaw, rootPitch, new Vector3f());
        return base.clone().add(offset.x, offset.y, offset.z);
    }

    public void remove() {
        for (int i = 0; i < displays.length; i++) {
            if (displays[i] != null) {
                displays[i].remove();
                displays[i] = null;
            }
        }
        layers.clear();
        stateLayer = null;
    }
}
