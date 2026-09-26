package dev.bluemoon.skills;

/** Outcome of {@link BlueMoonSkills#cast}. */
public enum CastResult {
    SUCCESS,
    /** No skill with that name. */
    UNKNOWN_SKILL,
    /** Still on cooldown. */
    COOLDOWN,
    /** A {@code Conditions:} / {@code TargetConditions:} entry failed. */
    CONDITIONS,
    /** A {@link PlayerSkillCastEvent} listener cancelled it. */
    CANCELLED
}
