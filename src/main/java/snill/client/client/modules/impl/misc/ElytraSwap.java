package snill.client.client.modules.impl.misc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventBinding;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.chat.ChatUtils;
import snill.client.api.utils.player.MoveUtils;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.movement.InventoryWalk;
import snill.client.client.modules.settings.implement.BindSetting;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.mixin.FireworkRocketEntityAccessor;

public class ElytraSwap extends Module {

    public static ElytraSwap INSTANCE = new ElytraSwap();

    private static final long SWAP_COOLDOWN_MS = 600L;
    private static final long FIREWORK_COOLDOWN_MS = 50L;
    private static final long PENDING_FIREWORK_TIMEOUT_MS = 500L;

    private final BindSetting elytraBind = new BindSetting("Бинд элитры", -1);
    private final BindSetting fireworkBind = new BindSetting("Бинд фейерверка", -1);
    private final BooleanSetting autoFly = new BooleanSetting("Авто-взлёт", true);
    private final BooleanSetting autoTarget = new BooleanSetting("АвтоТаргет", false);
    private final BooleanSetting syncGuiMove = new BooleanSetting("Синхр. с GuiMove", true);
    private final BooleanSetting swapDelay = new BooleanSetting("Задержка при свапе", true);
    private static final String MODE_GRIM = "Grim";
    private static final String MODE_RW = "RW";
    private static final int RW_STOP_TICKS = 2;
    private final ModeSetting swapMode = new ModeSetting("Тип свапа", MODE_GRIM, MODE_GRIM, MODE_RW);

    private ItemStack currentChest = ItemStack.EMPTY;
    private int observedTargetEntityId = -1;
    private net.minecraft.item.Item observedTargetChestItem = Items.AIR;
    private boolean autoTargetFirstFireworkConsumed = false;

    private long swapLastMs = 0L;
    private long fireworkLastMs = 0L;
    private long pendingFireworkUntil = 0L;

    private boolean swapQueued = false;
    private boolean fireworkQueued = false;
    private int swapDelayTicks = 0;
    private enum RwPhase { IDLE, STOP_MOVE, PICKUP_SRC, PUT_CHEST, CLOSE_INV, DONE }
    private RwPhase rwPhase = RwPhase.IDLE;
    private int rwSrcSlot = -1;
    private int rwTick;

    public ElytraSwap() {
        super("Elytra Util", "Автоматический свап элитр", ModuleCategory.MISC);
        addSettings(elytraBind, fireworkBind, autoFly, autoTarget, syncGuiMove, swapDelay, swapMode);
    }

    @EventLink
    public void onUpdate(final EventUpdate ignored) {
        if (mc.player == null || mc.world == null) return;

        currentChest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        LivingEntity auraTarget = getAuraTarget();

        if (autoTarget.isState()) {
            updateAutoTargetObservation(auraTarget);
            tryMirrorAutoTargetFirstFirework(auraTarget);
        } else {
            resetAutoTargetState();
        }

        if (swapDelayTicks > 0) swapDelayTicks--;

        if (swapMode.is(MODE_RW)) tickRwSwap();

        if (swapQueued && (!swapDelay.isState()
                || !syncGuiMove.isState()
                || !MoveUtils.isMoving())) {
            swapQueued = false;
            trySwap();
        }

        if (fireworkQueued) {
            fireworkQueued = false;
            requestFirework();
        }

        if ((autoFly.isState() || shouldAutoTargetMirrorTakeoff(auraTarget)) && currentChest.isOf(Items.ELYTRA)) {
            tryTakeoff(currentChest);
        }

        tryLaunchPendingFirework();
    }

    @EventLink
    public void onMoveInput(final EventMoveInput event) {
        if (swapMode.is(MODE_RW) && rwPhase != RwPhase.IDLE && rwPhase != RwPhase.DONE) {
            if (mc.player != null) mc.player.setSprinting(false);
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
            event.setSneak(false);
            return;
        }
        if (swapDelay.isState() && syncGuiMove.isState() && swapDelayTicks > 0) {
            if (mc.player != null) mc.player.setSprinting(false);
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
            event.setSneak(false);
        }
    }

