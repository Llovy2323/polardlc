package snill.client.client.modules.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.MathHelper;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;

import java.util.HashMap;
import java.util.Map;

public class SmoothSwapping extends Module {
    public static SmoothSwapping INSTANCE = new SmoothSwapping();

    private final FloatSetting speed = new FloatSetting("Speed", 220.0F, 50.0F, 600.0F, 10.0F);

    private final Map<Integer, SlotSnapshot> previousSlots = new HashMap<>();
    private final Map<Integer, SlotAnimation> animations = new HashMap<>();
    private final Map<Integer, SlotSnapshot> previousHotbarSlots = new HashMap<>();
    private final Map<Integer, SlotAnimation> hotbarAnimations = new HashMap<>();
    private int containerId = -1;
    private int previousSelectedSlot = -1;
    private int selectedSlot = -1;
    private int selectedSlotOffset;
    private long selectedSlotAnimationStart;

    public SmoothSwapping() {
        super("Smooth Swapping", "Плавно анимирует перемещение предметов в инвентаре", ModuleCategory.RENDER);
        addSettings(speed);
    }

    public void beginFrame(ScreenHandler handler) {
        if (!isEnable() || handler == null) {
            clearInventory();
            return;
        }

        int id = System.identityHashCode(handler);
        if (containerId != id) {
            containerId = id;
            previousSlots.clear();
            animations.clear();
        }

        long now = System.currentTimeMillis();
        Map<Integer, SlotSnapshot> currentSlots = new HashMap<>();
        for (Slot slot : handler.slots) {
            currentSlots.put(slot.id, new SlotSnapshot(slot.id, slot.x, slot.y, slot.getStack().copy()));
        }

        updateAnimations(previousSlots, animations, currentSlots, now);
        previousSlots.clear();
        previousSlots.putAll(currentSlots);
    }

    public void beginHotbarFrame(int screenWidth, int screenHeight, PlayerInventory inventory) {
        if (!isEnable() || inventory == null) {
            previousHotbarSlots.clear();
            hotbarAnimations.clear();
            previousSelectedSlot = -1;
            selectedSlot = -1;
            return;
        }

        long now = System.currentTimeMillis();
        Map<Integer, SlotSnapshot> currentSlots = new HashMap<>();
        int centerX = screenWidth / 2;
        for (int i = 0; i < 9; i++) {
            int x = centerX - 90 + i * 20 + 2;
            int y = screenHeight - 16 - 3;
            currentSlots.put(i, new SlotSnapshot(i, x, y, inventory.getStack(i).copy()));
        }

        updateAnimations(previousHotbarSlots, hotbarAnimations, currentSlots, now);
        updateSelectedSlot(inventory.selectedSlot, now);
        previousHotbarSlots.clear();
        previousHotbarSlots.putAll(currentSlots);
    }

    public boolean applyTransform(MatrixStack matrices, ScreenHandler handler, Slot slot, ItemStack stack) {
        if (!isEnable() || handler == null || slot == null || stack.isEmpty() || containerId != System.identityHashCode(handler)) {
            return false;
        }
        SlotAnimation animation = animations.get(slot.id);
        return applyAnimation(matrices, animations, animation, slot.id, slot.x, slot.y);
    }

    public boolean applyHotbarTransform(MatrixStack matrices, int slot, ItemStack stack, int x, int y) {
        if (!isEnable() || stack.isEmpty()) {
            return false;
        }
        SlotAnimation animation = hotbarAnimations.get(slot);
        return applyAnimation(matrices, hotbarAnimations, animation, slot, x, y);
    }

    public int getAnimatedSelectedSlotX(int vanillaX) {
        if (!isEnable() || selectedSlotAnimationStart <= 0L) {
            return vanillaX;
        }

        float progress = (System.currentTimeMillis() - selectedSlotAnimationStart) / Math.max(1.0F, speed.get());
        if (progress >= 1.0F) {
            selectedSlotAnimationStart = 0L;
            return vanillaX;
        }

        float eased = 1.0F - (float) Math.pow(1.0F - MathHelper.clamp(progress, 0.0F, 1.0F), 3.0D);
        return Math.round(vanillaX + selectedSlotOffset * (1.0F - eased));
    }

