package dev.bluemoon.model.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bluemoon.model.bbmodel.BBModel.Animation;
import dev.bluemoon.model.bbmodel.BBModel.BoneAnimator;
import dev.bluemoon.model.bbmodel.BBModel.Cube;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.bbmodel.BBModel.EffectType;
import dev.bluemoon.model.bbmodel.BBModel.Face;
import dev.bluemoon.model.bbmodel.BBModel.Group;
import dev.bluemoon.model.bbmodel.BBModel.Interpolation;
import dev.bluemoon.model.bbmodel.BBModel.Keyframe;
import dev.bluemoon.model.bbmodel.BBModel.Locator;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.bbmodel.BBModel.Texture;
import dev.bluemoon.model.molang.Molang;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Reads Blockbench {@code .bbmodel} files (format 4.x / 5.x, "Generic Model" or "Java Block"). */
public final class BBModelParser {

    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    private final BBModel model = new BBModel();
    private final Map<String, JsonObject> elementsByUuid = new HashMap<>();
    private final Map<String, JsonObject> groupsByUuid = new HashMap<>();
    private float offsetX;
    private float offsetZ;

    private BBModelParser() {
    }

    public static BBModel parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            BBModel m = new BBModelParser().read(root, file.getParent());
            if (m.name.isEmpty()) {
                m.name = file.getFileName().toString().replaceFirst("\\.bbmodel$", "");
            }
            return m;
        } catch (IllegalStateException | ClassCastException e) {
            throw new IOException("bbmodel JSON 형식이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }

    public static BBModel parse(String json) {
        return new BBModelParser().read(JsonParser.parseString(json).getAsJsonObject(), null);
    }

    private BBModel read(JsonObject root, Path baseDir) {
        JsonObject meta = obj(root, "meta");
        if (meta != null) {
            model.format = str(meta, "model_format", "free");
        }
        model.name = str(root, "name", "");
        if ("java_block".equals(model.format)) {
            // Java block models live in 0..16 space, Blockbench shows them centered on 8,8.
            offsetX = -8;
            offsetZ = -8;
        }
        JsonObject resolution = obj(root, "resolution");
        if (resolution != null) {
            model.resolutionWidth = intOf(resolution, "width", 16);
            model.resolutionHeight = intOf(resolution, "height", 16);
        }

        readTextures(root, baseDir);

        JsonArray elements = arr(root, "elements");
        if (elements != null) {
            for (JsonElement e : elements) {
                JsonObject o = e.getAsJsonObject();
                elementsByUuid.put(str(o, "uuid", ""), o);
            }
        }
        JsonArray groups = arr(root, "groups");
        if (groups != null) {
            for (JsonElement e : groups) {
                JsonObject o = e.getAsJsonObject();
                groupsByUuid.put(str(o, "uuid", ""), o);
            }
        }

        JsonArray outliner = arr(root, "outliner");
        if (outliner != null) {
            for (JsonElement node : outliner) {
                if (node.isJsonPrimitive()) {
                    addElement(node.getAsString(), null);
                } else {
                    model.rootGroups.add(readGroup(node.getAsJsonObject()));
                }
            }
        } else {
            // no outliner: every element is at the root
            for (String uuid : elementsByUuid.keySet()) {
                addElement(uuid, null);
            }
        }

        readAnimations(root);
        return model;
    }

    private void readTextures(JsonObject root, Path baseDir) {
        JsonArray textures = arr(root, "textures");
        if (textures == null) {
            return;
        }
        int index = 0;
        for (JsonElement e : textures) {
            JsonObject o = e.getAsJsonObject();
            Texture t = new Texture();
            t.index = index++;
            t.uuid = str(o, "uuid", "");
            t.name = str(o, "name", "texture_" + t.index);
            t.frameTime = Math.max(1, intOf(o, "frame_time", 1));
            String source = str(o, "source", null);
            if (source != null && source.startsWith("data:")) {
                int comma = source.indexOf(',');
                try {
                    t.png = Base64.getDecoder().decode(source.substring(comma + 1));
                } catch (IllegalArgumentException ex) {
                    model.warnings.add("텍스처 '" + t.name + "' 의 base64 데이터를 읽을 수 없습니다");
                }
            }
            if (t.png == null) {
                String path = str(o, "path", null);
                if (path != null && baseDir != null) {
                    Path p = baseDir.resolve(Path.of(path).getFileName().toString());
                    try {
                        if (Files.isRegularFile(p)) {
                            t.png = Files.readAllBytes(p);
                        }
                    } catch (IOException ignored) {
                        // reported below
                    }
                }
            }
            if (t.png == null) {
                model.warnings.add("텍스처 '" + t.name + "' 가 bbmodel 안에 저장되어 있지 않습니다 (블록벤치에서 텍스처를 저장 후 다시 저장하세요)");
            } else {
                int[] size = pngSize(t.png);
                t.width = size[0];
                t.height = size[1];
            }
            t.uvWidth = intOf(o, "uv_width", model.resolutionWidth);
            t.uvHeight = intOf(o, "uv_height", model.resolutionHeight);
            model.textures.add(t);
        }
    }

    private Group readGroup(JsonObject node) {
        String uuid = str(node, "uuid", "");
        JsonObject data = node;
        JsonObject extra = groupsByUuid.get(uuid);
        if (extra != null && !node.has("name")) {
            data = extra;
        }
        Group g = new Group();
        g.uuid = uuid;
        g.name = str(data, "name", "bone");
        g.origin = vec(data, "origin", new float[3]);
        g.origin[0] += offsetX;
        g.origin[2] += offsetZ;
        g.rotation = vec(data, "rotation", new float[3]);
        g.visible = bool(data, "visibility", true);

        JsonArray children = arr(node, "children");
        if (children != null) {
            for (JsonElement child : children) {
                if (child.isJsonPrimitive()) {
                    addElement(child.getAsString(), g);
                } else {
                    g.groups.add(readGroup(child.getAsJsonObject()));
                }
            }
        }
        return g;
    }

    private void addElement(String uuid, Group parent) {
        JsonObject o = elementsByUuid.get(uuid);
        if (o == null) {
            return;
        }
        String type = str(o, "type", "cube");
        switch (type) {
            case "cube" -> {
                if (!bool(o, "visibility", true) || !bool(o, "export", true)) {
                    return;
                }
                Cube c = readCube(o);
                if (parent == null) {
                    model.rootCubes.add(c);
                } else {
                    parent.cubes.add(c);
                }
            }
            case "locator", "null_object" -> {
                Locator l = new Locator();
                l.uuid = uuid;
                l.name = str(o, "name", "locator");
                l.position = vec(o, "position", vec(o, "from", new float[3]));
                l.position[0] += offsetX;
                l.position[2] += offsetZ;
                if (parent == null) {
                    model.rootLocators.add(l);
                } else {
                    parent.locators.add(l);
                }
            }
            default -> model.warnings.add("'" + str(o, "name", uuid) + "' (" + type + ") 요소는 지원하지 않아 건너뜁니다. 큐브만 사용하세요.");
        }
    }

    private Cube readCube(JsonObject o) {
        Cube c = new Cube();
        c.uuid = str(o, "uuid", "");
        c.name = str(o, "name", "cube");
        c.from = vec(o, "from", new float[3]);
        c.to = vec(o, "to", new float[3]);
        c.origin = vec(o, "origin", new float[3]);
        c.rotation = vec(o, "rotation", new float[3]);
        c.inflate = (float) num(o, "inflate", 0);
        c.from[0] += offsetX;
        c.to[0] += offsetX;
        c.origin[0] += offsetX;
        c.from[2] += offsetZ;
        c.to[2] += offsetZ;
        c.origin[2] += offsetZ;

        JsonObject faces = obj(o, "faces");
        boolean boxUv = bool(o, "box_uv", false);
        float[] uvOffset = vec2(o, "uv_offset");
        for (String faceName : FACE_NAMES) {
            JsonObject f = faces == null ? null : obj(faces, faceName);
            Face face = new Face();
            if (f != null) {
                JsonElement tex = f.get("texture");
                face.texture = resolveTexture(tex);
                face.rotation = intOf(f, "rotation", 0);
                JsonArray uv = arr(f, "uv");
                if (uv != null && uv.size() >= 4) {
                    face.uv = new float[]{uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat()};
                }
            }
            if (face.uv == null && boxUv) {
                face.uv = boxUv(faceName, c, uvOffset);
                if (face.texture < 0 && !model.textures.isEmpty()) {
                    face.texture = 0;
                }
            }
            if (face.uv != null && face.texture >= 0) {
                c.faces.put(faceName, face);
            }
        }
        return c;
    }

    private int resolveTexture(JsonElement tex) {
        if (tex == null || tex.isJsonNull()) {
            return -1;
        }
        if (tex.isJsonPrimitive() && tex.getAsJsonPrimitive().isNumber()) {
            return tex.getAsInt();
        }
        if (tex.isJsonPrimitive()) {
            String s = tex.getAsString();
            for (Texture t : model.textures) {
                if (s.equals(t.uuid)) {
                    return t.index;
                }
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static float[] boxUv(String face, Cube c, float[] off) {
        float w = Math.abs(c.to[0] - c.from[0]);
        float h = Math.abs(c.to[1] - c.from[1]);
        float d = Math.abs(c.to[2] - c.from[2]);
        float u = off[0];
        float v = off[1];
        return switch (face) {
            case "north" -> new float[]{u + d, v + d, u + d + w, v + d + h};
            case "east" -> new float[]{u, v + d, u + d, v + d + h};
            case "south" -> new float[]{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h};
            case "west" -> new float[]{u + d + w, v + d, u + 2 * d + w, v + d + h};
            case "up" -> new float[]{u + d + w, v + d, u + d, v};
            default -> new float[]{u + d + 2 * w, v, u + d + w, v + d};
        };
    }

    private void readAnimations(JsonObject root) {
        JsonArray animations = arr(root, "animations");
        if (animations == null) {
            return;
        }
        for (JsonElement e : animations) {
            JsonObject o = e.getAsJsonObject();
            Animation a = new Animation();
            a.name = str(o, "name", "animation");
            a.loop = switch (str(o, "loop", "once")) {
                case "loop" -> LoopMode.LOOP;
                case "hold" -> LoopMode.HOLD;
                default -> LoopMode.ONCE;
            };
            a.length = (float) num(o, "length", 0);
            JsonObject animators = obj(o, "animators");
            if (animators != null) {
                for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                    JsonObject an = entry.getValue().getAsJsonObject();
                    String type = str(an, "type", "bone");
                    JsonArray keyframes = arr(an, "keyframes");
                    if (keyframes == null) {
                        continue;
                    }
                    if (type.equals("effect") || entry.getKey().equals("effects")) {
                        readEffects(a, keyframes);
                    } else if (type.equals("bone")) {
                        BoneAnimator ba = new BoneAnimator();
                        ba.boneUuid = entry.getKey();
                        ba.boneName = str(an, "name", "");
                        readBoneKeyframes(a, ba, keyframes);
                        a.bones.put(ba.boneUuid, ba);
                    }
                }
            }
            a.effects.sort(Comparator.comparingDouble(EffectKeyframe::time));
            if (a.length <= 0) {
                // derive length from the last keyframe
                float max = 0;
                for (BoneAnimator ba : a.bones.values()) {
                    for (var list : java.util.List.of(ba.rotation, ba.position, ba.scale)) {
                        for (Keyframe k : list) {
                            max = Math.max(max, k.time);
                        }
                    }
                }
                for (EffectKeyframe k : a.effects) {
                    max = Math.max(max, k.time());
                }
                a.length = max;
            }
            model.animations.put(a.name.toLowerCase(Locale.ROOT), a);
        }
    }

    private void readBoneKeyframes(Animation a, BoneAnimator ba, JsonArray keyframes) {
        for (JsonElement ke : keyframes) {
            JsonObject k = ke.getAsJsonObject();
            String channel = str(k, "channel", "rotation");
            JsonArray points = arr(k, "data_points");
            if (points == null || points.isEmpty()) {
                continue;
            }
            Keyframe kf = new Keyframe();
            kf.time = (float) num(k, "time", 0);
            kf.interpolation = switch (str(k, "interpolation", "linear")) {
                case "catmullrom" -> Interpolation.CATMULLROM;
                case "step" -> Interpolation.STEP;
                case "bezier" -> Interpolation.BEZIER;
                default -> Interpolation.LINEAR;
            };
            float def = channel.equals("scale") ? 1 : 0;
            kf.pre = point(a, ba, points.get(0).getAsJsonObject(), def);
            kf.post = points.size() > 1 ? point(a, ba, points.get(1).getAsJsonObject(), def) : kf.pre;
            kf.bezierLeftTime = vec(k, "bezier_left_time", kf.bezierLeftTime);
            kf.bezierLeftValue = vec(k, "bezier_left_value", kf.bezierLeftValue);
            kf.bezierRightTime = vec(k, "bezier_right_time", kf.bezierRightTime);
            kf.bezierRightValue = vec(k, "bezier_right_value", kf.bezierRightValue);
            switch (channel) {
                case "rotation" -> ba.rotation.add(kf);
                case "position" -> ba.position.add(kf);
                case "scale" -> ba.scale.add(kf);
                default -> {
                    // unsupported channel
                }
            }
        }
        Comparator<Keyframe> byTime = Comparator.comparingDouble(k -> k.time);
        ba.rotation.sort(byTime);
        ba.position.sort(byTime);
        ba.scale.sort(byTime);
    }

    private Molang.Expr[] point(Animation a, BoneAnimator ba, JsonObject dp, float def) {
        Molang.Expr[] out = new Molang.Expr[3];
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            JsonElement v = dp.get(axes[i]);
            if (v == null || v.isJsonNull()) {
                out[i] = Molang.constant(def);
                continue;
            }
            String src = v.getAsString();
            try {
                out[i] = src.isBlank() ? Molang.constant(def) : Molang.compile(src);
            } catch (IllegalArgumentException ex) {
                model.warnings.add("애니메이션 '" + a.name + "' 본 '" + ba.boneName + "' 의 값 '" + src + "' 을 해석할 수 없어 0 으로 처리합니다");
                out[i] = Molang.constant(def);
            }
        }
        return out;
    }

    private static void readEffects(Animation a, JsonArray keyframes) {
        for (JsonElement ke : keyframes) {
            JsonObject k = ke.getAsJsonObject();
            String channel = str(k, "channel", "");
            float time = (float) num(k, "time", 0);
            JsonArray points = arr(k, "data_points");
            if (points == null) {
                continue;
            }
            for (JsonElement pe : points) {
                JsonObject p = pe.getAsJsonObject();
                switch (channel) {
                    case "sound" -> a.effects.add(new EffectKeyframe(time, EffectType.SOUND, str(p, "effect", ""), null, null));
                    case "particle" -> a.effects.add(new EffectKeyframe(time, EffectType.PARTICLE, str(p, "effect", ""),
                            str(p, "locator", ""), str(p, "script", "")));
                    case "timeline" -> a.effects.add(new EffectKeyframe(time, EffectType.TIMELINE, null, null, str(p, "script", "")));
                    default -> {
                        // unknown effect channel
                    }
                }
            }
        }
    }

    // --- tiny JSON helpers -------------------------------------------------

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static double num(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static int intOf(JsonObject o, String key, int def) {
        return (int) Math.round(num(o, key, def));
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
    }

    private static float[] vec(JsonObject o, String key, float[] def) {
        JsonArray a = arr(o, key);
        if (a == null || a.size() < 3) {
            return def.clone();
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            JsonElement e = a.get(i);
            try {
                out[i] = e.isJsonNull() ? 0 : e.getAsFloat();
            } catch (NumberFormatException ex) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static float[] vec2(JsonObject o, String key) {
        JsonArray a = arr(o, key);
        if (a == null || a.size() < 2) {
            return new float[2];
        }
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    /** Reads width/height from the PNG IHDR chunk. */
    static int[] pngSize(byte[] png) {
        if (png.length < 24) {
            return new int[]{0, 0};
        }
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        return new int[]{w, h};
    }
}
