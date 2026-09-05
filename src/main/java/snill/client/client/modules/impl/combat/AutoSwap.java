package snill.client.client.modules.impl.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventBinding;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.movement.Sprint;
import snill.client.client.modules.settings.implement.BindSetting;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AutoSwap extends Module {

    public static AutoSwap INSTANCE = new AutoSwap();

    private static final String MODE_GRIM = "Grim";
    private static final String MODE_RW = "RW";
    private static final String MODE_HEAD = "\u0413\u043e\u043b\u043e\u0432\u0430";
    private static final String ITEM_HELMET = "\u0428\u043b\u0435\u043c";

    private final ModeSetting swapType = new ModeSetting("\u0422\u0438\u043f \u0441\u0432\u0430\u043f\u0430", MODE_GRIM, MODE_GRIM, MODE_RW, MODE_HEAD);
    private final ModeSetting firstItem = new ModeSetting("\u041f\u0435\u0440\u0432\u044b\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442", "\u0420\u0443\u043d\u0430", "\u0420\u0443\u043d\u0430", "\u0422\u043e\u0442\u0435\u043c", "\u0428\u0430\u0440", "\u0413\u0435\u043f\u043b", "\u0429\u0438\u0442")
            .visible(() -> !swapType.is(MODE_HEAD));
    private final ModeSetting secondItem = new ModeSetting("\u0412\u0442\u043e\u0440\u043e\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442", "\u0422\u043e\u0442\u0435\u043c", "\u0420\u0443\u043d\u0430", "\u0422\u043e\u0442\u0435\u043c", "\u0428\u0430\u0440", "\u0413\u0435\u043f\u043b", "\u0429\u0438\u0442")
            .visible(() -> !swapType.is(MODE_HEAD));
    private final ModeSetting headFirstItem = new ModeSetting("\u041f\u0435\u0440\u0432\u044b\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442", "\u0428\u0430\u0440", "\u0428\u0430\u0440", ITEM_HELMET)
            .visible(() -> swapType.is(MODE_HEAD));
    private final ModeSetting headSecondItem = new ModeSetting("\u0412\u0442\u043e\u0440\u043e\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442", "\u0428\u0430\u0440", "\u0428\u0430\u0440", ITEM_HELMET)
            .visible(() -> swapType.is(MODE_HEAD));
    private final BindSetting swapKey = new BindSetting("\u041a\u043d\u043e\u043f\u043a\u0430 \u0441\u0432\u0430\u043f\u0430", -98);
    private final BooleanSetting bypassgrim = new BooleanSetting("\u041e\u0431\u0445\u043e\u0434\u0438\u0442\u044c Grim", true);

    private int bypassTicks;
    private boolean sprintPaused;
    private int swapCooldown;
    private int targetSlot = -1;
    private boolean needSwap = false;

    private enum RwPhase {
        IDLE, STOP_MOVE, PICKUP_SRC, PUT_OFFHAND, CLOSE_INV, DONE
    }

    private RwPhase rwPhase = RwPhase.IDLE;
    private int rwTick = 0;
    private int rwSrcSlot = -1;

    private static final int RW_STOP_TICKS = 2;

    public AutoSwap() {
        super("AutoSwap", "\u0411\u044b\u0441\u0442\u0440\u0430\u044f \u0441\u043c\u0435\u043d\u0430 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432 \u0432 \u043e\u0444\u0444-\u0445\u0435\u043d\u0434\u0435", ModuleCategory.COMBAT);
        addSettings(swapType, firstItem, secondItem, headFirstItem, headSecondItem, swapKey, bypassgrim);
    }

    @Override
    public void onEnable() {
        needSwap = false;
        targetSlot = -1;
        bypassTicks = 0;
        swapCooldown = 0;
        rwPhase = RwPhase.IDLE;
        rwTick = 0;
        rwSrcSlot = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        bypassTicks = 0;
        swapCooldown = 0;
        needSwap = false;
        targetSlot = -1;
        rwPhase = RwPhase.IDLE;
        rwSrcSlot = -1;
        rwTick = 0;
        restoreSprint();
        super.onDisable();
    }

    @EventLink
    public void onBinding(final EventBinding event) {
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null) return;

        if (event.getKey() == swapKey.getKey()) {
            if (swapCooldown == 0 && rwPhase == RwPhase.IDLE) {
                needSwap = true;
            }
        }
    }

    @EventLink
    public void onInput(final EventMoveInput e) {
        if (mc.player == null) return;

        boolean block = false;

        if (swapType.getCurrent().equals(MODE_GRIM) && bypassgrim.isState() && bypassTicks > 0) {
            block = true;
        }

        if (swapType.getCurrent().equals(MODE_RW) && rwPhase != RwPhase.IDLE && rwPhase != RwPhase.DONE) {
            block = true;
        }

        if (block) {
            mc.player.setSprinting(false);
            e.setForward(0);
            e.setStrafe(0);
            e.setJump(false);
            e.setSneak(false);
        }
    }

    @EventLink
    public void onUpdate(final EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (swapCooldown > 0) swapCooldown--;

        switch (swapType.getCurrent()) {
            case MODE_GRIM -> tickGrimMode();
            case MODE_HEAD -> tickHeadMode();
            default -> tickRwMode();
        }
    }

    private void tickGrimMode() {
        if (bypassgrim.isState() && bypassTicks > 0) {
            mc.player.setSprinting(false);
            bypassTicks--;

            if (bypassTicks <= 0) {
                performSwapGrim();
                restoreSprint();
            }
            return;
        }

        if (needSwap && targetSlot == -1) {
            needSwap = false;
            int slot = resolveTargetSlot();
            if (slot == -1) return;

            targetSlot = slot;

            if (bypassgrim.isState()) {
                disableSprint();
                bypassTicks = 2;
                swapCooldown = 2;
            } else {
                performSwapGrim();
                swapCooldown = 2;
            }
        }
    }

    private void performSwapGrim() {
        if (targetSlot == -1) return;
        doSwapOffhand(targetSlot);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        targetSlot = -1;
    }

    private void tickHeadMode() {
        if (!needSwap || swapCooldown != 0) return;

        needSwap = false;
        int slot = resolveHeadTargetSlot();
        if (slot == -1) return;

        moveToHeadSlot(slot);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        swapCooldown = 2;
    }

    private int resolveHeadTargetSlot() {
        String first = headFirstItem.getCurrent();
        String second = headSecondItem.getCurrent();

        int firstSlot = findTargetSlot(first);
        int secondSlot = findTargetSlot(second);

        if (firstSlot == -1 && secondSlot == -1) return -1;

        ItemStack headStack = mc.player.getEquippedStack(EquipmentSlot.HEAD);
        if (matchesConfiguredItem(headStack, first) && secondSlot != -1) {
            return secondSlot;
        } else if (firstSlot != -1) {
            return firstSlot;
        } else {
            return secondSlot;
        }
    }

    private void doSwapOffhand(int slot) {
        if (slot >= 36 && slot <= 44) {
            int hotbarSlot = slot - 36;
            mc.interactionManager.clickSlot(0, 45, hotbarSlot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }
    }

    private void moveToHeadSlot(int slot) {
        if (slot >= 36 && slot <= 44) {
            int hotbarSlot = slot - 36;
            mc.interactionManager.clickSlot(0, 5, hotbarSlot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 5, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }
    }

    private void tickRwMode() {
        switch (rwPhase) {
            case IDLE -> {
                if (needSwap && swapCooldown == 0) {
                    needSwap = false;
                    rwSrcSlot = resolveTargetSlot();
                    if (rwSrcSlot == -1) return;

                    disableSprint();
                    rwPhase = RwPhase.STOP_MOVE;
                    rwTick = 0;
                }
            }

            case STOP_MOVE -> {
                mc.player.setSprinting(false);
                rwTick++;
                if (rwTick >= RW_STOP_TICKS) {
                    rwPhase = RwPhase.PICKUP_SRC;
                    rwTick = 0;
                }
            }

            case PICKUP_SRC -> {
                mc.interactionManager.clickSlot(0, rwSrcSlot, 0, SlotActionType.PICKUP, mc.player);
                rwPhase = RwPhase.PUT_OFFHAND;
                rwTick = 0;
            }

            case PUT_OFFHAND -> {
                mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.PICKUP, mc.player);

                ItemStack cursor = mc.player.playerScreenHandler.getCursorStack();
                if (!cursor.isEmpty()) {
                    mc.interactionManager.clickSlot(0, rwSrcSlot, 0, SlotActionType.PICKUP, mc.player);
                }

                rwPhase = RwPhase.CLOSE_INV;
                rwTick = 0;
            }

            case CLOSE_INV -> {
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                swapCooldown = 3;
                rwPhase = RwPhase.DONE;
                rwTick = 0;
            }

            case DONE -> {
                restoreSprint();
                rwPhase = RwPhase.IDLE;
                rwSrcSlot = -1;
                rwTick = 0;
            }
        }
    }

    private int resolveTargetSlot() {
        String first = firstItem.getCurrent();
        String second = secondItem.getCurrent();

        int firstSlot = findTargetSlot(first);
        int secondSlot = findTargetSlot(second);

        if (firstSlot == -1 && secondSlot == -1) return -1;

        if (matchesConfiguredItem(mc.player.getOffHandStack(), first) && secondSlot != -1) {
            return secondSlot;
        } else if (firstSlot != -1) {
            return firstSlot;
        } else {
            return secondSlot;
        }
    }

    private boolean matchesConfiguredItem(ItemStack stack, String itemName) {
        if (ITEM_HELMET.equals(itemName)) {
            return isHelmet(stack);
        }
        return stack.getItem() == getItem(itemName);
    }

    private int findItemSlot(Item item) {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findTargetSlot(String itemName) {
        if (ITEM_HELMET.equals(itemName)) {
            return findHelmetSlot();
        }
        return findItemSlot(getItem(itemName));
    }

    private int findHelmetSlot() {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (isHelmet(stack)) return i;
        }
        return -1;
    }

    private boolean isHelmet(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ArmorItem)) return false;

        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            return equippable.slot() == EquipmentSlot.HEAD;
        }

        return stack.getItem().toString().toLowerCase().contains("helmet");
    }

    private Item getItem(String name) {
        return switch (name) {
            case "\u0428\u0430\u0440" -> Items.PLAYER_HEAD;
            case "\u0420\u0443\u043d\u0430" -> Items.FIREWORK_STAR;
            case "\u0422\u043e\u0442\u0435\u043c" -> Items.TOTEM_OF_UNDYING;
            case "\u0413\u0435\u043f\u043b" -> Items.GOLDEN_APPLE;
            case "\u0429\u0438\u0442" -> Items.SHIELD;
            default -> Items.AIR;
        };
    }

    private void disableSprint() {
        if (sprintPaused) return;
        Sprint.pushPause(1000);
        sprintPaused = true;
    }

    private void restoreSprint() {
        if (!sprintPaused) return;
        sprintPaused = false;
        Sprint.popPause();
    }
}
