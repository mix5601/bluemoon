package dev.bluemoon.skill;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

/** Result of a targeter: entities and/or locations. */
public record Targets(List<Entity> entities, List<Location> locations) {

    public static final Targets NONE = new Targets(List.of(), List.of());

    public static Targets of(Entity entity) {
        return entity == null ? NONE : new Targets(List.of(entity), List.of());
    }

    public static Targets of(Location location) {
        return location == null ? NONE : new Targets(List.of(), List.of(location));
    }

    public static Targets entities(List<? extends Entity> entities) {
        return new Targets(List.copyOf(entities), List.of());
    }

    public static Targets locations(List<Location> locations) {
        return new Targets(List.of(), List.copyOf(locations));
    }

    public boolean isEmpty() {
        return entities.isEmpty() && locations.isEmpty();
    }
}
