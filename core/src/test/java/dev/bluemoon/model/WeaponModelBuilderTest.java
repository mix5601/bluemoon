package dev.bluemoon.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.model.pack.WeaponModelBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponModelBuilderTest {

    private static BBModel model(String groupRotation, String cubeRotation) {
        return BBModelParser.parse("""
                {"meta":{"model_format":"free"},"resolution":{"width":16,"height":16},
                 "textures":[{"name":"t","uv_width":16,"uv_height":16}],
                 "elements":[{"uuid":"c","name":"c","from":[0,0,0],"to":[1,1,1],"origin":[1,0,1],"rotation":%s,
                   "faces":{"north":{"uv":[0,0,1,1],"texture":0}}}],
                 "outliner":[{"uuid":"g","name":"g","origin":[2,0,3],"rotation":%s,"children":["c"]}]}
                """.formatted(cubeRotation, groupRotation));
    }

    private static JsonObject element(BBModel m, List<String> warnings) {
        JsonObject out = WeaponModelBuilder.build(m, Map.of(0, "ns:item/t"), warnings);
        return out.getAsJsonArray("elements").get(0).getAsJsonObject();
    }

    private static float[] arr(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    @Test
    void groupRotationBecomesElementRotationAroundGroupPivot() {
        List<String> warnings = new ArrayList<>();
        JsonObject el = element(model("[0,22.5,0]", "[0,0,0]"), warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(22.5f, el.getAsJsonObject("rotation").get("angle").getAsFloat(), 1e-3);
        assertEquals("y", el.getAsJsonObject("rotation").get("axis").getAsString());
        float[] origin = arr(el.getAsJsonObject("rotation").getAsJsonArray("origin"));
        assertEquals(10f, origin[0], 1e-3); // group pivot x 2 + 8
        assertEquals(11f, origin[2], 1e-3); // group pivot z 3 + 8
        float[] from = arr(el.getAsJsonArray("from"));
        assertEquals(8f, from[0], 1e-3);
        assertEquals(0f, from[1], 1e-3);
        assertEquals(8f, from[2], 1e-3);
    }

    @Test
    void sameAxisRotationsAdd() {
        List<String> warnings = new ArrayList<>();
        JsonObject el = element(model("[0,22.5,0]", "[0,22.5,0]"), warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(45f, el.getAsJsonObject("rotation").get("angle").getAsFloat(), 1e-3);
    }

    @Test
    void noRotationIsPlainTranslation() {
        List<String> warnings = new ArrayList<>();
        JsonObject el = element(model("[0,0,0]", "[0,0,0]"), warnings);
        assertFalse(el.has("rotation"));
        assertEquals(8f, arr(el.getAsJsonArray("from"))[0], 1e-3);
    }

    @Test
    void mixedAxesAreSkippedWithWarning() {
        List<String> warnings = new ArrayList<>();
        JsonObject out = WeaponModelBuilder.build(model("[22.5,0,0]", "[0,22.5,0]"), Map.of(0, "ns:item/t"), warnings);
        assertEquals(0, out.getAsJsonArray("elements").size());
        assertEquals(1, warnings.size());
    }

    @Test
    void defaultDisplayWhenMissing() {
        JsonObject out = WeaponModelBuilder.build(model("[0,0,0]", "[0,0,0]"), Map.of(0, "ns:item/t"), new ArrayList<>());
        assertEquals(55f, out.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand")
                .getAsJsonArray("rotation").get(2).getAsFloat(), 1e-3);
    }
}
