package snill.client.mixin;

import net.minecraft.client.render.item.model.SelectItemModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import snill.client.client.modules.impl.render.ItemReplacer;

@Mixin(SelectItemModel.class)
public abstract class SelectItemModelMixin {

    @ModifyVariable(
            method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/item/ItemModelManager;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack snill$replaceSelectedItemModel(ItemStack stack) {
        return ItemReplacer.INSTANCE.getRenderStack(stack);
    }
}
