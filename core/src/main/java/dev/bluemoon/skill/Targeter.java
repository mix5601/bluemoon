package dev.bluemoon.skill;

@FunctionalInterface
public interface Targeter {

    Targets resolve(SkillMeta meta);
}
