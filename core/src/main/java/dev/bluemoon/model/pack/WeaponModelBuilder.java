package dev.bluemoon.model.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModel.Cube;
import dev.bluemoon.model.bbmodel.BBModel.Face;
import dev.bluemoon.model.bbmodel.BBModel.Group;
import dev.bluemoon.model.bbmodel.BBModel.Texture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flattens a Blockbench model into one static item model (for held weapons / items).
 * Group rotations are baked into the cubes; a cube may end up rotated around a single axis in
 * 22.5 degree steps only, other cubes are skipped with a warning.
 */
public final class WeaponModelBuilder {

    private static final float DEG = (float) (Math.PI / 180);
    private static final String[] CONTEXTS = {
            "thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
            "gui", "head", "ground", "fixed"};

    private record Placed(Cube cube, Vector3f from, Vector3f to, int axis, float angle, Vector3f origin) {
    }

    private WeaponModelBuilder() {
    }

    /**
     * @param textureIds texture index to resource id ({@code ns:item/...})
     * @param warnings   receives problems found while flattening
     */
    public static JsonObject build(BBModel model, Map<Integer, String> textureIds, List<String> warnings) {
        List<Placed> placed = new ArrayList<>();
        for (Cube c : model.rootCubes) {
            place(c, new Matrix4f(), new Vector3f(), placed, warnings);
        }
        for (Group g : model.rootGroups) {
            walk(g, new Matrix4f(), new Vector3f(), placed, warnings);
        }

        // Blockbench centers models on 0; item models live in 0..16
        float extent = 0;
        for (Placed p : placed) {
            p.from.add(8, 0, 8);
            p.to.add(8, 0, 8);
            p.origin.add(8, 0, 8);
            for (int i = 0; i < 3; i++) {
                extent = Math.max(extent, Math.abs(p.from.get(i) - 8));
                extent = Math.max(extent, Math.abs(p.to.get(i) - 8));
            }
        }
        float k = Math.max(1f, extent / 24f * 1.0001f);
        if (k > 4f) {
            warnings.add("무기 모델이 너무 큽니다 (최대 약 6블록). 일부 표시 배율이 제한됩니다");
        }

        JsonObject textures = new JsonObject();
        JsonArray elements = new JsonArray();
        String particle = null;
        for (Placed p : placed) {
            JsonObject el = new JsonObject();
            el.add("from", vec(shrink(p.from, k)));
            el.add("to", vec(shrink(p.to, k)));
            if (p.axis >= 0 && p.angle != 0) {
                JsonObject rot = new JsonObject();
                rot.addProperty("angle", p.angle);
                rot.addProperty("axis", p.axis == 0 ? "x" : p.axis == 1 ? "y" : "z");
                rot.add("origin", vec(shrink(p.origin, k)));
                el.add("rotation", rot);
            }
            JsonObject faces = new JsonObject();
            for (Map.Entry<String, Face> e : p.cube.faces.entrySet()) {
                Face f = e.getValue();
                String texId = textureIds.get(f.texture);
                if (texId == null) {
                    continue;
                }
                Texture t = model.textures.get(f.texture);
                textures.addProperty(String.valueOf(f.texture), texId);
                if (particle == null) {
                    particle = texId;
                }
                float su = 16f / Math.max(1, t.uvWidth);
                float sv = 16f / Math.max(1, t.uvHeight);
                JsonObject face = new JsonObject();
                face.add("uv", nums(f.uv[0] * su, f.uv[1] * sv, f.uv[2] * su, f.uv[3] * sv));
                face.addProperty("texture", "#" + f.texture);
                if (f.rotation != 0) {
                    face.addProperty("rotation", f.rotation);
                }
                faces.add(e.getKey(), face);
            }
            el.add("faces", faces);
            elements.add(el);
        }
        if (particle != null) {
            textures.addProperty("particle", particle);
        }
        JsonObject out = new JsonObject();
        out.add("textures", textures);
        out.add("elements", elements);
        out.add("display", display(model.display, k));
        return out;
    }

