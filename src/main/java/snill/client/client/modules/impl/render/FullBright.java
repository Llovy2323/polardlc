package snill.client.client.modules.impl.render;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;

public class FullBright extends Module {

    public static FullBright INSTANCE = new FullBright();

    public FullBright() {
        super("FullBright", "Всегда светло", ModuleCategory.RENDER);
    }

    @EventLink
    public void onUpdate(final EventUpdate ignored) {
        if (mc.player == null || mc.world == null) return;
        mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 777, 1));
    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.world == null) return;
        mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        super.onDisable();
    }
}
