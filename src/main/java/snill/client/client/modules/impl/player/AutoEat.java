package snill.client.client.modules.impl.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class AutoEat extends Module {
    public static AutoEat INSTANCE = new AutoEat();

    public final FloatSetting health = new FloatSetting("Порог HP", 14.0f, 1.0f, 20.0f, 0.5f);
    public final FloatSetting hunger = new FloatSetting("Порог голода", 16.0f, 1.0f, 20.0f, 1.0f);
    public final BooleanSetting preferGapples = new BooleanSetting("Приоритет яблок", true);

    private boolean isEating = false;
    private int previousSlot = -1;
    private boolean usedOffhand = false;

    public AutoEat() {
        super("AutoEat", "Автоматически кушает еду или золотые яблоки при низком здоровье или голоде", ModuleCategory.PLAYER);
        addSettings(health, hunger, preferGapples);
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        boolean lowHp = mc.player.getHealth() <= health.get();
        boolean lowHunger = mc.player.getHungerManager().getFoodLevel() <= (int) hunger.get();

        if (!lowHp && !lowHunger) {
            stopEating();
            return;
        }

        if (isEating) {
            if (mc.player.isUsingItem()) {
                mc.options.useKey.setPressed(true);
            } else {
                stopEating();
            }
            return;
        }

        // Check offhand first
        ItemStack offhand = mc.player.getOffHandStack();
        if (isEdible(offhand, lowHp)) {
            mc.options.useKey.setPressed(true);
            isEating = true;
            usedOffhand = true;
            return;
        }

        // Find best food in hotbar
        int foodSlot = findFoodSlot(lowHp);
        if (foodSlot != -1) {
            previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = foodSlot;
            mc.options.useKey.setPressed(true);
            isEating = true;
            usedOffhand = false;
        }
    }

    private void stopEating() {
        if (isEating) {
            mc.options.useKey.setPressed(false);
            if (!usedOffhand && previousSlot != -1 && mc.player != null) {
                mc.player.getInventory().selectedSlot = previousSlot;
            }
            isEating = false;
            previousSlot = -1;
            usedOffhand = false;
        }
    }

    private boolean isEdible(ItemStack stack, boolean lowHp) {
        if (stack == null || stack.isEmpty()) return false;
        if (lowHp) {
            return stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE || stack.getItem() == Items.GOLDEN_APPLE;
        }
        return stack.contains(DataComponentTypes.FOOD);
    }

    private int findFoodSlot(boolean lowHp) {
        if (mc.player == null) return -1;

        if (lowHp && preferGapples.isState()) {
            int gappleSlot = findItemInHotbar(Items.ENCHANTED_GOLDEN_APPLE);
            if (gappleSlot != -1) return gappleSlot;
            gappleSlot = findItemInHotbar(Items.GOLDEN_APPLE);
            if (gappleSlot != -1) return gappleSlot;
        }

        int bestFoodSlot = -1;
        int bestNutrition = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.contains(DataComponentTypes.FOOD)) {
                var foodComp = stack.get(DataComponentTypes.FOOD);
                int nutrition = foodComp != null ? foodComp.nutrition() : 0;
                if (nutrition > bestNutrition) {
                    bestNutrition = nutrition;
                    bestFoodSlot = i;
                }
            }
        }

        return bestFoodSlot;
    }

    private int findItemInHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        stopEating();
        super.onDisable();
    }
}
