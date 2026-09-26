package dev.bluemoon.model.bbmodel;

import dev.bluemoon.model.molang.Molang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory representation of a Blockbench {@code .bbmodel} project. */
public final class BBModel {

    public String name = "";
    public String format = "free";
    public int resolutionWidth = 16;
    public int resolutionHeight = 16;
    public final List<Texture> textures = new ArrayList<>();
    public final List<Group> rootGroups = new ArrayList<>();
    public final List<Cube> rootCubes = new ArrayList<>();
    public final List<Locator> rootLocators = new ArrayList<>();
    public final Map<String, Animation> animations = new LinkedHashMap<>();
    public final List<String> warnings = new ArrayList<>();
    /** Item display settings (Java Block/Item format "Display" tab), or null. */
    public com.google.gson.JsonObject display;

    public static final class Texture {
        public int index;
        public String uuid;
        public String name;
        public byte[] png;
        public int width;
        public int height;
        public int uvWidth;
        public int uvHeight;
        public int frameTime = 1;
    }

    public static final class Face {
        public float[] uv;
        /** Index into {@link #textures}, or -1 for no texture. */
        public int texture = -1;
        public int rotation;
    }

    public static final class Cube {
        public String uuid;
        public String name;
        public float[] from;
        public float[] to;
        public float[] origin = new float[3];
        public float[] rotation = new float[3];
        public float inflate;
        public final Map<String, Face> faces = new LinkedHashMap<>();

        public Cube copyWithoutRotation() {
            Cube c = new Cube();
            c.uuid = uuid;
            c.name = name;
            c.from = from.clone();
            c.to = to.clone();
            c.origin = origin.clone();
            c.rotation = new float[3];
            c.inflate = inflate;
            c.faces.putAll(faces);
            return c;
        }
    }

    public static final class Locator {
        public String uuid;
        public String name;
        public float[] position = new float[3];
    }

    public static final class Group {
        public String uuid;
        public String name;
        public float[] origin = new float[3];
        public float[] rotation = new float[3];
        public boolean visible = true;
        public final List<Group> groups = new ArrayList<>();
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Locator> locators = new ArrayList<>();
    }

    public enum LoopMode { ONCE, LOOP, HOLD }

    public enum Interpolation { LINEAR, CATMULLROM, STEP, BEZIER }

    public static final class Keyframe {
        public float time;
        public Interpolation interpolation = Interpolation.LINEAR;
        /** Value used when approaching this keyframe. */
        public Molang.Expr[] pre;
        /** Value used when leaving this keyframe (differs from {@link #pre} only for split keyframes). */
        public Molang.Expr[] post;
        public float[] bezierLeftTime = {-0.1f, -0.1f, -0.1f};
        public float[] bezierLeftValue = new float[3];
        public float[] bezierRightTime = {0.1f, 0.1f, 0.1f};
        public float[] bezierRightValue = new float[3];
    }

    public static final class BoneAnimator {
        public String boneUuid;
        public String boneName;
        public final List<Keyframe> rotation = new ArrayList<>();
        public final List<Keyframe> position = new ArrayList<>();
        public final List<Keyframe> scale = new ArrayList<>();
    }

    public enum EffectType { SOUND, PARTICLE, TIMELINE }

    public record EffectKeyframe(float time, EffectType type, String effect, String locator, String script) {
    }

    public static final class Animation {
        public String name;
        public LoopMode loop = LoopMode.ONCE;
        public float length;
        /** Keyed by bone (group) uuid. */
        public final Map<String, BoneAnimator> bones = new LinkedHashMap<>();
        /** Sorted by time. */
        public final List<EffectKeyframe> effects = new ArrayList<>();
    }
}