    private static void walk(Group g, Matrix4f parent, Vector3f parentOrigin, List<Placed> out, List<String> warnings) {
        Matrix4f m = new Matrix4f(parent)
                .translate(g.origin[0] - parentOrigin.x, g.origin[1] - parentOrigin.y, g.origin[2] - parentOrigin.z)
                .rotateZYX(g.rotation[2] * DEG, g.rotation[1] * DEG, g.rotation[0] * DEG);
        Vector3f origin = new Vector3f(g.origin[0], g.origin[1], g.origin[2]);
        for (Cube c : g.cubes) {
            place(c, m, origin, out, warnings);
        }
        for (Group child : g.groups) {
            walk(child, m, origin, out, warnings);
        }
    }

    /** @param bone bone-local (relative to {@code boneOrigin}, pixels) to model space */
    private static void place(Cube c, Matrix4f bone, Vector3f boneOrigin, List<Placed> out, List<String> warnings) {
        Vector3f co = new Vector3f(c.origin[0], c.origin[1], c.origin[2]);
        // point p (absolute rest pixels) -> bone(Rc (p - co) + co - boneOrigin)
        Matrix4f total = new Matrix4f(bone)
                .translate(co.x - boneOrigin.x, co.y - boneOrigin.y, co.z - boneOrigin.z)
                .rotateZYX(c.rotation[2] * DEG, c.rotation[1] * DEG, c.rotation[0] * DEG)
                .translate(-co.x, -co.y, -co.z);
        Matrix3f r = total.get3x3(new Matrix3f());
        Vector3f t = total.getTranslation(new Vector3f());

        Vector3f from = new Vector3f(Math.min(c.from[0], c.to[0]), Math.min(c.from[1], c.to[1]), Math.min(c.from[2], c.to[2])).sub(c.inflate, c.inflate, c.inflate);
        Vector3f to = new Vector3f(Math.max(c.from[0], c.to[0]), Math.max(c.from[1], c.to[1]), Math.max(c.from[2], c.to[2])).add(c.inflate, c.inflate, c.inflate);

        int axis = singleAxis(r);
        if (axis == -2) {
            out.add(new Placed(c, from.add(t), to.add(t), -1, 0, new Vector3f()));
            return;
        }
        if (axis < 0) {
            warnings.add("무기 모델의 큐브 '" + c.name + "' 회전을 표현할 수 없어 건너뜁니다 (한 축, 22.5° 단위만 가능)");
            return;
        }
        float angle = angle(r, axis);
        float snapped = Math.round(angle / 22.5f) * 22.5f;
        if (Math.abs(snapped - angle) > 0.01f || Math.abs(snapped) > 45.01f) {
            warnings.add("무기 모델의 큐브 '" + c.name + "' 회전 " + angle + "° 는 지원되지 않아 건너뜁니다");
            return;
        }
        // total(x) = R x + t = R (x - p) + p + ta: solve (I - R) p = t in the rotation plane
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        float m00 = 1 - r.get(a, a);
        float m01 = -r.get(b, a);
        float m10 = -r.get(a, b);
        float m11 = 1 - r.get(b, b);
        float det = m00 * m11 - m01 * m10;
        Vector3f pivot = new Vector3f();
        pivot.setComponent(a, (t.get(a) * m11 - m01 * t.get(b)) / det);
        pivot.setComponent(b, (m00 * t.get(b) - m10 * t.get(a)) / det);
        Vector3f along = new Vector3f();
        along.setComponent(axis, t.get(axis));
        out.add(new Placed(c, from.add(along), to.add(along), axis, snapped, pivot));
    }

