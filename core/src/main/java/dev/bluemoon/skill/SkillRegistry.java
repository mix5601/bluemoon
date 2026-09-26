package dev.bluemoon.skill;

import dev.bluemoon.skill.parse.Params;

import java.util.function.Function;

/** Where mechanics, targeters and conditions are registered (implemented by {@link SkillManager}). */
public interface SkillRegistry {

    void registerMechanic(Function<Params, Mechanic> factory, String... names);

    void registerTargeter(Function<Params, Targeter> factory, String... names);

    void registerCondition(Function<Params, Condition> factory, String... names);

    /** Registers every built-in mechanic, targeter and condition. */
    static void registerBuiltins(SkillRegistry registry) {
        dev.bluemoon.skill.mechanics.BuiltinMechanics.register(registry);
        Targeters.register(registry);
        Conditions.register(registry);
    }
}
