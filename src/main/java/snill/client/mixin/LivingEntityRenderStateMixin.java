package snill.client.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import snill.client.client.modules.impl.render.SeeInvisiblesRenderState;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements SeeInvisiblesRenderState {

    @Unique
    private boolean snill$seeInvisiblesTarget;

    @Override
    public boolean snill$isSeeInvisiblesTarget() {
        return snill$seeInvisiblesTarget;
    }

    @Override
    public void snill$setSeeInvisiblesTarget(boolean value) {
        snill$seeInvisiblesTarget = value;
    }
}
