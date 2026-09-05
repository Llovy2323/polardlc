package snill.client.client.ui.clickgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import snill.client.Snill;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.input.KeyBoardUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.Setting;
import snill.client.client.modules.settings.implement.BindSetting;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ListSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.client.modules.settings.implement.TextSetting;

import java.util.List;

public class ClickGuiSettingRenderer {
    private static final float HOVER_SCROLL_OVERFLOW_THRESHOLD = 6.0f;
    private static final float OPTION_H = 11f;
    private static final float TAG_GAP = 2f;
    private static final float OPTION_RADIUS = 0.5f;

    public void render(DrawContext context, Module module, float panelX, float moduleY, float openProgress, int colorTheme, double mouseX, double mouseY, ClickGuiState state) {
        List<Setting> settings = module.getSettings();
        if (settings == null || settings.isEmpty() || openProgress <= 0.01f) return;

        float maxSettingHeight = ClickGuiLayout.calculateSettingsHeight(module);
        float settingsClipY = moduleY + ClickGuiLayout.SETTING_START_Y;
        float settingsClipHeight = maxSettingHeight * openProgress;

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(panelX + ClickGuiLayout.MODULE_PADDING, settingsClipY - 3, ClickGuiLayout.MODULE_INNER_WIDTH, settingsClipHeight);

        float settingYoffset = ClickGuiLayout.SETTING_START_Y;
        for (Setting setting : settings) {
            if (setting == null || !setting.visible()) continue;

            float settingY = moduleY + settingYoffset + ClickGuiLayout.SETTING_PADDING;
            int alpha = (int) (255 * openProgress);

            if (setting instanceof BooleanSetting booleanSetting) {
                renderBooleanSetting(context, panelX, settingY, alpha, colorTheme, mouseX, mouseY, booleanSetting, state);
                settingYoffset += 12f;
            } else if (setting instanceof TextSetting textSetting) {
                renderTextSetting(context, panelX, settingY, alpha, colorTheme, mouseX, mouseY, textSetting, state);
                settingYoffset += 22f;
            } else if (setting instanceof FloatSetting floatSetting) {
                renderFloatSetting(context, panelX, settingY, alpha, colorTheme, mouseX, mouseY, floatSetting, state);
                settingYoffset += 22f;
            } else if (setting instanceof ModeSetting modeSetting) {
                renderModeSetting(context, panelX, settingY, alpha, colorTheme, mouseX, mouseY, modeSetting, state);
                settingYoffset += ClickGuiLayout.calculateModeSettingHeight(modeSetting);
            } else if (setting instanceof ListSetting listSetting) {
                renderListSetting(context, panelX, settingY, alpha, colorTheme, mouseX, mouseY, listSetting, state);
                settingYoffset += ClickGuiLayout.calculateListSettingHeight(listSetting);
            } else if (setting instanceof BindSetting bindSetting) {
                renderBindSetting(context, panelX, settingY, alpha, colorTheme, mouseX, mouseY, bindSetting, state);
                settingYoffset += 12f;
            }
        }

        ScissorUtils.pop();
    }

