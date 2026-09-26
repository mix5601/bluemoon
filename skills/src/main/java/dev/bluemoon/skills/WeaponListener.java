package dev.bluemoon.skills;

import dev.bluemoon.skill.CompiledSkillLine;
import dev.bluemoon.skill.SkillMeta;
import dev.bluemoon.skill.SkillTrigger;
import dev.bluemoon.skill.mechanics.BuiltinMechanics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Runs the automatic skills of the weapon a player is holding. */
final class WeaponListener implements Listener {

    private final BlueMoonSkills plugin;
    private long ticks;

    WeaponListener(BlueMoonSkills plugin) {
        this.plugin = plugin;
    }

    private WeaponType held(Player player) {
        return plugin.weapons().type(plugin.weapons().weaponOf(player.getInventory().getItemInMainHand()));
    }

    private void fire(Player player, WeaponType type, SkillTrigger trigger, Entity cause) {
        run(player, type.lines(trigger), cause);
        if (trigger == SkillTrigger.ATTACK || trigger == SkillTrigger.DAMAGED) {
            run(player, type.lines(SkillTrigger.COMBAT), cause);
        }
    }

    private void run(Player player, Iterable<CompiledSkillLine> lines, Entity cause) {
        SkillMeta meta = null;
        for (CompiledSkillLine line : lines) {
            if (meta == null) {
                meta = SkillMeta.create(plugin, player, null, cause);
            }
            line.execute(meta);
        }
    }

    /** Called every tick for {@code ~onTimer} weapon skills. */
    void tick() {
        ticks++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            WeaponType type = held(player);
            if (type == null || type.timers.isEmpty() || player.isDead()) {
                continue;
            }
            for (CompiledSkillLine line : type.timers) {
                if (ticks % line.timerInterval() == 0) {
                    line.execute(SkillMeta.create(plugin, player, null, null));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player attacker && !BuiltinMechanics.isSkillDamage()) {
            WeaponType type = held(attacker);
            if (type != null) {
                fire(attacker, type, SkillTrigger.ATTACK, event.getEntity());
            }
        }
        if (event.getEntity() instanceof Player victim) {
            WeaponType type = held(victim);
            if (type != null) {
                fire(victim, type, SkillTrigger.DAMAGED, damager);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            WeaponType type = held(killer);
            if (type != null) {
                fire(killer, type, SkillTrigger.KILL, event.getEntity());
            }
        }
    }
}
