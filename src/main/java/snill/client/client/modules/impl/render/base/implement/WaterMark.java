package snill.client.client.modules.impl.render.base.implement;

import static snill.client.Snill.INSTANCE;
import net.minecraft.client.util.math.MatrixStack;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.render.fonts.ttf.MCFontRenderer;
import snill.client.api.utils.rpc.DiscordProfileCache;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.awt.Color;

public class WaterMark extends InterfaceProcessing {

    private static final String LOGO_GLYPH = "A";
    private static final int BG_COLOR = new Color(20, 20, 20, 100).getRGB();
    private static final int WHITE_COLOR = new Color(255, 255, 255, 255).getRGB();

    private static final float BAR_H = 17f;
    private static final float BAR_RADIUS = 5.0f;
    private static final float H_PAD = 4f;
    private static final float H_PAD_RIGHT = 3f;
    private static final float ELEMENT_GAP = 1f;
    private static final float PILL_GAP = 1f;

    private boolean showFps = true;
    private boolean showMs = true;
    private boolean showServer = true;

    public static String getUsername() { return "???"; }
    public static String getUID() { return "1"; }

    public WaterMark(Draggable draggable) { super(draggable); }

    public boolean isShowFps() { return showFps; }
    public void setShowFps(boolean v) { this.showFps = v; }
    public boolean isShowMs() { return showMs; }
    public void setShowMs(boolean v) { this.showMs = v; }
    public boolean isShowServer() { return showServer; }
    public void setShowServer(boolean v) { this.showServer = v; }

    private MCFontRenderer myfont(int size) {
        return snill.client.api.utils.render.fonts.ttf.Fonts.getFont("myfont.ttf", size);
    }

    private MCFontRenderer logoFont(int size) {
        return snill.client.api.utils.render.fonts.ttf.Fonts.getFont("logo.ttf", size);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        renderStyle(eventRender);
        super.onRender(eventRender);
    }

    private void renderStyle(EventRender.Default event) {
        var matrices = event.getContext().getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();

        var textFont = Fonts.getFont("suisse", 13);
        var iconNew14 = Fonts.getFont("iconnew", 14);
        var myFont14 = myfont(14);
        var logoFont = logoFont(21);

        String brandText = "SNILL";

        String username = DiscordProfileCache.getUsername();
        if (username == null || username.isEmpty()) {
            username = getDisplayUsername();
        }

        int fps = mc != null ? mc.getCurrentFps() : 0;
        String fpsText = fps + "fps";

        int ping = 0;
        if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        String pingText = ping + "ms";

        float logoSize = 10f;
        float logoWidth = logoFont.getStringWidth(LOGO_GLYPH);
        float brandPillW = H_PAD * 2f + logoWidth + 4f + textFont.getStringWidth(brandText);

        float mainPillW = H_PAD + H_PAD_RIGHT;
        boolean firstMain = true;

        boolean showDiscordAvatar = DiscordProfileCache.hasAvatar();
        float avatarSize = 10f;

        if (showServer && !username.isEmpty()) {
            float iconW = showDiscordAvatar ? avatarSize + 2f : iconNew14.getStringWidth("e") + 2f;
            mainPillW += (firstMain ? 0f : ELEMENT_GAP) + iconW + textFont.getStringWidth(username);
            firstMain = false;
        }
        if (showFps) {
            float iw = myFont14.getStringWidth("f") + 2f;
            mainPillW += (firstMain ? 0f : ELEMENT_GAP) + iw + textFont.getStringWidth(fpsText);
            firstMain = false;
        }
        if (showMs) {
            float iw = iconNew14.getStringWidth("m") + 2f;
            mainPillW += (firstMain ? 0f : ELEMENT_GAP) + iw + textFont.getStringWidth(pingText);
            firstMain = false;
        }

        float totalW = brandPillW + PILL_GAP + mainPillW;
        float totalH = BAR_H;

        float textY1 = y + BAR_H / 2f - 2.0f;
        float textY2 = textY1 + +0.5f;
        int iconColor = getThemeColor();

        drawBar(matrices, x, y, brandPillW);
        float brandCx = x + H_PAD;

        logoFont.drawGradientStringHorizontal(LOGO_GLYPH, brandCx, y + (BAR_H - logoSize) / 1f - 1.0f, WHITE_COLOR, WHITE_COLOR);
        brandCx += logoWidth + 4f;

        textFont.drawString(matrices, brandText, brandCx, textY2, WHITE_COLOR);

        float x1b = x + brandPillW + PILL_GAP;
        drawBar(matrices, x1b, y, mainPillW);
        float cx = x1b + H_PAD;
        boolean drawnMain = false;

        if (showServer && !username.isEmpty()) {
            if (drawnMain) cx += ELEMENT_GAP;

            if (showDiscordAvatar) {
                RenderUtils.drawRoundedImage(matrices, DiscordProfileCache.getAvatarTexture(), cx, y + (BAR_H - avatarSize) / 2.2f - 0.0f + 0.2f, avatarSize, avatarSize * 0.5f, 1.0f);
                cx += avatarSize + 2f;
            } else {
                float iw = iconNew14.getStringWidth("e");
                iconNew14.drawGradientStringHorizontal(matrices, "e", cx, textY2 + 0.5f, iconColor, iconColor);
                cx += iw + 2f;
            }

            textFont.drawString(matrices, username, cx, textY2, WHITE_COLOR);
            cx += textFont.getStringWidth(username);
            drawnMain = true;
        }

        if (showFps) {
            if (drawnMain) cx += ELEMENT_GAP;
            float iw = myFont14.getStringWidth("f");
            myFont14.drawGradientStringHorizontal("f", cx, textY2 + 0.5f, iconColor, iconColor);
            cx += iw + 2f;

            textFont.drawString(matrices, fpsText, cx, textY2, WHITE_COLOR);
            cx += textFont.getStringWidth(fpsText);
            drawnMain = true;
        }

        if (showMs) {
            if (drawnMain) cx += ELEMENT_GAP;
            float iw = iconNew14.getStringWidth("m");
            iconNew14.drawGradientStringHorizontal(matrices, "m", cx, textY2 + 0.5f, iconColor, iconColor);
            cx += iw + 2f;

            textFont.drawString(matrices, pingText, cx, textY2, WHITE_COLOR);
            cx += textFont.getStringWidth(pingText);
            drawnMain = true;
        }

        draggable.setWidth(totalW);
        draggable.setHeight(totalH);
    }

    private void drawBar(MatrixStack matrices, float x, float y, float w) {
        RenderUtils.drawBlur(matrices, x, y, w, BAR_H, BAR_RADIUS, 5f, ColorUtils.rgba(255, 255, 255, 255));
        RenderUtils.drawBlur(matrices, x, y, w, BAR_H, BAR_RADIUS, 5f, ColorUtils.rgba(0, 0, 0, 180));
        RenderUtils.drawRoundedRect(matrices, x, y, w, BAR_H, BAR_RADIUS, BG_COLOR);
    }

    private int getThemeColor() {
        if (INSTANCE != null && INSTANCE.themeStorage != null) {
            var theme = INSTANCE.themeStorage.getThemes().getTheme();
            if (!theme.getName().equals("Rainbow")) {
                return theme.color[0];
            }
        }
        return ColorUtils.getThemeColor();
    }

    private String getDisplayUsername() {
        if (mc != null && mc.player != null) return mc.player.getName().getString();
        return getUsername();
    }
}