    @EventLink
    public void onBinding(final EventBinding event) {
        if (event.getKey() == elytraBind.getKey()) {
            swapQueued = true;
            swapDelayTicks = swapDelay.isState() ? 2 : 0;
        }
        if (event.getKey() == fireworkBind.getKey()) fireworkQueued = true;
    }

    private void trySwap() {
        if (System.currentTimeMillis() - swapLastMs < SWAP_COOLDOWN_MS) return;
        doChangeChest(currentChest);
        swapLastMs = System.currentTimeMillis();
    }

    private void doChangeChest(ItemStack chest) {
        if (chest.isOf(Items.ELYTRA)) {
            int armorSlot = InventoryUtils.findBestChestplateSlot();
            if (armorSlot < 0) {
                ChatUtils.sendMessage(Formatting.RED + "" + Formatting.BOLD + "Нет нагрудника!");
                return;
            }
            moveToChestSlot(armorSlot);
        } else {
            int elytraSlot = InventoryUtils.findBestElytraSlot();
            if (elytraSlot < 0) {
                ChatUtils.sendMessage(Formatting.RED + "" + Formatting.BOLD + "Нет элитры!");
                return;
            }
            moveToChestSlot(elytraSlot);
        }
    }

    private void moveToChestSlot(int slot) {
        if (swapMode.is(MODE_RW)) {
            startRwSwap(slot);
            return;
        }
        InventoryWalk guiMove = InventoryWalk.INSTANCE;
        boolean guiActive = guiMove != null && guiMove.isEnable() && syncGuiMove.isState();

        if (guiActive) guiMove.swapBypass = true;
        if (slot >= 0 && slot < 9) {
            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));

