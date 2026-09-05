package snill.client.client.modules.impl.render.base.implement;

import snill.client.Snill;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.notification.NotificationManager;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Notifications extends InterfaceProcessing {
    private static final float DEFAULT_PAD_X = 7f;
    private static final float DEFAULT_ICON_TEXT_GAP = 1f;
    private static final float PREVIEW_ICON_TEXT_GAP = 2f;
    private static final float STATE_RING_SIZE = 6f;
    private static final float STATE_RING_GAP = 2f;

    private final Map<NotificationManager.Entry, AnimationUtils> appearAnimations = new HashMap<>();
    private final Map<NotificationManager.Entry, Float> currentYPositions = new HashMap<>();
    private final Set<NotificationManager.Entry> activeEntriesScratch = new HashSet<>();
    private long lastRenderTime = System.currentTimeMillis();
    private float previewAlpha = 0f;

    public Notifications(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) { return Fonts.getFont("suisse", size); }
    private Font icons(int size) { return Fonts.getFont("clickgui", size); }

    private String getEntryText(NotificationManager.Entry entry) {
        if (entry.isCustom()) return entry.customText;
        String state = entry.enabled ? "Включен!" : "Выключен!";
        return entry.moduleName + " " + state;
    }

    private float getDefaultEntryWidth(NotificationManager.Entry entry, float padX) {
        String text = getEntryText(entry);
        String iconGlyph = entry.categoryIcon != null && !entry.categoryIcon.isEmpty() ? entry.categoryIcon : "?";
        float stateRingWidth = entry.isCustom() ? 0f : STATE_RING_GAP + STATE_RING_SIZE + 1f;
        return issue(13).getWidth(text) + icons(14).getWidth(iconGlyph) + padX * 2f + DEFAULT_ICON_TEXT_GAP + stateRingWidth;
    }

    private float getPreviewWidth(String previewText, String previewIconGlyph, float padX) {
        return issue(13).getWidth(previewText) + icons(16).getWidth(previewIconGlyph) + padX * 2f + PREVIEW_ICON_TEXT_GAP;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        DefaultStyle(eventRender);
        super.onRender(eventRender);
    }

    private void DefaultStyle(EventRender.Default eventRender) {
        if (mc == null) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;

        List<NotificationManager.Entry> entries = NotificationManager.getActive();
        boolean isChatOpen = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean shouldRender = !entries.isEmpty() || isChatOpen;

        float targetPreviewAlpha = isChatOpen ? 0.7f : 0f;
        float alphaSpeed = 8f;
        previewAlpha += (targetPreviewAlpha - previewAlpha) * Math.min(1f, alphaSpeed * deltaTime);

        if (!shouldRender && previewAlpha < 0.01f) {
            appearAnimations.clear();
            currentYPositions.clear();
            previewAlpha = 0f;
            return;
        }

        float baseX = draggable.getX();
        float baseY = draggable.getY();

        int colorTheme;
        if (!Snill.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Snill.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        boolean drawSquares = isUnusualRectType();

        long now = System.currentTimeMillis();
        float height = 16f;
        float spacing = 3f;
        float lerpSpeed = 12f;
        float padX = DEFAULT_PAD_X;

        String previewText = "Кликни на меня для открытия настроек!";
        String previewIconGlyph = "A";
        float previewIconW = icons(16).getWidth(previewIconGlyph);
        float previewWidth = getPreviewWidth(previewText, previewIconGlyph, padX);

        float maxWidth = previewWidth;
        for (NotificationManager.Entry entry : entries) {
            float width = getDefaultEntryWidth(entry, padX);
            if (width > maxWidth) maxWidth = width;
        }

        float targetY = baseY;

        if (previewAlpha > 0.01f) {
            float x = baseX + (maxWidth - previewWidth) * 0.5f;
            float renderY = targetY;
            float alpha = previewAlpha;
            float scale = 0.86f + 0.14f * alpha;

            int base = ColorUtils.setAlphaColor(ColorUtils.rgba(50, 50, 50, 255), (int) (255 * alpha));
            int top = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.15F), (int) (255 * alpha));
            int bottom = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.05F), (int) (255 * alpha));

            float cx = x + previewWidth * 0.5f;
            float cy = renderY + height * 0.5f;
            var ms = eventRender.getContext().getMatrices();
            ms.push();
            ms.translate(cx, cy, 0);
            ms.scale(scale, scale, 1.0f);
            ms.translate(-cx, -cy, 0);

            RenderUtils.drawDefaultHudPanel(ms, x, renderY, previewWidth, height, 4, 4, base, top, bottom);
            float squareAlpha = alpha * alpha * (3.0f - 2.0f * alpha);
            if (drawSquares && squareAlpha > 0.08f) {
                RenderUtils.drawHudSquarePattern(ms, x, renderY, previewWidth, height, ColorUtils.setAlphaColor(colorTheme, (int) (255 * squareAlpha)));
            }

            int textColor = ColorUtils.setAlphaColor(-1, (int) (255 * alpha));
            int iconColor = ColorUtils.setAlphaColor(colorTheme, (int) (255 * alpha));
            icons(16).draw(ms, previewIconGlyph, x + padX - 3.5f, renderY + 5.2f, iconColor);
            issue(13).draw(ms, previewText, x + padX + previewIconW + 5.5f, renderY + 6.8f, textColor);

            ms.pop();
            targetY += height + spacing;
        }

        for (NotificationManager.Entry entry : entries) {
            AnimationUtils anim = appearAnimations.computeIfAbsent(entry, e -> new AnimationUtils(0f, 12f, Easings.QUAD_OUT));
            long age = now - entry.startTime;
            anim.update(1f);
            float appear = anim.getValue();
            float alpha = appear;
            if (age > NotificationManager.DURATION_MS - 200) {
                alpha = (1f - (age - (NotificationManager.DURATION_MS - 200)) / 200f) * appear;
            }
            if (alpha <= 0f) {
                targetY += height + spacing;
                continue;
            }

            Float currentY = currentYPositions.get(entry);
            if (currentY == null) currentY = targetY;

            float diff = targetY - currentY;
            if (Math.abs(diff) > 0.01f) {
                currentY = currentY + diff * Math.min(1f, lerpSpeed * deltaTime);
            } else {
                currentY = targetY;
            }
            currentYPositions.put(entry, currentY);

            String text = getEntryText(entry);
            String iconGlyph = entry.categoryIcon != null && !entry.categoryIcon.isEmpty() ? entry.categoryIcon : "?";
            float iconW = icons(14).getWidth(iconGlyph);
            float width = getDefaultEntryWidth(entry, padX);
            float x = baseX + (maxWidth - width) * 0.5f;
            float slide = 6f * (1f - appear);
            float renderY = currentY + slide;
            float scale = 0.86f + 0.14f * alpha;
            boolean disabled = !entry.isCustom() && !entry.enabled;
            int enabledGreen = ColorUtils.rgba(70, 220, 95, 255);
            int disabledRed = ColorUtils.rgba(225, 70, 70, 255);

            int base = ColorUtils.setAlphaColor(ColorUtils.rgba(50, 50, 50, 255), (int) (255 * alpha));
            int top = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.15F), (int) (255 * alpha));
            int bottom = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.05F), (int) (255 * alpha));

            float cx = x + width * 0.5f;
            float cy = renderY + height * 0.5f;
            var ms = eventRender.getContext().getMatrices();
            ms.push();
            ms.translate(cx, cy, 0);
            ms.scale(scale, scale, 1.0f);
            ms.translate(-cx, -cy, 0);

            RenderUtils.drawDefaultHudPanel(ms, x, renderY, width, height, 4, 4, base, top, bottom);
            float squareAlpha = alpha * alpha * (3.0f - 2.0f * alpha);
            if (drawSquares && squareAlpha > 0.08f) {
                RenderUtils.drawHudSquarePattern(ms, x, renderY, width, height, ColorUtils.setAlphaColor(colorTheme, (int) (255 * squareAlpha)));
            }

            int textColor = ColorUtils.setAlphaColor(-1, (int) (255 * alpha));
            int iconColor = ColorUtils.setAlphaColor(colorTheme, (int) (255 * alpha));
            icons(14).draw(ms, iconGlyph, x + padX - 1.5f, renderY + 5.8f, iconColor);
            float textX = x + padX + iconW + DEFAULT_ICON_TEXT_GAP;
            if (!entry.isCustom()) {
                String modulePart = entry.moduleName + " ";
                String statePart = text.length() > modulePart.length() ? text.substring(modulePart.length()) : "";
                int stateColor = disabled ? disabledRed : enabledGreen;
                issue(13).draw(ms, modulePart, textX + 2, renderY + 6.8f, textColor);
                float stateX = textX + issue(13).getWidth(modulePart) - 0.5f + 2;
                issue(13).draw(ms, statePart, stateX, renderY + 7f, ColorUtils.setAlphaColor(stateColor, (int) (255 * alpha)));

                float ringX = stateX + issue(13).getWidth(statePart) + STATE_RING_GAP;
                float ringY = renderY + 5.2f;
                float progress = MathHelper.clamp(1.0f - (age / (float) NotificationManager.DURATION_MS), 0.0f, 1.0f);
                int ringBg = ColorUtils.rgba(55, 55, 55, (int) (170 * alpha));
                int ringColor = ColorUtils.setAlphaColor(stateColor, (int) (255 * alpha));
                RenderUtils.drawRingArc(ms, ringX, ringY, STATE_RING_SIZE, 1.7f, -90f, 270f, ringBg);
                if (progress > 0f) {
                    RenderUtils.drawRingArc(ms, ringX, ringY, STATE_RING_SIZE, 1.7f, -90f, -90f + 360f * progress, ringColor);
                }
            } else {
                issue(13).draw(ms, text, textX, renderY + 6.8f, textColor);
            }

            ms.pop();
            targetY += height + spacing;
        }

        activeEntriesScratch.clear();
        activeEntriesScratch.addAll(entries);
        appearAnimations.keySet().removeIf(entry -> !activeEntriesScratch.contains(entry));
        currentYPositions.keySet().removeIf(entry -> !activeEntriesScratch.contains(entry));

        draggable.setWidth(maxWidth);
        draggable.setHeight(Math.max(1f, targetY - baseY));
    }

}
