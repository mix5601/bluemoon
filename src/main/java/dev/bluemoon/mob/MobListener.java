package dev.bluemoon.mob;

import dev.bluemoon.BlueMoon;
import dev.bluemoon.skill.SkillTrigger;
import dev.bluemoon.skill.mechanics.BuiltinMechanics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/** Turns Bukkit events into skill triggers and keeps models in sync with entity loading. */
public final class MobListener implements Listener {

    private final BlueMoon plugin;
    private final MobManager mobs;

    public MobListener(BlueMoon plugin) {
        this.plugin = plugin;
        this.mobs = plugin.mobs();
    }

    private static Entity resolve(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity damager = event instanceof EntityDamageByEntityEvent by ? resolve(by.getDamager()) : null;
        ActiveMob victim = mobs.get(event.getEntity());
        if (victim != null) {
            mobs.damaged(victim, damager);
        }
        if (damager != null && !BuiltinMechanics.isSkillDamage()) {
            ActiveMob attacker = mobs.get(damager);
            if (attacker != null && attacker != victim) {
                mobs.attacked(attacker, event.getEntity());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Entity killer = dead.getKiller();
        if (killer == null && dead.getLastDamageCause() instanceof EntityDamageByEntityEvent by) {
            killer = resolve(by.getDamager());
        }

        ActiveMob mob = mobs.get(dead);
        if (mob != null) {
            MobType type = mob.type();
            if (type.preventDrops() || !type.drops().isEmpty()) {
                event.getDrops().clear();
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (MobType.Drop drop : type.drops()) {
                if (rnd.nextDouble() < drop.chance()) {
                    int amount = drop.min() >= drop.max() ? drop.min() : rnd.nextInt(drop.min(), drop.max() + 1);
                    if (amount > 0) {
                        event.getDrops().add(new ItemStack(drop.material(), amount));
                    }
                }
            }
            if (type.exp() >= 0) {
                event.setDroppedExp(type.exp());
            }
            mobs.died(mob, killer);
        }

        ActiveMob killerMob = mobs.get(killer);
        if (killerMob != null && killerMob != mob) {
            killerMob.trigger(SkillTrigger.KILL, dead);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ActiveMob mob = mobs.get(event.getRightClicked());
        if (mob != null) {
            mob.trigger(SkillTrigger.INTERACT, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null && event.getTarget() != null
                && mob.entity() instanceof org.bukkit.entity.Mob m && event.getTarget() != m.getTarget()) {
            mob.trigger(SkillTrigger.TARGET, event.getTarget());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByEntityEvent || event instanceof EntityCombustByBlockEvent) {
            return;
        }
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null && mob.type().preventSunburn()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        // e.g. zombie -> drowned would delete the entity the model is attached to
        if (mobs.get(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof ItemDisplay && mobs.isModelPart(e)) {
                e.remove();
            } else {
                mobs.attach(e);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.sendPack(event.getPlayer());
    }
}
