package snill.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snill.client.Snill;
import snill.client.api.events.EventInvoker;
import snill.client.api.events.implement.EventGameUpdate;
import snill.client.api.events.implement.EventTickPost;
import snill.client.api.events.implement.EventTickPre;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.baritone.BaritoneAntiStuck;
import snill.client.api.utils.player.Counter;
import snill.client.client.modules.impl.render.Chams;
import snill.client.client.modules.impl.render.ShaderEsp;

import java.lang.reflect.InvocationTargetException;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Unique
    private long lastHookTime = Util.getMeasuringTimeNano();
    @Unique
    private int accumulatedCalls = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        if (EventInvoker.hasListeners(EventTickPre.class)) {
            EventTickPre event = new EventTickPre();
            EventInvoker.invoke(event);
        }
        Counter.updateFPS();
    }

    @Inject(method = "handleInputEvents", at = @At("RETURN"), order = 2000)
    private void snill$afterFiguraInput(CallbackInfo ci) {
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void tickEnd(CallbackInfo ci) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        if (EventInvoker.hasListeners(EventTickPost.class)) {
            EventTickPost event = new EventTickPost();
            EventInvoker.invoke(event);
        }
        BaritoneAntiStuck.tick();
    }

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(boolean tick, CallbackInfo ci) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        if (!EventInvoker.hasListeners(EventGameUpdate.class)) {
            this.lastHookTime = Util.getMeasuringTimeNano();
            this.accumulatedCalls = 0;
            return;
        }

        long now = Util.getMeasuringTimeNano();
        long delta = now - this.lastHookTime;
        this.accumulatedCalls += (int) (delta / 4_166_666L);
        this.lastHookTime += (long) this.accumulatedCalls * 4_166_666L;

        for (this.accumulatedCalls = Math.min(this.accumulatedCalls, 240); this.accumulatedCalls > 0; --this.accumulatedCalls) {
            EventInvoker.invoke(new EventGameUpdate());
        }
    }

    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void snill$hasOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ModuleClass.INSTANCE == null) return;

        ShaderEsp shaderEsp = ModuleClass.INSTANCE.shaderEsp;
        if (shaderEsp != null && shaderEsp.shouldOutline(entity)) {
            cir.setReturnValue(true);
            return;
        }

        Chams chams = ModuleClass.INSTANCE.chams;
        if (chams != null && entity instanceof PlayerEntity player && chams.shouldUseOutlineAssist(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void snill$onStop(CallbackInfo ci) {
        if (Snill.INSTANCE != null) {
            Snill.INSTANCE.closeMinecraft();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void snill$onClose(CallbackInfo ci) {
        if (Snill.INSTANCE != null) {
            Snill.INSTANCE.closeMinecraft();
        }
    }
}
