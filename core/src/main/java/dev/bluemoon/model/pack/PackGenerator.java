package dev.bluemoon.model.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModel.Cube;
import dev.bluemoon.model.bbmodel.BBModel.Face;
import dev.bluemoon.model.bbmodel.BBModel.Texture;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.model.runtime.ModelBlueprint.Bone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a resource pack (Minecraft 1.21.4+ item model definitions) from model blueprints.
 * Each bone with geometry becomes {@code assets/<ns>/items/<model>/<bone>.json}.
 */
public final class PackGenerator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final String namespace;
    private final Map<String, byte[]> files = new TreeMap<>();

    public PackGenerator(String namespace, int minFormat, int maxFormat, String description) {
        this.namespace = namespace;
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", minFormat);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", minFormat);
        supported.addProperty("max_inclusive", maxFormat);
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", minFormat);
        pack.addProperty("max_format", maxFormat);
        pack.addProperty("description", description);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        putJson("pack.mcmeta", root);
    }

    public void addFile(String path, byte[] data) {
        files.put(path.replace('\\', '/'), data);
    }

    public Map<String, byte[]> files() {
        return files;
    }

    /** Item model id (namespace:path) used on the display item of a bone. */
    public static String itemModelId(String namespace, Bone bone) {
        return namespace + ":" + bone.modelPath;
    }

    public void addModel(ModelBlueprint bp) {
        Map<Integer, String> textureIds = writeTextures(bp.source, bp.id);

        for (Bone bone : bp.bones) {
            if (!bone.hasGeometry()) {
                continue;
            }
            putJson("assets/" + namespace + "/models/item/" + bone.modelPath + ".json", boneModel(bp, bone, textureIds));

            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:dye");
            tint.addProperty("default", -1);
            JsonArray tints = new JsonArray();
            tints.add(tint);
            JsonObject itemModel = new JsonObject();
            itemModel.addProperty("type", "minecraft:model");
            itemModel.addProperty("model", namespace + ":item/" + bone.modelPath);
            itemModel.add("tints", tints);
            JsonObject item = new JsonObject();
            item.add("model", itemModel);
            putJson("assets/" + namespace + "/items/" + bone.modelPath + ".json", item);
        }
    }

    /**
     * Adds a static held-item model at {@code items/weapon/<id>} (use item model {@code ns:weapon/<id>}).
     */
    public void addWeapon(String id, BBModel model, List<String> warnings) {
        Map<Integer, String> textureIds = writeTextures(model, "weapon/" + id);
        putJson("assets/" + namespace + "/models/item/weapon/" + id + ".json",
                WeaponModelBuilder.build(model, textureIds, warnings));
        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("type", "minecraft:model");
        itemModel.addProperty("model", namespace + ":item/weapon/" + id);
        JsonObject item = new JsonObject();
        item.add("model", itemModel);
        putJson("assets/" + namespace + "/items/weapon/" + id + ".json", item);
    }

    /** Writes a model's textures below {@code textures/item/<folder>/} and returns index to texture id. */
    private Map<Integer, String> writeTextures(BBModel model, String folder) {
        Map<Integer, String> textureIds = new HashMap<>();
        for (Texture t : model.textures) {
            if (t.png == null) {
                continue;
            }
            String texName = ModelBlueprint.sanitize(t.name.replaceFirst("(?i)\\.png$", ""));
            String texPath = folder + "/" + t.index + "_" + texName;
            addFile("assets/" + namespace + "/textures/item/" + texPath + ".png", t.png);
            int frameHeight = t.uvWidth > 0 ? t.width * t.uvHeight / t.uvWidth : 0;
            if (frameHeight > 0 && t.height > frameHeight && t.height % frameHeight == 0) {
                // vertical strip of frames: animated texture
                JsonObject anim = new JsonObject();
                anim.addProperty("frametime", t.frameTime);
                JsonObject mcmeta = new JsonObject();
                mcmeta.add("animation", anim);
                putJson("assets/" + namespace + "/textures/item/" + texPath + ".png.mcmeta", mcmeta);
            }
            textureIds.put(t.index, namespace + ":item/" + texPath);
        }
        return textureIds;
    }

    private JsonObject boneModel(ModelBlueprint bp, Bone bone, Map<Integer, String> textureIds) {
        BBModel model = bp.source;
        float k = bone.geometryScale;
        JsonObject textures = new JsonObject();
        String particle = null;
        JsonArray elements = new JsonArray();

        for (Cube c : bone.cubes) {
            JsonObject el = new JsonObject();
            float[] from = new float[3];
            float[] to = new float[3];
            float[] origin = {bone.origin.x, bone.origin.y, bone.origin.z};
            for (int i = 0; i < 3; i++) {
                float lo = Math.min(c.from[i], c.to[i]) - c.inflate;
                float hi = Math.max(c.from[i], c.to[i]) + c.inflate;
                from[i] = (lo - origin[i]) / k + 8;
                to[i] = (hi - origin[i]) / k + 8;
            }
            el.add("from", vec(from));
            el.add("to", vec(to));

            float[] r = c.rotation;
            int axis = r[0] != 0 ? 0 : r[1] != 0 ? 1 : r[2] != 0 ? 2 : -1;
            if (axis >= 0) {
                JsonObject rot = new JsonObject();
                rot.addProperty("angle", r[axis]);
                rot.addProperty("axis", axis == 0 ? "x" : axis == 1 ? "y" : "z");
                float[] ro = new float[3];
                for (int i = 0; i < 3; i++) {
                    ro[i] = (c.origin[i] - origin[i]) / k + 8;
                }
                rot.add("origin", vec(ro));
                rot.addProperty("rescale", false);
                el.add("rotation", rot);
            }

            JsonObject faces = new JsonObject();
            for (Map.Entry<String, Face> e : c.faces.entrySet()) {
                Face f = e.getValue();
                String texId = textureIds.get(f.texture);
                if (texId == null || f.texture >= model.textures.size()) {
                    continue;
                }
                Texture t = model.textures.get(f.texture);
                String key = String.valueOf(f.texture);
                textures.addProperty(key, texId);
                if (particle == null) {
                    particle = texId;
                }
                float su = 16f / Math.max(1, t.uvWidth);
                float sv = 16f / Math.max(1, t.uvHeight);
                JsonObject face = new JsonObject();
                face.add("uv", vec(new float[]{f.uv[0] * su, f.uv[1] * sv, f.uv[2] * su, f.uv[3] * sv}));
                face.addProperty("texture", "#" + key);
                if (f.rotation != 0) {
                    face.addProperty("rotation", f.rotation);
                }
                face.addProperty("tintindex", 0);
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
        return out;
    }

    private static JsonArray vec(float[] v) {
        JsonArray a = new JsonArray();
        for (float f : v) {
            // round to keep the json small and stable
            a.add(Math.round(f * 10000f) / 10000f);
        }
        return a;
    }

    private void putJson(String path, JsonObject json) {
        addFile(path, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    /** Deterministic zip (sorted entries, fixed timestamps) so the SHA-1 only changes with the content. */
    public byte[] buildZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(315532800000L); // 1980-01-01
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<String> validate(Map<String, byte[]> files) {
        return files.keySet().stream()
                .filter(p -> !p.equals("pack.mcmeta") && !p.matches("[a-z0-9_./-]+"))
                .map(p -> "리소스팩 경로에 사용할 수 없는 문자가 있습니다: " + p)
                .toList();
    }
}
