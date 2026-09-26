package dev.bluemoon.model.animation;

import dev.bluemoon.model.bbmodel.BBModel.Interpolation;
import dev.bluemoon.model.bbmodel.BBModel.Keyframe;
import dev.bluemoon.model.molang.Molang;
import org.joml.Vector3f;

import java.util.List;

/** Samples a keyframe channel the same way Blockbench does. */
public final class KeyframeSampler {

    private KeyframeSampler() {
    }

    /**
     * Writes the channel value at {@code time} into {@code out}.
     *
     * @return false when the channel has no keyframes
     */
    public static boolean sample(List<Keyframe> frames, double time, Molang.Context ctx, Vector3f out) {
        int n = frames.size();
        if (n == 0) {
            return false;
        }
        Keyframe first = frames.get(0);
        if (n == 1 || time <= first.time) {
            eval(first.pre, ctx, out);
            return true;
        }
        Keyframe last = frames.get(n - 1);
        if (time >= last.time) {
            eval(last.post, ctx, out);
            return true;
        }
        int i = 0;
        while (i < n - 2 && frames.get(i + 1).time <= time) {
            i++;
        }
        Keyframe before = frames.get(i);
        Keyframe after = frames.get(i + 1);
        double span = after.time - before.time;
        double alpha = span <= 0 ? 1 : (time - before.time) / span;

        if (before.interpolation == Interpolation.STEP) {
            eval(before.post, ctx, out);
            return true;
        }
        if (before.interpolation == Interpolation.CATMULLROM || after.interpolation == Interpolation.CATMULLROM) {
            Molang.Expr[] p0 = i > 0 ? frames.get(i - 1).post : before.post;
            Molang.Expr[] p3 = i + 2 < n ? frames.get(i + 2).pre : after.pre;
            for (int axis = 0; axis < 3; axis++) {
                double v = catmullRom(alpha,
                        p0[axis].eval(ctx), before.post[axis].eval(ctx), after.pre[axis].eval(ctx), p3[axis].eval(ctx));
                out.setComponent(axis, (float) v);
            }
            return true;
        }
        if (before.interpolation == Interpolation.BEZIER || after.interpolation == Interpolation.BEZIER) {
            for (int axis = 0; axis < 3; axis++) {
                double v1 = before.post[axis].eval(ctx);
                double v2 = after.pre[axis].eval(ctx);
                double c1t = before.interpolation == Interpolation.BEZIER ? before.bezierRightTime[axis] : 0;
                double c1v = before.interpolation == Interpolation.BEZIER ? before.bezierRightValue[axis] : 0;
                double c2t = after.interpolation == Interpolation.BEZIER ? after.bezierLeftTime[axis] : 0;
                double c2v = after.interpolation == Interpolation.BEZIER ? after.bezierLeftValue[axis] : 0;
                out.setComponent(axis, (float) bezier(time,
                        before.time, v1, before.time + c1t, v1 + c1v, after.time + c2t, v2 + c2v, after.time, v2));
            }
            return true;
        }
        for (int axis = 0; axis < 3; axis++) {
            double a = before.post[axis].eval(ctx);
            double b = after.pre[axis].eval(ctx);
            out.setComponent(axis, (float) (a + (b - a) * alpha));
        }
        return true;
    }

    private static void eval(Molang.Expr[] v, Molang.Context ctx, Vector3f out) {
        out.set((float) v[0].eval(ctx), (float) v[1].eval(ctx), (float) v[2].eval(ctx));
    }

    static double catmullRom(double t, double p0, double p1, double p2, double p3) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Cubic bezier through (time, value) control points, solved for the given time. */
    static double bezier(double time, double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3) {
        double lo = 0;
        double hi = 1;
        double s = 0.5;
        for (int i = 0; i < 24; i++) {
            s = (lo + hi) / 2;
            double x = cubic(s, x0, x1, x2, x3);
            if (x < time) {
                lo = s;
            } else {
                hi = s;
            }
        }
        return cubic(s, y0, y1, y2, y3);
    }

    private static double cubic(double s, double a, double b, double c, double d) {
        double u = 1 - s;
        return u * u * u * a + 3 * u * u * s * b + 3 * u * s * s * c + s * s * s * d;
    }
}
