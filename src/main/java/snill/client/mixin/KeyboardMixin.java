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
import snill.client.client.modules.impl.render.Browser;

@Mixin(Keyboard.class)
public class KeyboardMixin implements QClient {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    public void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (mc.currentScreen == null) {
            KeyBoardUtils.call(key, action);
            if (Browser.handleKey(key, action)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (mc.currentScreen == null && Browser.handleChar(codePoint)) {
            ci.cancel();
        }
    }

    @Inject(method = "processF3", at = @At("RETURN"))
    private void processF3(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_A && cir.getReturnValue()) {
            new EventChunkReload().call();
        }
    }
}
