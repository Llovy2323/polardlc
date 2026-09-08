package snill.client.client.modules.impl.movement;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventSlowWalking;
import snill.client.api.events.implement.EventUpdatePost;
import snill.client.api.utils.input.MovingUtil;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class NoSlow extends Module {
    public static NoSlow INSTANCE = new NoSlow();

    public final ModeSetting mode = new ModeSetting("Режим", "Grim", "Grim", "Vanilla");
    public final BooleanSetting food = new BooleanSetting("Еда и зелья", true);
    public final BooleanSetting bow = new BooleanSetting("Лук", true);
    public final BooleanSetting shield = new BooleanSetting("Щит", true);

    public NoSlow() {
        super("NoSlow", "Убирает замедление при использовании предметов (еда, зелья, лук, щит)", ModuleCategory.MOVEMENT);
        addSettings(mode, food, bow, shield);
    }

    @EventLink
    public void onSlow(final EventSlowWalking event) {
        if (mc.player == null || !mc.player.isUsingItem()) return;

        if (isApplicableItem(mc.player.getActiveItem())) {
            event.cancel();
        }
    }

    @EventLink
    public void onUpdatePost(final EventUpdatePost event) {
        if (mc.player == null || !mode.is("Grim") || !mc.player.isUsingItem()) return;
        if (!MovingUtil.hasPlayerMovement()) return;

        if (isApplicableItem(mc.player.getActiveItem())) {
            int currentSlot = mc.player.getInventory().selectedSlot;
            int fakeSlot = (currentSlot + 1) % 9;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(fakeSlot));
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
        }
    }

    private boolean isApplicableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();

        boolean isFoodOrPotion = stack.contains(DataComponentTypes.FOOD)
                || stack.contains(DataComponentTypes.POTION_CONTENTS)
                || item == Items.HONEY_BOTTLE
                || item == Items.MILK_BUCKET;

        boolean isBow = item == Items.BOW || item == Items.CROSSBOW;
        boolean isShield = item == Items.SHIELD;

        return (isFoodOrPotion && food.isState())
                || (isBow && bow.isState())
                || (isShield && shield.isState());
    }
}
