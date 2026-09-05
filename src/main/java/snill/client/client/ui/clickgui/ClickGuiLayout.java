package snill.client.client.ui.clickgui;

import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.Setting;
import snill.client.client.modules.settings.implement.BindSetting;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ListSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.client.modules.settings.implement.TextSetting;

import java.util.List;

public final class ClickGuiLayout {
    public static final float WIDTH = 100f;
    public static final float HEIGHT = 205f;
    public static final float CATEGORY_PANEL_STEP = 108f;
    public static final float PANEL_RADIUS = 8f;
    public static final float PANEL_HEADER_HEIGHT = 24f;
    public static final float MODULE_RADIUS = 4f;
    public static final float CONTROL_RADIUS = 3f;

    public static final float THEME_PANEL_H = 15f;
    public static final float THEME_BOX_SIZE = 8f;
    public static final float THEME_BOX_GAP = 4f;
    public static final float THEME_BOX_RADIUS = 2f;
    public static final float THEME_SIDE_PADDING = 4f;

    public static final float MODULE_PADDING = 3f;
    public static final float MODULE_GAP = 2f;
    public static final float MODULE_HEADER_HEIGHT = 17f;
    public static final float MODULE_INNER_WIDTH = 93.5f;
    public static final float SETTING_START_Y = 20f;
    public static final float SETTING_PADDING = 4f;
    public static final float SETTING_BOTTOM_PADDING = 3f;
    public static final float SETTING_LEFT = 10f;
    public static final float SETTING_RIGHT = 89f;
    public static final float SLIDER_WIDTH = 79f;
    public static final float TEXT_SETTING_WIDTH = 42f;
    public static final float CLICKABLE_WIDTH = 79f;
    public static final float TAG_START_Y = 10f;
    public static final float TAG_GAP = 3f;
    public static final float TAG_ROW_GAP = 2f;
    public static final int SEARCH_MAX_CHARS = 24;
    public static final float SEARCH_WIDTH = 75f;
    public static final float SEARCH_HEIGHT = 18f;
    public static final float SEARCH_GAP = 8f;
    public static final float SEARCH_ICON_X = 3.5f;
    public static final float SEARCH_TEXT_X = 19f;
    public static final float SEARCH_RIGHT_PADDING = 8f;

    private ClickGuiLayout() {
    }

    public static float getTotalCategoriesWidth(int categoryCount) {
        return (WIDTH * categoryCount) + (8f * (categoryCount - 1));
    }

    public static float getCategoryPanelX(float x, int index) {
        return x + (index * CATEGORY_PANEL_STEP);
    }

    public static float getContentY(float y) {
        return y + PANEL_HEADER_HEIGHT;
    }

    public static float getContentHeight() {
        return HEIGHT - PANEL_HEADER_HEIGHT - 6f;
    }

    public static float getSearchX(float x, int categoryCount) {
        return x + (getTotalCategoriesWidth(categoryCount) / 2f) - (SEARCH_WIDTH / 2f);
    }

    public static float getSearchX(float x, int categoryCount, float searchWidth) {
        return x + (getTotalCategoriesWidth(categoryCount) / 2f) - (searchWidth / 2f);
    }

    public static float getSearchY(float y) {
        return y + HEIGHT + SEARCH_GAP;
    }

    public static boolean hasVisibleSettings(List<Setting> settings) {
        for (Setting setting : settings) {
            if (setting != null && setting.visible()) {
                return true;
            }
        }
        return false;
    }

    public static float calculateModeSettingHeight(ModeSetting modeSetting) {
        return calculateWrappedChipHeight(modeSetting.getMods()) + 12f;
    }

    public static float calculateListSettingHeight(ListSetting listSetting) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (BooleanSetting entry : listSetting.getSettings()) {
            if (entry.visible()) {
                labels.add(entry.name());
            }
        }
        return calculateWrappedChipHeight(labels) + 12f;
    }

    public static float getTagAvailableWidth() {
        return SETTING_RIGHT - SETTING_LEFT + 2.0f;
    }

    public static float getTagHeight() {
        Font font = Fonts.getFont("suisse", 12);
        return (font != null ? font.getHeight() : 6.0f) + 6.0f;
    }

    public static float getTagChipHeight() {
        return getTagHeight() - 7.0f;
    }

    public static float calculateTagWidth(String text) {
        Font font = Fonts.getFont("suisse", 12);
        float textWidth = font != null ? font.getWidth(text) : (text != null ? text.length() * 6.0f : 0.0f);
        return textWidth + 5.0f;

    }

    public static float calculateTagChipWidth(String text) {
        return Math.max(24.0f, calculateTagWidth(text) + 2.0f);
    }

    private static float calculateWrappedChipHeight(java.util.List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return getTagChipHeight();
        }

        Font font = Fonts.getFont("suisse", 12);
        float chipHeight = getTagChipHeight();
        float availableWidth = getTagAvailableWidth();
        float offsetX = 0.0f;
        float totalHeight = chipHeight;

        for (String entry : entries) {
            float chipWidth = (font != null ? font.getWidth(entry) : entry.length() * 6f) + 8f;
            if (offsetX > 0.0f && offsetX + chipWidth > availableWidth) {
                offsetX = 0.0f;
                totalHeight += chipHeight + TAG_ROW_GAP;
            }
            offsetX += chipWidth + TAG_GAP;
        }

        return totalHeight;
    }

    public static float calculateSettingsHeight(Module module) {
        float height = 0f;
        List<Setting> settings = module.getSettings();
        if (settings == null || settings.isEmpty()) {
            return 0f;
        }

        boolean hasVisibleSetting = false;
        for (Setting setting : settings) {
            if (setting == null || !setting.visible()) {
                continue;
            }

            hasVisibleSetting = true;
            if (setting instanceof BooleanSetting || setting instanceof BindSetting) {
                height += 12f;
            } else if (setting instanceof TextSetting) {
                height += 22f;
            } else if (setting instanceof FloatSetting) {
                height += 22f;
            } else if (setting instanceof ModeSetting modeSetting) {
                height += calculateModeSettingHeight(modeSetting);
            } else if (setting instanceof ListSetting listSetting) {
                height += calculateListSettingHeight(listSetting);
            }
        }

        if (hasVisibleSetting) {
            height += SETTING_BOTTOM_PADDING;
        }
        return height;
    }

    public static float getModuleHeight(Module module, float openProgress) {
        return MODULE_HEADER_HEIGHT + (calculateSettingsHeight(module) * openProgress);
    }
}
