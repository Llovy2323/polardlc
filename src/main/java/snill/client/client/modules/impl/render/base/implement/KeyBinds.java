package snill.client.client.modules.impl.render.base.implement;

import static snill.client.Snill.INSTANCE;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.input.KeyBoardUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.render.fonts.ttf.MCFontRenderer;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.HashMap;
import java.util.Map;
public class KeyBinds extends InterfaceProcessing {
    private static final float BASE_MIN_WIDTH   = 64f;
    private static final float EXTRA_WIDTH      = 0f;
    private static final float ROW_RIGHT_MARGIN = 25f;
    private static final float ROW_HEIGHT       = 11f;
    private static final float HEADER_HEIGHT    = 15f;
    private static final float HEADER_GAP       = 0.2f;
    private static final float CONTENT_PAD_TOP  = 2.5f;
    private static final float CONTENT_PAD_BOTTOM = 0.8f;

    private final Map<Module, AnimationUtils> animations = new HashMap<>();
    private final AnimationUtils widthAnimation  = new AnimationUtils(60, 10.5f, Easings.QUAD_OUT);
    private final AnimationUtils heightAnimation = new AnimationUtils(16, 10.5f, Easings.QUAD_OUT);

    private static final Map<Character, Character> RU_TO_EN = new HashMap<>();
    static {
        String ru = "йцукенгшщзхъфывапролджэячсмитьбюЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ";
        String en = "qwertyuiop[]asdfghjkl;'zxcvbnm,.QWERTYUIOP[]ASDFGHJKL;'ZXCVBNM,.";
        for (int i = 0; i < ru.length(); i++) {
            RU_TO_EN.put(ru.charAt(i), en.charAt(i));
        }
    }
    private Font issue(int size) { return Fonts.getFont("suisse", size); }
    private Font icons(int size) { return Fonts.getFont("clickgui", size); }
    private MCFontRenderer divine(int size) { return snill.client.api.utils.render.fonts.ttf.Fonts.getFont("divine.ttf", size); }

    public KeyBinds(Draggable draggable) {
        super(draggable);
    }

    private AnimationUtils getAnimation(Module module) {
        return animations.computeIfAbsent(module, m -> new AnimationUtils(0, 10.5f, Easings.QUAD_OUT));
    }

    private String toEnglish(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(RU_TO_EN.getOrDefault(c, c));
        }
        return result.toString();
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        DefaultStyle(eventRender);
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        float baseX = draggable.getX(), y = draggable.getY();
        int colorTheme = getStableThemeColor();

        float targetWidth = BASE_MIN_WIDTH;
        int enabledCount = 0;

        for (Module module : snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1) {
                getAnimation(module).update(module.isEnable() ? 1 : 0);
            }
        }


        boolean hasVisibleModules = false;
        for (Module module : snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1 && getAnimation(module).getValue() > 0.01f) {
                hasVisibleModules = true;
                break;
            }
        }


        if (!hasVisibleModules) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        for (Module module : snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1 && module.isEnable()) {
                enabledCount++;
                String keyName = toEnglish(KeyBoardUtils.getKeyName(module.getKey()));
                Font iconFont = icons(11);
                float iconWidth = iconFont != null ? iconFont.getWidth(module.getCategory().getIcons()) : 0f;
                float moduleWidth = iconWidth + 4f + issue(12).getWidth(module.getDisplayName())
                        + issue(10).getWidth(keyName) + ROW_RIGHT_MARGIN;
                if (moduleWidth > targetWidth) targetWidth = moduleWidth;
            }
        }

        float targetHeight = HEADER_HEIGHT + HEADER_GAP + CONTENT_PAD_TOP + enabledCount * ROW_HEIGHT + CONTENT_PAD_BOTTOM;

        widthAnimation.update(targetWidth);
        heightAnimation.update(targetHeight);

        float width  = widthAnimation.getValue() + EXTRA_WIDTH;
        float height = heightAnimation.getValue();
        float rightEdge = baseX + width;
        float x = baseX;

        RenderUtils.drawDefaultHudElementRects(eventRender.getContext().getMatrices(), x, y, width, height, colorTheme, isUnusualRectType());
        issue(14).draw(eventRender.getContext().getMatrices(), "Keybinds", x + 5.2f, y + 6f, -1);
        MCFontRenderer divineIcon = divine(14);
        if (divineIcon != null) {
            divineIcon.drawString("l", rightEdge - 12f, y + 6f, colorTheme);
        } else {
            icons(14).draw(eventRender.getContext().getMatrices(), "l", rightEdge - 12f, y + 3.6f, colorTheme);
        }

        float offsetY = HEADER_HEIGHT + HEADER_GAP + CONTENT_PAD_TOP;
        for (Module module : snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1) {
                AnimationUtils anim = getAnimation(module);
                float animValue = anim.getValue();
                if (animValue <= 0.01f) continue;

                ScissorUtils.push();
                ScissorUtils.setFromComponentCoordinates(x, y, width, height);

                String keyName = toEnglish(KeyBoardUtils.getBindName(module.getKey()));
                float keyBoxWidth = Math.max(issue(10).getStringWidth(keyName) + 4, 9f);

                int alpha = (int) (255 * animValue);
                int textColor = ColorUtils.rgba(255, 255, 255, alpha);
                Font iconFont = icons(13);

                issue(13).draw(eventRender.getContext().getMatrices(), module.getDisplayName(), x + 5.2f, y + offsetY + 1f, textColor);
                float keyBoxX = rightEdge - keyBoxWidth - 5;
                if (iconFont != null) {
                    String categoryIcon = module.getCategory().getIcons();
                    float iconX = keyBoxX - iconFont.getWidth(categoryIcon) - 2f;
                    float boxX = iconX - 1f;
                    float boxW = (keyBoxX + keyBoxWidth) - boxX + 1f;
                    RenderUtils.drawBlur(eventRender.getContext().getMatrices(), boxX - 0.25f, y + offsetY - 2.4f, boxW + 0.5f, 9.5f, 1.5f, 5f, ColorUtils.rgba(255, 255, 255, 255));
                    RenderUtils.drawBlur(eventRender.getContext().getMatrices(), boxX - 0.25f, y + offsetY - 2.4f, boxW + 0.5f, 9.5f, 1.5f, 5f, ColorUtils.rgba(0, 0, 0, 180));
                    iconFont.draw(eventRender.getContext().getMatrices(), categoryIcon, iconX, y + offsetY + 0.8f, colorTheme);
                }
                issue(12).drawCenteredString(eventRender.getContext().getMatrices(), keyName, keyBoxX + keyBoxWidth / 2, y + offsetY + 1.5f, colorTheme);

                offsetY += ROW_HEIGHT * animValue;
                ScissorUtils.pop();
                ScissorUtils.unset();
            }
        }

        draggable.setWidth(width);
        draggable.setHeight(height);
    }

    private int getStableThemeColor() {
        if (!INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }
}
