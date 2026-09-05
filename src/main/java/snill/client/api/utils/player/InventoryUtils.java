package snill.client.api.utils.player;

import lombok.experimental.UtilityClass;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import snill.client.api.QClient;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;

@UtilityClass
public class InventoryUtils implements QClient {

    public int getItemSlot(Item input) {
        for (ItemStack stack : mc.player.getArmorItems()) {
            if (stack.getItem() == input) {
                return -2;
            }
        }
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == input) {
                slot = i;
                break;
            }
        }
        if (slot < 9 && slot != -1) {
            slot += 36;
        }
        return slot;
    }

    public int getEnchantmentLevel(ItemStack stack, RegistryKey<Enchantment> enchantmentKey) {
        ItemEnchantmentsComponent enchantments = stack.getOrDefault(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantmentsComponent.DEFAULT
        );

        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            if (enchantment.matchesKey(enchantmentKey)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    public int findBestElytraSlot() {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        double bestScore = -1.0;

        for (int slot = 0; slot < 36; ++slot) {
            ItemStack stack = mc.player.getInventory().getStack(slot);

            if (stack.getItem() != Items.ELYTRA) continue;

            int protection = getEnchantmentLevel(stack, Enchantments.PROTECTION);
            int unbreaking = getEnchantmentLevel(stack, Enchantments.UNBREAKING);
            int mending = getEnchantmentLevel(stack, Enchantments.MENDING);

            int maxDurability = stack.getMaxDamage();
            int currentDamage = stack.getDamage();
            double durabilityRatio = (double) (maxDurability - currentDamage) / (double) maxDurability;

            double score = (double) (protection * 100 + unbreaking * 10 + (mending > 0 ? 1 : 0))
                    + durabilityRatio * 10.0;

            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public int findBestChestplateSlot() {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        double bestScore = -1.0;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof ArmorItem armor)) continue;

            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null || equippable.slot() != EquipmentSlot.CHEST) continue;

            int protection = getEnchantmentLevel(stack, Enchantments.PROTECTION);
            int unbreaking = getEnchantmentLevel(stack, Enchantments.UNBREAKING);
            int mending = getEnchantmentLevel(stack, Enchantments.MENDING);
            int priority = getChestplatePriority(armor);

            int maxDamage = stack.getMaxDamage();
            int damage = stack.getDamage();
            double durabilityRatio = maxDamage == 0 ? 1.0 : (maxDamage - damage) / (double) maxDamage;

            double score = priority * 10000.0
                    + protection * 100.0
                    + unbreaking * 10.0
                    + (mending > 0 ? 1 : 0)
                    + durabilityRatio * 10.0;

            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public int getChestplatePriority(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 5;
        if (item == Items.DIAMOND_CHESTPLATE) return 4;
        if (item == Items.IRON_CHESTPLATE) return 3;
        if (item == Items.GOLDEN_CHESTPLATE) return 2;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 2;
        if (item == Items.LEATHER_CHESTPLATE) return 1;
        return 0;
    }

    public static int find(Item item, int start, int end) {
        if (mc.player != null) {
            for (int i = end; i >= start; --i) {
                if (mc.player.currentScreenHandler.syncId != 0 && mc.player.currentScreenHandler.getSlot(i).getStack().getItem() == item) {
                    return i;
                }

                if (mc.player.currentScreenHandler.syncId == 0 && mc.player.getInventory().getStack(i).getItem() == item) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static boolean hasItem(Item item) {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static int getSlot(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    public static int toContainerSlot(int slot) {
        return slot < 9 ? slot + 36 : slot;
    }

    public static void moveItem(int from, int to) {
        if (mc.player == null || mc.interactionManager == null || from == to) return;
        mc.interactionManager.clickSlot(0, from, to, SlotActionType.SWAP, mc.player);
    }

    public static void inventorySwapClick(Item item) {
        inventorySwapClick(item, false);
    }

    public static void inventorySwapClick(Item item, boolean useOffhand) {
        if (mc.player == null || mc.interactionManager == null) return;
        int slot = getSlot(item);
        if (slot == -1) return;

        if (useOffhand) {
            useItemFromOffhandSlot(slot);
            return;
        }

        int currentSlot = mc.player.getInventory().selectedSlot;
        boolean isInHotbar = slot < 9;

        if (isInHotbar && slot == currentSlot) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        if (isInHotbar) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
            return;
        }

        int containerSlot = toContainerSlot(slot);
        int nearbyHotbarSlot = getNearbyHotbarSlot(currentSlot);
        mc.interactionManager.clickSlot(0, containerSlot, nearbyHotbarSlot, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        try {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(nearbyHotbarSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
        } finally {
            mc.interactionManager.clickSlot(0, containerSlot, nearbyHotbarSlot, SlotActionType.SWAP, mc.player);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        }
    }

    public static boolean antipoletrwfix(Item item) {
        if (mc.player == null || mc.interactionManager == null) return false;

        int slot = getSlot(item);
        if (slot == -1) return false;

        int containerSlot = toContainerSlot(slot);
        boolean sneaking = mc.player.isSneaking();
        boolean sprinting = mc.player.isSprinting();

        if (sprinting) {
            mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            mc.player.setSprinting(false);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            if (!ModuleClass.INSTANCE.sprint.isEnable()) {
                mc.options.sprintKey.setPressed(false);
            }
        }

        mc.interactionManager.clickSlot(0, containerSlot, 40, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));

        try {
            mc.options.sneakKey.setPressed(true);
            mc.player.setSneaking(true);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, true, false, false)));

            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } finally {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            mc.options.sneakKey.setPressed(sneaking);
            mc.player.setSneaking(sneaking);

            mc.interactionManager.clickSlot(0, containerSlot, 40, SlotActionType.SWAP, mc.player);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));

            if (sprinting) {
                mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
            }
        }

        return true;
    }

    public static void swapAndUseHvH(Item item) {
        int slot = find(item, 9, 45);
        int slotHotbar = find(item, 0, 8);
        int previousSlot = mc.player.getInventory().selectedSlot;

        boolean isUsingItem = mc.player.isUsingItem();

        if (mc.player.getMainHandStack().getItem() == item) {
            if (!isUsingItem) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            return;
        }

        if (mc.player.getOffHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        if (isUsingItem) {
            if (slotHotbar != -1) {
                mc.interactionManager.clickSlot(0, 36 + slotHotbar, 40, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.interactionManager.clickSlot(0, 36 + slotHotbar, 40, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
            } else if (slot != -1) {
                mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
            }
            return;
        }

        if (slotHotbar != -1) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slotHotbar));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            return;
        }

        if (slot != -1) {
            int slotCorrectable = -1;

            for (int slotNone = 0; slotNone < 8; ++slotNone) {
                ItemStack stack = mc.player.getInventory().getStack(slotNone);
                if (stack.isEmpty()) {
                    slotCorrectable = slotNone;
                    break;
                }

                UseAction action = stack.getUseAction();
                if (action == UseAction.NONE) {
                    slotCorrectable = slotNone;
                }
            }

            boolean wasSprinting = false;

            if (mc.player.isSprinting()) {
                mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                mc.player.setSprinting(false);
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                if (!ModuleClass.INSTANCE.sprint.isEnable()) {
                    mc.options.sprintKey.setPressed(false);
                }
                wasSprinting = true;
            }

            if (slotCorrectable == -1) {
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(8));
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
            } else {
                mc.interactionManager.clickSlot(0, slot, slotCorrectable, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slotCorrectable));
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                mc.interactionManager.clickSlot(0, slot, slotCorrectable, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
            }

            if (wasSprinting) {
                mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
            }
        }
    }

    private static void useItemFromOffhandSlot(int slot) {
        if (mc.player == null || mc.interactionManager == null || slot < 0) return;
        int containerSlot = toContainerSlot(slot);
        mc.interactionManager.clickSlot(0, containerSlot, 40, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        try {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } finally {
            mc.interactionManager.clickSlot(0, containerSlot, 40, SlotActionType.SWAP, mc.player);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        }
    }

    private static int getNearbyHotbarSlot(int currentSlot) {
        if (currentSlot <= 0) return 1;
        if (currentSlot >= 8) return 7;
        return currentSlot + 1;
    }
}