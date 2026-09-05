package snill.client.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snill.client.api.QClient;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.client.modules.impl.render.Chams;
import snill.client.client.modules.impl.render.SeeInvisibles;
import snill.client.client.modules.impl.render.SeeInvisiblesRenderState;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> implements QClient {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void snill$updateSeeInvisiblesState(T entity, S state, float tickDelta, CallbackInfo ci) {
        boolean shouldRenderInvisible = snill$shouldRenderInvisible(entity);
        ((SeeInvisiblesRenderState) state).snill$setSeeInvisiblesTarget(shouldRenderInvisible);
        if (shouldRenderInvisible) {
            state.invisible = true;
            state.invisibleToPlayer = false;
        }
    }

    @ModifyConstant(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            constant = @Constant(intValue = 654311423)
    )
    private int snill$changeInvisibleAlpha(int original, S state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        return ((SeeInvisiblesRenderState) state).snill$isSeeInvisiblesTarget()
                ? SeeInvisibles.INVISIBLE_COLOR
                : original;
    }

    @Inject(
            method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snill$hideOriginalModel(S state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        Chams chams = ModuleClass.INSTANCE != null ? ModuleClass.chams : null;
        if (chams == null || !chams.isEnable()) {
            return;
        }

        PlayerEntity player = snill$resolvePlayer(state);
        if (player != null && chams.shouldHideBaseModel(player)) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private boolean snill$shouldRenderInvisible(T entity) {
        if (!(entity instanceof PlayerEntity player) || ModuleClass.INSTANCE == null) {
            return false;
        }

        SeeInvisibles seeInvisibles = ModuleClass.seeInvisibles;
        return seeInvisibles != null && seeInvisibles.shouldRenderInvisible(player);
    }

    @Unique
    private PlayerEntity snill$resolvePlayer(S state) {
        if (!(state instanceof PlayerEntityRenderState playerState) || mc.world == null) {
            return null;
        }

        Entity entity = mc.world.getEntityById(playerState.id);
        return entity instanceof PlayerEntity player ? player : null;
    }
}
