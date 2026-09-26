package dev.bluemoon.skill;

import java.util.Locale;

/** When a skill line on a mob runs, written as {@code ~onDamaged}, {@code ~onTimer:40}, ... */
public enum SkillTrigger {
    /** Default: when the mob attacks or is damaged. */
    COMBAT,
    ATTACK,
    DAMAGED,
    SPAWN,
    DEATH,
    TIMER,
    INTERACT,
    TARGET,
    KILL;

    public static SkillTrigger parse(String raw) {
        if (raw == null) {
            return COMBAT;
        }
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.startsWith("on")) {
            s = s.substring(2);
        }
        return switch (s) {
            case "combat" -> COMBAT;
            case "attack" -> ATTACK;
            case "damaged", "hurt" -> DAMAGED;
            case "spawn" -> SPAWN;
            case "death" -> DEATH;
            case "timer" -> TIMER;
            case "interact", "use" -> INTERACT;
            case "target", "entertcombat", "entercombat" -> TARGET;
            case "kill", "killplayer" -> KILL;
            default -> null;
        };
    }
}
