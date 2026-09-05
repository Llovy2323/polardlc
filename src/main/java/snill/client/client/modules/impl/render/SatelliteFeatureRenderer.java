package snill.client.client.modules.impl.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.AllayEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.AllayEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;

public class SatelliteFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier ALLAY_TEXTURE = Identifier.ofVanilla("textures/entity/allay/allay.png");

    private final AllayEntityModel model;
    private final AllayEntityRenderState allayState = new AllayEntityRenderState();

    public SatelliteFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context,
            EntityRendererFactory.Context rendererContext
    ) {
        super(context);
        this.model = new AllayEntityModel(rendererContext.getPart(EntityModelLayers.ALLAY));
    }

    @Override
    public void render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityRenderState playerState,
            float yawDegrees,
            float pitch
    ) {
        matrices.push();

        float baseY = playerState.isInSneakingPose ? -1.3f : -1.5f;

        float idleBob = 0.0f;
        float idleYaw = 0.0f;
        float idleRoll = 0.0f;
        float idlePitch = 0.0f;
        float animationAge = playerState.age;

        allayState.age = animationAge;
        allayState.limbFrequency = playerState.limbFrequency;
        allayState.limbAmplitudeMultiplier = playerState.limbAmplitudeMultiplier;
        allayState.yawDegrees = yawDegrees;
        allayState.pitch = pitch;
        allayState.invisible = playerState.invisible;
        allayState.invisibleToPlayer = playerState.invisibleToPlayer;
        allayState.hasOutline = playerState.hasOutline;
        allayState.shaking = playerState.shaking;
        allayState.baby = false;
        allayState.touchingWater = playerState.touchingWater;
        allayState.bodyYaw = playerState.bodyYaw;
        allayState.baseScale = 1.0f;
        allayState.ageScale = 1.0f;
        allayState.pose = playerState.pose;
        allayState.deathTime = 0.0f;
        allayState.hurt = playerState.hurt;
        allayState.dancing = false;
        allayState.spinning = false;
        allayState.spinningAnimationTicks = 0.0f;
        allayState.itemHoldAnimationTicks = 0.0f;

        model.setAngles(allayState);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(model.getLayer(ALLAY_TEXTURE));
        model.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
    }
}
