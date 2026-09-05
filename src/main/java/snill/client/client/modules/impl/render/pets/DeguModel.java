package snill.client.client.modules.impl.render.pets;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.MathHelper;

public final class DeguModel extends Model {
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart tail;
    private final ModelPart tail2;
    private final ModelPart head;
    private final ModelPart body;

    public DeguModel(ModelPart root) {
        super(root, RenderLayer::getEntityTranslucent);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.tail = root.getChild("tail");
        this.tail2 = root.getChild("tail2");
        this.backLeftLeg = root.getChild("left_hind_leg");
        this.backRightLeg = root.getChild("right_hind_leg");
        this.frontLeftLeg = root.getChild("left_front_leg");
        this.frontRightLeg = root.getChild("right_front_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("left_hind_leg", ModelPartBuilder.create().uv(21, 11).cuboid(-0.65f, 2.0f, 0.0f, 1.0f, 2.0f, 1.0f).uv(23, 0).cuboid(-0.65f, 3.0f, -1.0f, 1.0f, 1.0f, 1.0f).uv(8, 19).cuboid(-0.5f, -0.4f, -1.5f, 1.0f, 3.0f, 3.0f), ModelTransform.pivot(2.9f, 20.0f, 2.0f));
        root.addChild("right_hind_leg", ModelPartBuilder.create().uv(0, 18).cuboid(-0.5f, -0.4f, -1.5f, 1.0f, 3.0f, 3.0f).uv(0, 12).cuboid(-0.35f, 2.0f, 0.0f, 1.0f, 2.0f, 1.0f).uv(4, 24).cuboid(-0.35f, 3.0f, -1.0f, 1.0f, 1.0f, 1.0f), ModelTransform.pivot(-2.9f, 20.0f, 2.0f));
        root.addChild("left_front_leg", ModelPartBuilder.create().uv(20, 22).cuboid(-0.55f, 0.0f, -1.0f, 1.0f, 2.0f, 1.0f).uv(12, 12).cuboid(-1.55f, 2.0f, -2.0f, 3.0f, 0.0f, 2.0f), ModelTransform.pivot(1.8f, 22.0f, -2.0f));
        root.addChild("right_front_leg", ModelPartBuilder.create().uv(22, 5).cuboid(-0.45f, 0.0f, -1.0f, 1.0f, 2.0f, 1.0f).uv(15, 0).cuboid(-1.45f, 2.0f, -2.0f, 3.0f, 0.0f, 2.0f), ModelTransform.pivot(-1.8f, 22.0f, -2.0f));
        root.addChild("tail", ModelPartBuilder.create().uv(16, 22).cuboid(-0.5f, 0.1f, 0.0f, 1.0f, 4.0f, 1.0f), ModelTransform.of(0.0f, 20.5f, 4.0f, 0.9f, 0.0f, 0.0f));
        ModelPartData tail2 = root.addChild("tail2", ModelPartBuilder.create().uv(0, 0).cuboid(-0.5f, 0.0f, -1.0f, 1.0f, 4.0f, 1.0f), ModelTransform.pivot(0.0f, 22.0f, 7.0f));
        tail2.addChild("tail3", ModelPartBuilder.create().uv(21, 2).cuboid(-0.5f, 0.0f, -1.0f, 1.0f, 2.0f, 1.0f), ModelTransform.pivot(0.0f, 4.0f, 0.0f));
        root.addChild("head", ModelPartBuilder.create().uv(14, 14).cuboid(-2.0f, -1.4f, -4.0f, 4.0f, 4.0f, 4.0f).uv(14, 14).cuboid(-0.5f, 0.75f, -4.3f, 1.0f, 1.0f, 1.0f).uv(0, 24).cuboid(-1.25f, -3.25f, -1.0f, 1.0f, 2.0f, 1.0f).uv(5, 18).cuboid(-2.0f, -3.25f, -1.0f, 1.0f, 2.0f, 1.0f).uv(22, 8).cuboid(0.25f, -3.25f, -1.0f, 1.0f, 2.0f, 1.0f).uv(17, 2).cuboid(1.0f, -3.25f, -1.0f, 1.0f, 2.0f, 1.0f), ModelTransform.pivot(0.0f, 19.0f, -4.0f));
        ModelPartData body = root.addChild("body", ModelPartBuilder.create(), ModelTransform.pivot(0.0f, 19.0f, -1.9f));
        body.addChild("body", ModelPartBuilder.create().uv(0, 12).cuboid(-3.25f, 1.9f, -2.1f, 5.0f, 2.0f, 4.0f).uv(0, 0).cuboid(-3.75f, -4.1f, -2.5f, 6.0f, 7.0f, 5.0f), ModelTransform.of(0.75f, 1.0f, 2.0f, 1.5708f, 0.0f, 0.0f));
        return TexturedModelData.of(modelData, 32, 32);
    }

    public void setAngles(float ageInTicks, float partialTicks, DogBrain brain, int activeClickAnimation) {
        this.body.setPivot(0.0f, 19.0f, -1.9f);
        this.head.setPivot(0.0f, 19.0f, -4.0f);
        this.backLeftLeg.setPivot(2.9f, 20.0f, 2.0f);
        this.backRightLeg.setPivot(-2.9f, 20.0f, 2.0f);
        this.frontLeftLeg.setPivot(1.8f, 22.0f, -2.0f);
        this.frontRightLeg.setPivot(-1.8f, 22.0f, -2.0f);
        this.tail.setPivot(0.0f, 20.5f, 4.0f);
        this.tail2.setPivot(0.0f, 22.0f, 7.0f);
        this.body.pitch = 0.0f;
        this.body.yaw = 0.0f;
        this.body.roll = 0.0f;
        this.head.roll = 0.0f;
        this.backLeftLeg.yaw = 0.0f;
        this.backRightLeg.yaw = 0.0f;
        this.frontLeftLeg.yaw = 0.0f;
        this.frontRightLeg.yaw = 0.0f;
        this.tail.pitch = 0.9f;
        this.tail.yaw = 0.0f;
        this.tail.roll = 0.0f;
        this.tail2.yaw = 0.0f;
        this.tail2.roll = 0.0f;
        this.tail2.pitch = 1.7278761f + 0.31415927f * MathHelper.sin((float)(ageInTicks * 0.25f));
        this.head.pitch = brain.getRenderPitch(partialTicks) * ((float)Math.PI / 180);
        this.head.yaw = brain.getRenderYaw(partialTicks) * ((float)Math.PI / 360);
        float limbSwing = brain.getRenderLimbSwing(partialTicks);
        float limbSwingAmount = brain.getRenderLimbSwingAmount(partialTicks);
        this.backLeftLeg.pitch = MathHelper.cos((float)(limbSwing * 0.6662f)) * limbSwingAmount;
        this.backRightLeg.pitch = MathHelper.cos((float)(limbSwing * 0.6662f + 0.3f)) * limbSwingAmount;
        this.frontLeftLeg.pitch = MathHelper.cos((float)(limbSwing * 0.6662f + (float)Math.PI + 0.3f)) * limbSwingAmount;
        this.frontRightLeg.pitch = MathHelper.cos((float)(limbSwing * 0.6662f + (float)Math.PI)) * limbSwingAmount;
        this.applyIdleAnimation(brain.getIdleAnimationTicks(partialTicks));
        this.applyClickAnimation(brain.getHypeAnimationTicks(partialTicks), brain.getHypeAnimationProgress(partialTicks), brain, activeClickAnimation, partialTicks);
    }

    private void applyIdleAnimation(float ticks) {
        if (ticks <= 0.0f) {
            return;
        }
        float blend = MathHelper.clamp((float)(ticks / 18.0f), 0.0f, 1.0f);
        blend = blend * blend * (3.0f - 2.0f * blend);
        float cycle = ticks * 0.13f;
        float breathe = 0.5f + 0.5f * MathHelper.sin((float)cycle);
        float headBob = MathHelper.sin((float)(cycle * 1.7f + 0.4f));
        float tailWave = MathHelper.sin((float)(cycle * 2.4f));
        float pawShift = 0.5f + 0.5f * MathHelper.sin((float)(cycle * 1.35f + 1.3f));
        this.body.pitch += (0.035f + 0.045f * breathe) * blend;
        this.body.roll += 0.018f * headBob * blend;
        this.head.pitch += (0.1f + 0.12f * breathe) * blend;
        this.head.yaw += 0.1f * headBob * blend;
        this.frontLeftLeg.pitch -= (0.12f + 0.08f * pawShift) * blend;
        this.frontRightLeg.pitch -= (0.1f + 0.08f * (1.0f - pawShift)) * blend;
        this.backLeftLeg.pitch += 0.035f * breathe * blend;
        this.backRightLeg.pitch += 0.035f * breathe * blend;
        this.tail.pitch += 0.06f * breathe * blend;
        this.tail2.pitch += 0.22f * tailWave * blend;
        this.tail2.yaw += 0.22f * MathHelper.sin((float)(cycle * 2.4f + 1.4f)) * blend;
    }

    private void applyClickAnimation(float ticks, float progress, DogBrain brain, int activeClickAnimation, float partialTicks) {
        if (activeClickAnimation == 1) {
            this.applyBurnoutAnimation(ticks, progress);
        } else if (activeClickAnimation == 2) {
            this.applyBurnout2Animation(ticks, brain.getBurnout2WarmupProgress(partialTicks), brain.getBurnout2FlipProgress(partialTicks));
        } else {
            this.applyHypeAnimation(ticks, progress);
        }
    }

    private void applyHypeAnimation(float ticks, float progress) {
        if (ticks <= 0.0f || progress <= 0.0f || progress >= 1.0f) {
            return;
        }
        float amount = MathHelper.sin((float)(progress * (float)Math.PI));
        float beat = MathHelper.sin((float)(ticks * 0.72f));
        float fastBeat = MathHelper.sin((float)(ticks * 1.44f));
        float side = MathHelper.sin((float)(ticks * 0.36f));
        this.body.pitch += (0.16f + 0.08f * beat) * amount;
        this.body.roll += 0.18f * side * amount;
        this.head.pitch += (-0.24f + 0.18f * fastBeat) * amount;
        this.head.yaw += 0.24f * side * amount;
        this.head.roll += 0.16f * beat * amount;
        this.frontLeftLeg.pitch = -0.78f * amount + 0.26f * fastBeat * amount;
        this.frontRightLeg.pitch = -0.78f * amount - 0.26f * fastBeat * amount;
        this.frontLeftLeg.yaw += 0.28f * amount;
        this.frontRightLeg.yaw -= 0.28f * amount;
        this.backLeftLeg.pitch += 0.18f * beat * amount;
        this.backRightLeg.pitch -= 0.18f * beat * amount;
        this.tail.pitch += 0.32f * amount;
        this.tail2.pitch += 0.55f * fastBeat * amount;
        this.tail2.yaw += 0.46f * side * amount;
    }

    private void applyBurnoutAnimation(float ticks, float progress) {
        if (ticks <= 0.0f || progress <= 0.0f || progress >= 1.0f) {
            return;
        }
        float amount = MathHelper.sin((float)(progress * (float)Math.PI));
        float launch = MathHelper.clamp((float)(progress / 0.22f), 0.0f, 1.0f);
        launch = launch * launch * (3.0f - 2.0f * launch);
        float spinPose = MathHelper.clamp((float)((progress - 0.1f) / 0.7f), 0.0f, 1.0f);
        spinPose = MathHelper.sin((float)(spinPose * (float)Math.PI));
        float landing = MathHelper.clamp((float)((progress - 0.72f) / 0.28f), 0.0f, 1.0f);
        this.body.pitch += (-0.42f * launch + 0.24f * landing) * amount;
        this.head.pitch += (-0.3f * launch + 0.18f * landing) * amount;
        this.frontLeftLeg.pitch = -1.18f * spinPose * amount;
        this.frontRightLeg.pitch = -1.18f * spinPose * amount;
        this.backLeftLeg.pitch = 0.82f * spinPose * amount;
        this.backRightLeg.pitch = 0.82f * spinPose * amount;
        this.frontLeftLeg.yaw += 0.22f * spinPose * amount;
        this.frontRightLeg.yaw -= 0.22f * spinPose * amount;
        this.tail.pitch += (0.46f * spinPose + 0.18f * landing) * amount;
        this.tail2.pitch += 0.72f * spinPose * amount;
    }

    private void applyBurnout2Animation(float ticks, float warmupProgress, float flipProgress) {
        if (ticks <= 0.0f) {
            return;
        }
        if (warmupProgress > 0.0f && flipProgress <= 0.0f) {
            float warmup = warmupProgress * warmupProgress * (3.0f - 2.0f * warmupProgress);
            float rev = MathHelper.sin((float)(ticks * 1.85f));
            float revFast = MathHelper.sin((float)(ticks * 3.7f));
            this.body.pitch += 0.18f * warmup;
            this.body.roll += 0.035f * revFast * warmup;
            this.head.pitch += 0.16f * warmup;
            this.head.yaw += 0.08f * rev * warmup;
            this.frontLeftLeg.pitch = (-0.42f + 0.1f * rev) * warmup;
            this.frontRightLeg.pitch = (-0.42f - 0.1f * rev) * warmup;
            this.backLeftLeg.pitch = 0.95f * Math.max(0.0f, rev) * warmup;
            this.backRightLeg.pitch = 0.95f * Math.max(0.0f, -rev) * warmup;
            this.backLeftLeg.yaw += -0.18f * warmup;
            this.backRightLeg.yaw += 0.18f * warmup;
            this.tail.pitch += 0.18f * warmup;
            this.tail2.pitch += 0.42f * rev * warmup;
            this.tail2.yaw += 0.3f * revFast * warmup;
            return;
        }
        this.applyBurnoutAnimation(ticks, flipProgress);
    }
}
