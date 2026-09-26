package dev.bluemoon.skill;

import dev.bluemoon.model.bbmodel.BBModel;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.bbmodel.BBModel.EffectType;
import dev.bluemoon.model.bbmodel.BBModelParser;
import dev.bluemoon.skill.parse.Params;
import dev.bluemoon.skill.parse.SkillLineParser;
import dev.bluemoon.skill.parse.SkillLineParser.ParsedCondition;
import dev.bluemoon.skill.parse.SkillLineParser.ParsedLine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every skill line in the shipped example files (YAML and Blockbench timeline scripts) must parse
 * and only use known mechanics, targeters, conditions, triggers and metaskills.
 */
class ExampleSkillFilesTest {

    private static final Pattern SECTION = Pattern.compile("^\\s+(\\w+):\\s*(#.*)?$");
    private static final Pattern ITEM = Pattern.compile("^\\s+- (.*)$");
    private static final Pattern TOP_KEY = Pattern.compile("^(\\w+):\\s*$");

    private static final Set<String> mechanics = new HashSet<>();
    private static final Set<String> targeters = new HashSet<>();
    private static final Set<String> conditions = new HashSet<>();

    private record Line(String file, String section, String text) {
    }

    @BeforeAll
    static void collectNames() {
        SkillRegistry.registerBuiltins(new SkillRegistry() {
            @Override
            public void registerMechanic(Function<Params, Mechanic> factory, String... names) {
                Collections.addAll(mechanics, names);
            }

            @Override
            public void registerTargeter(Function<Params, Targeter> factory, String... names) {
                Collections.addAll(targeters, names);
            }

            @Override
            public void registerCondition(Function<Params, Condition> factory, String... names) {
                Collections.addAll(conditions, names);
            }
        });
        // registered by BlueMoonSkills
        conditions.add("holding");
    }

    private static List<Path> files(String folder, String suffix) throws IOException, URISyntaxException {
        List<Path> out = new ArrayList<>();
        var urls = ExampleSkillFilesTest.class.getClassLoader().getResources(folder);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try (Stream<Path> s = Files.list(Path.of(url.toURI()))) {
                s.filter(p -> p.toString().endsWith(suffix)).forEach(out::add);
            }
        }
        return out;
    }

    @Test
    void allExampleLinesAreValid() throws Exception {
        List<Line> lines = new ArrayList<>();
        Set<String> skillNames = new HashSet<>();
        for (String folder : List.of("skills", "mobs", "summons", "weapons")) {
            for (Path file : files(folder, ".yml")) {
                String section = "";
                for (String raw : Files.readAllLines(file)) {
                    if (raw.isBlank() || raw.trim().startsWith("#")) {
                        continue;
                    }
                    Matcher top = TOP_KEY.matcher(raw);
                    if (top.matches() && folder.equals("skills")) {
                        skillNames.add(top.group(1).toLowerCase(Locale.ROOT));
                    }
                    Matcher sec = SECTION.matcher(raw);
                    if (sec.matches()) {
                        section = sec.group(1);
                        continue;
                    }
                    Matcher item = ITEM.matcher(raw);
                    if (item.matches()) {
                        lines.add(new Line(folder + "/" + file.getFileName(), section, item.group(1)));
                    }
                }
            }
        }
        for (Path file : files("models", ".bbmodel")) {
            BBModel model = BBModelParser.parse(Files.readString(file));
            for (BBModel.Animation a : model.animations.values()) {
                for (EffectKeyframe k : a.effects) {
                    if (k.type() == EffectType.TIMELINE) {
                        for (String row : k.script().split("\\R")) {
                            if (!row.isBlank() && !row.trim().startsWith("#")) {
                                lines.add(new Line(file.getFileName() + " timeline", "Skills", row.trim()));
                            }
                        }
                    }
                }
            }
        }

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Line line : lines) {
            try {
                switch (line.section()) {
                    case "Skills" -> {
                        ParsedLine p = SkillLineParser.parse(line.text());
                        checked++;
                        if (p.isDelay()) {
                            continue;
                        }
                        if (!mechanics.contains(p.mechanic())) {
                            problems.add(line + ": unknown mechanic " + p.mechanic());
                        }
                        if (p.targeter() != null && !targeters.contains(p.targeter().name())) {
                            problems.add(line + ": unknown targeter " + p.targeter().name());
                        }
                        for (ParsedCondition c : p.conditions()) {
                            if (!conditions.contains(c.name())) {
                                problems.add(line + ": unknown condition " + c.name());
                            }
                        }
                        if (p.trigger() != null && SkillTrigger.parse(p.trigger()) == null) {
                            problems.add(line + ": unknown trigger " + p.trigger());
                        }
                        for (String key : List.of("s", "skill", "ontick", "onhit", "onend", "onstart")) {
                            String ref = p.params().getString(null, key);
                            boolean isSkillRef = !key.equals("s") && !key.equals("skill")
                                    || List.of("skill", "repeat", "metaskill").contains(p.mechanic());
                            if (ref != null && isSkillRef && !skillNames.contains(ref.toLowerCase(Locale.ROOT))) {
                                problems.add(line + ": unknown metaskill " + ref);
                            }
                        }
                    }
                    case "Conditions", "TargetConditions" -> {
                        ParsedCondition c = SkillLineParser.parseConditionEntry(line.text(), false);
                        checked++;
                        if (!conditions.contains(c.name())) {
                            problems.add(line + ": unknown condition " + c.name());
                        }
                    }
                    default -> {
                        // Drops, Lore, ...
                    }
                }
            } catch (Exception e) {
                problems.add(line + ": " + e.getMessage());
            }
        }
        assertTrue(checked > 50, "only " + checked + " lines found");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