        if (guiActive) guiMove.swapBypass = false;
    }

    private void startRwSwap(int slot) {
        if (rwPhase != RwPhase.IDLE || slot < 0) return;
        InventoryWalk guiMove = InventoryWalk.INSTANCE;
        if (guiMove != null && guiMove.isEnable() && syncGuiMove.isState()) guiMove.swapBypass = true;
        // InventoryUtils returns inventory indexes; PICKUP uses player-screen slot indexes.
        rwSrcSlot = slot < 9 ? slot + 36 : slot;
        rwTick = 0;
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
                rwPhase = RwPhase.PUT_CHEST;
            }
            case PUT_CHEST -> {
                mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, mc.player);
                if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(0, rwSrcSlot, 0, SlotActionType.PICKUP, mc.player);
                }
                rwPhase = RwPhase.CLOSE_INV;
            }
            case CLOSE_INV -> {
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                rwPhase = RwPhase.DONE;
            }
            case DONE -> {
                InventoryWalk guiMove = InventoryWalk.INSTANCE;
                if (guiMove != null) guiMove.swapBypass = false;
                rwSrcSlot = -1;
                rwTick = 0;
                rwPhase = RwPhase.IDLE;
            }
        }
    }

    private void requestFirework() {
        if (System.currentTimeMillis() - fireworkLastMs < FIREWORK_COOLDOWN_MS) return;
        if (!hasFirework()) return;

        if (launchFirework()) {
            fireworkLastMs = System.currentTimeMillis();
            pendingFireworkUntil = 0L;
            return;
        }

        if (canWaitForFlight()) {
            pendingFireworkUntil = System.currentTimeMillis() + PENDING_FIREWORK_TIMEOUT_MS;
        }
    }

    private void tryLaunchPendingFirework() {
        if (pendingFireworkUntil == 0L) return;
        if (System.currentTimeMillis() > pendingFireworkUntil || !hasFirework()) {
            pendingFireworkUntil = 0L;
            return;
        }
        if (launchFirework()) {
            fireworkLastMs = System.currentTimeMillis();
            pendingFireworkUntil = 0L;
        }
    }

    private boolean launchFirework() {
        if (!mc.player.isGliding()) return false;
        if (!hasFirework()) return false;

        InventoryWalk guiMove = InventoryWalk.INSTANCE;
        boolean guiActive = guiMove != null && guiMove.isEnable() && syncGuiMove.isState();

        if (guiActive) guiMove.swapBypass = true;
        InventoryUtils.swapAndUseHvH(Items.FIREWORK_ROCKET);
        if (guiActive) guiMove.swapBypass = false;

        return true;
    }

    private boolean canWaitForFlight() {
        return currentChest.isOf(Items.ELYTRA)
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private LivingEntity getAuraTarget() {
        Aura aura = Aura.INSTANCE;
        return aura != null && aura.isEnable() ? aura.getTarget() : null;
    }

    private boolean shouldAutoTargetMirrorTakeoff(LivingEntity target) {
        if (!autoTarget.isState() || !(target instanceof PlayerEntity playerTarget)) return false;
        return playerTarget.isGliding()
                && playerTarget.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void updateAutoTargetObservation(LivingEntity target) {
        if (!(target instanceof PlayerEntity playerTarget)) {
            resetAutoTargetState();
            return;
        }

        int targetId = playerTarget.getId();
        net.minecraft.item.Item chestNow = playerTarget.getEquippedStack(EquipmentSlot.CHEST).getItem();

        if (observedTargetEntityId != targetId) {
            observedTargetEntityId = targetId;
            observedTargetChestItem = chestNow;
            autoTargetFirstFireworkConsumed = false;
            return;
        }

        tryMirrorTargetElytraSwapOnTransition(observedTargetChestItem, chestNow);
        observedTargetChestItem = chestNow;

        if (chestNow != Items.ELYTRA || !playerTarget.isGliding()) {
            autoTargetFirstFireworkConsumed = false;
        }
    }

    private void tryMirrorTargetElytraSwapOnTransition(net.minecraft.item.Item previousChest, net.minecraft.item.Item currentTargetChest) {
        if (previousChest == Items.ELYTRA || currentTargetChest != Items.ELYTRA) return;
        if (currentChest.isOf(Items.ELYTRA)) return;
        if (InventoryUtils.findBestElytraSlot() < 0) return;
        if (mc.currentScreen != null && !syncGuiMove.isState()) return;

        doChangeChest(currentChest);
        swapLastMs = System.currentTimeMillis();
    }

    private void tryMirrorAutoTargetFirstFirework(LivingEntity target) {
        if (!(target instanceof PlayerEntity playerTarget)) return;
        if (autoTargetFirstFireworkConsumed) return;
        if (!playerTarget.isGliding() || !playerTarget.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
        if (!currentChest.isOf(Items.ELYTRA) || !mc.player.isGliding()) return;
        if (!hasFirework() || !targetHasElytraBoostFirework(playerTarget)) return;

        if (launchFirework()) {
            fireworkLastMs = System.currentTimeMillis();
            autoTargetFirstFireworkConsumed = true;
            pendingFireworkUntil = 0L;
        }
    }

    private boolean targetHasElytraBoostFirework(PlayerEntity target) {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof FireworkRocketEntity rocket)) continue;
            LivingEntity shooter = ((FireworkRocketEntityAccessor) rocket).snill$getShooter();
            if (shooter == target) return true;
        }
        return false;
    }

    private void resetAutoTargetState() {
        observedTargetEntityId = -1;
        observedTargetChestItem = Items.AIR;
        autoTargetFirstFireworkConsumed = false;
    }

    private boolean hasFirework() {
        return findItemSlot(Items.FIREWORK_ROCKET) >= 0;
    }

    private void tryTakeoff(ItemStack chest) {
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return;
        if (mc.player.isOnGround()) {
            mc.player.jump();
        } else if (isElytraUsable(chest) && !mc.player.isGliding() && !mc.player.getAbilities().flying) {
            mc.player.startGliding();
            mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            );
        }
    }

    private boolean isElytraUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    private int findItemSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        pendingFireworkUntil = 0L;
        swapQueued = false;
        swapDelayTicks = 0;
        rwPhase = RwPhase.IDLE;
        rwSrcSlot = -1;
        rwTick = 0;
        InventoryWalk guiMove = InventoryWalk.INSTANCE;
        if (guiMove != null) guiMove.swapBypass = false;
        fireworkQueued = false;
        resetAutoTargetState();
        super.onDisable();
    }
}