    public void popTransform(MatrixStack matrices, boolean pushed) {
        if (pushed) {
            matrices.pop();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        clearInventory();
        previousHotbarSlots.clear();
        hotbarAnimations.clear();
        previousSelectedSlot = -1;
        selectedSlot = -1;
        selectedSlotAnimationStart = 0L;
    }

    private void updateAnimations(Map<Integer, SlotSnapshot> previous,
                                  Map<Integer, SlotAnimation> targetAnimations,
                                  Map<Integer, SlotSnapshot> current,
                                  long now) {
        for (SlotSnapshot currentSlot : current.values()) {
            SlotSnapshot previousSlot = previous.get(currentSlot.slotNumber);
            if (currentSlot.stack.isEmpty() || sameVisualStack(previousSlot == null ? ItemStack.EMPTY : previousSlot.stack, currentSlot.stack)) {
                continue;
            }

            SlotSnapshot source = findSourceSlot(currentSlot, current, previous);
            if (source != null) {
                targetAnimations.put(currentSlot.slotNumber, SlotAnimation.move(source.x - currentSlot.x, source.y - currentSlot.y, now));
            } else if (previousSlot == null || previousSlot.stack.isEmpty()) {
                targetAnimations.put(currentSlot.slotNumber, SlotAnimation.appear(now));
            }
        }
    }

    private boolean applyAnimation(MatrixStack matrices,
                                   Map<Integer, SlotAnimation> targetAnimations,
                                   SlotAnimation animation,
                                   int slotId,
                                   int x,
                                   int y) {
        if (animation == null) {
            return false;
        }

        float progress = (System.currentTimeMillis() - animation.startTime) / Math.max(1.0F, speed.get());
        if (progress >= 1.0F) {
            targetAnimations.remove(slotId);
            return false;
        }

        float eased = 1.0F - (float) Math.pow(1.0F - MathHelper.clamp(progress, 0.0F, 1.0F), 3.0D);
        matrices.push();
        if (animation.appear) {
            float scale = 0.72F + eased * 0.28F;
            float centerX = x + 8.0F;
            float centerY = y + 8.0F;
            matrices.translate(centerX, centerY - (1.0F - eased) * 5.0F, 0.0F);
            matrices.scale(scale, scale, 1.0F);
            matrices.translate(-centerX, -centerY, 0.0F);
        } else {
            matrices.translate(animation.offsetX * (1.0F - eased), animation.offsetY * (1.0F - eased), 0.0F);
        }
        return true;
    }

    private SlotSnapshot findSourceSlot(SlotSnapshot target, Map<Integer, SlotSnapshot> current, Map<Integer, SlotSnapshot> previous) {
        SlotSnapshot fallback = null;
        float fallbackDistance = Float.MAX_VALUE;
        for (SlotSnapshot previousSlot : previous.values()) {
            if (previousSlot.slotNumber == target.slotNumber || !sameVisualStack(previousSlot.stack, target.stack)) {
                continue;
            }

            float dx = previousSlot.x - target.x;
            float dy = previousSlot.y - target.y;
            float distance = dx * dx + dy * dy;
            if (distance < fallbackDistance) {
                fallbackDistance = distance;
                fallback = previousSlot;
            }

            SlotSnapshot currentSource = current.get(previousSlot.slotNumber);
            if (currentSource == null || !sameVisualStack(currentSource.stack, target.stack)) {
                return previousSlot;
            }
        }
        return fallback;
    }

    private boolean sameVisualStack(ItemStack first, ItemStack second) {
        return !first.isEmpty() && !second.isEmpty() && ItemStack.areItemsAndComponentsEqual(first, second);
    }

    private void updateSelectedSlot(int currentSelectedSlot, long now) {
        if (previousSelectedSlot < 0) {
            previousSelectedSlot = currentSelectedSlot;
            selectedSlot = currentSelectedSlot;
            selectedSlotAnimationStart = 0L;
            return;
        }

        if (currentSelectedSlot != selectedSlot) {
            selectedSlotOffset = (selectedSlot - currentSelectedSlot) * 20;
            selectedSlot = currentSelectedSlot;
            selectedSlotAnimationStart = now;
        }
        previousSelectedSlot = currentSelectedSlot;
    }

    private void clearInventory() {
        containerId = -1;
        previousSlots.clear();
        animations.clear();
    }

    private record SlotSnapshot(int slotNumber, int x, int y, ItemStack stack) {
    }

    private record SlotAnimation(float offsetX, float offsetY, long startTime, boolean appear) {
        private static SlotAnimation move(float offsetX, float offsetY, long startTime) {
            return new SlotAnimation(offsetX, offsetY, startTime, false);
        }

        private static SlotAnimation appear(long startTime) {
            return new SlotAnimation(0.0F, 0.0F, startTime, true);
        }
    }
}
