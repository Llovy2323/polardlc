package snill.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.ScreenHandler;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snill.client.client.modules.impl.misc.AhHelper;
import snill.client.client.modules.impl.player.ItemScroller;
import snill.client.client.modules.impl.render.ShulkerPreview;
import snill.client.client.modules.impl.render.SmoothSwapping;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected ScreenHandler handler;

    @Shadow
    @Nullable
    protected abstract Slot getSlotAt(double x, double y);

    @Shadow
    protected abstract void onMouseClick(@Nullable Slot slot, int slotId, int button, SlotActionType actionType);

    private boolean snill$smoothSwapPushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemScroller itemScroller = ItemScroller.INSTANCE;

        if (!itemScroller.isEnable() || mc.player == null || mc.interactionManager == null) {
            return;
        }

        long window = mc.getWindow().getHandle();
        boolean leftMousePressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean shiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (!leftMousePressed || !shiftPressed) {
            itemScroller.resetTimer();
            return;
        }

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) {
            return;
        }

        if (!itemScroller.canQuickMove()) {
            return;
        }

        onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
    }

    @Inject(method = "drawSlots", at = @At("HEAD"))
    private void snill$drawSlotsHead(DrawContext context, CallbackInfo ci) {
        SmoothSwapping.INSTANCE.beginFrame(handler);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void snill$afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AhHelper.INSTANCE.renderFromMixin(context, mouseX, mouseY);
        ShulkerPreview shulkerPreview = ShulkerPreview.getInstance();
        if (shulkerPreview != null) {
            shulkerPreview.renderFromMixin(context, mouseX, mouseY);
        }
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void snill$drawSlotHead(DrawContext context, Slot slot, CallbackInfo ci) {
        snill$smoothSwapPushed = SmoothSwapping.INSTANCE.applyTransform(context.getMatrices(), handler, slot, slot.getStack());
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void snill$drawSlotReturn(DrawContext context, Slot slot, CallbackInfo ci) {
        SmoothSwapping.INSTANCE.popTransform(context.getMatrices(), snill$smoothSwapPushed);
        snill$smoothSwapPushed = false;
    }
}
