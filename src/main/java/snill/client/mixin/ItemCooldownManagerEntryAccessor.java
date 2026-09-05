package snill.client.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownManagerEntryAccessor {
    @Accessor("startTick")
    int snill$getStartTick();

    @Accessor("endTick")
    int snill$getEndTick();
}
