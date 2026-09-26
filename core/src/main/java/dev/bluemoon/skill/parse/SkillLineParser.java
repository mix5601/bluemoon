package dev.bluemoon.skill.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses MythicMobs style skill lines:
 * <pre>
 * mechanic{key=value;key2=value2} @targeter{r=5} ~onTimer:20 ?condition{..} ?~targetCondition ?!negated 0.5
 * delay 20
 * </pre>
 */
public final class SkillLineParser {

    private SkillLineParser() {
    }

    /** A named component with options, e.g. {@code PlayersInRadius{r=5}}. */
    public record Component(String name, Params params) {
    }

    public record ParsedCondition(String name, Params params, boolean targetCondition, boolean negate) {
    }

    public record ParsedLine(
            String raw,
            String mechanic,
            Params params,
            Component targeter,
            String trigger,
            String triggerArg,
            List<ParsedCondition> conditions,
            double chance,
            int delayTicks
    ) {
        public boolean isDelay() {
            return "delay".equals(mechanic);
        }
    }

    public static ParsedLine parse(String raw) throws SkillParseException {
        String line = raw.trim();
        if (line.startsWith("- ")) {
            line = line.substring(2).trim();
        }
        List<String> tokens = splitTopLevel(line);
        if (tokens.isEmpty()) {
            throw new SkillParseException("빈 스킬 라인");
        }

        String first = tokens.get(0);
        if (first.equalsIgnoreCase("delay")) {
            if (tokens.size() < 2) {
                throw new SkillParseException("delay 뒤에 틱 수가 필요합니다: " + raw);
            }
            try {
                int ticks = Integer.parseInt(tokens.get(1));
                return new ParsedLine(raw, "delay", Params.EMPTY, null, null, null, List.of(), 1.0, Math.max(0, ticks));
            } catch (NumberFormatException e) {
                throw new SkillParseException("잘못된 delay 값: " + tokens.get(1));
            }
        }

        Component mechanic;
        if (first.regionMatches(true, 0, "skill:", 0, 6) && !first.contains("{")) {
            mechanic = new Component("skill", new Params(Map.of("s", first.substring(6))));
        } else {
            mechanic = parseComponent(first);
        }

        Component targeter = null;
        String trigger = null;
        String triggerArg = null;
        List<ParsedCondition> conditions = new ArrayList<>();
        double chance = 1.0;

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            char c = token.charAt(0);
            if (c == '@') {
                if (targeter != null) {
                    throw new SkillParseException("타겟터가 두 개 이상입니다: " + raw);
                }
                targeter = parseComponent(token.substring(1));
            } else if (c == '~') {
                String t = token.substring(1);
                int colon = t.indexOf(':');
                if (colon >= 0) {
                    triggerArg = t.substring(colon + 1);
                    t = t.substring(0, colon);
                }
                trigger = t.toLowerCase(Locale.ROOT);
            } else if (c == '?') {
                String body = token.substring(1);
                boolean target = false;
                boolean negate = false;
                // accept ?~!cond, ?!~cond, ?~cond, ?!cond
                for (int k = 0; k < 2 && !body.isEmpty(); k++) {
                    if (body.charAt(0) == '~') {
                        target = true;
                        body = body.substring(1);
                    } else if (body.charAt(0) == '!') {
                        negate = true;
                        body = body.substring(1);
                    }
                }
                Component cond = parseComponent(body);
                conditions.add(new ParsedCondition(cond.name(), cond.params(), target, negate));
            } else if (isNumber(token)) {
                chance = Double.parseDouble(token);
            } else {
                throw new SkillParseException("알 수 없는 토큰 '" + token + "' in: " + raw);
            }
        }

        return new ParsedLine(raw, mechanic.name(), mechanic.params(), targeter, trigger, triggerArg,
                List.copyOf(conditions), chance, 0);
    }

    /**
     * Parses a condition entry written in a {@code Conditions:} list, e.g. {@code healthpercent{max=0.5} true}.
     */
    public static ParsedCondition parseConditionEntry(String raw, boolean targetCondition) throws SkillParseException {
        List<String> tokens = splitTopLevel(raw.trim());
        if (tokens.isEmpty()) {
            throw new SkillParseException("빈 조건");
        }
        String first = tokens.get(0);
        boolean negate = false;
        if (first.startsWith("!")) {
            negate = true;
            first = first.substring(1);
        }
        Component c = parseComponent(first);
        if (tokens.size() > 1) {
            String action = tokens.get(1).toLowerCase(Locale.ROOT);
            if (action.equals("false")) {
                negate = !negate;
            } else if (!action.equals("true")) {
                throw new SkillParseException("조건 액션은 true/false 만 지원합니다: " + raw);
            }
        }
        return new ParsedCondition(c.name(), c.params(), targetCondition, negate);
    }

    /** Parses {@code name{a=1;b=2}} or {@code name[a=1;b=2]} or a bare {@code name}. */
    public static Component parseComponent(String token) throws SkillParseException {
        int open = -1;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '{' || c == '[') {
                open = i;
                break;
            }
        }
        if (open < 0) {
            if (token.isEmpty()) {
                throw new SkillParseException("이름이 비어 있습니다");
            }
            return new Component(token.toLowerCase(Locale.ROOT), Params.EMPTY);
        }
        char close = token.charAt(open) == '{' ? '}' : ']';
        if (token.charAt(token.length() - 1) != close) {
            throw new SkillParseException("괄호가 닫히지 않았습니다: " + token);
        }
        String name = token.substring(0, open).toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new SkillParseException("이름이 비어 있습니다: " + token);
        }
        String inner = token.substring(open + 1, token.length() - 1);
        return new Component(name, new Params(parseParams(inner)));
    }

    public static Map<String, String> parseParams(String inner) throws SkillParseException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : splitRespecting(inner, ';')) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq <= 0) {
                throw new SkillParseException("key=value 형식이 아닙니다: " + p);
            }
            String key = p.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = unquote(p.substring(eq + 1).trim());
            map.put(key, value);
        }
        return map;
    }

    /** Splits on whitespace that is not inside braces, brackets or quotes. */
    public static List<String> splitTopLevel(String s) throws SkillParseException {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if ((c == '"' || c == '\'') && depth > 0) {
                quote = c;
                cur.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                cur.append(c);
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth < 0) {
                    throw new SkillParseException("괄호 짝이 맞지 않습니다: " + s);
                }
                cur.append(c);
            } else if (Character.isWhitespace(c) && depth == 0) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (depth != 0 || quote != 0) {
            throw new SkillParseException("괄호 또는 따옴표가 닫히지 않았습니다: " + s);
        }
        if (!cur.isEmpty()) {
            out.add(cur.toString());
        }
        return out;
    }

    private static List<String> splitRespecting(String s, char separator) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == separator && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private static String unquote(String v) {
        if (v.length() >= 2) {
            char f = v.charAt(0);
            char l = v.charAt(v.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
