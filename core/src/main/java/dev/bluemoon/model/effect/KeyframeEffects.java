package dev.bluemoon.model.effect;

import dev.bluemoon.BlueMoonPlugin;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.runtime.ModelHost;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.util.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;

import java.util.Locale;
import java.util.function.Supplier;

/** Runs Blockbench effect keyframes (sound / particle / timeline script) for any model. */
public final class KeyframeEffects {

    private KeyframeEffects() {
    }

    /**
     * @param center fallback position for particles without a locator
     * @param meta   supplies the skill context for timeline scripts
     */
    public static void run(BlueMoonPlugin plugin, EffectKeyframe effect, ModelHost host, Location center,
                           String where, Supplier<SkillMeta> meta) {
        switch (effect.type()) {
            case SOUND -> {
                String sound = effect.effect();
                if (sound != null && !sound.isBlank()) {
                    center.getWorld().playSound(center, sound.toLowerCase(Locale.ROOT), SoundCategory.HOSTILE, 1f, 1f);
                }
            }
            case PARTICLE -> {
                Particle particle = Particles.parse(effect.effect());
                if (particle == null) {
                    return;
                }
                Location at = null;
                if (effect.locator() != null && !effect.locator().isBlank()) {
                    at = host.bonePosition(effect.locator());
                }
                if (at == null) {
                    at = center;
                }
                Particles.spawn(particle, at, 8, 0.15, 0.15, 0.15, 0.01, Color.WHITE, 1f, Material.STONE);
            }
            case TIMELINE -> {
                String script = effect.script();
                if (script != null && !script.isBlank()) {
                    plugin.skills().compileScript(script, where).execute(meta.get());
                }
            }
        }
    }
}
