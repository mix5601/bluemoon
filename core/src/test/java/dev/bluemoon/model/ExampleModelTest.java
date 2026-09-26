package dev.bluemoon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bluemoon.model.animation.AnimationLayer;
import dev.bluemoon.model.animation.PoseCalculator;
import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.bbmodel.BBModel.EffectType;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.model.runtime.ModelBlueprint.Bone;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parses the bundled example golem and checks the generated pack and poses. */
class ExampleModelTest {

    private static BBModel model;
    private static ModelBlueprint blueprint;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = ExampleModelTest.class.getResourceAsStream("/models/golem.bbmodel")) {
            assertNotNull(in, "example model missing");
            model = BBModelParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        blueprint = ModelBlueprint.build("golem", model);
    }

    @Test
    void parsesStructure() {
        assertTrue(model.warnings.isEmpty(), model.warnings.toString());
        assertEquals(1, model.textures.size());
        assertEquals(64, model.textures.get(0).width);
        assertNotNull(blueprint.bone("head"));
        assertTrue(blueprint.bone("head").head);
        assertEquals("body", blueprint.bone("head").parent.name);
        assertTrue(blueprint.locators.containsKey("right_hand"));
        assertTrue(model.animations.keySet().containsAll(List.of("idle", "walk", "attack", "slam", "hurt", "death", "spawn")));
    }

    @Test
    void unsupportedCubeRotationGetsOwnBone() {
        Bone synthetic = blueprint.bones.stream().filter(b -> b.synthetic).findFirst().orElseThrow();
        assertEquals("body", synthetic.parent.name);
        assertEquals(new Vector3f(20, 45, 0), synthetic.rotation);
        assertEquals(1, synthetic.cubes.size());
        assertTrue(synthetic.hasGeometry());
    }

    @Test
    void timelineEffectsAreRead() {
        BBModel.Animation slam = blueprint.animation("slam");
        EffectKeyframe timeline = slam.effects.stream().filter(e -> e.type() == EffectType.TIMELINE).findFirst().orElseThrow();
        assertEquals(0.7f, timeline.time(), 1e-6);
        assertTrue(timeline.script().contains("damage{a=10} @EntitiesInRadius{r=4}"));
    }

    @Test
    void generatedPackIsValid() throws IOException {
        PackGenerator gen = new PackGenerator("bluemoon", 46, 99, "test");
        gen.addModel(blueprint);
        assertTrue(PackGenerator.validate(gen.files()).isEmpty());
        Map<String, byte[]> files = gen.files();
        assertTrue(files.containsKey("pack.mcmeta"));
        assertTrue(files.containsKey("assets/bluemoon/textures/item/golem/0_golem.png"));
        int models = 0;
        for (Bone bone : blueprint.bones) {
            if (!bone.hasGeometry()) {
                continue;
            }
            models++;
            JsonObject item = json(files.get("assets/bluemoon/items/" + bone.modelPath + ".json"));
            assertEquals("bluemoon:item/" + bone.modelPath, item.getAsJsonObject("model").get("model").getAsString());
            JsonObject m = json(files.get("assets/bluemoon/models/item/" + bone.modelPath + ".json"));
            for (JsonElement e : m.getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                for (String key : new String[]{"from", "to"}) {
                    JsonArray v = el.getAsJsonArray(key);
                    for (JsonElement c : v) {
                        float f = c.getAsFloat();
                        assertTrue(f >= -16 && f <= 32, bone.name + " " + key + " out of range: " + v);
                    }
                }
                if (el.has("rotation")) {
                    float angle = el.getAsJsonObject("rotation").get("angle").getAsFloat();
                    assertTrue(Math.abs(angle) <= 45 && angle % 22.5f == 0, "bad angle " + angle);
                }
                for (Map.Entry<String, JsonElement> face : el.getAsJsonObject("faces").entrySet()) {
                    for (JsonElement uv : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                        assertTrue(uv.getAsFloat() >= 0 && uv.getAsFloat() <= 16);
                    }
                }
            }
        }
        assertEquals(7, models); // body, crystal (synthetic), head, 2 arms, 2 legs

        byte[] zip = gen.buildZip();
        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertEquals(files.size(), entries.size());
        assertEquals(PackGenerator.sha1(zip), PackGenerator.sha1(gen.buildZip()), "zip must be deterministic");
    }

    @Test
    void restPoseMatchesBlockbenchLayout() {
        PoseCalculator pose = new PoseCalculator(blueprint);
        pose.compute(List.of(), 0, 0, 0);
        Bone body = blueprint.bone("body");
        Matrix4f m = pose.displayMatrix(body, 1f, new Matrix4f());
        Vector3f t = m.getTranslation(new Vector3f());
        assertEquals(0, t.x, 1e-5);
        assertEquals(12 / 16f, t.y, 1e-5);
        assertEquals(0, t.z, 1e-5);

        // right hand is on the model's +X (Blockbench); entity space mirrors X and Z
        Vector3f hand = pose.pointOffset(blueprint.locators.get("right_hand").bone(),
                blueprint.locators.get("right_hand").position(), 1f, 0f, new Vector3f());
        assertEquals(-8.5f / 16f, hand.x, 1e-5);
        assertEquals(9f / 16f, hand.y, 1e-5);
        assertEquals(0, hand.z, 1e-5);

        // body yaw 90 (facing west, -X): the hand, on the entity's left... rotates with the body
        Vector3f turned = pose.pointOffset(blueprint.locators.get("right_hand").bone(),
                blueprint.locators.get("right_hand").position(), 1f, 90f, new Vector3f());
        assertEquals(0, turned.x, 1e-5);
        assertEquals(-8.5f / 16f, turned.z, 1e-5);
    }

    @Test
    void animationMovesBones() {
        PoseCalculator pose = new PoseCalculator(blueprint);
        AnimationLayer slam = new AnimationLayer(blueprint.animation("slam"), LoopMode.ONCE, 1, 0, 0, 10, 0);
        for (int i = 0; i < 10; i++) {
            slam.advance(0.05);
        }
        assertEquals(0.5, slam.time(), 1e-9);
        pose.compute(List.of(slam), 0, 0, 0);
        Vector3f hand = pose.pointOffset(blueprint.locators.get("right_hand").bone(),
                blueprint.locators.get("right_hand").position(), 1f, 0f, new Vector3f());
        // arms are raised above the shoulders at 0.5s
        assertTrue(hand.y > 23f / 16f, "hand should be raised, y=" + hand.y);
    }

    @Test
    void effectKeyframesFireOnce() {
        AnimationLayer slam = new AnimationLayer(blueprint.animation("slam"), LoopMode.ONCE, 1, 0, 0, 10, 0);
        List<EffectKeyframe> fired = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            fired.addAll(slam.advance(0.05));
        }
        assertEquals(1, fired.stream().filter(e -> e.type() == EffectType.TIMELINE).count());
        assertEquals(2, fired.stream().filter(e -> e.type() == EffectType.PARTICLE).count());
        assertTrue(slam.isFinished());

        AnimationLayer attack = new AnimationLayer(blueprint.animation("attack"), LoopMode.LOOP, 1, 0, 0, 10, 0);
        int sounds = 0;
        for (int i = 0; i < 36; i++) { // 1.8s = 3 loops of 0.6s
            sounds += (int) attack.advance(0.05).stream().filter(e -> e.type() == EffectType.SOUND).count();
        }
        assertEquals(3, sounds);
        assertFalse(attack.isFinished());
    }

    private static JsonObject json(byte[] data) {
        assertNotNull(data);
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
