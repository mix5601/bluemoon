package dev.bluemoon.util;

import dev.bluemoon.skill.SkillMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Color codes ({@code &c}) and MythicMobs style placeholders ({@code <caster.name>}). */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s);
    }

    public static String name(Entity e) {
        if (e == null) {
            return "";
        }
        if (e instanceof Player p) {
            return p.getName();
        }
        Component custom = e.customName();
        return custom != null ? PlainTextComponentSerializer.plainText().serialize(custom) : e.getName();
    }

    public static String placeholders(String s, SkillMeta meta, Entity target) {
        if (s.indexOf('<') < 0) {
            return s;
        }
        String out = s;
        out = replaceEntity(out, "caster", meta.caster);
        out = replaceEntity(out, "target", target);
        out = replaceEntity(out, "trigger", meta.trigger);
        return out;
    }

    private static String replaceEntity(String s, String prefix, Entity e) {
        if (!s.contains("<" + prefix + ".")) {
            return s;
        }
        String hp = "";
        String mhp = "";
        if (e instanceof LivingEntity le) {
            hp = String.format(Locale.ROOT, "%.1f", le.getHealth());
            var inst = le.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            mhp = inst == null ? "" : String.format(Locale.ROOT, "%.1f", inst.getValue());
        }
        return s.replace("<" + prefix + ".name>", name(e))
                .replace("<" + prefix + ".uuid>", e == null ? "" : e.getUniqueId().toString())
                .replace("<" + prefix + ".hp>", hp)
                .replace("<" + prefix + ".mhp>", mhp)
                .replace("<" + prefix + ".x>", e == null ? "" : String.valueOf(e.getLocation().getBlockX()))
                .replace("<" + prefix + ".y>", e == null ? "" : String.valueOf(e.getLocation().getBlockY()))
                .replace("<" + prefix + ".z>", e == null ? "" : String.valueOf(e.getLocation().getBlockZ()));
    }
}
