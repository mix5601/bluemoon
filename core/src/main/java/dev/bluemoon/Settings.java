package dev.bluemoon;

import dev.bluemoon.util.Particles;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;

/** Values from config.yml. */
public record Settings(
        int packMinFormat,
        int packMaxFormat,
        String packDescription,
        boolean packServerEnabled,
        String packServerBind,
        int packServerPort,
        String packPublicUrl,
        boolean packRequired,
        String packPrompt,
        float viewRange,
        int interpolationTicks,
        int teleportTicks,
        int fadeTicks,
        Color hurtTint,
        int hurtTintTicks
) {

    public static Settings load(FileConfiguration c) {
        return new Settings(
                c.getInt("resource-pack.min-format", 46),
                c.getInt("resource-pack.max-format", 99),
                c.getString("resource-pack.description", "BlueMoon models"),
                c.getBoolean("resource-pack.server.enabled", false),
                c.getString("resource-pack.server.bind", "0.0.0.0"),
                c.getInt("resource-pack.server.port", 8163),
                c.getString("resource-pack.server.public-url", "http://127.0.0.1:8163"),
                c.getBoolean("resource-pack.server.required", false),
                c.getString("resource-pack.server.prompt", "&b커스텀 몹 모델 리소스팩을 적용해 주세요."),
                (float) c.getDouble("model.view-range", 1.0),
                Math.max(0, c.getInt("model.interpolation-ticks", 1)),
                Math.max(0, c.getInt("model.teleport-ticks", 2)),
                Math.max(0, c.getInt("model.fade-ticks", 3)),
                Particles.color(c.getString("model.hurt-tint", "FF7070"), Color.fromRGB(0xFF7070)),
                Math.max(0, c.getInt("model.hurt-tint-ticks", 6))
        );
    }
}
