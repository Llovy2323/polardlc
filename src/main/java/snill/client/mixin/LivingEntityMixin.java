package snill.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.client.modules.impl.render.SwingAnimations;
import net.minecraft.entity.Entity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentEffectContext;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snill.client.api.events.implement.EventThorns;
import snill.client.Snill;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void onGetHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this != MinecraftClient.getInstance().player) {
            return;
        }

        if (ModuleClass.INSTANCE == null) {
            return;
        }

        SwingAnimations tweaks = ModuleClass.swingAnimations;
        if (tweaks != null && tweaks.isEnable() && tweaks.smoothEnabled.isState()) {
            cir.setReturnValue((int) tweaks.slowAnimationSpeed.get());
        }
    }
}
