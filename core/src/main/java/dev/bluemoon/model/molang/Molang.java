package dev.bluemoon.model.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A small Molang subset for Blockbench keyframe values:
 * arithmetic, comparisons, ternary, {@code query.anim_time}, {@code query.life_time} and {@code math.*}.
 * Unknown variables evaluate to 0, like in Bedrock.
 */
public final class Molang {

    public interface Expr {
        double eval(Context ctx);
    }

    public static final class Context {
        public double animTime;
        public double lifeTime;
    }

    private record Const(double value) implements Expr {
        @Override
        public double eval(Context ctx) {
            return value;
        }
    }

    public static final Expr ZERO = new Const(0);

    private static final Map<String, Expr> CACHE = new ConcurrentHashMap<>();

    private Molang() {
    }

    public static Expr constant(double value) {
        return new Const(value);
    }

    public static boolean isConstant(Expr expr) {
        return expr instanceof Const;
    }

    /** Compiles an expression; throws {@link IllegalArgumentException} on syntax errors. */
    public static Expr compile(String source) {
        if (source == null) {
            return ZERO;
        }
        String src = source.trim();
        if (src.isEmpty()) {
            return ZERO;
        }
        try {
            return new Const(Double.parseDouble(src));
        } catch (NumberFormatException ignored) {
            // fall through to the parser
        }
        return CACHE.computeIfAbsent(src, s -> {
            String body = s.toLowerCase(Locale.ROOT);
            while (body.endsWith(";")) {
                body = body.substring(0, body.length() - 1).trim();
            }
            if (body.startsWith("return ")) {
                body = body.substring(7);
            }
            if (body.contains(";")) {
                throw new IllegalArgumentException("여러 문장 Molang 은 지원하지 않습니다: " + s);
            }
            Parser p = new Parser(body);
            Expr e = p.parseExpression();
            p.skipWs();
            if (!p.eof()) {
                throw new IllegalArgumentException("Molang 해석 실패 (위치 " + p.pos + "): " + s);
            }
            return e;
        });
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        boolean accept(String token) {
            skipWs();
            if (s.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        void expect(String token) {
            if (!accept(token)) {
                throw new IllegalArgumentException("'" + token + "' 가 필요합니다 (위치 " + pos + "): " + s);
            }
        }

        Expr parseExpression() {
            return parseTernary();
        }

        Expr parseTernary() {
            Expr cond = parseOr();
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '?' && !s.startsWith("??", pos)) {
                pos++;
                Expr a = parseTernary();
                expect(":");
                Expr b = parseTernary();
                return fold(c -> cond.eval(c) != 0 ? a.eval(c) : b.eval(c), cond, a, b);
            }
            return cond;
        }

        Expr parseOr() {
            Expr left = parseAnd();
            while (accept("||")) {
                Expr l = left;
                Expr r = parseAnd();
                left = fold(c -> (l.eval(c) != 0 || r.eval(c) != 0) ? 1 : 0, l, r);
            }
            return left;
        }

        Expr parseAnd() {
            Expr left = parseCompare();
            while (accept("&&")) {
                Expr l = left;
                Expr r = parseCompare();
                left = fold(c -> (l.eval(c) != 0 && r.eval(c) != 0) ? 1 : 0, l, r);
            }
            return left;
        }

        Expr parseCompare() {
            Expr left = parseAdd();
            while (true) {
                Expr l = left;
                if (accept("<=")) {
                    Expr r = parseAdd();
                    left = fold(c -> l.eval(c) <= r.eval(c) ? 1 : 0, l, r);
                } else if (accept(">=")) {
                    Expr r = parseAdd();
                    left = fold(c -> l.eval(c) >= r.eval(c) ? 1 : 0, l, r);
                } else if (accept("==")) {
                    Expr r = parseAdd();
                    left = fold(c -> l.eval(c) == r.eval(c) ? 1 : 0, l, r);
                } else if (accept("!=")) {
                    Expr r = parseAdd();
                    left = fold(c -> l.eval(c) != r.eval(c) ? 1 : 0, l, r);
                } else if (accept("<")) {
                    Expr r = parseAdd();
                    left = fold(c -> l.eval(c) < r.eval(c) ? 1 : 0, l, r);
                } else if (accept(">")) {
                    Expr r = parseAdd();
                    left = fold(c -> l.eval(c) > r.eval(c) ? 1 : 0, l, r);
                } else {
                    return left;
                }
            }
        }

        Expr parseAdd() {
            Expr left = parseMul();
            while (true) {
                Expr l = left;
                if (accept("+")) {
                    Expr r = parseMul();
                    left = fold(c -> l.eval(c) + r.eval(c), l, r);
                } else if (accept("-")) {
                    Expr r = parseMul();
                    left = fold(c -> l.eval(c) - r.eval(c), l, r);
                } else {
                    return left;
                }
            }
        }

        Expr parseMul() {
            Expr left = parseUnary();
            while (true) {
                Expr l = left;
                if (accept("*")) {
                    Expr r = parseUnary();
                    left = fold(c -> l.eval(c) * r.eval(c), l, r);
                } else if (accept("/")) {
                    Expr r = parseUnary();
                    left = fold(c -> {
                        double d = r.eval(c);
                        return d == 0 ? 0 : l.eval(c) / d;
                    }, l, r);
                } else {
                    return left;
                }
            }
        }

        Expr parseUnary() {
            if (accept("-")) {
                Expr e = parseUnary();
                return fold(c -> -e.eval(c), e);
            }
            if (accept("+")) {
                return parseUnary();
            }
            if (accept("!")) {
                Expr e = parseUnary();
                return fold(c -> e.eval(c) == 0 ? 1 : 0, e);
            }
            return parsePrimary();
        }

        Expr parsePrimary() {
            skipWs();
            if (eof()) {
                throw new IllegalArgumentException("식이 끝났습니다: " + s);
            }
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                Expr e = parseExpression();
                expect(")");
                return e;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                    pos++;
                }
                if (pos < s.length() && s.charAt(pos) == 'f') {
                    pos++;
                    return new Const(Double.parseDouble(s.substring(start, pos - 1)));
                }
                return new Const(Double.parseDouble(s.substring(start, pos)));
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) {
                    pos++;
                }
                String ident = s.substring(start, pos);
                List<Expr> args = null;
                if (accept("(")) {
                    args = new ArrayList<>();
                    if (!accept(")")) {
                        do {
                            args.add(parseExpression());
                        } while (accept(","));
                        expect(")");
                    }
                }
                return resolve(ident, args);
            }
            throw new IllegalArgumentException("예상치 못한 문자 '" + c + "' (위치 " + pos + "): " + s);
        }

        private Expr resolve(String ident, List<Expr> args) {
            String name = ident.startsWith("q.") ? "query." + ident.substring(2) : ident;
            if (args == null) {
                return switch (name) {
                    case "query.anim_time" -> ctx -> ctx.animTime;
                    case "query.life_time" -> ctx -> ctx.lifeTime;
                    case "math.pi" -> new Const(Math.PI);
                    case "true" -> new Const(1);
                    case "false" -> new Const(0);
                    default -> ZERO;
                };
            }
            Expr a = args.isEmpty() ? ZERO : args.get(0);
            Expr b = args.size() > 1 ? args.get(1) : ZERO;
            Expr d = args.size() > 2 ? args.get(2) : ZERO;
            return switch (name) {
                case "math.sin" -> fold(c -> Math.sin(Math.toRadians(a.eval(c))), a);
                case "math.cos" -> fold(c -> Math.cos(Math.toRadians(a.eval(c))), a);
                case "math.asin" -> fold(c -> Math.toDegrees(Math.asin(a.eval(c))), a);
                case "math.acos" -> fold(c -> Math.toDegrees(Math.acos(a.eval(c))), a);
                case "math.atan" -> fold(c -> Math.toDegrees(Math.atan(a.eval(c))), a);
                case "math.atan2" -> fold(c -> Math.toDegrees(Math.atan2(a.eval(c), b.eval(c))), a, b);
                case "math.abs" -> fold(c -> Math.abs(a.eval(c)), a);
                case "math.sqrt" -> fold(c -> Math.sqrt(a.eval(c)), a);
                case "math.floor" -> fold(c -> Math.floor(a.eval(c)), a);
                case "math.ceil" -> fold(c -> Math.ceil(a.eval(c)), a);
                case "math.round" -> fold(c -> (double) Math.round(a.eval(c)), a);
                case "math.trunc" -> fold(c -> (double) (long) a.eval(c), a);
                case "math.exp" -> fold(c -> Math.exp(a.eval(c)), a);
                case "math.ln" -> fold(c -> Math.log(a.eval(c)), a);
                case "math.pow" -> fold(c -> Math.pow(a.eval(c), b.eval(c)), a, b);
                case "math.min" -> fold(c -> Math.min(a.eval(c), b.eval(c)), a, b);
                case "math.max" -> fold(c -> Math.max(a.eval(c), b.eval(c)), a, b);
                case "math.mod" -> fold(c -> {
                    double m = b.eval(c);
                    return m == 0 ? 0 : a.eval(c) % m;
                }, a, b);
                case "math.clamp" -> fold(c -> Math.max(b.eval(c), Math.min(d.eval(c), a.eval(c))), a, b, d);
                case "math.lerp" -> fold(c -> {
                    double x = a.eval(c);
                    return x + (b.eval(c) - x) * d.eval(c);
                }, a, b, d);
                case "math.hermite_blend" -> fold(c -> {
                    double t = a.eval(c);
                    return 3 * t * t - 2 * t * t * t;
                }, a);
                case "math.random" -> c -> {
                    double lo = a.eval(c);
                    double hi = b.eval(c);
                    return lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
                };
                case "math.random_integer" -> c -> {
                    long lo = Math.round(a.eval(c));
                    long hi = Math.round(b.eval(c));
                    return hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
                };
                default -> ZERO;
            };
        }

        /** Evaluates the node once if every child is constant. */
        private static Expr fold(Expr node, Expr... children) {
            for (Expr child : children) {
                if (!(child instanceof Const)) {
                    return node;
                }
            }
            return new Const(node.eval(new Context()));
        }
    }
}
