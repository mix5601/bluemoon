package dev.bluemoon.model.runtime;

import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModel.Animation;
import dev.bluemoon.model.bbmodel.BBModel.Cube;
import dev.bluemoon.model.bbmodel.BBModel.Group;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A Blockbench model converted into a flat bone list ready to be rendered with item display entities.
 * Every bone that owns cubes becomes one item model in the resource pack.
 */
public final class ModelBlueprint {

    /** Minecraft item model elements must stay within -16..32, i.e. 24 pixels around the center. */
    public static final float MAX_EXTENT = 24f;
    private static final float[] ALLOWED_ANGLES = {-45f, -22.5f, 0f, 22.5f, 45f};

    public final String id;
    public final BBModel source;
    /** Parents always come before their children. */
    public final List<Bone> bones = new ArrayList<>();
    public final Map<String, Bone> bonesByName = new LinkedHashMap<>();
    public final Map<String, Bone> bonesByUuid = new LinkedHashMap<>();
    public final Map<String, LocatorPoint> locators = new LinkedHashMap<>();
    public final List<String> warnings = new ArrayList<>();

    public static final class Bone {
        public int index;
        public String name;
        public String uuid;
        public Bone parent;
        /** Pivot in Blockbench pixels (model space). */
        public final Vector3f origin = new Vector3f();
        /** Rest rotation in degrees (Blockbench euler, ZYX order). */
        public final Vector3f rotation = new Vector3f();
        public final List<Cube> cubes = new ArrayList<>();
        /** Geometry is shrunk by this factor in the item model and scaled back up by the display entity. */
        public float geometryScale = 1f;
        /** Resource path below the namespace, e.g. {@code golem/head}; null when the bone has no geometry. */
        public String modelPath;
        /** Rotates with the entity's head (bone named {@code head} or prefixed {@code h_}). */
        public boolean head;
        /** Generated to hold a cube whose rotation Minecraft cannot represent. */
        public boolean synthetic;
        public boolean visibleByDefault = true;

        public boolean hasGeometry() {
            return modelPath != null;
        }
    }

    public record LocatorPoint(String name, Bone bone, Vector3f position) {
    }

    private ModelBlueprint(String id, BBModel source) {
        this.id = id;
        this.source = source;
    }

    public Animation animation(String name) {
        return name == null ? null : source.animations.get(name.toLowerCase(Locale.ROOT));
    }

    public Bone bone(String name) {
        return name == null ? null : bonesByName.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Bone> bones() {
        return Collections.unmodifiableList(bones);
    }

    public static ModelBlueprint build(String id, BBModel model) {
        ModelBlueprint bp = new ModelBlueprint(id, model);
        bp.warnings.addAll(model.warnings);
        Set<String> usedPaths = new HashSet<>();

        if (!model.rootCubes.isEmpty() || !model.rootLocators.isEmpty()) {
            Group root = new Group();
            root.uuid = "";
            root.name = "root";
            root.cubes.addAll(model.rootCubes);
            root.locators.addAll(model.rootLocators);
            bp.addGroup(root, null, usedPaths);
        }
        for (Group g : model.rootGroups) {
            bp.addGroup(g, null, usedPaths);
        }
        return bp;
    }

    private void addGroup(Group g, Bone parent, Set<String> usedPaths) {
        Bone bone = newBone(g.name, g.uuid, parent, g.origin, g.rotation, false);
        bone.visibleByDefault = g.visible;
        String lower = g.name.toLowerCase(Locale.ROOT);
        bone.head = lower.equals("head") || lower.startsWith("h_");
        boolean hitbox = lower.equals("hitbox");

        if (!hitbox) {
            int syntheticIndex = 0;
            for (Cube cube : g.cubes) {
                if (isRepresentable(cube.rotation)) {
                    bone.cubes.add(cube);
                } else {
                    // Minecraft only supports one axis in 22.5 degree steps, so rotate a dedicated bone instead.
                    Bone holder = newBone(g.name + "_r" + (syntheticIndex++), null, bone,
                            cube.origin, cube.rotation, true);
                    holder.cubes.add(cube.copyWithoutRotation());
                    finishGeometry(holder, usedPaths);
                }
            }
        }
        finishGeometry(bone, usedPaths);

        for (BBModel.Locator l : g.locators) {
            locators.put(l.name.toLowerCase(Locale.ROOT),
                    new LocatorPoint(l.name, bone, new Vector3f(l.position[0], l.position[1], l.position[2])));
        }
        for (Group child : g.groups) {
            addGroup(child, bone, usedPaths);
        }
    }

    private Bone newBone(String name, String uuid, Bone parent, float[] origin, float[] rotation, boolean synthetic) {
        Bone bone = new Bone();
        bone.index = bones.size();
        bone.name = name;
        bone.uuid = uuid;
        bone.parent = parent;
        bone.origin.set(origin[0], origin[1], origin[2]);
        bone.rotation.set(rotation[0], rotation[1], rotation[2]);
        bone.synthetic = synthetic;
        bones.add(bone);
        bonesByName.putIfAbsent(name.toLowerCase(Locale.ROOT), bone);
        if (uuid != null && !uuid.isEmpty()) {
            bonesByUuid.put(uuid, bone);
        }
        return bone;
    }

    private void finishGeometry(Bone bone, Set<String> usedPaths) {
        if (bone.cubes.isEmpty()) {
            return;
        }
        float extent = 0;
        for (Cube c : bone.cubes) {
            for (int i = 0; i < 3; i++) {
                float o = i == 0 ? bone.origin.x : i == 1 ? bone.origin.y : bone.origin.z;
                extent = Math.max(extent, Math.abs(c.from[i] - c.inflate - o));
                extent = Math.max(extent, Math.abs(c.to[i] + c.inflate - o));
            }
        }
        bone.geometryScale = Math.max(1f, extent / MAX_EXTENT * 1.0001f);

        String base = id + "/" + sanitize(bone.name);
        String path = base;
        int n = 1;
        while (!usedPaths.add(path)) {
            path = base + "_" + (n++);
        }
        bone.modelPath = path;
    }

    /** True when Minecraft's block model format can express the rotation directly. */
    static boolean isRepresentable(float[] r) {
        int axes = 0;
        float angle = 0;
        for (float v : r) {
            if (v != 0) {
                axes++;
                angle = v;
            }
        }
        return axes == 0 || (axes == 1 && isAllowedAngle(angle));
    }

    static boolean isAllowedAngle(float angle) {
        for (float a : ALLOWED_ANGLES) {
            if (Math.abs(a - angle) < 1e-3) {
                return true;
            }
        }
        return false;
    }

    public static String sanitize(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return s.isEmpty() ? "bone" : s;
    }
}
