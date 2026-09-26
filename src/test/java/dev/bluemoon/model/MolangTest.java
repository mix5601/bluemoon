package dev.bluemoon.model;

import dev.bluemoon.model.molang.Molang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MolangTest {

    private static double eval(String src, double animTime) {
        Molang.Context ctx = new Molang.Context();
        ctx.animTime = animTime;
        return Molang.compile(src).eval(ctx);
    }

    @Test
    void constantsAndArithmetic() {
        assertEquals(12.5, eval("12.5", 0), 1e-9);
        assertEquals(7, eval("1 + 2 * 3", 0), 1e-9);
        assertEquals(9, eval("(1 + 2) * 3", 0), 1e-9);
        assertEquals(-4, eval("-(2 * 2)", 0), 1e-9);
        assertEquals(0, eval("5 / 0", 0), 1e-9);
        assertTrue(Molang.isConstant(Molang.compile("math.sin(90) * 10")));
    }

    @Test
    void queriesAndMath() {
        assertEquals(1.5, eval("query.anim_time", 1.5), 1e-9);
        assertEquals(3, eval("q.anim_time * 2", 1.5), 1e-9);
        assertEquals(10, eval("math.sin(q.anim_time * 90) * 10", 1.0), 1e-9);
        assertEquals(-1, eval("math.cos(180)", 0), 1e-9);
        assertEquals(5, eval("math.clamp(12, 0, 5)", 0), 1e-9);
        assertEquals(2.5, eval("math.lerp(0, 10, 0.25)", 0), 1e-9);
        assertEquals(0, eval("variable.unknown + query.nothing", 0), 1e-9);
    }

    @Test
    void comparisonsAndTernary() {
        assertEquals(1, eval("q.anim_time > 1 ? 1 : 0", 2), 1e-9);
        assertEquals(0, eval("q.anim_time > 1 ? 1 : 0", 0.5), 1e-9);
        assertEquals(1, eval("1 < 2 && 3 >= 3", 0), 1e-9);
        assertEquals(4, eval("return 4;", 0), 1e-9);
    }

    @Test
    void syntaxErrors() {
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("1 +"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("(1 + 2"));
    }
}
