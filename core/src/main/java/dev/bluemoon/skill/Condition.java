package dev.bluemoon.skill;

import org.bukkit.entity.Entity;

@FunctionalInterface
public interface Condition {

    /**
     * @param subject the caster for normal conditions, the target for {@code ?~} target conditions
     */
    boolean test(SkillMeta meta, Entity subject);

    /** A condition together with its negation flag. */
    record Entry(String name, Condition condition, boolean negate, boolean target) {
        public boolean check(SkillMeta meta, Entity subject) {
            if (subject == null) {
                return false;
            }
            return condition.test(meta, subject) != negate;
        }
    }
}
