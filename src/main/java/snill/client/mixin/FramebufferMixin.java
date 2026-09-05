package snill.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snill.client.api.utils.render.hands.ShaderHandsRenderer;

/** Keeps RenderLayer.MAIN_TARGET inside the Hands capture framebuffer. */
@Mixin(Framebuffer.class)
public abstract class FramebufferMixin {

    @Inject(method = "beginWrite", at = @At("HEAD"), cancellable = true)
    private void snill$redirectHandsMainTarget(boolean setViewport, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this == client.getFramebuffer()
                && ShaderHandsRenderer.getInstance().redirectMainWrite(setViewport)) {
            ci.cancel();
        }
    }
}