    private void renderBooleanSetting(DrawContext context, float panelX, float settingY, int alpha, int colorTheme, double mouseX, double mouseY, BooleanSetting booleanSetting, ClickGuiState state) {
        AnimationUtils backgroundAnimation = state.getBooleanBackgroundAnimation(booleanSetting);
        AnimationUtils circleAnimation = state.getBooleanCircleAnimation(booleanSetting);
        backgroundAnimation.update(booleanSetting.isState() ? 1f : 0f);
        circleAnimation.update(booleanSetting.isState() ? 1f : 0f);

        float backgroundProgress = backgroundAnimation.getValue();
        float circleProgress = circleAnimation.getValue();

        int offColor = ColorUtils.darken(colorTheme, 0.05f);
        int onColor = colorTheme;

        int r = (int) ((offColor >> 16 & 255) + ((onColor >> 16 & 255) - (offColor >> 16 & 255)) * backgroundProgress);
        int g = (int) ((offColor >> 8 & 255) + ((onColor >> 8 & 255) - (offColor >> 8 & 255)) * backgroundProgress);
        int b = (int) ((offColor & 255) + ((onColor & 255) - (offColor & 255)) * backgroundProgress);
        int a = (int) ((offColor >> 24 & 255) + ((onColor >> 24 & 255) - (offColor >> 24 & 255)) * backgroundProgress);
        int interpolatedColor = (a << 24) | (r << 16) | (g << 8) | b;

        String translatedName = translate(booleanSetting.name());
        drawStringWithHoverScroll(issue(13), context.getMatrices(), translatedName, panelX + ClickGuiLayout.SETTING_LEFT, settingY - 3, (panelX + 73f) - (panelX + ClickGuiLayout.SETTING_LEFT), getPrimarySettingColor(alpha), mouseX, mouseY, state, getSettingTextKey(booleanSetting));

        RenderUtils.drawRoundedRect(context.getMatrices(), panelX + 75, settingY - 2 - 3, 16, 9, 3f, ColorUtils.rgba((interpolatedColor >> 16) & 255, (interpolatedColor >> 8) & 255, interpolatedColor & 255, alpha));

        float circleX = panelX + 79.5f + (circleProgress * 6.2f);
        RenderUtils.drawRoundCircle(context.getMatrices(), circleX + 0.5f, settingY + 2.5f - 3, 7, ColorUtils.rgba(255, 255, 255, alpha));
    }

