package dev.bluemoon.skill;

import dev.bluemoon.skill.parse.Params;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * A single action of a skill line. Subclasses override the cast methods matching {@link #mode()}.
 */
public abstract class Mechanic {

    public enum Mode {
        /** Runs once per target entity (location targets are ignored). */
        ENTITY,
        /** Runs once per location; entity targets are converted to their location. */
        LOCATION,
        /** Entity targets use {@link #castEntity}, location targets use {@link #castLocation}. */
        BOTH,
        /** Runs once regardless of targets. */
        NONE
    }

    protected final Params params;

    protected Mechanic(Params params) {
        this.params = params;
    }

    public Mode mode() {
        return Mode.ENTITY;
    }

    /** Targeter used when the line has none and no targets were inherited. */
    public String defaultTargeter() {
        return "self";
    }

    /** Whether lines without a targeter use targets inherited from a parent {@code skill{}} call. */
    public boolean usesInheritedTargets() {
        return true;
    }

    public void castEntity(SkillMeta meta, Entity target) {
    }

    public void castLocation(SkillMeta meta, Location target) {
    }

    public void cast(SkillMeta meta) {
    }
}
