package dev.bluemoon.model.runtime;

import org.bukkit.Location;

/** Something that renders a Blockbench model in the world (a mob or an effect). */
public interface ModelHost {

    ModelInstance model();

    /** Current world position of a bone pivot or locator, or null. */
    Location bonePosition(String name);
}
