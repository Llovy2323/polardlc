package snill.client.client.modules.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.movement.Sprint;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ListSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AutoTotem extends Module {

    public static AutoTotem INSTANCE = new AutoTotem();

    private final ListSetting triggers = new ListSetting("Брать от",
            new BooleanSetting("Кристалл рядом", true),
            new BooleanSetting("Кристалл в руке", true),
            new BooleanSetting("Обсидиан в руке", true),
            new BooleanSetting("Падения", true));

    private final FloatSetting hp = new FloatSetting("Брать от хп", 6, 1, 20, 0.5f);
    private final FloatSetting hpOnElytra = new FloatSetting("Хп на элитрах", 10, 1, 20, 0.5f);
    private final FloatSetting crystalRadius = new FloatSetting("Радиус кристалла", 6, 1, 12, 0.5f)
            .visible(this::isCrystalRadiusVisible);
    private final FloatSetting fallHeight = new FloatSetting("Высота падения", 10, 3, 50, 1f)
            .visible(() -> triggers.is("Падения"));
    private final BooleanSetting saveEnchanted = new BooleanSetting("Сохранять зачар", true);
    private final BooleanSetting returnTotem = new BooleanSetting("Возвращать тотем", true);
    private final FloatSetting returnDelay = new FloatSetting("Задержка возврата", 20, 5, 100, 5f)
            .visible(returnTotem::isState);
    private final BooleanSetting bypassgrim = new BooleanSetting("Обходить Grim", true);

    private static final String MODE_RW = "RW";
    private static final int RW_STOP_TICKS = 2;

    private final ModeSetting swapVersion = new ModeSetting("Версия свапа", "1.21.4", "1.21.4", "1.16.5", MODE_RW);
    private int bypassTicks;
    private boolean sprintPaused;
    private int swapCooldown;
    private int savedTotemSlot = -1;
    private ItemStack originalOffhandItem = ItemStack.EMPTY;
    private boolean totemTakenByUs = false;
    private boolean returnMode = false;
    private boolean needFastSwap = false;
    private int safeTicks = 0;
    private enum RwPhase { IDLE, STOP_MOVE, PICKUP_SRC, PUT_OFFHAND, CLOSE_INV, DONE }
    private enum RwAction { TAKE, RETURN }
    private RwPhase rwPhase = RwPhase.IDLE;
    private RwAction rwAction;
    private int rwSrcSlot = -1;
    private int rwTick;

    public AutoTotem() {
        super("AutoTotem", "Автоматически берёт тотем в опасности", ModuleCategory.COMBAT);
        addSettings(
                hp,
                hpOnElytra,
                saveEnchanted,
                bypassgrim,
                returnTotem,
                swapVersion,
                returnDelay,
                triggers,
                crystalRadius,
                fallHeight
        );
    }

    @EventLink
    public void onInput(final EventMoveInput e) {
        if (swapVersion.is(MODE_RW) && rwPhase != RwPhase.IDLE && rwPhase != RwPhase.DONE) {
            if (mc.player == null) return;
            mc.player.setSprinting(false);
            e.setForward(0);
            e.setStrafe(0);
            e.setJump(false);
            e.setSneak(false);
            return;
        }
        if (!swapVersion.is(MODE_RW) && bypassgrim.isState() && bypassTicks > 0) {
            if (mc.player == null) return;
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

        boolean isCrystalDanger = isCrystalDanger();

        if (isCrystalDanger) {
            needFastSwap = true;
            safeTicks = 0;
        }

        if (swapCooldown > 0) {
            swapCooldown--;
        }

        if (swapVersion.is(MODE_RW)) tickRwSwap();

        if (!swapVersion.is(MODE_RW) && bypassgrim.isState() && bypassTicks > 0) {
            mc.player.setSprinting(false);
            bypassTicks--;

            if (bypassTicks <= 0) {
                if (returnMode) {
                    performReturn();
                } else {
                    performSwap();
                }
                restoreSprint();
            }
            return;
        }

        boolean needTotem = shouldTakeTotem(isCrystalDanger);

        if (needTotem && !hasTotemInOffhand()) {
            int totemSlot = findTotemSlot();
            if (totemSlot == -1) return;

            if (!needFastSwap && swapCooldown > 0) return;

            if (originalOffhandItem.isEmpty() && !totemTakenByUs) {
                originalOffhandItem = mc.player.getOffHandStack().copy();
            }

            savedTotemSlot = totemSlot;
            returnMode = false;
            safeTicks = 0;

            if (swapVersion.is(MODE_RW)) {
                startRwSwap(RwAction.TAKE, totemSlot);
            } else if (bypassgrim.isState()) {
                disableSprint();
                bypassTicks = needFastSwap ? 1 : 2;
                swapCooldown = needFastSwap ? 0 : 2;
            } else {
                performSwap();
                swapCooldown = needFastSwap ? 0 : 2;
            }
        }

        boolean isSafe = !needTotem;

        if (isSafe) {
            safeTicks++;
        } else {
            safeTicks = 0;
        }

        if (returnTotem.isState() && !needTotem && hasTotemInOffhand() && totemTakenByUs
                && safeTicks >= returnDelay.getValue().intValue()) {
            if (!needFastSwap && swapCooldown > 0) return;

            returnMode = true;

            if (swapVersion.is(MODE_RW)) {
                startRwSwap(RwAction.RETURN, resolveReturnSlot());
            } else if (bypassgrim.isState()) {
                disableSprint();
                bypassTicks = needFastSwap ? 1 : 2;
                swapCooldown = needFastSwap ? 0 : 2;
            } else {
                performReturn();
                swapCooldown = needFastSwap ? 0 : 2;
            }
        }

        if (!isCrystalDanger) {
            needFastSwap = false;
        }
    }

    private boolean isCrystalDanger() {
        float radius = crystalRadius.getValue().floatValue();
        double radiusSq = radius * radius;

        if (triggers.is("Кристалл рядом")) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity) {
                    if (mc.player.squaredDistanceTo(entity) <= radiusSq) {
                        return true;
                    }
                }
            }
        }

        if (triggers.is("Кристалл в руке")) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (mc.player.squaredDistanceTo(player) <= radiusSq) {
                    if (player.getMainHandStack().isOf(Items.END_CRYSTAL)
                            || player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean shouldTakeTotem(boolean isCrystalDanger) {
        float currentHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean isGliding = mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && mc.player.isGliding();

        float hpThreshold = isGliding ? hpOnElytra.getValue().floatValue() : hp.getValue().floatValue();

        if (currentHp <= hpThreshold) {
            return true;
        }

        if (isCrystalDanger) {
            return true;
        }

        float radius = crystalRadius.getValue().floatValue();
        double radiusSq = radius * radius;

        if (triggers.is("Обсидиан в руке")) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (mc.player.squaredDistanceTo(player) <= radiusSq) {
                    if (player.getMainHandStack().isOf(Items.OBSIDIAN) || player.getOffHandStack().isOf(Items.OBSIDIAN)) {
                        return true;
                    }
                }
            }
        }

        if (triggers.is("Падения")) {
            if (mc.player.fallDistance >= fallHeight.getValue().floatValue() && !isGliding) {
                return true;
            }
        }

        return false;
    }

    private boolean hasTotemInOffhand() {
        return mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private int findTotemSlot() {
        int normalTotem = -1;
        int enchantedTotem = -1;

        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                boolean isEnchanted = stack.hasEnchantments();

                if (isEnchanted) {
                    if (enchantedTotem == -1) {
                        enchantedTotem = i;
                    }
                } else if (normalTotem == -1) {
                    normalTotem = i;
                }
            }
        }

        if (saveEnchanted.isState()) {
            return normalTotem != -1 ? normalTotem : enchantedTotem;
        } else {
            return enchantedTotem != -1 ? enchantedTotem : normalTotem;
        }
    }

    private void performSwap() {
        int totemSlot = findTotemSlot();

        if (totemSlot == -1) {
            return;
        }

        savedTotemSlot = totemSlot;
        doSwap(totemSlot);
        totemTakenByUs = true;
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    private void performReturn() {
        if (!hasTotemInOffhand()) {
            totemTakenByUs = false;
            return;
        }

        if (!originalOffhandItem.isEmpty()) {
            int slotToReturn = findSlotForItem(originalOffhandItem);
            if (slotToReturn != -1) {
                doSwap(slotToReturn);
            } else {
                if (savedTotemSlot == -1) {
                    savedTotemSlot = 9;
                }
                doSwap(savedTotemSlot);
            }
        } else {
            if (savedTotemSlot == -1) {
                savedTotemSlot = 9;
            }
            doSwap(savedTotemSlot);
        }

        totemTakenByUs = false;
        savedTotemSlot = -1;
        originalOffhandItem = ItemStack.EMPTY;
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    private int resolveReturnSlot() {
        if (!originalOffhandItem.isEmpty()) {
            int slot = findSlotForItem(originalOffhandItem);
            if (slot != -1) return slot;
        }
        return savedTotemSlot == -1 ? 9 : savedTotemSlot;
    }

    private void startRwSwap(RwAction action, int slot) {
        if (rwPhase != RwPhase.IDLE || slot < 0) return;
        rwAction = action;
        rwSrcSlot = slot;
        rwTick = 0;
        disableSprint();
        rwPhase = RwPhase.STOP_MOVE;
    }

    private void tickRwSwap() {
        switch (rwPhase) {
            case IDLE -> { }
            case STOP_MOVE -> {
                mc.player.setSprinting(false);
                if (++rwTick >= RW_STOP_TICKS) {
                    rwTick = 0;
                    rwPhase = RwPhase.PICKUP_SRC;
                }
            }
            case PICKUP_SRC -> {
                mc.interactionManager.clickSlot(0, rwSrcSlot, 0, SlotActionType.PICKUP, mc.player);
                rwPhase = RwPhase.PUT_OFFHAND;
            }
            case PUT_OFFHAND -> {
                mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.PICKUP, mc.player);
                if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(0, rwSrcSlot, 0, SlotActionType.PICKUP, mc.player);
                }
                rwPhase = RwPhase.CLOSE_INV;
            }
            case CLOSE_INV -> {
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                swapCooldown = 3;
                rwPhase = RwPhase.DONE;
            }
            case DONE -> {
                if (rwAction == RwAction.TAKE) {
                    totemTakenByUs = true;
                } else {
                    totemTakenByUs = false;
                    savedTotemSlot = -1;
                    originalOffhandItem = ItemStack.EMPTY;
                }
                rwAction = null;
                rwSrcSlot = -1;
                rwTick = 0;
                rwPhase = RwPhase.IDLE;
                restoreSprint();
            }
        }
    }

    private int findSlotForItem(ItemStack item) {
        if (item.isEmpty()) return -1;

        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (ItemStack.areItemsEqual(stack, item) && ItemStack.areEqual(stack, item)) {
                return i;
            }
        }
        return -1;
    }

    private void doSwap(int slot) {
        if (swapVersion.is("1.16.5")) {
            doSwap1165(slot);
            return;
        }

        doSwap1214(slot);
    }

    private void doSwap1214(int slot) {
        if (slot >= 36 && slot <= 44) {
            int hotbarSlot = slot - 36;
            mc.interactionManager.clickSlot(0, 45, hotbarSlot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }
    }

    private void doSwap1165(int slot) {
        mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
    }

    private void disableSprint() {
        if (sprintPaused) {
            return;
        }

        Sprint.pushPause(1000);
        sprintPaused = true;
    }

    private void restoreSprint() {
        if (!sprintPaused) {
            return;
        }

        sprintPaused = false;
        Sprint.popPause();
    }

    private boolean isCrystalRadiusVisible() {
        return triggers.is("Кристалл рядом")
                || triggers.is("Кристалл в руке")
                || triggers.is("Обсидиан в руке");
    }

    @Override
    public void onDisable() {
        bypassTicks = 0;
        swapCooldown = 0;
        savedTotemSlot = -1;
        originalOffhandItem = ItemStack.EMPTY;
        totemTakenByUs = false;
        returnMode = false;
        needFastSwap = false;
        safeTicks = 0;
        rwPhase = RwPhase.IDLE;
        rwAction = null;
        rwSrcSlot = -1;
        rwTick = 0;
        restoreSprint();
        super.onDisable();
    }
}