    private void renderFloatSetting(DrawContext context, float panelX, float settingY, int alpha, int colorTheme, double mouseX, double mouseY, FloatSetting floatSetting, ClickGuiState state) {
        if (floatSetting.isActive()) {
            floatSetting.setValue(state.updateActiveSliderValue(floatSetting, mouseX));
        }

        AnimationUtils sliderAnimation = state.getSliderAnimation(floatSetting);
        sliderAnimation.update(state.getSliderPos(floatSetting));
        float animatedPos = sliderAnimation.getValue();

        String valueString = formatSliderValue(floatSetting);
        float valueX = panelX + ClickGuiLayout.SETTING_RIGHT - issue(12).getWidth(valueString);
        float nameMaxWidth = (valueX - 4f) - (panelX + ClickGuiLayout.SETTING_LEFT);

        String translatedName = translate(floatSetting.name());
        drawStringWithHoverScroll(issue(12), context.getMatrices(), translatedName, panelX + ClickGuiLayout.SETTING_LEFT, settingY - 3 + 1, nameMaxWidth, getPrimarySettingColor(alpha), mouseX, mouseY, state, getSettingTextKey(floatSetting));
        issue(12).drawString(context.getMatrices(), valueString, valueX, settingY - 3 + 1, ColorUtils.setAlphaColor(colorTheme, alpha));

        int sliderBackgroundColor = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.2f), alpha);
        RenderUtils.drawRoundedRect(context.getMatrices(), panelX + ClickGuiLayout.SETTING_LEFT, settingY - 3 + 9, ClickGuiLayout.SLIDER_WIDTH, 4.5f, 1.25f, sliderBackgroundColor);

        int sliderFillColor = ColorUtils.setAlphaColor(colorTheme, alpha);
        RenderUtils.drawRoundedRect(context.getMatrices(), panelX + ClickGuiLayout.SETTING_LEFT, settingY - 3 + 9, animatedPos * ClickGuiLayout.SLIDER_WIDTH, 4.5f, 1.25f, sliderFillColor);
        RenderUtils.drawRoundCircle(context.getMatrices(), panelX + ClickGuiLayout.SETTING_LEFT + animatedPos * ClickGuiLayout.SLIDER_WIDTH, settingY - 3 + 11.25f, 6, ColorUtils.setAlphaColor(-1, alpha));
    }

    private void renderTextSetting(DrawContext context, float panelX, float settingY, int alpha, int colorTheme, double mouseX, double mouseY, TextSetting textSetting, ClickGuiState state) {
        String value = textSetting.get();
        boolean editing = state.getEditingTextSetting() == textSetting;
        String preview = value == null || value.isEmpty() ? "..." : value;
        String boxText = editing ? preview + "_" : preview;
        float boxWidth = ClickGuiLayout.TEXT_SETTING_WIDTH;
        float boxX = panelX + 49f;

        String translatedName = translate(textSetting.name());
        drawStringWithHoverScroll(issue(13), context.getMatrices(), translatedName, panelX + ClickGuiLayout.SETTING_LEFT, settingY - 3, (boxX - 1f) - (panelX + ClickGuiLayout.SETTING_LEFT), getPrimarySettingColor(alpha), mouseX, mouseY, state, getSettingTextKey(textSetting));

        int textColor = ColorUtils.setAlphaColor(-1, alpha);
        float boxY = settingY - 2.5f;
        RenderUtils.drawRoundedRect(context.getMatrices(), boxX, boxY - 3, boxWidth, 9f, 1.5f, ColorUtils.rgba(0,0,0,150));
        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(boxX + 2f, boxY + 1f - 3, boxWidth - 4f, 7f);
        drawStringWithHoverScroll(issue(12), context.getMatrices(), boxText, boxX + 3f, settingY + 1f - 3.5f, boxWidth - 6f, textColor, mouseX, mouseY, state, getSettingTextKey(textSetting) + "_value");
        ScissorUtils.pop();
    }

    private void renderModeSetting(DrawContext context, float panelX, float settingY, int alpha, int colorTheme, double mouseX, double mouseY, ModeSetting modeSetting, ClickGuiState state) {
        String translatedName = translate(modeSetting.name());
        drawStringWithHoverScroll(issue(12), context.getMatrices(), translatedName, panelX + ClickGuiLayout.SETTING_LEFT, settingY + 1 - 3, ClickGuiLayout.SETTING_RIGHT - ClickGuiLayout.SETTING_LEFT, getPrimarySettingColor(alpha), mouseX, mouseY, state, getSettingTextKey(modeSetting));

        Font chipFont = issue(12);
        float chipX = panelX + ClickGuiLayout.SETTING_LEFT - 1.5f;
        float chipY = settingY + ClickGuiLayout.TAG_START_Y - 0.5f;
        float availableWidth = ClickGuiLayout.getTagAvailableWidth();
        float offsetX = 0.0f;
        float offsetY = 0.0f;

        for (String mode : modeSetting.getMods()) {
            String translatedMode = translate(mode);
            float chipWidth = chipFont.getWidth(translatedMode) + 8f;

            if (offsetX > 0.0f && offsetX + chipWidth > availableWidth) {
                offsetX = 0.0f;
                offsetY += OPTION_H + TAG_GAP;
            }

            boolean selected = modeSetting.getCurrent().equals(mode);
            AnimationUtils animation = state.getModeAnimation(getModeKey(modeSetting, mode), selected);
            animation.update(selected ? 1f : 0f);
            float progress = animation.getValue();

            int bgColor = ColorUtils.setAlphaColor(
                    ColorUtils.interpolateColor(
                            ColorUtils.rgba(255, 255, 255, 255),
                            ColorUtils.rgba(255, 255, 255, 255),
                            progress
                    ),
                    alpha
            );
            int textColor = ColorUtils.setAlphaColor(
                    ColorUtils.interpolateColor(
                            ColorUtils.rgba(140, 139, 145, 255),
                            ColorUtils.rgba(255, 255, 255, 255),
                            progress
                    ),
                    alpha
            );

            float currentX = chipX + offsetX;
            float currentY = chipY + offsetY;

            RenderUtils.drawRoundedRect(context.getMatrices(), currentX, currentY - 5, chipWidth, OPTION_H, OPTION_RADIUS, ColorUtils.rgba(0, 0, 0, 150));
            chipFont.drawString(context.getMatrices(), translatedMode, currentX + (chipWidth / 2.0f) - (chipFont.getWidth(translatedMode) / 2.0f) + 0.5f, currentY + (OPTION_H / 2.0f) - (chipFont.getHeight() / 2.0f) + 1.0f + 4 - 5, textColor);

            offsetX += chipWidth + TAG_GAP;
        }
    }

    private void renderListSetting(DrawContext context, float panelX, float settingY, int alpha, int colorTheme, double mouseX, double mouseY, ListSetting listSetting, ClickGuiState state) {
        int enabledCount = 0;
        for (BooleanSetting entry : listSetting.getSettings()) {
            if (entry.isState()) enabledCount++;
        }

        String counter = enabledCount + "/" + listSetting.getSettings().size();
        float counterX = panelX + ClickGuiLayout.SETTING_RIGHT - issue(12).getWidth(counter);
        String translatedName = translate(listSetting.name());
        drawStringWithHoverScroll(issue(12), context.getMatrices(), translatedName, panelX + ClickGuiLayout.SETTING_LEFT, settingY + 1 - 3, (counterX - 4.0f) - (panelX + ClickGuiLayout.SETTING_LEFT), getPrimarySettingColor(alpha), mouseX, mouseY, state, getSettingTextKey(listSetting));
        issue(12).draw(context.getMatrices(), counter, counterX, settingY + 1 - 3, getPrimarySettingColor(alpha));

        Font chipFont = issue(12);
        float chipX = panelX + ClickGuiLayout.SETTING_LEFT - 1.5f;
        float chipY = settingY + ClickGuiLayout.TAG_START_Y - 0.5f;
        float availableWidth = ClickGuiLayout.getTagAvailableWidth();
        float offsetX = 0.0f;
        float offsetY = 0.0f;

        for (BooleanSetting entry : listSetting.getSettings()) {
            if (!entry.visible()) continue;

            String translatedEntry = translate(entry.name());
            float chipWidth = chipFont.getWidth(translatedEntry) + 8f;

            if (offsetX > 0.0f && offsetX + chipWidth > availableWidth) {
                offsetX = 0.0f;
                offsetY += OPTION_H + TAG_GAP;
            }

            boolean selected = entry.isState();
            AnimationUtils animation = state.getListAnimation(getListKey(listSetting, entry), selected);
            animation.update(selected ? 1f : 0f);
            float progress = animation.getValue();

            int bgColor = ColorUtils.setAlphaColor(
                    ColorUtils.interpolateColor(
                            ColorUtils.rgba(255, 255, 255, 255),
                            ColorUtils.rgba(255, 255, 255, 255),
                            progress
                    ),
                    alpha
            );
            int textColor = ColorUtils.setAlphaColor(
                    ColorUtils.interpolateColor(
                            ColorUtils.rgba(140, 139, 145, 255),
                            ColorUtils.rgba(255, 255, 255, 255),
                            progress
                    ),
                    alpha
            );

            float currentX = chipX + offsetX;
            float currentY = chipY + offsetY;

            RenderUtils.drawRoundedRect(context.getMatrices(), currentX, currentY - 5, chipWidth, OPTION_H, OPTION_RADIUS, ColorUtils.rgba(0, 0, 0, 200));
            chipFont.drawString(context.getMatrices(), translatedEntry, currentX + (chipWidth / 2.0f) - (chipFont.getWidth(translatedEntry) / 2.0f) + 0.5f, currentY - 5 + (OPTION_H / 2.0f) - (chipFont.getHeight() / 2.0f) + 1.0f + 4, textColor);

            offsetX += chipWidth + TAG_GAP;
        }
    }

    private void renderBindSetting(DrawContext context, float panelX, float settingY, int alpha, int colorTheme, double mouseX, double mouseY, BindSetting bindSetting, ClickGuiState state) {
        boolean binding = state.getBindingSetting() == bindSetting;
        AnimationUtils bindAnimation = state.getBindAnimation(getBindKey(bindSetting), binding);
        bindAnimation.update(binding ? 1f : 0f);
        float progress = bindAnimation.getValue();

        String bindString = binding ? "..." : state.toEnglish(KeyBoardUtils.getBindName(bindSetting.getKey()));
        float bindTextWidth = issue(12).getWidth(bindString);
        float bindWidth = bindTextWidth + 6f;
        float bindX = panelX + ClickGuiLayout.SETTING_RIGHT - bindWidth;

        int bindBackgroundColor = ColorUtils.setAlphaColor(ColorUtils.interpolateColor(ColorUtils.darken(colorTheme, 0.15f), colorTheme, progress), alpha);
        int bindTextColor = ColorUtils.setAlphaColor(ColorUtils.interpolateColor(ColorUtils.rgb(140, 139, 145), -1, progress), alpha);

        RenderUtils.drawRoundedRect(context.getMatrices(), bindX, settingY - 2.5f - 3, bindWidth, 9, 1.5f, ColorUtils.rgba(0,0,0,150));
        issue(12).drawString(context.getMatrices(), bindString, bindX + 3, settingY + 1 - 3, bindTextColor);

        String translatedName = translate(bindSetting.name());
        drawStringWithHoverScroll(issue(12), context.getMatrices(), translatedName, panelX + ClickGuiLayout.SETTING_LEFT, settingY + 1 - 3, (bindX - 4f) - (panelX + ClickGuiLayout.SETTING_LEFT), getPrimarySettingColor(alpha), mouseX, mouseY, state, getSettingTextKey(bindSetting));
    }

    private String translate(String key) {
        if (Snill.INSTANCE == null || Snill.INSTANCE.localizationStorage == null) return key;
        return Snill.INSTANCE.localizationStorage.translate(key);
    }

    private String getModeKey(ModeSetting setting, String mode) {
        return System.identityHashCode(setting) + "_mode_" + mode;
    }

    private String getListKey(ListSetting setting, BooleanSetting entry) {
        return setting.hashCode() + "_list_" + entry.name();
    }

    private String getBindKey(BindSetting setting) {
        return setting.hashCode() + "_bind";
    }

    private String formatSliderValue(FloatSetting setting) {
        float value = setting.get();
        float increment = setting.getIncrement();
        if (increment >= 1f) return String.valueOf((int) value);
        if (increment >= 0.1f) return String.format("%.1f", value);
        return String.format("%.2f", value);
    }

    private void drawStringWithHoverScroll(Font font, MatrixStack matrix, String text, float x, float y, float maxWidth, int color, double mouseX, double mouseY, ClickGuiState state, String animationKey) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) return;

        float totalWidth = font.getWidth(text);
        float overflow = totalWidth - maxWidth;
        if (overflow <= HOVER_SCROLL_OVERFLOW_THRESHOLD) {
            font.draw(matrix, text, x, y, color);
            return;
        }

        boolean hovered = isTextHovered(x, y, maxWidth, font.getHeight(), mouseX, mouseY);
        float scrollPhase = state.advanceTextScrollPhase(animationKey, hovered);
        boolean scrollActive = state.isTextScrollActive(animationKey, hovered);
        AnimationUtils hoverAnimation = state.getTextHoverAnimation(animationKey, scrollActive);
        hoverAnimation.update(scrollActive ? 1f : 0f);
        float hoverProgress = hoverAnimation.getValue();
        float scrollOffset = getHoverScrollOffset(overflow, scrollPhase) * hoverProgress;

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x, y - 2.0f, maxWidth, font.getHeight() + 4.0f);
        font.draw(matrix, text, x - scrollOffset, y, color);
        ScissorUtils.pop();
    }

    private int getPrimarySettingColor(int alpha) {
        return ColorUtils.rgba(245, 245, 248, alpha);
    }

    private boolean isTextHovered(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y - 2.0f && mouseY <= y + height + 2.0f;
    }

    private float getHoverScrollOffset(float maxOffset, float phase) {
        if (maxOffset <= 0.0f) return 0.0f;
        float pingPong = phase < 0.5f ? (phase * 2.0f) : (2.0f - phase * 2.0f);
        float eased = pingPong * pingPong * (3.0f - 2.0f * pingPong);
        return maxOffset * eased;
    }

    private String getSettingTextKey(Setting setting) {
        return "setting_text_" + System.identityHashCode(setting);
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }
}
