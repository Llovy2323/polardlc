package snill.client.client.modules.impl.render.base.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import snill.client.Snill;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.input.KeyBoardUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.impl.misc.ServerHelper;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.List;

public class HelperBinds extends InterfaceProcessing {
    private final AnimationUtils widthAnimation = new AnimationUtils(80.0f, 10.5f, Easings.QUAD_OUT);

    public HelperBinds(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        List<ServerHelper.HelperBind> binds = getVisibleBinds();
        if (binds.isEmpty()) {
            widthAnimation.update(0.0f);
            draggable.setWidth(0.0f);
            draggable.setHeight(0.0f);
            return;
        }

        DefaultStyle(eventRender, binds);

        super.onRender(eventRender);
    }

    private List<ServerHelper.HelperBind> getVisibleBinds() {
        ServerHelper serverHelper = ServerHelper.INSTANCE;
        List<ServerHelper.HelperBind> binds = new ArrayList<>();
        if (serverHelper == null || !serverHelper.isEnable()) return binds;

        for (ServerHelper.HelperBind bind : serverHelper.getActiveHelperBinds()) {
            if (bind.bind().getKey() != -1) {
                binds.add(bind);
            }
        }

        return binds;
    }

    private void DefaultStyle(EventRender.Default eventRender, List<ServerHelper.HelperBind> binds) {
        MatrixStack matrices = eventRender.getContext().getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();
        int colorTheme = getThemeColor();

        int fontSize = 13;
        Font keyFont = issue(fontSize);
        float height = 19.0f;
        float itemSize = 9.8f;
        float itemScale = 0.61f;
        float fontGap = 2.8f;
        float cellGap = 5.0f;
        float sidePadding = 6.0f;
        float width = getCompactWidth(binds, keyFont, itemSize, fontGap, cellGap, sidePadding, 60.0f);

        widthAnimation.update(width);
        float animatedWidth = widthAnimation.getValue();

        drawDefaultPanel(matrices, x, y, animatedWidth, height, colorTheme);

        if (binds.isEmpty()) {
            issue(12).draw(matrices, "Helper", x + 5.0f, y + 6.0f, ColorUtils.rgba(255, 255, 255, 230));
            draggable.setWidth(animatedWidth);
            draggable.setHeight(height);
            return;
        }

        drawCompactBinds(eventRender.getContext(), binds, keyFont, x, y, height, itemSize, itemScale, fontGap, cellGap, sidePadding, 8.2f);

        draggable.setWidth(animatedWidth);
        draggable.setHeight(height);
    }

    private float getCompactWidth(List<ServerHelper.HelperBind> binds, Font keyFont, float itemSize, float fontGap, float cellGap, float sidePadding, float emptyWidth) {
        if (binds.isEmpty()) return emptyWidth;

        float width = sidePadding * 2.0f;
        for (int i = 0; i < binds.size(); i++) {
            String keyName = KeyBoardUtils.getBindName(binds.get(i).bind().getKey());
            width += itemSize + fontGap + keyFont.getWidth(keyName);
            if (i < binds.size() - 1) width += cellGap;
        }
        return width;
    }

    private void drawCompactBinds(DrawContext context, List<ServerHelper.HelperBind> binds, Font keyFont, float x, float y, float height,
                                  float itemSize, float itemScale, float fontGap, float cellGap, float sidePadding, float textOffsetY) {
        MatrixStack matrices = context.getMatrices();
        float offsetX = x + sidePadding;
        float itemY = y + (height - itemSize) * 0.5f;
        float textY = y + textOffsetY;

        for (int i = 0; i < binds.size(); i++) {
            ServerHelper.HelperBind bind = binds.get(i);
            String keyName = KeyBoardUtils.getBindName(bind.bind().getKey());
            drawItemIcon(context, new ItemStack(bind.item()), offsetX, itemY, itemScale);
            keyFont.draw(matrices, keyName, offsetX + itemSize + fontGap, textY, ColorUtils.rgba(255, 255, 255, 240));

            offsetX += itemSize + fontGap + keyFont.getWidth(keyName);
            if (i < binds.size() - 1) offsetX += cellGap;
        }
    }

    private void drawItemIcon(DrawContext context, ItemStack stack, float x, float y, float scale) {
        MatrixStack matrices = context.getMatrices();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        matrices.push();
        matrices.translate(x, y, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        context.drawItem(stack, 0, 0);
        matrices.pop();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
    }

    private void drawDefaultPanel(MatrixStack matrices, float x, float y, float width, float height, int colorTheme) {
        RenderUtils.drawDefaultHudThemedPanel(matrices, x, y, width, height, 4, 4, colorTheme);
        if (isUnusualRectType()) {
            RenderUtils.drawHudSquarePattern(matrices, x, y, width, height, colorTheme);
        }
    }

    private int getThemeColor() {
        if (!Snill.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return Snill.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }
}