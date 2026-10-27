package dev.bluemoon.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobTemplatesTest {

    private static Map<String, ConfigurationSection> parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(yaml);
        Map<String, ConfigurationSection> raw = new LinkedHashMap<>();
        for (String key : yml.getKeys(false)) {
            raw.put(key.toLowerCase(Locale.ROOT), yml.getConfigurationSection(key));
        }
        return raw;
    }

    @Test
    void mergesSectionsOverridesValuesAndAppendsLists() throws Exception {
        Map<String, ConfigurationSection> raw = parse("""
                Base:
                  Abstract: true
                  Health: 100
                  Damage: 5
                  BossBar:
                    Color: RED
                    Range: 32
                  Skills:
                  - message{m=base} @self ~onSpawn
                  Drops:
                  - exp 10
                Child:
                  Template: Base
                  health: 300
                  bossbar:
                    Range: 64
                  Skills:
                  - message{m=child} @self ~onSpawn
                """);
        List<String> warnings = new ArrayList<>();
        MobTemplates templates = new MobTemplates(raw, warnings::add);
        ConfigurationSection child = templates.resolve("child");

        assertEquals(300, child.getDouble("health"));
        assertFalse(child.contains("Health"), "differently cased key replaced, not duplicated");
        assertEquals(5, child.getDouble("Damage"));
        ConfigurationSection bar = child.getConfigurationSection("BossBar");
        assertEquals("RED", bar.getString("Color"));
        assertEquals(64, bar.getDouble("Range"));
        assertEquals(List.of("message{m=base} @self ~onSpawn", "message{m=child} @self ~onSpawn"),
                child.getStringList("Skills"));
        assertEquals(List.of("exp 10"), child.getStringList("Drops"));
        assertFalse(child.contains("Abstract"), "Abstract is not inherited");
        assertTrue(MobTemplates.isAbstract(raw.get("base")));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void multipleTemplatesLaterWins() throws Exception {
        Map<String, ConfigurationSection> raw = parse("""
                A:
                  Health: 10
                  Armor: 1
                B:
                  Template: A
                  Health: 20
                C:
                  Template: B, A
                """);
        ConfigurationSection c = new MobTemplates(raw, w -> { }).resolve("C");
        // A applied after B, so A's own Health wins
        assertEquals(10, c.getDouble("Health"));
        assertEquals(1, c.getDouble("Armor"));
    }

    @Test
    void reportsMissingTemplatesAndLoops() throws Exception {
        Map<String, ConfigurationSection> raw = parse("""
                A:
                  Template: B
                  Health: 1
                B:
                  Template: A
                C:
                  Template: Missing
                  Health: 3
                """);
        List<String> warnings = new ArrayList<>();
        MobTemplates templates = new MobTemplates(raw, warnings::add);
        assertEquals(1, templates.resolve("A").getDouble("Health"));
        assertEquals(3, templates.resolve("C").getDouble("Health"));
        assertNull(templates.resolve("nope"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("순환")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Missing")), warnings.toString());
    }

    /** The shipped boss examples must resolve against the shipped templates without warnings. */
    @Test
    void exampleBossTemplatesResolve() throws Exception {
        Map<String, ConfigurationSection> raw = shippedMobs();
        List<String> warnings = new ArrayList<>();
        MobTemplates templates = new MobTemplates(raw, warnings::add);
        for (String tier : TIER_SKILLS.keySet()) {
            assertTrue(raw.containsKey(tier), "missing template " + tier);
            assertTrue(MobTemplates.isAbstract(raw.get(tier)), tier + " should be Abstract");
        }
        for (String id : raw.keySet()) {
            ConfigurationSection sec = templates.resolve(id);
            if (raw.get(id).contains("Template")) {
                assertTrue(sec.contains("BossBar"), id + " should inherit a BossBar");
                assertFalse(sec.getStringList("Skills").isEmpty(), id + " should inherit skills");
            }
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    /** Own timer skills per phase for each tier, as in the "등급 규격" table of boss_templates.yml. */
    private static final Map<String, List<Integer>> TIER_SKILLS = Map.of(
            "miniboss", List.of(2),
            "midboss", List.of(3, 1),
            "boss", List.of(3, 2, 1),
            "fieldboss", List.of(3, 2, 1));

    private static final Pattern HEALTH_MAX = Pattern.compile("\\?healthpercent\\{max=([0-9.]+)}");

    /** The shipped example bosses must have as many own skills per phase as their tier asks for. */
    @Test
    void exampleBossesFollowTierSpec() throws Exception {
        Map<String, ConfigurationSection> raw = shippedMobs();
        int checked = 0;
        for (ConfigurationSection mob : raw.values()) {
            String tier = mob.getString("Template", "").toLowerCase(Locale.ROOT);
            if (!TIER_SKILLS.containsKey(tier)) {
                continue;
            }
            // phase threshold (1.0 = first phase) -> number of own timer skills starting there
            TreeMap<Double, Integer> phases = new TreeMap<>(Comparator.reverseOrder());
            for (String line : mob.getStringList("Skills")) {
                if (!line.contains("~onTimer")) {
                    continue;
                }
                Matcher m = HEALTH_MAX.matcher(line);
                phases.merge(m.find() ? Double.parseDouble(m.group(1)) : 1.0, 1, Integer::sum);
            }
            assertEquals(TIER_SKILLS.get(tier), new ArrayList<>(phases.values()),
                    mob.getName() + " (" + tier + ") skills per phase");
            checked++;
        }
        assertEquals(TIER_SKILLS.size(), checked, "one example per tier");
    }

    private static Map<String, ConfigurationSection> shippedMobs() throws IOException, URISyntaxException {
        Map<String, ConfigurationSection> raw = new LinkedHashMap<>();
        for (Path file : mobFiles()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file.toFile());
            for (String key : yml.getKeys(false)) {
                raw.put(key.toLowerCase(Locale.ROOT), yml.getConfigurationSection(key));
            }
        }
        return raw;
    }

    private static List<Path> mobFiles() throws IOException, URISyntaxException {
        List<Path> out = new ArrayList<>();
        var urls = MobTemplatesTest.class.getClassLoader().getResources("mobs");
        while (urls.hasMoreElements()) {
            try (Stream<Path> s = Files.list(Path.of(urls.nextElement().toURI()))) {
                s.filter(p -> p.toString().endsWith(".yml")).forEach(out::add);
            }
        }
        return out;
    }
}
