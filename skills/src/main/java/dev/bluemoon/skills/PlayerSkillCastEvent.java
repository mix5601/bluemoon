package dev.bluemoon.skills;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired before a player skill runs. Cancel it to block the cast (mana, class checks, ...).
 * Cooldowns are checked before this event, so a cancelled cast does not start the cooldown.
 */
public final class PlayerSkillCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String skill;
    private final Entity target;
    private boolean cancelled;

    public PlayerSkillCastEvent(Player player, String skill, Entity target) {
        super(player);
        this.skill = skill;
        this.target = target;
    }

    /** Skill name as written in the skills folder. */
    public String getSkill() {
        return skill;
    }

    /** The entity the player was looking at (becomes {@code @trigger}), may be null. */
    public Entity getTarget() {
        return target;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
