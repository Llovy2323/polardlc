package snill.client.mixin;

import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.client.modules.impl.misc.NameProtect;

@Mixin(TextVisitFactory.class)
public class TextVisitFactoryMixin {

    @ModifyArg(
            method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/TextVisitFactory;visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
                    ordinal = 0
            ),
            index = 0
    )
    private static String snill$patchVisitedText(String text) {
        if (ModuleClass.INSTANCE == null) {
            return text;
        }

        NameProtect nameProtect = ModuleClass.INSTANCE.nameProtect;
        if (nameProtect == null || !nameProtect.isEnable()) {
            return text;
        }

        return nameProtect.patchIncomingText(text);
    }
}
