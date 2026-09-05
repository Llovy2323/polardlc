package snill.client.client.modules.impl.movement;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.PlayerInput;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.movement.InputUtils;
import snill.client.api.utils.network.NetworkUtils;
import snill.client.api.utils.script.ScriptManager;
import snill.client.api.utils.script.ScriptTask;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.client.ui.MenuPanel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InventoryWalk extends Module {

    public static InventoryWalk INSTANCE = new InventoryWalk();

    private final ModeSetting mode = new ModeSetting("Режим", "Обычный", "Обычный", "Обход", "Легит");

    private final List<Packet<?>> delayedPackets = new CopyOnWriteArrayList<>();
    private final ScriptManager scriptManager = new ScriptManager();

    private boolean processingPackets;
    private boolean movedInGui;

    public boolean swapBypass;

    public InventoryWalk() {
        super("GuiMove", "Позволяет перемещаться с открытым инвентарём, не прерывая процесс передвижения", ModuleCategory.MOVEMENT);
        addSettings(mode);
    }

    public void setExternalMovementLock(boolean lock) {
        if (lock) {
            InputUtils.lockMovement();
        } else {
            InputUtils.unlockMovement();
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            cleanup();
            return;
        }

        scriptManager.tick(event);

        if (!isMovementScreen()) {
            if (!processingPackets && delayedPackets.isEmpty()) {
                movedInGui = false;
            }
            return;
        }

        if (mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof SignEditScreen) {
            return;
        }
        if (mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        movedInGui |= movementKeysDown() && !delayedPackets.isEmpty();
        if (!InputUtils.isMovementLocked()) {
            InputUtils.syncMovementKeys(movementKeys(false));
        }
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != EventPacket.Type.SEND) return;
        if (mode.is("Обычный") || swapBypass) return;

        boolean moving = movedInGui || movementKeysDown();
        movedInGui |= moving && !delayedPackets.isEmpty();

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClickSlotC2SPacket clickPacket
                && mc.currentScreen instanceof InventoryScreen
                && moving
                && shouldAllowMovement()) {
            delayedPackets.add(clickPacket);
            event.cancel();
        } else if (packet instanceof CloseHandledScreenC2SPacket closePacket
                && closePacket.getSyncId() == 0
                && moving
                && !processingPackets) {
            if (delayedPackets.isEmpty()) {
                event.cancel();
            } else {
                delayedPackets.add(closePacket);
                event.cancel();
                processDelayedPackets();
            }
        }

        if (processingPackets && packet instanceof PlayerInputC2SPacket) {
            event.cancel();
            NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(
                    new PlayerInput(false, false, false, false, false, false, false)
            ));
        }

        if (!delayedPackets.isEmpty() && processingPackets) {
            if (packet instanceof HandSwingC2SPacket
                    || packet instanceof PlayerInteractEntityC2SPacket
                    || packet instanceof PlayerInteractItemC2SPacket
                    || packet instanceof PlayerInteractBlockC2SPacket) {
                event.cancel();
            }
        }
    }

    private void processDelayedPackets() {
        processingPackets = true;
        ScriptTask task = new ScriptTask();
        scriptManager.addTask(task);

        if (mode.is("Обход")) {
            task.schedule(e -> {
                InputUtils.lockMovement();
                return true;
            }).schedule(e -> {
                for (Packet<?> p : delayedPackets) {
                    NetworkUtils.sendSilentPacket(p);
                }
                delayedPackets.clear();
                processingPackets = false;
                movedInGui = false;
                return true;
            }).schedule(e -> {
                for (Packet<?> p : delayedPackets) {
                    if (p instanceof CloseHandledScreenC2SPacket) {
                        NetworkUtils.sendSilentPacket(p);
                    }
                }
                InputUtils.unlockMovement();
                return true;
            });
        } else {
            task.schedule(e -> {
                InputUtils.lockMovement();
                return true;
            }).schedule(e -> true)
                    .schedule(e -> true)
                    .schedule(e -> {
                        for (Packet<?> p : delayedPackets) {
                            if (!(p instanceof CloseHandledScreenC2SPacket)) {
                                NetworkUtils.sendSilentPacket(p);
                            }
                        }
                        return true;
                    }).schedule(e -> true)
                    .schedule(e -> {
                        for (Packet<?> p : delayedPackets) {
                            if (p instanceof CloseHandledScreenC2SPacket) {
                                NetworkUtils.sendSilentPacket(p);
                            }
                        }
                        delayedPackets.clear();
                        return true;
                    }).schedule(e -> true)
                    .schedule(e -> {
                        InputUtils.unlockMovement();
                        processingPackets = false;
                        movedInGui = false;
                        return true;
                    });
        }
    }

    private boolean movementKeysDown() {
        if (mc == null || mc.getWindow() == null || mc.options == null) return false;
        boolean inventory = mc.currentScreen instanceof InventoryScreen
                || mc.currentScreen instanceof CreativeInventoryScreen;

        for (KeyBinding binding : movementKeys(true)) {
            if (inventory && (
                    binding == mc.options.sneakKey
                            || (binding == mc.options.sprintKey
                            && !mc.options.forwardKey.equals(mc.options.sprintKey))
            )) {
                continue;
            }
            if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), binding.getDefaultKey().getCode())) {
                return true;
            }
        }
        return false;
    }

    private KeyBinding[] movementKeys(boolean includeModifiers) {
        return includeModifiers
                ? new KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey, mc.options.rightKey, mc.options.leftKey,
                mc.options.jumpKey, mc.options.sneakKey, mc.options.sprintKey
        }
                : new KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey, mc.options.rightKey, mc.options.leftKey,
                mc.options.jumpKey
        };
    }

    private boolean shouldAllowMovement() {
        return mc.player != null
                && mc.player.currentScreenHandler != null
                && mc.player.currentScreenHandler.slots.size() >= 27;
    }

    private boolean isMovementScreen() {
        return mc.currentScreen instanceof InventoryScreen
                || mc.currentScreen instanceof MenuPanel
                || mc.currentScreen instanceof CreativeInventoryScreen;
    }

    private void cleanup() {
        delayedPackets.clear();
        processingPackets = false;
        movedInGui = false;
        InputUtils.unlockMovement();
        scriptManager.clear();
    }

    @Override
    public void onDisable() {
        cleanup();
        swapBypass = false;
        super.onDisable();
    }
}
