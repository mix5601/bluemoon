package dev.bluemoon.skill;

import dev.bluemoon.skill.parse.SkillLineParser;
import dev.bluemoon.skill.parse.SkillLineParser.ParsedCondition;
import dev.bluemoon.skill.parse.SkillLineParser.ParsedLine;
import dev.bluemoon.skill.parse.SkillParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLineParserTest {

    @Test
    void fullLine() throws SkillParseException {
        ParsedLine p = SkillLineParser.parse(
                "damage{amount=10;ignorearmor=true} @PlayersInRadius{r=5} ~onTimer:40 ?healthpercent{max=0.5} ?~!isplayer 0.25");
        assertEquals("damage", p.mechanic());
        assertEquals(10, p.params().getDouble(0, "a", "amount"));
        assertTrue(p.params().getBoolean(false, "ignorearmor"));
        assertEquals("playersinradius", p.targeter().name());
        assertEquals(5, p.targeter().params().getDouble(0, "r"));
        assertEquals("ontimer", p.trigger());
        assertEquals("40", p.triggerArg());
        assertEquals(0.25, p.chance());
        assertEquals(2, p.conditions().size());
        ParsedCondition caster = p.conditions().get(0);
        assertEquals("healthpercent", caster.name());
        assertFalse(caster.targetCondition());
        ParsedCondition target = p.conditions().get(1);
        assertEquals("isplayer", target.name());
        assertTrue(target.targetCondition());
        assertTrue(target.negate());
    }

    @Test
    void quotedValuesKeepSpacesAndSemicolons() throws SkillParseException {
        ParsedLine p = SkillLineParser.parse("message{m=\"&c안녕 <target.name>; 반가워\"} @trigger");
        assertEquals("&c안녕 <target.name>; 반가워", p.params().getString("", "m"));
        assertEquals("trigger", p.targeter().name());
        assertNull(p.trigger());
    }

    @Test
    void unquotedSpacesInsideBraces() throws SkillParseException {
        ParsedLine p = SkillLineParser.parse("- message{m=hello world} @self");
        assertEquals("hello world", p.params().getString("", "m"));
    }

    @Test
    void delayAndShorthand() throws SkillParseException {
        ParsedLine d = SkillLineParser.parse("delay 20");
        assertTrue(d.isDelay());
        assertEquals(20, d.delayTicks());
        ParsedLine s = SkillLineParser.parse("skill:Fireball @target");
        assertEquals("skill", s.mechanic());
        assertEquals("Fireball", s.params().getString("", "s"));
    }

    @Test
    void conditionEntries() throws SkillParseException {
        ParsedCondition c = SkillLineParser.parseConditionEntry("healthpercent{max=0.5} false", false);
        assertTrue(c.negate());
        ParsedCondition t = SkillLineParser.parseConditionEntry("isplayer", true);
        assertTrue(t.targetCondition());
        assertFalse(t.negate());
    }

    @Test
    void errors() {
        assertThrows(SkillParseException.class, () -> SkillLineParser.parse("damage{a=5"));
        assertThrows(SkillParseException.class, () -> SkillLineParser.parse("damage{a} @self"));
        assertThrows(SkillParseException.class, () -> SkillLineParser.parse("damage @a @b"));
        assertThrows(SkillParseException.class, () -> SkillLineParser.parse("damage what"));
    }
}
