package snill.client.client.ui.clickgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import static snill.client.Snill.INSTANCE;
import snill.client.api.storages.implement.ThemeStorage;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.client.modules.Module;

import java.util.List;

public class ClickGuiThemeSelector {
    public void render(DrawContext context, Window window, float stateX, float stateY, float offsetY, float alphaMul, int shadeColor) {
        if (context == null) {
            return;
        }

        List<ThemeStorage.Themes> themes = INSTANCE.themeStorage.getThemeList();
        if (themes == null || themes.isEmpty()) {
            return;
        }

        float totalWidth = themes.size() * ClickGuiLayout.THEME_BOX_SIZE + (themes.size() - 1) * ClickGuiLayout.THEME_BOX_GAP;
        float panelWidth = totalWidth + ClickGuiLayout.THEME_SIDE_PADDING * 2f;
        float panelX = getThemePanelX(stateX, panelWidth);
        float panelY = stateY + offsetY - ClickGuiLayout.THEME_PANEL_H - 8f;
        float startX = panelX + ClickGuiLayout.THEME_SIDE_PADDING;
        float startY = panelY + (ClickGuiLayout.THEME_PANEL_H - ClickGuiLayout.THEME_BOX_SIZE) / 2f;

        drawBlurPanel(context, panelX, panelY, panelWidth, ClickGuiLayout.THEME_PANEL_H, 3.5f);
        ThemeStorage.Themes selected = INSTANCE.themeStorage.getThemes();
        for (int i = 0; i < themes.size(); i++) {
            ThemeStorage.Themes theme = themes.get(i);
            float boxX = startX + i * (ClickGuiLayout.THEME_BOX_SIZE + ClickGuiLayout.THEME_BOX_GAP);
            float boxY = startY;
            if (theme == selected) {
                RenderUtils.drawRoundedRect(
                        context.getMatrices(),
                        boxX - 0.5f,
                        boxY - 0.5f,
                        ClickGuiLayout.THEME_BOX_SIZE + 1,
                        ClickGuiLayout.THEME_BOX_SIZE + 1,
                        ClickGuiLayout.THEME_BOX_RADIUS + 0.5f,
                        ColorUtils.setAlphaColor(-1, Math.max(1, (int) (200 * alphaMul)))
                );
            }
            RenderUtils.drawRoundedRect(
                    context.getMatrices(),
                    boxX,
                    boxY,
                    ClickGuiLayout.THEME_BOX_SIZE,
                    ClickGuiLayout.THEME_BOX_SIZE,
                    ClickGuiLayout.THEME_BOX_RADIUS,
                    ColorUtils.applyAlpha(getThemeDisplayColor(theme), Math.max(0.55f, alphaMul))
            );
        }
    }

    public void render(DrawContext context, Window window, float offsetY, float alphaMul, int shadeColor) {
        float centerX = window != null ? window.getScaledWidth() / 2f : 200f;
        float stateX = centerX - (ClickGuiLayout.getTotalCategoriesWidth(Module.ModuleCategory.values().length) / 2f);
        float stateY = window != null ? (window.getScaledHeight() / 2f) - (ClickGuiLayout.HEIGHT / 2f) : 100f;
        render(context, window, stateX, stateY, offsetY, alphaMul, shadeColor);
    }

    public boolean handleClick(Window window, double mouseX, double mouseY, int button, float stateX, float stateY, float offsetY) {
        if (button != 0) {
            return false;
        }

        List<ThemeStorage.Themes> themes = INSTANCE.themeStorage.getThemeList();
        if (themes == null || themes.isEmpty()) {
            return false;
        }

        float totalWidth = themes.size() * ClickGuiLayout.THEME_BOX_SIZE + (themes.size() - 1) * ClickGuiLayout.THEME_BOX_GAP;
        float panelWidth = totalWidth + ClickGuiLayout.THEME_SIDE_PADDING * 2f;
        float panelX = getThemePanelX(stateX, panelWidth);
        float panelY = stateY + offsetY - ClickGuiLayout.THEME_PANEL_H - 8f;
        float startX = panelX + ClickGuiLayout.THEME_SIDE_PADDING;
        float startY = panelY + (ClickGuiLayout.THEME_PANEL_H - ClickGuiLayout.THEME_BOX_SIZE) / 2f;

        if (!HoveringUtils.isHovered(mouseX, mouseY, panelX, panelY, panelWidth, ClickGuiLayout.THEME_PANEL_H)) {
            return false;
        }

        for (int i = 0; i < themes.size(); i++) {
            float boxX = startX + i * (ClickGuiLayout.THEME_BOX_SIZE + ClickGuiLayout.THEME_BOX_GAP);
            float boxY = startY;
            if (HoveringUtils.isHovered(mouseX, mouseY, boxX, boxY, ClickGuiLayout.THEME_BOX_SIZE, ClickGuiLayout.THEME_BOX_SIZE)) {
                INSTANCE.themeStorage.setThemes(themes.get(i));
                return true;
            }
        }
        return false;
    }

    public boolean handleClick(Window window, double mouseX, double mouseY, int button, float offsetY) {
        float centerX = window != null ? window.getScaledWidth() / 2f : 200f;
        float stateX = centerX - (ClickGuiLayout.getTotalCategoriesWidth(Module.ModuleCategory.values().length) / 2f);
        float stateY = window != null ? (window.getScaledHeight() / 2f) - (ClickGuiLayout.HEIGHT / 2f) : 100f;
        return handleClick(window, mouseX, mouseY, button, stateX, stateY, offsetY);
    }

    private int getThemeDisplayColor(ThemeStorage.Themes theme) {
        int color = theme.getTheme().getColor(0);
        if (ColorUtils.alpha(color) == 0) {
            return ColorUtils.rgba(220, 220, 220, 180);
        }
        return color;
    }

    private float getThemePanelX(float stateX, float panelWidth) {
        float totalCatW = ClickGuiLayout.getTotalCategoriesWidth(Module.ModuleCategory.values().length);
        return stateX + (totalCatW / 2F) - (panelWidth / 2F);
    }

    private void drawBlurPanel(DrawContext context, float x, float y, float width, float height, float radius) {
        RenderUtils.drawBlur(context.getMatrices(), x, y, width, height, radius, 5f, ColorUtils.rgba(255, 255, 255, 255));
        RenderUtils.drawBlur(context.getMatrices(), x, y, width, height, radius, 5f, ColorUtils.rgba(0, 0, 0, 180));
        RenderUtils.drawRoundedRect(context.getMatrices(), x, y, width, height, radius, ColorUtils.rgba(20, 20, 20, 100));
    }
}
