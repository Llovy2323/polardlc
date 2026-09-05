package snill.client.mixin;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;
import snill.client.api.QClient;
import snill.client.api.events.implement.EventChunkReload;
import snill.client.api.utils.input.KeyBoardUtils;

@Mixin(Keyboard.class)
public class KeyboardMixin implements QClient {
    @Inject(method = "onKey", at = @At("HEAD"))
    public  void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (mc.currentScreen == null) KeyBoardUtils.call(key, action);
    }

    @Inject(method = "processF3", at = @At("RETURN"))
    private void processF3(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_A && cir.getReturnValue()) {
            new EventChunkReload().call();
        }
    }
}
