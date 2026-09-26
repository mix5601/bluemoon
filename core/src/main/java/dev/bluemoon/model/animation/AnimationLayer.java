package dev.bluemoon.model.animation;

import dev.bluemoon.model.bbmodel.BBModel.Animation;
import dev.bluemoon.model.bbmodel.BBModel.EffectKeyframe;
import dev.bluemoon.model.bbmodel.BBModel.LoopMode;

import java.util.ArrayList;
import java.util.List;

/** One playing animation on a model instance. Advanced once per server tick. */
public final class AnimationLayer {

    public final Animation animation;
    public final LoopMode mode;
    public final int priority;
    public final long order;
    private final double speed;
    private final int fadeIn;
    private int fadeOut;

    private double time;
    private int age;
    private int stopAge = -1;
    private boolean started;
    private boolean finished;

    public AnimationLayer(Animation animation, LoopMode mode, double speed, int fadeIn, int fadeOut, int priority, long order) {
        this.animation = animation;
        this.mode = mode;
        this.speed = speed;
        this.fadeIn = Math.max(0, fadeIn);
        this.fadeOut = Math.max(0, fadeOut);
        this.priority = priority;
        this.order = order;
    }

    public String name() {
        return animation.name;
    }

    public double time() {
        return time;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isStopping() {
        return stopAge >= 0;
    }

    /** True once a non-looping animation reached its last frame. */
    public boolean reachedEnd() {
        return mode != LoopMode.LOOP && time >= animation.length;
    }

    public void stop(int fadeOutTicks) {
        if (stopAge >= 0) {
            return;
        }
        fadeOut = Math.max(0, fadeOutTicks);
        if (fadeOut == 0) {
            finished = true;
        } else {
            stopAge = age;
        }
    }

    /** Blend weight from fade in / fade out, 0..1. */
    public double weight() {
        double w = fadeIn > 0 ? Math.min(1.0, (age + 1) / (double) (fadeIn + 1)) : 1.0;
        if (stopAge >= 0 && fadeOut > 0) {
            w *= Math.max(0.0, 1.0 - (age - stopAge) / (double) fadeOut);
        }
        return w;
    }

    /**
     * Moves the animation forward.
     *
     * @return effect keyframes crossed during this step
     */
    public List<EffectKeyframe> advance(double seconds) {
        List<EffectKeyframe> fired = new ArrayList<>(0);
        if (finished) {
            return fired;
        }
        age++;
        if (stopAge >= 0) {
            if (age - stopAge >= fadeOut) {
                finished = true;
            }
            return fired;
        }
        double length = animation.length;
        if (!started) {
            started = true;
            collect(fired, -1, 0);
        }
        double prev = time;
        time += seconds * speed;

        if (mode == LoopMode.LOOP && length > 0) {
            if (time >= length) {
                collect(fired, prev, length);
                time %= length;
                collect(fired, -1, time);
            } else {
                collect(fired, prev, time);
            }
        } else {
            if (time >= length) {
                collect(fired, prev, length);
                time = length;
                if (mode == LoopMode.ONCE) {
                    stop(fadeOut);
                }
            } else {
                collect(fired, prev, time);
            }
        }
        return fired;
    }

    /** Adds effects with {@code from < t <= to}. */
    private void collect(List<EffectKeyframe> out, double from, double to) {
        for (EffectKeyframe k : animation.effects) {
            if (k.time() > from && k.time() <= to) {
                out.add(k);
            }
        }
    }
}
