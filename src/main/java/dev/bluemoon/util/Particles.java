package dev.bluemoon.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

/** Particle lookup by name (vanilla key or enum name) and spawning with the right data type. */
public final class Particles {

    private static final Map<String, String> ALIASES = Map.of(
            "reddust", "dust",
            "redstone", "dust",
            "largeexplode", "explosion",
            "hugeexplosion", "explosion_emitter",
            "blockcrack", "block",
            "block_crack", "block",
            "iconcrack", "item",
            "mobspell", "entity_effect",
            "largesmoke", "large_smoke"
    );

    private Particles() {
    }

    public static Particle parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String s = name.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("minecraft:")) {
            s = s.substring(10);
        }
        String key = ALIASES.getOrDefault(s, s);
        NamespacedKey id = NamespacedKey.fromString(key);
        return id == null ? null : Registry.PARTICLE_TYPE.get(id);
    }

    public static Color color(String hex, Color def) {
        if (hex == null || hex.isBlank()) {
            return def;
        }
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static Material material(String name, Material def) {
        if (name == null || name.isBlank()) {
            return def;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }

    /**
     * Spawns a particle, supplying the data object the particle type requires.
     *
     * @return false if the particle needs data we cannot build
     */
    public static boolean spawn(Particle particle, Location loc, int count, double dx, double dy, double dz,
                                double speed, Color color, float size, Material material) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        Class<?> type = particle.getDataType();
        Object data;
        if (type == Void.class) {
            data = null;
        } else if (type == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color, size);
        } else if (type == Particle.DustTransition.class) {
            data = new Particle.DustTransition(color, color, size);
        } else if (type == Color.class) {
            data = color;
        } else if (type == BlockData.class) {
            try {
                data = material.createBlockData();
            } catch (IllegalArgumentException e) {
                data = Material.STONE.createBlockData();
            }
        } else if (type == ItemStack.class) {
            data = new ItemStack(material.isItem() ? material : Material.STONE);
        } else if (type == Float.class) {
            data = 0f;
        } else if (type == Integer.class) {
            data = 0;
        } else {
            return false;
        }
        world.spawnParticle(particle, loc, count, dx, dy, dz, speed, data);
        return true;
    }
}