    /** -2 = no rotation, 0..2 = rotation about that axis only, -1 = anything else. */
    private static int singleAxis(Matrix3f r) {
        if (near(r, new Matrix3f())) {
            return -2;
        }
        for (int axis = 0; axis < 3; axis++) {
            boolean ok = Math.abs(r.get(axis, axis) - 1) < 1e-4;
            for (int i = 0; i < 3 && ok; i++) {
                if (i != axis && (Math.abs(r.get(axis, i)) > 1e-4 || Math.abs(r.get(i, axis)) > 1e-4)) {
                    ok = false;
                }
            }
            if (ok) {
                return axis;
            }
        }
        return -1;
    }

    /** Right-handed angle about the axis (same convention as Blockbench / block models). */
    private static float angle(Matrix3f r, int axis) {
        // Matrix3f.get(column, row)
        return switch (axis) {
            case 0 -> (float) Math.toDegrees(Math.atan2(r.get(1, 2), r.get(1, 1)));
            case 1 -> (float) Math.toDegrees(Math.atan2(r.get(2, 0), r.get(0, 0)));
            default -> (float) Math.toDegrees(Math.atan2(r.get(0, 1), r.get(0, 0)));
        };
    }

    private static boolean near(Matrix3f a, Matrix3f b) {
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                if (Math.abs(a.get(c, r) - b.get(c, r)) > 1e-4) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Vector3f shrink(Vector3f v, float k) {
        return new Vector3f(v).sub(8, 8, 8).div(k).add(8, 8, 8);
    }

    private static JsonObject display(JsonObject source, float k) {
        JsonObject out = new JsonObject();
        for (String ctx : CONTEXTS) {
            JsonObject entry = source != null && source.get(ctx) instanceof JsonObject o ? o.deepCopy() : defaults(ctx);
            if (k > 1f) {
                JsonArray scale = entry.get("scale") instanceof JsonArray a && a.size() == 3 ? a : null;
                JsonArray scaled = new JsonArray();
                for (int i = 0; i < 3; i++) {
                    float v = scale == null ? 1f : scale.get(i).getAsFloat();
                    scaled.add(Math.min(4f, v * k));
                }
                entry.add("scale", scaled);
            }
            out.add(ctx, entry);
        }
        return out;
    }

    /** Vanilla "handheld" item transforms, used when the model has no display settings. */
    private static JsonObject defaults(String ctx) {
        JsonObject o = new JsonObject();
        switch (ctx) {
            case "thirdperson_righthand" -> put(o, new float[]{0, -90, 55}, new float[]{0, 4, 0.5f}, 0.85f);
            case "thirdperson_lefthand" -> put(o, new float[]{0, 90, -55}, new float[]{0, 4, 0.5f}, 0.85f);
            case "firstperson_righthand" -> put(o, new float[]{0, -90, 25}, new float[]{1.13f, 3.2f, 1.13f}, 0.68f);
            case "firstperson_lefthand" -> put(o, new float[]{0, 90, -25}, new float[]{1.13f, 3.2f, 1.13f}, 0.68f);
            case "ground" -> put(o, new float[]{0, 0, 0}, new float[]{0, 2, 0}, 0.5f);
            case "gui" -> put(o, new float[]{0, 0, 0}, new float[]{0, 0, 0}, 1f);
            default -> put(o, new float[]{0, 0, 0}, new float[]{0, 0, 0}, 1f);
        }
        return o;
    }

    private static void put(JsonObject o, float[] rot, float[] trans, float scale) {
        o.add("rotation", nums(rot));
        o.add("translation", nums(trans));
        o.add("scale", nums(scale, scale, scale));
    }

    private static JsonArray vec(Vector3f v) {
        return nums(v.x, v.y, v.z);
    }

    private static JsonArray nums(float... values) {
        JsonArray a = new JsonArray();
        for (float f : values) {
            a.add(Math.round(f * 10000f) / 10000f);
        }
        return a;
    }

    static boolean inBounds(JsonObject model) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            for (String key : new String[]{"from", "to"}) {
                for (JsonElement c : e.getAsJsonObject().getAsJsonArray(key)) {
                    if (c.getAsFloat() < -16 || c.getAsFloat() > 32) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
