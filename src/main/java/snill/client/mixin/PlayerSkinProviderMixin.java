package snill.client.mixin;

import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(PlayerSkinProvider.class)
public abstract class PlayerSkinProviderMixin {

    @Inject(
            method = "fetchSkinTextures(Ljava/util/UUID;Lcom/mojang/authlib/minecraft/MinecraftProfileTextures;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void snill$fallbackWhenTextureDownloadFails(
            UUID profileId,
            MinecraftProfileTextures textures,
            CallbackInfoReturnable<CompletableFuture<SkinTextures>> cir
    ) {
        CompletableFuture<SkinTextures> download = cir.getReturnValue();
        cir.setReturnValue(download.exceptionally(error -> {
            if (snill$isIoFailure(error)) {
                return DefaultSkinHelper.getSkinTextures(profileId);
            }

            if (error instanceof CompletionException completionException) {
                throw completionException;
            }
            throw new CompletionException(error);
        }));
    }

    private static boolean snill$isIoFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
