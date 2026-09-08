package snill.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snill.client.client.ui.mainmenu.MainMenuRenderer;

/** Replaces the vanilla title screen; MainMenuRenderer owns rendering and input. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void snill$openMainMenu(CallbackInfo ci) {
        MinecraftClient.getInstance().setScreen(new MainMenuRenderer());
        ci.cancel();
    }
}
