package snill.client.client.modules.impl.render.base.implement;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.List;

public class ArmorHud extends InterfaceProcessing {

    private final ItemStack[] armorScratch = new ItemStack[4];

    public ArmorHud(Draggable draggable) {
        super(draggable);
    }

    private void drawBlurPanel(MatrixStack matrices, float x, float y, float width, float height, float radius) {
        RenderUtils.drawBlur(matrices, x, y, width, height, radius, 5f, ColorUtils.rgba(255, 255, 255, 255));
        RenderUtils.drawBlur(matrices, x, y, width, height, radius, 5f, ColorUtils.rgba(0, 0, 0, 180));
        RenderUtils.drawRoundedRect(matrices, x, y, width, height, radius, ColorUtils.rgba(20, 20, 20, 100));
    }

    private void drawItem(EventRender.Default eventRender, MatrixStack matrices, ItemStack stack,
                          float slotX, float slotY, float slotSize, float itemScale) {
        if (stack.isEmpty() || itemScale < 0.05f) return;
        float itemScreenSize = 16f * itemScale;
        float offsetX = slotX + (slotSize - itemScreenSize) / 2f;
        float offsetY = slotY + (slotSize - itemScreenSize) / 2f;
        matrices.push();
        matrices.translate(offsetX, offsetY, 0);
        matrices.scale(itemScale, itemScale, 1f);
        eventRender.getContext().drawItem(stack, 0, 0);
        matrices.pop();
    }

    private float getItemDurabilityProgress(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return -1f;
        int max = stack.getMaxDamage();
        if (max <= 0) return -1f;
        return MathHelper.clamp((max - stack.getDamage()) / (float) max, 0f, 1f);
    }

    private int getDurabilityColor(float progress) {
        if (progress > 0.6f) return ColorUtils.rgba(80, 220, 120, 255);
        if (progress > 0.3f) return ColorUtils.rgba(230, 190, 50, 255);
        return ColorUtils.rgba(220, 70, 70, 255);
    }

    private void drawDurabilityBar(MatrixStack matrices, float x, float y, float width, float height,
                                   float progress, float alpha) {
        int bg = ColorUtils.rgba(30, 32, 42, (int) (160 * alpha));
        RenderUtils.drawRoundedRect(matrices, x, y, width, height, 1f, bg);
        if (progress <= 0.01f) return;
        float fillW = Math.max(2f, width * progress);
        int color = getDurabilityColor(progress);
        int fillLeft = ColorUtils.applyAlpha(ColorUtils.darken(color, 0.45f), alpha);
        int fillRight = ColorUtils.applyAlpha(color, alpha);
        int glowColor = ColorUtils.applyAlpha(color, alpha * 0.28f);
        RenderUtils.drawRoundedRect(matrices, x, y - 0.35f, fillW, height + 0.7f, 1f, glowColor);
        RenderUtils.drawGradientRect(matrices, x, y, fillW, height, 1f, fillLeft, fillRight, true);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (mc.player == null) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        List<ItemStack> armorItems = new ObjectArrayList<>();
        for (ItemStack stack : mc.player.getArmorItems()) {
            if (!stack.isEmpty()) {
                armorItems.add(stack);
            }
        }

        int armorCount = Math.min(armorItems.size(), armorScratch.length);
        boolean hasArmor = armorCount > 0;
        if (!hasArmor) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        float slotSize = 10f;
        float slotGap = 1.5f;
        float slotPad = 1.5f;
        float durGap = 1f;
        float durBarH = 2f;
        float blurRadius = 4f;
        float slotBlockH = slotSize + durGap + durBarH;
        float armorContW = slotPad * 2 + armorCount * slotSize + (armorCount - 1) * slotGap;
        float armorContH = slotPad * 2 + slotBlockH;
        float itemScale = 0.52f;
        float x = draggable.getX();
        float y = draggable.getY();
        MatrixStack matrices = eventRender.getContext().getMatrices();

        matrices.push();
        drawBlurPanel(matrices, x, y, armorContW, armorContH, blurRadius);
        for (int i = 0; i < armorCount; i++) {
            ItemStack stack = armorItems.get(i);
            armorScratch[i] = stack;
            float sx = x + slotPad + i * (slotSize + slotGap);
            float sy = y + slotPad;
            drawItem(eventRender, matrices, stack, sx, sy, slotSize, itemScale);
            float durProgress = getItemDurabilityProgress(stack);
            if (durProgress >= 0f) {
                drawDurabilityBar(matrices, sx, sy + slotSize + durGap, slotSize, durBarH, durProgress, 1f);
            }
            armorScratch[i] = ItemStack.EMPTY;
        }
        matrices.pop();

        draggable.setWidth(armorContW);
        draggable.setHeight(armorContH);
    }
}
