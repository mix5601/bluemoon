package dev.bluemoon.skill.mechanics;

import dev.bluemoon.skill.SkillRegistry;

/** Registers every built-in mechanic with its MythicMobs style aliases. */
public final class BuiltinMechanics {

    private BuiltinMechanics() {
    }

    /** True while a skill mechanic is dealing damage (used to avoid {@code ~onAttack} loops). */
    public static boolean isSkillDamage() {
        return CombatMechanics.isSkillDamage();
    }

    public static void register(SkillRegistry m) {
        // combat
        m.registerMechanic(CombatMechanics.Damage::new, "damage", "d");
        m.registerMechanic(CombatMechanics.Heal::new, "heal");
        m.registerMechanic(CombatMechanics.Ignite::new, "ignite");
        m.registerMechanic(CombatMechanics.Potion::new, "potion");
        m.registerMechanic(p -> new CombatMechanics.Lightning(p, false), "lightning");
        m.registerMechanic(p -> new CombatMechanics.Lightning(p, true), "effect:lightning", "e:lightning");
        m.registerMechanic(CombatMechanics.Summon::new, "summon");
        m.registerMechanic(CombatMechanics.Command::new, "command", "cmd");
        m.registerMechanic(CombatMechanics.Remove::new, "remove");
        m.registerMechanic(CombatMechanics.Invulnerable::new, "invulnerable", "iframes");

        // effects
        m.registerMechanic(EffectMechanics.ParticleEffect::new, "particle", "effect:particle", "e:p");
        m.registerMechanic(EffectMechanics.ParticleRing::new, "particlering", "effect:particlering", "e:pr");
        m.registerMechanic(EffectMechanics.ParticleLine::new, "particleline", "effect:particleline", "e:pl");
        m.registerMechanic(EffectMechanics.Sound::new, "sound", "effect:sound", "e:s");
        m.registerMechanic(EffectMechanics.Message::new, "message", "msg");
        m.registerMechanic(EffectMechanics.ActionMessage::new, "actionmessage", "actionbar");
        m.registerMechanic(EffectMechanics.SendTitle::new, "sendtitle", "title");

        // movement
        m.registerMechanic(MovementMechanics.Throw::new, "throw");
        m.registerMechanic(MovementMechanics.Pull::new, "pull");
        m.registerMechanic(MovementMechanics.Leap::new, "leap", "jump");
        m.registerMechanic(MovementMechanics.Lunge::new, "lunge");
        m.registerMechanic(MovementMechanics.Velocity::new, "velocity");
        m.registerMechanic(MovementMechanics.Teleport::new, "teleport", "tp");

        // meta
        m.registerMechanic(MetaMechanics.SkillCall::new, "skill", "metaskill", "meta");
        m.registerMechanic(MetaMechanics.RandomSkill::new, "randomskill");
        m.registerMechanic(MetaMechanics.Repeat::new, "repeat");
        m.registerMechanic(MetaMechanics.Projectile::new, "projectile");

        // blockbench model
        m.registerMechanic(ModelMechanics.Model::new, "model");
        m.registerMechanic(ModelMechanics.Animation::new, "animation", "anim", "state", "playanimation");
        m.registerMechanic(ModelMechanics.StopAnimation::new, "stopanimation", "stopanim", "stopstate");
        m.registerMechanic(ModelMechanics.Tint::new, "tint", "modeltint");
        m.registerMechanic(ModelMechanics.BoneVisibility::new, "bonevisibility", "partvisibility");
        m.registerMechanic(ModelMechanics.ModelEffect::new, "modeleffect", "effectmodel", "vfx");
    }
}
