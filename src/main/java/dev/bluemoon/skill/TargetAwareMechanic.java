package dev.bluemoon.skill;

/**
 * Mechanics that hand their targets to other skills ({@code skill}, {@code randomskill}) instead of
 * running once per target.
 */
public interface TargetAwareMechanic {

    /**
     * @param targets  the resolved targets (explicit targeter), or the inherited targets otherwise
     * @param explicit whether the line had its own targeter
     */
    void castTargets(SkillMeta meta, Targets targets, boolean explicit);
}
