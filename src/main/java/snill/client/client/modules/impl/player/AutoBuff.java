package snill.client.client.modules.impl.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class AutoBuff extends Module {
    public static AutoBuff INSTANCE = new AutoBuff();

    public final BooleanSetting speed = new BooleanSetting("Скорость", true);
    public final BooleanSetting strength = new BooleanSetting("Сила", true);
    public final BooleanSetting fireRes = new BooleanSetting("Огнестойкость", true);
    public final BooleanSetting regeneration = new BooleanSetting("Регенерация", true);
    public final FloatSetting delay = new FloatSetting("Задержка (мс)", 500.0f, 200.0f, 1500.0f, 50.0f);
    public final BooleanSetting onlyGround = new BooleanSetting("Только на земле", true);

    private long lastThrowTime = 0;

    public AutoBuff() {
        super("AutoBuff", "Автоматически кидает взрывные зелья баффов", ModuleCategory.PLAYER);
        addSettings(speed, strength, fireRes, regeneration, delay, onlyGround);
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (onlyGround.isState() && !mc.player.isOnGround()) return;
        if (mc.player.isUsingItem()) return;
        if (System.currentTimeMillis() - lastThrowTime < (long) delay.get()) return;

        if (speed.isState() && needsEffect(StatusEffects.SPEED)) {
            int slot = findPotionSlot(StatusEffects.SPEED);
            if (slot != -1) {
                throwPotion(slot);
                return;
            }
        }

        if (strength.isState() && needsEffect(StatusEffects.STRENGTH)) {
            int slot = findPotionSlot(StatusEffects.STRENGTH);
            if (slot != -1) {
                throwPotion(slot);
                return;
            }
        }

        if (fireRes.isState() && needsEffect(StatusEffects.FIRE_RESISTANCE)) {
            int slot = findPotionSlot(StatusEffects.FIRE_RESISTANCE);
            if (slot != -1) {
                throwPotion(slot);
                return;
            }
        }

        if (regeneration.isState() && needsEffect(StatusEffects.REGENERATION)) {
            int slot = findPotionSlot(StatusEffects.REGENERATION);
            if (slot != -1) {
                throwPotion(slot);
            }
        }
    }

    private boolean needsEffect(RegistryEntry<StatusEffect> effect) {
        if (mc.player == null) return false;
        StatusEffectInstance instance = mc.player.getStatusEffect(effect);
        return instance == null || instance.getDuration() <= 20;
    }

    private int findPotionSlot(RegistryEntry<StatusEffect> targetEffect) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.SPLASH_POTION) {
                PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null && hasEffect(contents, targetEffect)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean hasEffect(PotionContentsComponent contents, RegistryEntry<StatusEffect> targetEffect) {
        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType().equals(targetEffect)) {
                return true;
            }
        }
        return false;
    }

    private void throwPotion(int slot) {
        if (mc.player == null || mc.interactionManager == null || mc.player.networkHandler == null) return;
        int oldSlot = mc.player.getInventory().selectedSlot;

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(), 90.0f, mc.player.isOnGround(), false));

        if (slot != oldSlot) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.player.getInventory().selectedSlot = slot;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        if (slot != oldSlot) {
            mc.player.getInventory().selectedSlot = oldSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));

        lastThrowTime = System.currentTimeMillis();
    }
}
