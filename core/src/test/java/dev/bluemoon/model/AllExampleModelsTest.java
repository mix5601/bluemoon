package dev.bluemoon.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.model.pack.PackGenerator;
import dev.bluemoon.model.runtime.ModelBlueprint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every example model shipped with the plugins must parse cleanly and produce a valid pack. */
class AllExampleModelsTest {

    static BBModel load(String path) throws IOException {
        try (InputStream in = AllExampleModelsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " missing");
            return BBModelParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"golem", "slash", "fireball", "spirit_wolf", "star_slash", "wither_vortex", "radiant_skull"})
    void modelIsValid(String id) throws IOException {
        BBModel model = load("/models/" + id + ".bbmodel");
        assertTrue(model.warnings.isEmpty(), model.warnings.toString());
        assertFalse(model.animations.isEmpty());
        ModelBlueprint bp = ModelBlueprint.build(id, model);
        PackGenerator gen = new PackGenerator("test", 46, 99, "t");
        gen.addModel(bp);
        assertTrue(PackGenerator.validate(gen.files()).isEmpty());
        for (Map.Entry<String, byte[]> e : gen.files().entrySet()) {
            if (e.getKey().contains("/models/item/")) {
                assertElementsValid(json(e.getValue()), e.getKey());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"moon_sword", "star_greatsword"})
    void weaponIsValid(String id) throws IOException {
        BBModel model = load("/weapons/" + id + ".bbmodel");
        assertEquals("java_block", model.format);
        List<String> warnings = new ArrayList<>();
        PackGenerator gen = new PackGenerator("test", 46, 99, "t");
        gen.addWeapon(id, model, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        JsonObject m = json(gen.files().get("assets/test/models/item/weapon/" + id + ".json"));
        assertElementsValid(m, id);
        assertEquals(model.rootGroups.get(0).cubes.size(), m.getAsJsonArray("elements").size());
        assertEquals(90f, m.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand")
                .getAsJsonArray("rotation").get(1).getAsFloat(), 1e-4);
        assertNotNull(gen.files().get("assets/test/items/weapon/" + id + ".json"));
    }

    static void assertElementsValid(JsonObject model, String where) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            for (String key : new String[]{"from", "to"}) {
                for (JsonElement c : el.getAsJsonArray(key)) {
                    assertTrue(c.getAsFloat() >= -16 && c.getAsFloat() <= 32, where + " out of range");
                }
            }
            if (el.has("rotation")) {
                float angle = el.getAsJsonObject("rotation").get("angle").getAsFloat();
                assertTrue(Math.abs(angle) <= 45 && angle % 22.5f == 0, where + " bad angle " + angle);
            }
        }
    }

    static JsonObject json(byte[] data) {
        assertNotNull(data);
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
