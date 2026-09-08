package snill.client.client.modules.impl.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class ChestStealer extends Module {
    public static ChestStealer INSTANCE = new ChestStealer();

    public final FloatSetting delay = new FloatSetting("Задержка (мс)", 80.0f, 10.0f, 300.0f, 5.0f);
    public final BooleanSetting autoClose = new BooleanSetting("Авто закрытие", true);
    public final BooleanSetting onlyValuable = new BooleanSetting("Только ценное", false);

    private long lastTakeTime = 0;

    public ChestStealer() {
        super("ChestStealer", "Автоматически забирает предметы из открытых сундуков", ModuleCategory.PLAYER);
        addSettings(delay, autoClose, onlyValuable);
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container) {
            int containerSlots = container.getRows() * 9;
            processContainer(container.syncId, containerSlots);
        } else if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler shulker) {
            processContainer(shulker.syncId, 27);
        }
    }

    private void processContainer(int syncId, int containerSlots) {
        boolean hasItemsLeft = false;

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (onlyValuable.isState() && !isValuable(stack)) {
                continue;
            }

            hasItemsLeft = true;

            if (System.currentTimeMillis() - lastTakeTime >= (long) delay.get()) {
                mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                lastTakeTime = System.currentTimeMillis();
                return;
            } else {
                return;
            }
        }

        if (!hasItemsLeft && autoClose.isState()) {
            mc.player.closeHandledScreen();
        }
    }

    private boolean isValuable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();

        if (item == Items.TOTEM_OF_UNDYING
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.END_CRYSTAL
                || item == Items.RESPAWN_ANCHOR
                || item == Items.GLOWSTONE
                || item == Items.OBSIDIAN
                || item == Items.ENDER_PEARL
                || item == Items.CHORUS_FRUIT
                || item == Items.ELYTRA
                || item == Items.MACE
                || item == Items.WIND_CHARGE
                || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_SCRAP
                || item == Items.ANCIENT_DEBRIS
                || item == Items.DIAMOND
                || item == Items.DIAMOND_BLOCK
                || item == Items.NETHERITE_BLOCK
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.ARROW
                || item == Items.SPECTRAL_ARROW
                || item == Items.TIPPED_ARROW) {
            return true;
        }

        if (item instanceof ArmorItem) return true;
        if (stack.contains(DataComponentTypes.FOOD)) return true;
        if (stack.contains(DataComponentTypes.POTION_CONTENTS)) return true;
        if (stack.contains(DataComponentTypes.CONTAINER)) return true; // Shulkers etc

        return false;
    }
}
