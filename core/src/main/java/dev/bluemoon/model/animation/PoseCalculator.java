package dev.bluemoon.model.animation;

import dev.bluemoon.model.bbmodel.BBModel.BoneAnimator;
import dev.bluemoon.model.molang.Molang;
import dev.bluemoon.model.runtime.ModelBlueprint;
import dev.bluemoon.model.runtime.ModelBlueprint.Bone;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Turns animation layers into per-bone matrices.
 *
 * <p>Bone matrices are built exactly like Blockbench's preview (three.js, ZYX euler order, keyframe
 * X/Y rotation and X position negated). The display matrix then converts Blockbench space to the
 * item display's space: item displays render their item rotated 180 degrees around Y, so the
 * matrix is conjugated with that rotation, which also turns a north-facing model to face the
 * entity's yaw-0 direction (south).</p>
 */
public final class PoseCalculator {

    private static final float DEG = (float) (Math.PI / 180.0);

    private final ModelBlueprint blueprint;
    private final Matrix4f[] world;
    private final Vector3f tmp = new Vector3f();
    private final Vector3f dPos = new Vector3f();
    private final Vector3f dRot = new Vector3f();
    private final Vector3f dScale = new Vector3f();
    private final Molang.Context ctx = new Molang.Context();

    public PoseCalculator(ModelBlueprint blueprint) {
        this.blueprint = blueprint;
        this.world = new Matrix4f[blueprint.bones.size()];
        for (int i = 0; i < world.length; i++) {
            world[i] = new Matrix4f();
        }
    }

    /** Bone to model matrices (in blocks) from the last {@link #compute} call. */
    public Matrix4f world(int boneIndex) {
        return world[boneIndex];
    }

    /**
     * @param layers     playing animations, lowest priority first
     * @param headYaw    head yaw relative to the body in degrees (Minecraft convention)
     * @param headPitch  head pitch in degrees (positive looks down)
     * @param lifeTime   seconds the entity has existed, for {@code query.life_time}
     */
    public void compute(List<AnimationLayer> layers, float headYaw, float headPitch, double lifeTime) {
        ctx.lifeTime = lifeTime;
        for (Bone bone : blueprint.bones) {
            dPos.zero();
            dRot.zero();
            dScale.set(1);
            if (bone.uuid != null && !bone.synthetic) {
                for (AnimationLayer layer : layers) {
                    BoneAnimator animator = layer.animation.bones.get(bone.uuid);
                    if (animator == null) {
                        continue;
                    }
                    float w = (float) layer.weight();
                    if (w <= 0) {
                        continue;
                    }
                    ctx.animTime = layer.time();
                    if (KeyframeSampler.sample(animator.position, layer.time(), ctx, tmp)) {
                        dPos.lerp(tmp, w);
                    }
                    if (KeyframeSampler.sample(animator.rotation, layer.time(), ctx, tmp)) {
                        dRot.lerp(tmp, w);
                    }
                    if (KeyframeSampler.sample(animator.scale, layer.time(), ctx, tmp)) {
                        dScale.lerp(tmp, w);
                    }
                }
            }

            Matrix4f m = world[bone.index];
            if (bone.parent != null) {
                m.set(world[bone.parent.index]);
            } else {
                m.identity();
            }
            Vector3f parentOrigin = bone.parent != null ? bone.parent.origin : new Vector3f();
            float px = bone.origin.x - parentOrigin.x - dPos.x;
            float py = bone.origin.y - parentOrigin.y + dPos.y;
            float pz = bone.origin.z - parentOrigin.z + dPos.z;
            m.translate(px / 16f, py / 16f, pz / 16f);
            m.rotateZYX(
                    (bone.rotation.z + dRot.z) * DEG,
                    (bone.rotation.y - dRot.y) * DEG,
                    (bone.rotation.x - dRot.x) * DEG);
            if (bone.head) {
                // Minecraft yaw turns clockwise seen from above; in the (north facing) model space both
                // yaw and pitch map to negative rotations.
                m.rotateY(-headYaw * DEG);
                m.rotateX(-headPitch * DEG);
            }
            m.scale(dScale.x, dScale.y, dScale.z);
        }
    }

    /**
     * Matrix for the bone's item display entity.
     *
     * @param modelScale overall model scale
     */
    public Matrix4f displayMatrix(Bone bone, float modelScale, Matrix4f out) {
        return out.identity()
                .rotateY((float) Math.PI)
                .scale(modelScale)
                .mul(world[bone.index])
                .scale(bone.geometryScale)
                .rotateY((float) -Math.PI);
    }

    /**
     * Position (relative to the entity, in blocks, already rotated by the body yaw) of a point given in
     * Blockbench pixels inside the bone's space.
     */
    public Vector3f pointOffset(Bone bone, Vector3f modelPixels, float modelScale, float bodyYaw, Vector3f out) {
        return pointOffset(bone, modelPixels, modelScale, bodyYaw, 0f, out);
    }

    /** Same as above for a model whose root is also pitched (display entities apply yaw, then pitch). */
    public Vector3f pointOffset(Bone bone, Vector3f modelPixels, float modelScale, float yaw, float pitch, Vector3f out) {
        out.set(modelPixels).sub(bone.origin).div(16f);
        world[bone.index].transformPosition(out);
        // Blockbench space -> entity space (rotate 180 around Y), then apply model scale and root rotation
        out.set(-out.x, out.y, -out.z).mul(modelScale);
        return out.rotateX(pitch * DEG).rotateY(-yaw * DEG);
    }
}
