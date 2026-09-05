package snill.client.client.modules.impl.render.base.implement;

import net.minecraft.client.util.math.MatrixStack;
import snill.client.Snill;
import snill.client.api.events.implement.EventRender;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArrayListHud extends InterfaceProcessing {

    private static final float LINE_HEIGHT = 9.5f;
    private static final float FLOW_SPEED = 1000f;
    private static final Comparator<ModuleEntry> MODULE_WIDTH_COMPARATOR = Comparator.comparingDouble((ModuleEntry entry) -> entry.width).reversed();

    private final List<ModuleEntry> visibleModules = new ArrayList<>();

    public ArrayListHud(Draggable draggable) {
        super(draggable);
    }

    private Font font() {
        return Fonts.getFont("suisse", 14);
    }

    private void drawFlowingText(MatrixStack matrices, Font font, String text, float x, float y, int color, float alphaMul) {
        int textColor = ColorUtils.setAlphaColor(color, (int) (255 * alphaMul));
        font.draw(matrices, text, x, y, textColor);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        final MatrixStack matrices = eventRender.getContext().getMatrices();
        final Font font = font();
        final List<Module> modules = ModuleClass.INSTANCE.getObject();
        visibleModules.clear();
        for (Module module : modules) {
            module.getArrayAnimka().update(module.isEnable() ? 1.0f : 0.0f);
            float anim = module.getArrayAnimka().getValue();
            if (anim <= 0.03f) continue;

            String displayName = module.getDisplayName();
            visibleModules.add(new ModuleEntry(displayName.toLowerCase(), font.getWidth(displayName), anim));
        }
        visibleModules.sort(MODULE_WIDTH_COMPARATOR);
        final long now = System.currentTimeMillis();

        float x = draggable.getX();
        float y = draggable.getY();
        float maxWidth = 0.0f;
        boolean leftHalf = x <= mc.getWindow().getScaledWidth() * 0.5f;

        for (ModuleEntry entry : visibleModules) {
            maxWidth = Math.max(maxWidth, entry.width);
        }

        float yOffset = 0.0f;
        for (int i = 0; i < visibleModules.size(); i++) {
            ModuleEntry entry = visibleModules.get(i);
            float anim = entry.anim;
            float lineStep = LINE_HEIGHT * anim;

            int indexShift = (int) ((now * FLOW_SPEED) / 10.0f) + i * 42;
            int rowColor = ColorUtils.getThemeColor(indexShift);
            int rowColor2 = ColorUtils.getThemeColor(indexShift + 90);
            int glowAlpha = (int) ((leftHalf ? 140 : 170) * anim);
            int glow1 = ColorUtils.setAlphaColor(rowColor, glowAlpha);
            int glow2 = ColorUtils.setAlphaColor(rowColor2, glowAlpha);

            float textWidth = entry.width;
            float drawX;
            if (leftHalf) {
                drawX = x - 3.0f;
            } else {
                drawX = x + (maxWidth - textWidth) - 3.0f;
            }
            float drawY = y + yOffset + (1.0f - anim) * 7.0f;

            float shadowX = leftHalf ? drawX - 0.6f : drawX - 1.5f;
            float shadowW = leftHalf ? textWidth - 4.0f : textWidth;
            RenderUtils.drawShadow(matrices, shadowX, drawY, shadowW, 6.0f, 5, 11, glow2, glow2, glow1, glow1);
            float textX = leftHalf ? drawX - 0.8f : drawX - 2.0f;
            drawFlowingText(matrices, font, entry.lowerName, textX, drawY + 1.5f, rowColor, anim);

            yOffset += lineStep;
        }

        if (yOffset > 0.5f) {
            float lineX = leftHalf ? x - 6.5f : x + maxWidth - 7;
            float lineWidth = 2.5f;
            int topLineColor = ColorUtils.setAlphaColor(ColorUtils.getThemeColor(0), 220);
            int bottomLineColor = ColorUtils.setAlphaColor(ColorUtils.getThemeColor(180), 220);

            RenderUtils.drawGradientRect(matrices, lineX, y, lineWidth, yOffset - 2.0f, 0, topLineColor, bottomLineColor);
        }

        draggable.setWidth(maxWidth + 4.0f);
        draggable.setHeight(yOffset);

        super.onRender(eventRender);
    }

    private record ModuleEntry(String lowerName, float width, float anim) {
    }
}
